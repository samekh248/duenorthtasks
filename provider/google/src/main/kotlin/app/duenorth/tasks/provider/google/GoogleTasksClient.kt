package app.duenorth.tasks.provider.google

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** The access token sent with every request. The provider refreshes it before each call. */
internal class BearerToken {
    @Volatile
    var value: String? = null
}

internal object GoogleTasksClient {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun api(baseUrl: String, bearer: BearerToken): GoogleTasksApi {
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                bearer.value?.let { request.header("Authorization", "Bearer $it") }
                chain.proceed(request.build())
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(GoogleTasksApi::class.java)
    }
}
