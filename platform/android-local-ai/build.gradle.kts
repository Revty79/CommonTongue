plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.commontongue.local.android"
    compileSdk = 36
    ndkVersion = "27.1.12297006"
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                targets += "common_tongue_ai"
                arguments +=
                    listOf(
                        "-DLOCAL_SOURCE_ROOT=${rootDir.resolve(".local/local-ai/sources").canonicalPath.replace('\\', '/')}",
                        "-DT5_LIBRARY_ROOT=${rootDir.resolve(".local/local-ai/native").canonicalPath.replace('\\', '/')}",
                        "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    )
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes { debug { isJniDebuggable = false } }
    lint {
        abortOnError = true
        checkDependencies = true
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":platform:local-ai"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
