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
private const val POSITIVE_DECIMAL_ID_PATTERN = "^[1-9][0-9]{0,9}$"
private const val NEWS_SLUG_PATTERN = "^[a-z0-9][a-z0-9-]{0,127}$"
private const val SEARCH_QUERY_PATTERN = "^(?!.*[\\u0000-\\u001F\\u007F-\\u009F])(?=.*[\\p{L}\\p{N}]).+$"

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
    HttpStatusCode.BadGateway {
        description = "The upstream source could not be retrieved or its public data could not be parsed."
        content { schema = jsonSchema<ApiErrorResponse>() }
    }
    HttpStatusCode.InternalServerError {
        description = "An unexpected server error occurred."
        content { schema = jsonSchema<ApiErrorResponse>() }
    }
}

internal fun Parameters.Builder.positiveDecimalIdPath(name: String) {
    path(name) {
        description = "Positive decimal ID containing up to 10 digits and no leading zeroes."
        required = true
        schema = JsonSchema(
            type = JsonType.STRING,
            minLength = 1,
            maxLength = 10,
            pattern = POSITIVE_DECIMAL_ID_PATTERN,
        )
    }
}

internal fun Parameters.Builder.newsSlugPath() {
    path("slug") {
        description = "Canonical lowercase article slug."
        required = true
        schema = JsonSchema(
            type = JsonType.STRING,
            minLength = 1,
            maxLength = 128,
            pattern = NEWS_SLUG_PATTERN,
        )
    }
}

internal fun Parameters.Builder.canonicalDecimalPageQuery(
    default: Int,
    maximum: Int,
    pattern: String,
    maximumLength: Int,
) {
    query("page") {
        description = "Optional canonical decimal page number. Defaults to $default."
        required = false
        schema = JsonSchema(
            type = JsonType.STRING,
            minLength = 1,
            maxLength = maximumLength,
            pattern = pattern,
            default = GenericElement(default.toString()),
        )
        extension("x-server-minimum", GenericElement(default))
        extension("x-server-maximum", GenericElement(maximum))
        extension("x-server-canonical-decimal", true)
        extension("x-server-single-value", true)
    }
}

internal fun Parameters.Builder.searchQuery() {
    query("q") {
        description = "Required search text. Validation trims surrounding whitespace before enforcing the documented length."
        required = true
        schema = JsonSchema(
            type = JsonType.STRING,
            minLength = 1,
            pattern = SEARCH_QUERY_PATTERN,
        )
        extension("x-server-single-value", true)
        extension("x-server-trim-before-validation", true)
        extension("x-server-trimmed-minimum-length", 1)
        extension("x-server-trimmed-maximum-length", 80)
        extension("x-server-requires-letter-or-digit", true)
        extension("x-server-rejects-iso-control", true)
    }
}

@OptIn(ExperimentalKtorApi::class)
internal fun Route.hideFromOpenApi() {
    hide()
}
