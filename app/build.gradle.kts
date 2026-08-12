import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'").apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date())

android {
    namespace = "fr.boitedefete"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.boitedefete"
        minSdk = 26
        targetSdk = 35
        // Compteur interne, strictement croissant : Android refuse d'installer
        // une version dont le code est inferieur a celle deja presente.
        versionCode = 29
        versionName = "1.1.1"

        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
    }

    // Cle de signature fixe et versionnee : sans elle, chaque compilation produit
    // une signature differente et Android refuse d'installer la mise a jour
    // par-dessus la precedente. C'est une cle de debogage, sans valeur secrete.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "partypair"
            keyPassword = "android"
        }
    }

    /**
     * Deux distributions.
     *
     * `github` garde la verification de mise a jour : l'application y est
     * installee a la main, personne ne la mettrait a jour autrement.
     *
     * `fdroid` s'en passe : le magasin s'en charge, et une application qui
     * telecharge ses propres mises a jour y est un motif de rejet.
     */
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            // Pas de signature imposee : F-Droid appose la sienne, et une
            // publication maison passera par sa propre cle.
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
