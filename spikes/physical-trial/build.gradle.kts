import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory

plugins { id("com.android.application") version "9.4.1" }

android {
    namespace = "com.commontongue.spike.device"
    compileSdk = 36
    ndkVersion = "27.1.12297006"
    defaultConfig {
        applicationId = "com.commontongue.spike.device"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.0.7-pass5-research"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                targets += "device_trial"
                arguments +=
                    listOf(
                        "-DSPIKE_SOURCE_ROOT=${rootDir.resolve("../../.local/device/sources").canonicalPath.replace('\\', '/')}",
                        "-DT5_LIBRARY_ROOT=${rootDir.resolve("../../.local/device/native").canonicalPath.replace('\\', '/')}",
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
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0")
    testImplementation("junit:junit:4.13.2")
}

val verifyResearchManifest = tasks.register("verifyResearchManifest") { group = "verification" }

androidComponents {
    onVariants(selector().all()) { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val guard =
            tasks.register(
                "verify${variant.name.replaceFirstChar { it.uppercase() }}ResearchManifest"
            ) {
                inputs.file(manifest)
                doLast {
                    val factory =
                        DocumentBuilderFactory.newInstance().apply {
                            isNamespaceAware = true
                            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                        }
                    val document = factory.newDocumentBuilder().parse(manifest.get().asFile)
                    val permissionNames = mutableListOf<String>()
                    for (tag in listOf("uses-permission", "uses-permission-sdk-23")) {
                        val nodes = document.getElementsByTagName(tag)
                        for (index in 0 until nodes.length) permissionNames +=
                            nodes
                                .item(index)
                                .attributes
                                .getNamedItemNS(
                                    "http://schemas.android.com/apk/res/android",
                                    "name",
                                )
                                .nodeValue
                    }
                    check(permissionNames == listOf("android.permission.RECORD_AUDIO")) {
                        "Research permission leak: $permissionNames"
                    }
                    check(document.getElementsByTagName("provider").length == 0) {
                        "No telemetry provider allowed"
                    }
                    val services = document.getElementsByTagName("service")
                    check(services.length == 1)
                    check(
                        services
                            .item(0)
                            .attributes
                            .getNamedItemNS(
                                "http://schemas.android.com/apk/res/android",
                                "exported",
                            )
                            .nodeValue == "false"
                    )
                    logger.lifecycle(
                        "${variant.name}: RECORD_AUDIO only; private inference service; no Internet/provider"
                    )
                }
            }
        verifyResearchManifest.configure { dependsOn(guard) }
    }
}

tasks.configureEach { if (name == "assembleDebug") dependsOn(verifyResearchManifest) }
