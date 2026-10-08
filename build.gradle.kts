import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

dependencies {
    testImplementation(libs.junit)

    intellijPlatform {
        webstorm("2025.3.6")
        bundledPlugin("JavaScript")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253.33813.27"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            // CI verifies each reviewed host separately; local default verifies both.
            val host = providers.gradleProperty("verifierHost").orNull
            require(host == null || host in setOf("WS", "IDEA")) { "verifierHost must be WS or IDEA" }
            if (host == null || host == "WS") create(IntelliJPlatformType.WebStorm, "2025.3.6")
            if (host == null || host == "IDEA") create(IntelliJPlatformType.IntellijIdea, "2025.3.6.1")
        }
    }
}

// Preserve inherited attribution inside the installable plugin, not just the source checkout.
tasks.processResources {
    from(listOf("LICENSE", "NOTICE")) { into("META-INF") }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
