import wing.publishMavenCentral

plugins {
    id("com.android.library")
    alias(wings.plugins.android)
}


group = "io.github.5gene"
version = "0.0.1"

publishMavenCentral("android view dsl", "debug")

android {
    namespace = "osp.june.dsl"
}

dependencies {
    implementation(wings.sparkj.cartoon)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.google.material)
    testImplementation(libs.test.junit)
}