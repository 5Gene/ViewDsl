import wing.publishMavenCentral

plugins {
    id("com.android.library")
    alias(vcl.plugins.gene.android)
}


group = "io.github.5gene"
version = wings.versions.viewDsl.get()

publishMavenCentral("android view dsl")

android {
    namespace = "osp.june.dsl"
}

dependencies {
    implementation(wings.gene.cartoon)
    implementation(vcl.google.material)
    implementation(vcl.androidx.constraintlayout)
    implementation(vcl.androidx.preference.ktx)
//    implementation(libs.google.material)
}