plugins { alias(libs.plugins.android.application) }
android {
    namespace = "dev.appfin.fixture"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.appfin.fixture"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["targetLabel"] = "AppFin Test Target"
    }
    buildTypes {
        create("second") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".second"
            manifestPlaceholders["targetLabel"] = "AppFin Test Target 2"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
