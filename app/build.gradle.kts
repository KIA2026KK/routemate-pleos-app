plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val destinationProvider = providers.gradleProperty("destinationProvider").orElse("mock").get()
val vehicleProvider = providers.gradleProperty("vehicleProvider").orElse(destinationProvider).get()
require(vehicleProvider in setOf("mock", "pleos")) { "vehicleProvider must be mock or pleos" }
require(destinationProvider in setOf("mock", "pleos")) {
    "destinationProvider must be mock or pleos"
}
val backendUrl = providers.gradleProperty("backendUrl").orElse("").get().trimEnd('/')
val prototypeDemo = providers.gradleProperty("prototypeDemo").orElse("false").get().toBooleanStrict()
require(!prototypeDemo || (destinationProvider=="mock"&&vehicleProvider=="mock"&&backendUrl.isBlank())) { "Offline prototype requires mock providers and no backend URL" }
val mvpTestApplicationId = providers.gradleProperty("mvpTestApplicationId").orNull
require(mvpTestApplicationId == null || mvpTestApplicationId.matches(Regex("com\\.routemate\\.app\\.mvpverify[a-f0-9]{8}"))) { "Invalid isolated MVP test application ID" }
require(backendUrl.none { it == '"' || it == '\\' || it.isISOControl() }) { "Invalid backendUrl" }
android {
    namespace = "com.routemate.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.routemate.app"
        minSdk = if (destinationProvider == "pleos" || vehicleProvider == "pleos") 28 else 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "USE_PLEOS_DESTINATION", (destinationProvider == "pleos").toString())
        buildConfigField("boolean", "USE_PLEOS_VEHICLE", (vehicleProvider == "pleos").toString())
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("boolean", "PROTOTYPE_DEMO", "false")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("debug") {
            if(prototypeDemo){
                require(mvpTestApplicationId==null) { "Use a separate prototype install" }
                applicationIdSuffix=".prototype"
                versionNameSuffix="-prototype"
                buildConfigField("boolean","PROTOTYPE_DEMO","true")
            }
            if (mvpTestApplicationId != null) applicationIdSuffix = mvpTestApplicationId.removePrefix("com.routemate.app")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.livekit:livekit-android:2.30.0")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
