import com.android.build.api.artifact.SingleArtifact
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.commontongue.prototype"
    compileSdk = 36

    defaultConfig {
        // Provisional. Finalize before any Play Store release.
        applicationId = "com.commontongue.prototype"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Intentionally unsigned. Production signing is outside Pass 1.
        }
    }

    buildFeatures { compose = true }

    // Allow repeated local install/test runs on API 26 as well as newer Android.
    installation { installOptions.add("-r") }

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
    implementation(project(":core:translation"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose's compatible BOM carries an older Espresso transitive dependency.
    androidTestImplementation(libs.androidx.test.espresso.core)
}

val verifyFoundationManifest =
    tasks.register("verifyFoundationManifest") {
        group = "verification"
        description = "Checks SDK and permission boundaries in every merged application manifest."
    }

androidComponents {
    onVariants(selector().all()) { variant ->
        val manifestFile = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val variantApplicationId = variant.applicationId
        val capitalizedVariant = variant.name.replaceFirstChar { it.uppercase() }
        val verifyVariantManifest =
            tasks.register("verify${capitalizedVariant}FoundationManifest") {
                group = "verification"
                inputs.file(manifestFile)
                doLast {
                    val parser =
                        DocumentBuilderFactory.newInstance().apply {
                            isNamespaceAware = true
                            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                        }
                    val manifest = parser.newDocumentBuilder().parse(manifestFile.get().asFile)
                    val androidNamespace = "http://schemas.android.com/apk/res/android"
                    val permissionNodes = manifest.getElementsByTagName("uses-permission")
                    val permissions =
                        (0 until permissionNodes.length).map { index ->
                            permissionNodes
                                .item(index)
                                .attributes
                                .getNamedItemNS(androidNamespace, "name")
                                .nodeValue
                        }
                    val signaturePermission =
                        "${variantApplicationId.get()}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
                    check(permissions.all { it == signaturePermission }) {
                        "Unexpected Pass 1 permissions: $permissions"
                    }
                    check(manifest.getElementsByTagName("uses-permission-sdk-23").length == 0) {
                        "SDK-conditional permissions are outside Pass 1."
                    }
                    val sdk = manifest.getElementsByTagName("uses-sdk").item(0).attributes
                    check(sdk.getNamedItemNS(androidNamespace, "minSdkVersion").nodeValue == "26")
                    check(
                        sdk.getNamedItemNS(androidNamespace, "targetSdkVersion").nodeValue == "36"
                    )
                    logger.lifecycle("${variant.name} merged-manifest permissions: $permissions")
                }
            }
        verifyFoundationManifest.configure { dependsOn(verifyVariantManifest) }
    }
}

tasks.named("check") {
    dependsOn(verifyFoundationManifest, rootProject.tasks.named("spotlessCheck"))
}
