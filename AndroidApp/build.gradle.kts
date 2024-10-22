// build.gradle.kts (корневой проект)
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Используйте актуальную версию Android Gradle Plugin
        classpath("com.android.tools.build:gradle:8.0.2")
        // Используйте совместимую версию Kotlin Gradle Plugin
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.21")
        // Обновите версию плагина Google Services до 4.3.15
        classpath("com.google.gms:google-services:4.3.15")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
