plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "com.example.sharedapp"
        compileSdk = 37
        minSdk = 24
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            // Ktor
            implementation("io.ktor:ktor-server-core:2.3.12")
            implementation("io.ktor:ktor-server-cio:2.3.12")
            implementation("io.ktor:ktor-client-core:2.3.12")
            implementation("io.ktor:ktor-client-cio:2.3.12")
            
            // Corrutinas
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.0")
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jmdns:jmdns:3.5.9")
            }
        }
    }
}

