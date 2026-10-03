import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The dictionary engine: plain Kotlin/JVM, shared by the Android app and the desktop app.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    // RealDictionaryTest reads DICT_DIR from the environment or a system property.
    System.getProperty("DICT_DIR")?.let { systemProperty("DICT_DIR", it) }
}
