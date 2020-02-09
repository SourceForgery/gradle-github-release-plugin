package com.sourceforgery.githubrelease

import com.eclipsesource.json.Json
import com.eclipsesource.json.WriterConfig
import githubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.support.zipTo
import ru.gildor.coroutines.okhttp.await
import java.io.File
import java.net.URI
import java.net.URLConnection

open class GithubReleaseTask : DefaultTask() {

    private val okHttpClient by lazy {
        OkHttpClient()
    }

    private suspend fun createRelease(): URI {
        val extension = project.githubRelease

        val url = with(extension) {
            "$baseUrl/repos/$owner/$repo/releases"
        }
        val accept = extension.acceptHeader

        val postBody: String = with(extension) {
            Json.`object`()
                .add("tag_name", tagName)
                .add("target_commitish", targetCommitish)
                .add("name", name)
                .add("body", body)
                .add("prerelease", prerelease)
                .add("draft", draft)
                .toString(WriterConfig.PRETTY_PRINT)
        }


        val request = Request.Builder()
            .url(url)
            .post(postBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", HEADER_USER_AGENT)
            .header("Authorization", "token ${extension.token}")
            .header("Accept", accept)
            .build()

        val postLogMessage = """
                |POST $url
                | > User-Agent: ${request.header("User-Agent")}
                | > Authorization: (not shown)
                | > Accept: ${request.header("Accept")}
                | > body: $postBody
            """.trimMargin()
        logger.debug(
            postLogMessage
        )

        val resp = okHttpClient.newCall(request).await()

        val statusLine = "${resp.code} ${resp.message}"
        logger.debug("< $statusLine")
        logger.debug("Response headers: \n${resp.headers.joinToString(separator = "\n") { "< $it" }}")
        return resp.body?.use {
            if (resp.code / 100 != 2) {
                logger.error("Got status $statusLine for $postLogMessage")
                logger.error("Got body ${it.string()}")
                error("Got non 200 status $statusLine and that's an error")
            } else {
                URI(Json.parse(it.charStream()).asObject()["upload_url"].asString().removeSuffix("{?name,label}"))
            }
        }
            ?: error("Invalid response. Got $statusLine for $postLogMessage")
    }

    @TaskAction
    fun release() {
        val extension = project.githubRelease
        runBlocking {
            val uploadUri = createRelease()
            withContext(Dispatchers.IO) {
                extension.assets
                    .map { asset ->
                        launch {
                            postAssets(uploadUri, asset)
                        }
                    }
                    .toList()
                    .joinAll()
            }
        }
    }

    private suspend fun zipAndUploadDir(uploadUrl: URI, asset: File) {
        val zipFile = File(asset.parentFile, asset.name + ".zip")
        zipFile.deleteOnExit()
        try {
            zipTo(zipFile, asset)
            uploadFile(uploadUrl, asset, zipFile.name)
        } finally {
            asset.delete()
        }
    }

    private suspend fun uploadFile(uploadUrl: URI, asset: File, name: String) {
        val extension = project.githubRelease

        val url = uploadUrl.toString().toHttpUrl().newBuilder()
            .addQueryParameter("name", name)
            .addQueryParameter("label", name)
            .build()
        val upload = uploadUrl.toString().replace("{?name,label}", "?name=${name}&label=${name}")
        logger.debug("upload url: $url")


        val map = URLConnection.getFileNameMap()
        val contentType = map.getContentTypeFor(asset.name)
            ?: "application/octet-stream"

        val request = Request.Builder()
            .url(upload)
            .post(asset.asRequestBody(contentType.toMediaType()))
            .header("User-Agent", HEADER_USER_AGENT)
            .header("Authorization", "token ${extension.token}")
            .header("Accept", extension.acceptHeader)
            .build()

        val postLogMessage = """
                |POST $url
                | > User-Agent: ${request.header("User-Agent")}
                | > Authorization: (not shown)
                | > Content-Type: ${request.body?.contentType()}
                | > Accept: ${request.header("Accept")}
                | > body: <redacted>
            """.trimMargin()

        logger.debug(
            postLogMessage
        )

        val resp = okHttpClient.newCall(request).await()

        val statusLine = "${resp.code} ${resp.message}"
        logger.debug("< $statusLine")
        logger.debug("Response headers: \n${resp.headers.joinToString(separator = "\n") { "< $it" }}")
        resp.body?.use {
            if (resp.code / 100 != 2) {
                logger.error("Got status $statusLine for $postLogMessage")
                logger.error("Got body ${it.string()}")
                error("Got non 200 status $statusLine and that's an error")
            }
        }
            ?: error("Invalid response. Got $statusLine for $postLogMessage")
    }

    private suspend fun postAssets(uploadUrl: URI, asset: File) {
        if (!asset.exists()) {
            logger.warn("File $asset does not exist.")
        } else if (asset.isDirectory) {
            zipAndUploadDir(uploadUrl, asset)
        } else {
            val name = asset.name
            uploadFile(uploadUrl, asset, name)
        }
    }


    companion object {
        // header
        private const val HEADER_USER_AGENT = "gradle-github-release-plugin"
    }
}
