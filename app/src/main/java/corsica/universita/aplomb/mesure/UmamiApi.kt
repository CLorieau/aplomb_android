package corsica.universita.aplomb.mesure

import android.content.Context
import android.os.Build
import android.util.Log
import corsica.universita.aplomb.BuildConfig
import corsica.universita.aplomb.data.local.EvenementEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * L'API HTTP d'Umami (Annexe 4 : « pas de SDK Android, une API HTTP, POST /api/send »).
 * Corps attendu (route /api/send d'Umami) : { "type": "event", "payload": { website, hostname, url, title, name?, data?, … } }.
 * Sans `name`, c'est une page vue ; avec, un événement nommé.
 */
interface UmamiApi {
    @POST("api/send")
    suspend fun envoyer(@Header("User-Agent") userAgent: String, @Body corps: JsonObject): Response<ResponseBody>

    companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun create(): UmamiApi {
            val client = OkHttpClient.Builder()
                .addInterceptor(
                    HttpLoggingInterceptor { message -> Log.d("Aplomb", message) }
                        .apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(BuildConfig.UMAMI_HOST)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(UmamiApi::class.java)
        }

        /**
         * Umami écarte les robots d'après le User-Agent (isbot) et en déduit l'OS et l'appareil.
         * Celui d'OkHttp (« okhttp/5.5.0 ») serait compté comme un robot : on en donne un, honnête, d'appareil Android.
         */
        val userAgent: String =
            "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) AplombAndroid/${BuildConfig.VERSION_NAME}"

        /** Un événement de la file → le corps de POST /api/send. */
        fun corps(ctx: Context, e: EvenementEntity): JsonObject {
            val m = ctx.resources.displayMetrics
            return buildJsonObject {
                put("type", "event")
                put(
                    "payload",
                    buildJsonObject {
                        put("website", BuildConfig.UMAMI_WEBSITE_ID)
                        // Le « domaine » du site dans Umami : celui de la PWA, préfixé android. (Umami exige un vrai nom de domaine).
                        put("hostname", "android.${BuildConfig.PWA_HOST}")
                        put("language", Locale.getDefault().toLanguageTag())
                        put("screen", "${m.widthPixels}x${m.heightPixels}")
                        put("url", e.ecran)
                        put("title", e.titre)
                        put("referrer", "")
                        // L'heure où l'événement a eu lieu (secondes), pas celle de l'envoi : la file peut attendre des heures.
                        put("timestamp", e.horodatage / 1000)
                        if (e.nom != null) put("name", e.nom)
                        e.donnees?.let { put("data", json.parseToJsonElement(it).jsonObject) }
                    },
                )
            }
        }
    }
}
