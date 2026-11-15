plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.xehuri.linkgate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xehuri.linkgate"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.1.2"
    }

    signingConfigs {
        // 调试包也用正式证书签名：可以直接覆盖安装到手机上排查问题，且保留用户数据
        create("releaseKey") {
            val storeFile = rootProject.file("keystore/linkgate-release.jks")
            val passFile = rootProject.file("keystore/.storepass")
            if (storeFile.exists() && passFile.exists()) {
                this.storeFile = storeFile
                storePassword = passFile.readText().trim()
                keyAlias = "linkgate"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseKey")
        }
        release {
            // 个人自用应用，先不开启混淆，保证行为可预测
            isMinifyEnabled = false
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)

    testImplementation(libs.junit)
}
