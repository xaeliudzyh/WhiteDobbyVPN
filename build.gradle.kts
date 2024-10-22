plugins {
    // ...

    // Add the dependency for the Google services Gradle plugin
    id("com.google.gms.google-services") version "4.4.2" apply false
}

buildscript {
    dependencies {
        // ... другие зависимости
        classpath 'com.google.gms:google-services:4.3.15'
    }
}