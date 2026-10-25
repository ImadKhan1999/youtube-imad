plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.imad.itube"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.imad.itube"
        minSdk = 26
        targetSdk = 35
        // Release builds get these from the tag, e.g. v1.2.3 -> 1.2.3 (see .github/workflows/release.yml).
        versionName = providers.gradleProperty("itubeVersionName").getOrElse("1.2.2")
        versionCode = providers.gradleProperty("itubeVersionCode").map(String::toInt).getOrElse(10202)
        vectorDrawables.useSupportLibrary = true
    }

    // The release key lives only in GitHub secrets. Without it, release builds fall back to
    // the debug key so pull-request builds still produce an installable APK.
    val keystore = System.getenv("ITUBE_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("ITUBE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ITUBE_KEY_ALIAS")
                keyPassword = System.getenv("ITUBE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Minification is off for now: NewPipeExtractor relies on Rhino and reflection,
            // which need a curated keep-rule set before R8 can be turned on safely.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
            )
        }
    }
    lint {
        // Media3 marks much of its API as @UnstableApi; we use it on purpose.
        disable += setOf("UnsafeOptInUsageError", "MissingTranslation")
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.webkit)
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.newpipe.extractor)
}
