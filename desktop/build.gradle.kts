import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val appVersion = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.sqlite.jdbc)
    implementation(libs.flatlaf)
    implementation(libs.jna)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.lexis.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")

        buildTypes.release.proguard {
            // sqlite-jdbc loads its native library and JDBC driver reflectively.
            isEnabled.set(false)
        }

        nativeDistributions {
            // jpackage only builds installers for the OS it runs on: build Windows packages on
            // Windows, macOS packages on a Mac and Linux packages on Linux (see .github/workflows).
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Lexis"
            packageVersion = appVersion
            description = "StarDict reader for dense dictionaries"
            vendor = "Lexis"
            copyright = "MIT License"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.instrument", "java.sql", "jdk.unsupported")

            windows {
                iconFile.set(project.file("icons/lexis.ico"))
                menu = true
                menuGroup = "Lexis"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Fixed so that newer installers upgrade an existing installation in place.
                upgradeUuid = "6f1d1d1e-5c43-4a59-9a8e-2b7b6f0f6a21"
            }
            macOS {
                iconFile.set(project.file("icons/lexis.icns"))
                bundleID = "com.lexis.reader.desktop"
                appCategory = "public.app-category.reference"
            }
            linux {
                iconFile.set(project.file("icons/lexis.png"))
                menuGroup = "Office;Dictionary"
                shortcut = true
            }
        }
    }
}

/**
 * One runnable jar for every desktop OS: bundles the Skia natives for Windows, macOS
 * (Intel and Apple silicon) and Linux. Needs a Java 17+ runtime: `java -jar lexis-desktop-all.jar`.
 */
val allPlatforms: Configuration = configurations.create("allPlatforms") {
    isCanBeConsumed = false
    extendsFrom(configurations.implementation.get(), configurations.runtimeOnly.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
    }
}

dependencies {
    allPlatforms(libs.compose.desktop.windows.x64)
    allPlatforms(libs.compose.desktop.macos.x64)
    allPlatforms(libs.compose.desktop.macos.arm64)
    allPlatforms(libs.compose.desktop.linux.x64)
    allPlatforms(libs.compose.desktop.linux.arm64)
}

tasks.register<Jar>("crossPlatformJar") {
    group = "distribution"
    description = "Builds a single jar that runs on Windows, macOS and Linux with an installed Java 17+."
    archiveFileName.set("lexis-desktop-$appVersion-all.jar")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Main-Class" to "com.lexis.desktop.MainKt",
            "Implementation-Version" to appVersion,
            "Enable-Native-Access" to "ALL-UNNAMED",
        )
    }
    from(sourceSets.main.get().output)
    dependsOn(allPlatforms)
    from({ allPlatforms.filter { it.name.endsWith(".jar") }.map { zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC", "META-INF/versions/9/module-info.class", "module-info.class")
    }
}

tasks.test {
    maxHeapSize = "1g"
    testLogging { showStandardStreams = true }
}
