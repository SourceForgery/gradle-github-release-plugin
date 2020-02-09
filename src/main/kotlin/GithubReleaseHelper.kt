import com.sourceforgery.githubrelease.GithubReleaseExtension
import org.gradle.api.Project

val Project.githubRelease: GithubReleaseExtension
    get() =
        extensions.findByType(GithubReleaseExtension::class.java)
            ?: error("Apply plugin first")
