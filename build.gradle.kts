import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

sourceSets.test { java.exclude("**/IntegrationSmoke.java") }

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Platform 242 ships Kotlin stdlib 1.9.24; do not emit calls to newer APIs.
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
        jvmDefault = org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    testImplementation(libs.junit)
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath")
        if (localIde.isPresent) {
            local(localIde.get())
        } else {
            val sdkVersion=providers.gradleProperty("platformVersion").get()
            if (providers.gradleProperty("platformType").getOrElse("IC") == "AI") {
                androidStudio(sdkVersion)
            } else {
                intellijIdeaCommunity(sdkVersion)
            }
        }
        bundledPlugin("org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    // Compile against the oldest supported platform; newer integrations use guarded adapters.
    projectName = "NikoTools"
    buildSearchableOptions = false
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            current()
            val verificationIde = providers.gradleProperty("verificationIdePath")
            if (verificationIde.isPresent) {
                local(file(verificationIde.get()))
            } else {
                create(IntelliJPlatformType.IntellijIdea, "2025.3.6.1")
                create(IntelliJPlatformType.PyCharm, "2025.3.6")
            }
        }
    }
    publishing { token = providers.environmentVariable("PUBLISH_TOKEN") }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}

tasks {
    // Standard Gradle builds also produce the installable plugin ZIP.
    assemble {
        dependsOn(buildPlugin)
    }
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
    }
    test {
        useJUnit()
        enableAssertions = true
        providers.gradleProperty("compatibilityFixture").orNull?.let {
            systemProperty("nikotools.compatibilityFixture", file(it).absolutePath)
        }
    }
    jar {
        from(files("LICENSE", "THIRD_PARTY_NOTICES.md")) { into("META-INF") }
    }
}
