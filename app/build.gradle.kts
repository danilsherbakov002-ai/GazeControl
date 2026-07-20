import de.undercouch.gradle.tasks.download.Download

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("de.undercouch.download")
}

android {
    namespace = "com.gazecontrol.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gazecontrol.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }

    // Model file (face_landmarker.task) can be large; keep uncompressed in APK
    androidResources {
        noCompress += "task"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")

    // MediaPipe Tasks Vision (Face Landmarker: gives 478 face landmarks incl. iris + blendshapes)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    implementation("androidx.lifecycle:lifecycle-service:2.8.2")
}

// Модель face_landmarker.task весит ~10 МБ и не хранится в git.
// Эта задача скачивает её автоматически перед каждой сборкой —
// как локально, так и в GitHub Actions — так что APK всегда собирается
// с рабочей моделью распознавания лица/глаз.
val faceModelUrl =
    "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"
val faceModelFile = file("src/main/assets/face_landmarker.task")

tasks.register<Download>("downloadFaceLandmarkerModel") {
    src(faceModelUrl)
    dest(faceModelFile)
    overwrite(false)
    onlyIfModified(false)
    doFirst { faceModelFile.parentFile.mkdirs() }
}

tasks.named("preBuild") {
    dependsOn("downloadFaceLandmarkerModel")
}
