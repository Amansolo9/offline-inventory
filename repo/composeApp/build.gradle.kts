plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.runtime)
                implementation(compose.animation)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)

                implementation("io.insert-koin:koin-core:3.5.6")
                implementation("com.arkivanov.decompose:decompose:3.2.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.2")
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("androidx.fragment:fragment-ktx:1.8.4")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
                implementation("androidx.recyclerview:recyclerview:1.3.2")
                implementation("com.google.android.material:material:1.12.0")
                implementation("androidx.cardview:cardview:1.0.0")
                implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
                implementation("androidx.room:room-runtime:2.7.0-alpha09")
                implementation("net.zetetic:android-database-sqlcipher:4.5.4")
            }
        }

        val androidInstrumentedTest by getting {
            dependencies {
                implementation("androidx.test.ext:junit:1.2.1")
                implementation("androidx.test.espresso:espresso-core:3.6.1")
                implementation("androidx.compose.ui:ui-test-junit4:1.7.5")
            }
        }
    }
}

android {
    namespace = "com.eaglepoint.task136"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.eaglepoint.task136"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
