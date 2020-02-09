package com.sourceforgery.githubrelease

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

internal class GithubReleasePlugin : Plugin<Project?> {

    @Suppress("UnstableApiUsage")
    override fun apply(project: Project) {
        project.extensions.create<GithubReleaseExtension>(name = taskName)
        project.tasks.create(name = "githubRelease", type= GithubReleaseTask::class)
    }

    companion object {
        const val taskName = "githubRelease"
        const val pluginId = "com.sourceforgery.github-release"
    }
}
