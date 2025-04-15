import june.wing.GroupIdMavenCentral
import june.wing.publishMavenCentral

plugins {
    id("com.android.library")
    alias(vcl.plugins.gene.android)
    alias(vcl.plugins.gene.compose)
}


group = GroupIdMavenCentral
version = libs.versions.gene.view.dsl.get()

publishMavenCentral("android view dsl")
//publish5hmlA("android view dsl")

android {
    namespace = "osp.spark.view.dsl"
}

dependencies {
    api(vcl.gene.cartoon)
    api(vcl.gene.adapter)
    api(vcl.google.material)
    api(vcl.androidx.constraintlayout)
    api(vcl.androidx.preference.ktx)
    api(vcl.androidx.activity.ktx)
    api(vcl.androidx.appcompat)
}