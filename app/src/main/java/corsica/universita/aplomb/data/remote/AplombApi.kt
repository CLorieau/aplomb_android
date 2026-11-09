package corsica.universita.aplomb.data.remote

import android.content.Context
import android.util.Log
import corsica.universita.aplomb.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Ce qu'Aplomb envoie pour un relevé : exactement ce qu'accepte validerReleve() côté serveur (api/releves.mjs).
 * DTO ≠ entité : la position (v2) reste sur l'appareil, comme le promet la PWA (« ni compte, ni position »).
 */
@Serializable
data class ReleveDto(
    val outil: String,
    val valeur: Double,
    val mode: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val note: String,
    val createdAt: Long,
)

/**
 * La même API que la PWA (Netlify Function, /api/releves). Response<Unit> : c'est le repository qui lit
 * le code HTTP (400/413 = refus définitif, 5xx = réessayer), exactement comme sync.js.
 */
interface AplombApi {
    @PUT("api/releves/{id}")
    suspend fun envoyer(
        @Path("id") id: String,
        @Header("X-Device-Id") appareil: String,
        @Header("X-Differe") differe: String?, // null = en-tête absent
        @Body releve: ReleveDto,
    ): Response<Unit>

    @DELETE("api/releves/{id}")
    suspend fun supprimer(@Path("id") id: String, @Header("X-Device-Id") appareil: String): Response<Unit>

    companion object {
        /** Un seul OkHttpClient par process. Retrofit fait l'appel hors du main thread : rien à faire côté appelant. */
        fun create(context: Context): AplombApi {
            val json = Json {
                explicitNulls = false
                ignoreUnknownKeys = true
            }
            val client = OkHttpClient.Builder()
                .cache(Cache(File(context.cacheDir, "http"), 10L * 1024 * 1024))
                .addInterceptor(
                    HttpLoggingInterceptor { message -> Log.d("Aplomb", message) }
                        .apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(BuildConfig.SYNC_BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(AplombApi::class.java)
        }
    }
}
