package cu.jcmachadoh.simplecargador.data.update

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP para consultar la última versión publicada de la app en el
 * repositorio de releases (RNF-02: internet solo bajo demanda del usuario).
 */
class UpdateApiClient {

    val api: UpdateApi by lazy {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UpdateApi::class.java)
    }

    suspend fun latest(): UpdateInfo = api.getLatestVersion()

    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/jcmachadoh/Releases/main/"
    }
}
