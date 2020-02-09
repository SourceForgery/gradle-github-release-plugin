import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    id("net.researchgate.release") version "2.8.1"
}

group = "com.sourceforgery.gradle-github-release-plugin"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())

    implementation("com.squareup.okhttp3:okhttp:4.3.1")
    implementation("com.eclipsesource.minimal-json:minimal-json:0.9.5")
    implementation("ru.gildor.coroutines:kotlin-coroutines-okhttp:1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.3.1")
    testImplementation("org.skyscreamer:jsonassert:1.5.0")
}

gradlePlugin {
    @Suppress("UnstableApiUsage")
    (plugins) {
        register("gradle-github-release-plugin") {
            id = "com.sourceforgery.github-release"
            implementationClass = "com.sourceforgery.githubrelease.GithubReleasePlugin"
            displayName = "Create Github releases with Gradle"
            description = """
                |Create github release and optionally add assets to the release.
                |Typically used as a task in the release process with e.g.
                |`net.researchgate.release`.
                |
                |This is a slightly improved and Kotlinized version of
                |https://github.com/riiid/gradle-github-plugin
            """.trimMargin()
        }
    }
}


tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
