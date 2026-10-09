plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.commontongue.speech.android"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
        checkDependencies = true
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:translation"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
