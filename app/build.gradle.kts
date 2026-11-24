import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.minimal.digitalclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.minimal.digitalclock"
        minSdk = 26
        targetSdk = 35
        // Bump both for every release: versionCode must always increase.
        versionCode = 2
        versionName = "1.1.0"

        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
        buildConfigField("String", "GITHUB_URL", "\"https://github.com/mohtashami740/DigitalClock\"")
    }

    // Signing details live in keystore/keystore.properties, which is not in the repository:
    //   storeFile=release.jks
    //   storePassword=...
    //   keyAlias=...
    //   keyPassword=...
    // Without it the release APK is unsigned (use assembleDebug for a test build).
    val keystoreProps = rootProject.file("keystore/keystore.properties")
    signingConfigs {
        if (keystoreProps.exists()) create("release") {
            val props = Properties().apply { keystoreProps.inputStream().use { load(it) } }
            storeFile = rootProject.file("keystore/" + props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Robolectric's own downloader can't use the build proxy; let Gradle fetch the runtime jar instead.
val robolectricRuntime: Configuration by configurations.creating
dependencies {
    robolectricRuntime("org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
}
val copyRobolectricRuntime by tasks.registering(Copy::class) {
    from(robolectricRuntime)
    into(layout.buildDirectory.dir("robolectric-deps"))
}
tasks.withType<Test>().configureEach {
    dependsOn(copyRobolectricRuntime)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-deps").get().asFile.absolutePath)
}
