plugins {
    id("com.android.application")
}

val releaseKeystorePath = System.getenv("BIBI_BRIDGE_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("BIBI_BRIDGE_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("BIBI_BRIDGE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("BIBI_BRIDGE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() } && releaseKeystorePath?.let { file(it).isFile } == true

android {
    namespace = "com.brycewg.asrkb.imebridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.brycewg.asrkb.imebridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    testImplementation("junit:junit:4.13.2")
}
