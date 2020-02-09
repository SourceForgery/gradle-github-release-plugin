package com.sourceforgery.githubrelease

import java.io.File
import java.net.URI

open class GithubReleaseExtension {
    var baseUrl = URI("https://api.github.com")
    var acceptHeader = "application/vnd.github.v3+json"
    lateinit var owner: String
    lateinit var repo: String
    lateinit var token: String
    var tagName: String? = null
    var targetCommitish = "master"
    var name: String? = null
    var body: String? = null
    var prerelease = false
    var draft = false
    var assets = mutableSetOf<File>()

    operator fun<T> invoke(block: GithubReleaseExtension.() -> T) = block()
}
