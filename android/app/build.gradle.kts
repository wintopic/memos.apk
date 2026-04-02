plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
}

val goBindingsAar = layout.projectDirectory.file("libs/memosmobile.aar").asFile

android {
	namespace = "com.usememos.android"
	compileSdk = 35

	defaultConfig {
		applicationId = "com.usememos.android"
		minSdk = 28
		targetSdk = 35
		versionCode = 1
		versionName = "1.0.0"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildFeatures {
		buildConfig = true
		viewBinding = true
	}

	buildTypes {
		debug {
			applicationIdSuffix = ".debug"
			versionNameSuffix = "-debug"
		}

		release {
			isMinifyEnabled = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro",
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

	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}
}

dependencies {
	implementation("androidx.activity:activity-ktx:1.9.2")
	implementation("androidx.appcompat:appcompat:1.7.0")
	implementation("androidx.core:core-ktx:1.13.1")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
	implementation("com.google.android.material:material:1.12.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

	if (goBindingsAar.exists()) {
		implementation(files(goBindingsAar))
	}
}
