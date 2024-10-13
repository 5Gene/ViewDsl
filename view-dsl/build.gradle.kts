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
    api(vcl.gene.cartoon)
    api(vcl.google.material)
    api(vcl.androidx.constraintlayout)
    api(vcl.androidx.preference.ktx)
}