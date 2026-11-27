plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

android {
    namespace = "app.youplay"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.youplay"
        minSdk = 26
        targetSdk = 36
        // CI bumps these per build so installed APKs update cleanly
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val releaseKeystore = System.getenv("KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true // NewPipeExtractor uses java.time etc.
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

// innertubex (and Ktor 3.6 / serialization 1.11) are built with Kotlin 2.4, so the Dagger/Room annotation
// processors must read 2.4 metadata too: force the metadata reader to the Kotlin version we compile with.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-metadata-jvm") useVersion("2.4.20")
    }
}

dependencies {
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.20")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource)
    implementation(libs.media3.database)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.coil.compose)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.logging)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.innertubex)
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.work.runtime.ktx)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
