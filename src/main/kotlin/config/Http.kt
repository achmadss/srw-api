package config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import org.slf4j.event.Level

fun Application.configureHttp() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        anyHost()
    }

    val logReqHeaders = System.getenv("API_LOG_REQUEST_HEADERS")?.lowercase() == "true"
    val logReqBody = System.getenv("API_LOG_REQUEST_BODY")?.lowercase() == "true"

    if (logReqHeaders || logReqBody) {
        intercept(ApplicationCallPipeline.Monitoring) {
            if (call.request.path().startsWith("/auth/")) {
                proceed()
            } else {
                val logInfo = mutableListOf<String>()

                if (logReqHeaders) {
                    val headers = call.request.headers.entries()
                        .joinToString(", ") { (key, values) -> "$key: ${values.joinToString()}" }
                    logInfo.add("Headers: [$headers]")
                }

                if (logReqBody) {
                    logInfo.add("Body: <set API_LOG_REQUEST_BODY=true and ensure content-type is application/json>")
                }

                proceed()

                val logger = call.application.environment?.log
                logger?.info("${call.request.httpMethod.value} ${call.request.path()} - ${call.response.status()?.value ?: 0} - ${logInfo.joinToString(" | ")}")
            }
        }
    }

    install(CallLogging) {
        val logLevelStr = System.getenv("API_LOG_LEVEL") ?: "INFO"
        val logLevel = when (logLevelStr.uppercase()) {
            "DEBUG" -> Level.DEBUG
            "WARN" -> Level.WARN
            "ERROR" -> Level.ERROR
            else -> Level.INFO
        }
        this.level = logLevel

        if (!logReqHeaders && !logReqBody) {
            filter { call: ApplicationCall ->
                !call.request.path().startsWith("/auth/")
            }
        }
    }
}
