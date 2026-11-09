import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// FCM : le plugin google-services exige app/google-services.json (propre à un projet Firebase, non commité).
// Sans lui, l'app se construit quand même : seul le push est désactivé (BuildConfig.FCM_ACTIF = false).
val fcmActif = file("google-services.json").exists()
if (fcmActif) apply(plugin = libs.plugins.google.services.get().pluginId)

// Valeurs propres à la machine, lues dans local.properties (jamais commité) :
//   PWA_HOST=playful-kataifi-da7d5a.netlify.app     → hôte de la PWA (App Link)
//   SYNC_BASE_URL=https://playful-kataifi-da7d5a.netlify.app/ → l'API /api/releves de la PWA
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val pwaHost: String = localProps.getProperty("PWA_HOST") ?: "playful-kataifi-da7d5a.netlify.app"
val syncBaseUrl: String = localProps.getProperty("SYNC_BASE_URL") ?: "https://$pwaHost/"
// Analytics (Umami, Annexe 4) : l'identifiant du site dans Umami (UUID) et le serveur.
//   UMAMI_WEBSITE_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx   → vide : mesure désactivée, rien n'est proposé
//   UMAMI_HOST=https://gateway.umami.is/                     → Umami Cloud ; ou l'adresse de son Umami auto-hébergé
val umamiWebsiteId: String = localProps.getProperty("UMAMI_WEBSITE_ID") ?: ""
val umamiHost: String = localProps.getProperty("UMAMI_HOST") ?: "https://gateway.umami.is/"

android {
    namespace = "corsica.universita.aplomb"
    compileSdk = 37

    defaultConfig {
        applicationId = "corsica.universita.aplomb"
        minSdk = 26
        targetSdk = 37
        versionCode = 3 // +1 à chaque livraison (v3 : base v3, analytics)
        versionName = "1.2"

        buildConfigField("String", "PWA_HOST", "\"$pwaHost\"")
        buildConfigField("String", "SYNC_BASE_URL", "\"$syncBaseUrl\"")
        buildConfigField("boolean", "FCM_ACTIF", "$fcmActif")
        buildConfigField("String", "UMAMI_WEBSITE_ID", "\"$umamiWebsiteId\"")
        buildConfigField("String", "UMAMI_HOST", "\"$umamiHost\"")
        manifestPlaceholders["pwaHost"] = pwaHost
    }

    // Clé debug du cours (la même pour tous) : une seule empreinte SHA-256 à déclarer dans assetlinks.json.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Le schéma de chaque version de la base est écrit ici à chaque build (1.json, 2.json…) et commité.
room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)

    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.serialization.json)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.play.services.location)

    testImplementation(libs.junit)
}
