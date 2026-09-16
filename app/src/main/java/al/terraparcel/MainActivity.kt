package al.terraparcel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import al.terraparcel.ui.TerraApp

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // HttpRequestImpl initializes its User-Agent from MapLibre's application context.
        // Initialize the SDK before the first reference to that class.
        org.maplibre.android.MapLibre.getInstance(applicationContext)
        org.maplibre.android.module.http.HttpRequestImpl.setOkHttpClient(
            okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "TerraParcel/0.2.0 (+https://github.com/newuser7171/Test)").build())
            }.build()
        )
        org.maplibre.android.module.http.HttpRequestImpl.enablePrintRequestUrlOnFailure(false)
        enableEdgeToEdge()
        setContent { TerraApp() }
    }
}
