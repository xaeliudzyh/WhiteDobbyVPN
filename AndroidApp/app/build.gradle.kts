plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'com.google.gms.google-services'
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.5.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation 'com.google.firebase:firebase-auth-ktx'
    implementation 'com.google.android.gms:play-services-auth:20.5.0'
    // Другие зависимости
}
