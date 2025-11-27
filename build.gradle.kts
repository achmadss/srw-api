plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.srw"
version = "0.0.1"

application {
    mainClass.set("ApplicationKt")
    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=true",
        "--enable-native-access=ALL-UNNAMED"
    )
}

kotlin {
    compilerOptions {
        jvmTarget
    }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.postgresql)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.logback.classic)

    // koin
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.ktor)

    // exposed
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.datetime)
    implementation(libs.exposed.jdbc)

    // crypto
    implementation("org.mindrot:jbcrypt:0.4")

    // resources
    implementation(libs.ktor.resource)
    implementation(libs.ktor.validation)

    // minio
    implementation(libs.minio)

    // rabbitmq
    implementation(libs.rabbitmq)

    // datetime
    implementation(libs.kotlinx.serialization)

    // swagger ui
    implementation(libs.ktor.server.swagger)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    useJUnitPlatform()
}
