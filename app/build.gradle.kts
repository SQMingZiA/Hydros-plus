import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import java.time.Instant
import java.util.Properties

plugins {
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val hydrosVersion = providers.gradleProperty("hydrosVersion").get()
val versionParts = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
    .matchEntire(hydrosVersion)?.groupValues?.drop(1)?.map { it.toLongOrNull() }
    ?: error("hydrosVersion must be a stable version X.Y.Z")
require(versionParts.all { it != null && it <= 2100 }) { "hydrosVersion is outside the Android versionCode range" }
val (versionMajor, versionMinor, versionPatch) = versionParts.map { requireNotNull(it) }
require(versionMinor < 1000 && versionPatch < 1000) { "Minor and patch versions must be below 1000" }
val hydrosVersionCode = versionMajor * 1_000_000 + versionMinor * 1_000 + versionPatch
require(hydrosVersionCode in 1..2_100_000_000L) { "hydrosVersion is outside the Android versionCode range" }

val releaseSigning = listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull }
val hasEnvSigning = releaseSigning.values.any { !it.isNullOrBlank() }
require(!hasEnvSigning || releaseSigning.values.all { !it.isNullOrBlank() }) {
    "Release signing requires ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD"
}
// 固定签名：项目内自带一把长期使用的密钥，保证每次升级安装都能覆盖旧版本。
// 之前的构建每次可能换一把 debug 密钥，签名不一致时 Android 只能先卸载再装，
// 卸载会清空应用数据，表现就是“升级后必须重新登录”。真实签名请通过环境变量覆盖。
val bundledKeystore = rootProject.layout.projectDirectory.file("keystore/hydros-release.jks").asFile
val bundledSigningReady = bundledKeystore.exists()
// 口令不写进仓库：优先环境变量，其次本地 keystore/signing.properties（已被 .gitignore 忽略）。
val signingPropertiesFile = rootProject.layout.projectDirectory.file("keystore/signing.properties").asFile
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) signingPropertiesFile.inputStream().use { load(it) }
}
fun signingValue(env: String, key: String): String? =
    releaseSigning[env]?.takeIf { it.isNotBlank() } ?: signingProperties.getProperty(key)?.takeIf { it.isNotBlank() }
val releaseStorePath = releaseSigning["ANDROID_KEYSTORE_PATH"]?.takeIf { it.isNotBlank() } ?: bundledKeystore.absolutePath
val releaseStorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias") ?: "hydros"
val releaseKeyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = hasEnvSigning || (bundledSigningReady && releaseStorePassword != null && releaseKeyPassword != null)
gradle.taskGraph.whenReady {
    val packagesRelease = allTasks.any {
        it.project == project && it.name in setOf("assembleRelease", "bundleRelease", "packageRelease", "packageReleaseBundle", "signReleaseBundle")
    }
    require(!packagesRelease || hasReleaseSigning) { "Release artifacts require the official signing key" }
}

abstract class GenerateProjectLicense : DefaultTask() {
    @get:InputFile
    abstract val licenseFile: RegularFileProperty
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
    @TaskAction
    fun generate() {
        val output = outputDirectory.file("project_license.txt").get().asFile
        output.parentFile.mkdirs()
        licenseFile.get().asFile.copyTo(output, overwrite = true)
    }
}
val generateProjectLicense = tasks.register<GenerateProjectLicense>("generateProjectLicense") {
    licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
    outputDirectory.set(layout.buildDirectory.dir("generated/projectLicense/assets"))
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(generateProjectLicense) { it.outputDirectory }
}

android {
    namespace = "org.cqutosp.hydros"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.cqutosp.hydros"
        minSdk = 31
        targetSdk = 35
        versionCode = hydrosVersionCode.toInt()
        versionName = hydrosVersion
        val buildTime = providers.environmentVariable("SOURCE_DATE_EPOCH").orNull?.toLongOrNull()
            ?.let(Instant::ofEpochSecond) ?: Instant.now()
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")

        val defaultBaseUrl = String(byteArrayOf(104, 116, 116, 112, 115, 58, 47, 47, 118, 51, 45, 97, 112, 105, 46, 99, 104, 105, 110, 97, 45, 113, 122, 120, 121, 46, 99, 110, 47), Charsets.UTF_8)
        buildConfigField("String", "CAMPUS_BASE_URL", "\"${providers.gradleProperty("campusBaseUrl").getOrElse(defaultBaseUrl)}\"")
        testInstrumentationRunner = "org.cqutosp.hydros.OfflineTestRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePassword!!
                keyAlias = releaseKeyAlias!!
                keyPassword = releaseKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            ndk.abiFilters += "arm64-v8a"
            // AGP 9.3+ enables R8, optimized resource shrinking and Android keep rules together.
            optimization {
                enable = true
            }
        }
    }

    androidResources {
        // The app uses Chinese; retain English as the fallback for library UI.
        localeFilters += listOf("zh", "en")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        }
    }
}

dependencies {
    implementation(libs.markdown.renderer.m3)
    implementation(libs.aboutlibraries.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.mqtt.paho)
    implementation(libs.alipay.sdk)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

