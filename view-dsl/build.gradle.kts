import wing.GroupIdMavenCentral
import wing.publishMavenCentral

plugins {
    id("com.android.library")
    alias(vcl.plugins.gene.android)
}


group = GroupIdMavenCentral
version = libs.versions.gene.view.dsl.get()

publishMavenCentral("android view dsl")

android {
    namespace = "osp.june.dsl"
}

dependencies {
    implementation(vcl.gene.cartoon)
    implementation(vcl.google.material)
    implementation(vcl.androidx.constraintlayout)
    implementation(vcl.androidx.preference.ktx)
}