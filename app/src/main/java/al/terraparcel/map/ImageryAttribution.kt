package al.terraparcel.map

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Metadata only, no viewport or parcel geometry. Called only when online imagery is selected. */
object ImageryAttribution {
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder().callTimeout(8,TimeUnit.SECONDS).build()
    suspend fun credit(context: Context): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cache = context.applicationContext.getSharedPreferences("imagery-attribution",Context.MODE_PRIVATE)
            val previous = cache.getString("credit",MapLayers.ESRI_CREDIT) ?: MapLayers.ESRI_CREDIT
            val now = System.currentTimeMillis()
            if (now-cache.getLong("checked",0) < 86_400_000) return@withLock previous
            val fresh = runCatching {
                client.newCall(Request.Builder().url(MapLayers.ESRI_METADATA).build()).execute().use { response ->
                    check(response.isSuccessful)
                    Html.fromHtml(JSONObject(requireNotNull(response.body).string()).getString("copyrightText"),Html.FROM_HTML_MODE_LEGACY)
                        .toString().trim().also { require(it.isNotBlank()) }
                }
            }.getOrDefault(previous)
            cache.edit().putString("credit",fresh).putLong("checked",now).apply()
            fresh
        }
    }
}
