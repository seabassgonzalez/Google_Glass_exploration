plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Load local properties for API keys
val localProperties = java.util.Properties()
val localPropertiesFile = rootProject.file("../GlassStrava/local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(java.io.FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.example.glasscompanion"
    compileSdk = 34  // Android 14
    
    defaultConfig {
        applicationId = "com.example.glasscompanion"
        minSdk = 31  // Android 12 minimum for S21 Ultra
        targetSdk = 34  // Android 14
        versionCode = 1
        versionName = "1.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Add Strava API credentials
        buildConfigField("String", "STRAVA_CLIENT_ID", "\"${localProperties.getProperty("strava.client.id", "")}\"")
        buildConfigField("String", "STRAVA_CLIENT_SECRET", "\"${localProperties.getProperty("strava.client.secret", "")}\"")
        
        // Add Google OAuth Client ID (you'll need to register this in Google Cloud Console)
        // For now, using a placeholder - replace with your actual client ID
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com\"")
        
        // Add OAuth redirect URI
        manifestPlaceholders["redirectUriScheme"] = "glasscompanion"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Compose dependencies
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Location Services
    implementation("com.google.android.gms:play-services-location:21.0.1")
    
    // JSON parsing (optional, Kotlin has built-in JSON support)
    implementation("org.json:json:20231013")
    
    // Networking for Strava API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Security - Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // QR Code generation for credential transfer
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    
    // Custom Tabs for OAuth
    implementation("androidx.browser:browser:1.7.0")
    
    // Image loading for profile pictures
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}