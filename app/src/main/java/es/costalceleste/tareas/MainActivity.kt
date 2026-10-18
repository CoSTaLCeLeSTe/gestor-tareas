package es.costalceleste.tareas

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var archivoCallback: ValueCallback<Array<Uri>>? = null
    private var tareaPendiente: String = ""

    private val elegirArchivo =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            archivoCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
            archivoCallback = null
        }

    private val pedirNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refrescar() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Avisos.crearCanal(this)
        tareaPendiente = intent?.getStringExtra("tarea") ?: ""

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val raiz = FrameLayout(this)
        raiz.setBackgroundColor(0xFF3B2618.toInt())

        web = WebView(this)
        web.setBackgroundColor(0xFFCAA074.toInt())
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = true
            textZoom = 100
        }
        web.addJavascriptInterface(Puente(), "Android")
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                v: WebView?, cb: ValueCallback<Array<Uri>>?, params: FileChooserParams?
            ): Boolean {
                archivoCallback?.onReceiveValue(null)
                archivoCallback = cb
                return try {
                    elegirArchivo.launch(params!!.createIntent())
                    true
                } catch (e: Exception) {
                    archivoCallback = null
                    false
                }
            }
        }
        raiz.addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(raiz)

        ViewCompat.setOnApplyWindowInsetsListener(raiz) { v, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        WindowInsetsControllerCompat(window, raiz).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        web.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.manejarAtras ? manejarAtras() : false") { r ->
                    if (r != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val t = intent.getStringExtra("tarea")
        if (!t.isNullOrEmpty()) web.evaluateJavascript("abrirTarea(${JSONObject.quote(t)})", null)
    }

    override fun onResume() {
        super.onResume()
        refrescar()
    }

    private fun refrescar() {
        if (::web.isInitialized) web.evaluateJavascript("window.alVolver && alVolver()", null)
    }

    inner class Puente {
        @JavascriptInterface
        fun sincronizarAlarmas(json: String) = Avisos.sincronizar(applicationContext, json)

        @JavascriptInterface
        fun pedirPermisos() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()) {
                    abrirAjustesNotificaciones()
                }
            }
        }

        @JavascriptInterface
        fun notificacionesActivas(): Boolean =
            NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()

        @JavascriptInterface
        fun alarmasExactas(): Boolean = Avisos.puedeExactas(this@MainActivity)

        @JavascriptInterface
        fun abrirAjustesAlarmas() {
            if (Build.VERSION.SDK_INT >= 31) {
                runOnUiThread {
                    try {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                    } catch (e: Exception) { }
                }
            }
        }

        @JavascriptInterface
        fun abrirAjustesNotificaciones() {
            runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                } catch (e: Exception) { }
            }
        }

        @JavascriptInterface
        fun probarAviso() {
            Avisos.mostrar(
                applicationContext, 999999, "🔔 Aviso de prueba",
                "Así te llegarán los avisos antes de que venza una tarea.", ""
            )
        }

        @JavascriptInterface
        fun compartir(texto: String, nombre: String) {
            runOnUiThread {
                try {
                    val dir = java.io.File(cacheDir, "copias").apply { mkdirs() }
                    val f = java.io.File(dir, nombre)
                    f.writeText(texto)
                    val uri = androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.archivos", f)
                    val i = Intent(Intent.ACTION_SEND)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_SUBJECT, nombre)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(i, "Guardar copia de Mi Despacho"))
                } catch (e: Exception) {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
                    startActivity(Intent.createChooser(i, "Guardar copia de Mi Despacho"))
                }
            }
        }

        @JavascriptInterface
        fun tareaInicial(): String {
            val t = tareaPendiente
            tareaPendiente = ""
            return t
        }

        @JavascriptInterface
        fun version(): String =
            try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }
}
