import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory

plugins { id("com.android.application") version "9.4.1" }

android {
    namespace = "com.commontongue.spike.offline"
    compileSdk = 36
    ndkVersion = "27.1.12297006"
    defaultConfig {
        applicationId = "com.commontongue.spike.offline"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1-research"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                targets += "feasibility"
                arguments +=
                    "-DSPIKE_SOURCE_ROOT=${rootDir.resolve("../../.local/offline-sources").canonicalPath.replace('\\', '/')}"
                cppFlags += "-std=c++17"
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
}

dependencies { implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0") }

val verifyOfflineManifest = tasks.register("verifyOfflineManifest") { group = "verification" }

androidComponents {
    onVariants(selector().all()) { variant ->
        val manifestFile = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val task =
            tasks.register(
                "verify${variant.name.replaceFirstChar { it.uppercase() }}OfflineManifest"
            ) {
                inputs.file(manifestFile)
                doLast {
                    val parser =
                        DocumentBuilderFactory.newInstance().apply {
                            isNamespaceAware = true
                            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                        }
                    val document = parser.newDocumentBuilder().parse(manifestFile.get().asFile)
                    check(document.getElementsByTagName("uses-permission").length == 0)
                    check(document.getElementsByTagName("uses-permission-sdk-23").length == 0)
                    check(document.getElementsByTagName("provider").length == 0) {
                        "No telemetry initializer is allowed in this spike"
                    }
                    val attributes = document.getElementsByTagName("uses-sdk").item(0).attributes
                    check(
                        attributes
                            .getNamedItemNS(
                                "http://schemas.android.com/apk/res/android",
                                "minSdkVersion",
                            )
                            .nodeValue == "26"
                    )
                    check(
                        attributes
                            .getNamedItemNS(
                                "http://schemas.android.com/apk/res/android",
                                "targetSdkVersion",
                            )
                            .nodeValue == "36"
                    )
                    logger.lifecycle(
                        "${variant.name} spike manifest: no requested permissions, min 26, target 36"
                    )
                }
            }
        verifyOfflineManifest.configure { dependsOn(task) }
    }
}

tasks.configureEach {
    if (name == "assembleDebug") dependsOn(verifyOfflineManifest)
}
