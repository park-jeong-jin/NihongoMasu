plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nihongo.masu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nihongo.masu"
        // 적응형 런처 아이콘(벡터)만 넣었으므로 API 26 이상으로 둔다.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // Srs와 ShapeCompare는 안드로이드 API를 쓰지 않아 JVM에서 그대로 돈다.
    testImplementation("junit:junit:4.13.2")
    // Backup은 org.json을 쓴다. android.jar의 것은 단위 테스트에서 던지기만 하는
    // 껍데기라, 진짜 구현을 테스트 쪽에만 얹는다 (앱 APK에는 안 들어간다).
    testImplementation("org.json:json:20240303")
}
