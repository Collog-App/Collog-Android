import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase 설정 파일이 있을 때만 FCM을 붙인다(없으면 통화 수신 알림 없이 빌드된다).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

android {
    namespace = "live.collog.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "live.collog.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 45
        versionName = "1.2"

        buildConfigField("String", "API_BASE_URL", "\"https://api.collog.live\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${providers.gradleProperty("collog.googleWebClientId").getOrElse("")}\"")
        // 카카오 네이티브 앱 키. 비우면 카카오 로그인 버튼이 숨는다(비밀 아님, 앱에 들어간다).
        val kakaoNativeAppKey = providers.gradleProperty("collog.kakaoNativeAppKey").getOrElse("")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        manifestPlaceholders["kakaoScheme"] = "kakao$kakaoNativeAppKey"
        // RevenueCat 공개 SDK 키(goog_…, 비밀 아님). local.properties 의 revenuecat.apiKey, 없으면 빈 값 —
        // 비어 있으면 SDK 를 켜지 않고 결제 화면은 「결제를 준비하고 있어요」만 보인다(CI 는 키 없이 돈다).
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        val revenueCatApiKey = localProperties.getProperty("revenuecat.apiKey", "")
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")
    }

    buildTypes {
        debug {
            // 로컬 서버에 붙일 때: ./gradlew installDebug -Pcollog.apiBaseUrl=http://10.0.2.2:8080
            providers.gradleProperty("collog.apiBaseUrl").orNull?.let {
                buildConfigField("String", "API_BASE_URL", "\"$it\"")
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // JVM 단위 테스트에서 android.util.Log 같은 프레임워크 호출은 기본값을 돌려준다.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.livekit.android)
    implementation(libs.androidx.core.telecom)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kakao.user)
    implementation(libs.androidx.health.connect.client)
    // 의존성이 끌어오는 옛 fragment(<1.3) 대신: registerForActivityResult 요구 사항
    implementation(libs.androidx.fragment)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.revenuecat.purchases)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
