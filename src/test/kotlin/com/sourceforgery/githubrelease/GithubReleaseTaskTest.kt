package com.sourceforgery.githubrelease

import com.sourceforgery.githubrelease.GithubReleasePlugin.Companion.pluginId
import com.sourceforgery.githubrelease.GithubReleasePlugin.Companion.taskName
import githubRelease
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.gradle.api.Project
import org.gradle.kotlin.dsl.get
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.io.TempDir
import org.skyscreamer.jsonassert.JSONAssert
import java.io.File
import java.nio.charset.StandardCharsets

class GithubReleaseTaskTest {

    lateinit var project: Project
    lateinit var server: MockWebServer
    lateinit var mockUploadServer: MockWebServer
    lateinit var task: GithubReleaseTask

    @BeforeEach
    fun setup(testInfo: TestInfo) {
        project = ProjectBuilder.builder().withName(testInfo.displayName).build()
        project.pluginManager.apply(pluginId)
        task = project.tasks[taskName] as GithubReleaseTask
        mockUploadServer = MockWebServer()
        mockUploadServer.start()
        server = MockWebServer()
        server.start()
        project.githubRelease {
            baseUrl = server.url("").toUri()
            owner = "octocat"
            repo = "Hello-World"
            body = "Description of the release"
            targetCommitish = "master"
            name = "v1.0.0"
            token = "this_is_not_a_real_token"
            tagName = "v1.0.0"
        }

        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """
                    {
                      "url": "https://api.github.com/repos/octocat/Hello-World/releases/1",
                      "html_url": "https://github.com/octocat/Hello-World/releases/v1.0.0",
                      "assets_url": "https://api.github.com/repos/octocat/Hello-World/releases/1/assets",
                      "upload_url": "${mockUploadServer.url("/repos/octocat/Hello-World/releases/1/assets")}{?name,label}",
                      "tarball_url": "https://api.github.com/repos/octocat/Hello-World/tarball/v1.0.0",
                      "zipball_url": "https://api.github.com/repos/octocat/Hello-World/zipball/v1.0.0",
                      "id": 1,
                      "node_id": "MDc6UmVsZWFzZTE=",
                      "tag_name": "v1.0.0",
                      "target_commitish": "master",
                      "name": "v1.0.0",
                      "body": "Description of the release",
                      "draft": false,
                      "prerelease": false,
                      "created_at": "2013-02-27T19:35:32Z",
                      "published_at": "2013-02-27T19:35:32Z",
                      "author": {
                        "login": "octocat",
                        "id": 1,
                        "node_id": "MDQ6VXNlcjE=",
                        "avatar_url": "https://github.com/images/error/octocat_happy.gif",
                        "gravatar_id": "",
                        "url": "https://api.github.com/users/octocat",
                        "html_url": "https://github.com/octocat",
                        "followers_url": "https://api.github.com/users/octocat/followers",
                        "following_url": "https://api.github.com/users/octocat/following{/other_user}",
                        "gists_url": "https://api.github.com/users/octocat/gists{/gist_id}",
                        "starred_url": "https://api.github.com/users/octocat/starred{/owner}{/repo}",
                        "subscriptions_url": "https://api.github.com/users/octocat/subscriptions",
                        "organizations_url": "https://api.github.com/users/octocat/orgs",
                        "repos_url": "https://api.github.com/users/octocat/repos",
                        "events_url": "https://api.github.com/users/octocat/events{/privacy}",
                        "received_events_url": "https://api.github.com/users/octocat/received_events",
                        "type": "User",
                        "site_admin": false
                      },
                      "assets": [
                      ]
                    }
                """.trimIndent()
                )
        )
    }

    private fun addUploadResponse() {
        mockUploadServer.enqueue(MockResponse().setResponseCode(200).setBody(""))
    }

    @Test
    fun `sunny day test no assets`() {
        for (action in task.actions) {
            action.execute(task)
        }
        val createRequest = server.takeRequest()
        assertJson(
            expected = """
                {
                  "tag_name": "v1.0.0",
                  "target_commitish": "master",
                  "name": "v1.0.0",
                  "body": "Description of the release",
                  "draft": false,
                  "prerelease": false
                }
            """.trimIndent(),
            request = createRequest
        )
    }

    @Test
    fun `sunny day test three assets`() {
        addUploadResponse()
        addUploadResponse()
        addUploadResponse()
        project.githubRelease {
            assets.add(png)
            assets.add(txt)
            assets.add(octet)
        }

        for (action in task.actions) {
            action.execute(task)
        }
        val createRequest = server.takeRequest()
        assertJson(
            expected = """
                {
                  "tag_name": "v1.0.0",
                  "target_commitish": "master",
                  "name": "v1.0.0",
                  "body": "Description of the release",
                  "draft": false,
                  "prerelease": false
                }
            """.trimIndent(),
            request = createRequest
        )

        val contentTypes = (1..3).map {
            mockUploadServer.takeRequest().getHeader("content-type")
        }
        assertTrue(contentTypes.containsAll(setOf("image/png", "text/plain", "application/octet-stream")))
    }

    companion object {
        @JvmStatic
        lateinit var png: File
        @JvmStatic
        lateinit var txt: File
        @JvmStatic
        lateinit var octet: File

        @BeforeAll
        @JvmStatic
        fun beforeAll(@TempDir tempDir: File) {
            png = copy(tempDir, "1200px-Cc-sa.svg.png")
            txt = copy(tempDir, "foobar.txt")
            octet = copy(tempDir, "foobar")
        }

        private fun copy(tempDir: File, name: String): File {
            val file = File(tempDir, name)
            file.writeBytes(GithubReleaseTaskTest::class.java.getResourceAsStream("/$name").readAllBytes())
            return file
        }
    }
}

fun assertJson(expected: String, request: RecordedRequest, strict: Boolean = true) {
    JSONAssert.assertEquals(expected, request.body.readString(StandardCharsets.UTF_8), strict)
}
