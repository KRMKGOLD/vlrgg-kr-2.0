package kr.co.cotton.vlrgg_mobile.routing

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.utils.io.*
import kr.co.cotton.vlrgg_mobile.common.http.ApiErrorResponse

private const val OPEN_API_PATH = "/openapi.json"
private const val SWAGGER_PATH = "/swagger"

/** Registers development-only API documentation without changing feature routing or services. */
@OptIn(ExperimentalKtorApi::class)
internal fun Application.configureOpenApiDocumentation() {
    val source = OpenApiDocSource.Routing(ContentType.Application.Json)
    val baseDocument = OpenApiDoc.Builder().apply {
        info = OpenApiInfo(
            title = "VLR.GG Mobile API",
            version = "v1",
            description = "Development documentation for the public app-facing API contract.",
        )
    }.build()

    routing {
        get(OPEN_API_PATH) {
            val document = source.read(this@configureOpenApiDocumentation, baseDocument)
            call.respondText(document.content, document.contentType)
        }.hide()

        swaggerUI(SWAGGER_PATH) {
            info = baseDocument.info
            this.source = source
            remotePath = "openapi.json"
        }
    }
}

@OptIn(ExperimentalKtorApi::class)
internal inline fun <reified T : Any> Route.describePublicGet(
    operationId: String,
    summary: String,
    operationDescription: String,
    tag: String,
    noinline parameters: Parameters.Builder.() -> Unit = {},
) = describe {
    this.operationId = operationId
    this.summary = summary
    this.description = operationDescription
    this.tag(tag)
    parameters(parameters)
    responses {
        HttpStatusCode.OK {
            description = "Successful response."
            content { schema = jsonSchema<T>() }
        }
        commonFailureResponses()
    }
}

@OptIn(ExperimentalKtorApi::class)
internal fun Responses.Builder.commonFailureResponses() {
    HttpStatusCode.BadRequest {
        description = "The path or query input does not satisfy this endpoint's validation rules."
        content { schema = jsonSchema<ApiErrorResponse>() }
    }
    HttpStatusCode.NotFound {
        description = "No route matches the requested resource."
        content { schema = jsonSchema<ApiErrorResponse>() }
    }
    HttpStatusCode.BadGateway {
        description = "The upstream source could not be retrieved or its public data could not be parsed."
        content { schema = jsonSchema<ApiErrorResponse>() }
    }
    HttpStatusCode.InternalServerError {
        description = "An unexpected server error occurred."
        content { schema = jsonSchema<ApiErrorResponse>() }
    }
}

@OptIn(ExperimentalKtorApi::class)
internal fun Route.hideFromOpenApi() {
    hide()
}
