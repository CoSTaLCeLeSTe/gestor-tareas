package es.costalceleste.tareas

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar

/** Descarga sencilla siguiendo redirecciones (los scripts de Google redirigen a otro dominio). */
object Red {
    fun get(url: String, timeout: Int): String {
        var u = URL(url)
        var saltos = 0
        while (true) {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = timeout
            c.readTimeout = timeout
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code in 300..399 && saltos < 6) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrEmpty()) throw IOException("Redirección sin destino")
                u = URL(u, loc)
                saltos++
                continue
            }
            val flujo = if (code in 200..299) c.inputStream else c.errorStream
            val texto = flujo?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            if (code !in 200..299) throw IOException("El servidor respondió $code")
            return texto
        }
    }
}

/** Notificación diaria con el parte del día. */
object InformeDiario {
    private const val ID = 777777

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("informe", Context.MODE_PRIVATE)

    fun programar(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, ID, Intent(ctx, InformeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        val p = prefs(ctx)
        if (!p.getBoolean("activo", true)) return
        val partes = (p.getString("hora", "08:00") ?: "08:00").split(":")
        val h = partes.getOrNull(0)?.toIntOrNull() ?: 8
        val m = partes.getOrNull(1)?.toIntOrNull() ?: 0
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 1000) add(Calendar.DAY_OF_YEAR, 1)
        }
        try {
            if (Avisos.puedeExactas(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    fun mostrar(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val p = prefs(ctx)
        val hoy = LocalDate.now()
        val ahora = LocalDateTime.now()
        var vencenHoy = 0
        var vencidas = 0
        var manana = 0
        var seguimiento = 0
        val lista = try { JSONArray(p.getString("instantanea", "[]")) } catch (e: Exception) { JSONArray() }
        for (i in 0 until lista.length()) {
            val t = lista.optJSONObject(i) ?: continue
            if (t.optString("e") == "seg") seguimiento++
            val f = t.optString("f")
            if (f.isEmpty()) continue
            val dia = try { LocalDate.parse(f) } catch (e: Exception) { continue }
            val hh = t.optString("h").ifEmpty { "23:59" }
            val vence = try { LocalDateTime.parse(f + "T" + hh) } catch (e: Exception) { dia.atTime(23, 59) }
            when {
                vence.isBefore(ahora) -> vencidas++
                dia == hoy -> vencenHoy++
                dia == hoy.plusDays(1) -> manana++
            }
        }
        // Correos sin leer y citas de hoy (si el usuario ha conectado Google)
        var correos = -1
        var citas = 0
        var primera = ""
        val url = p.getString("correo", "") ?: ""
        if (url.isNotEmpty()) {
            try {
                val j = JSONObject(Red.get(url, 9000))
                val arr = j.optJSONArray("correos")
                if (arr != null) {
                    correos = 0
                    for (i in 0 until arr.length()) if (arr.optJSONObject(i)?.optBoolean("sinLeer") == true) correos++
                }
                val zona = java.time.ZoneId.systemDefault()
                val iniHoy = hoy.atStartOfDay(zona).toInstant().toEpochMilli()
                val finHoy = hoy.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
                val ev = j.optJSONArray("eventos")
                if (ev != null) {
                    var primeraIni = Long.MAX_VALUE
                    for (i in 0 until ev.length()) {
                        val e = ev.optJSONObject(i) ?: continue
                        val ini = e.optLong("inicio")
                        val fin = e.optLong("fin")
                        val todo = e.optBoolean("todoElDia")
                        val esHoy = if (todo) ini < finHoy && fin > iniHoy else ini in iniHoy until finHoy
                        if (!esHoy) continue
                        citas++
                        if (!todo && ini < primeraIni) {
                            primeraIni = ini
                            val hora = java.time.Instant.ofEpochMilli(ini).atZone(zona).toLocalTime()
                            primera = String.format("%02d:%02d %s", hora.hour, hora.minute, e.optString("titulo"))
                        }
                    }
                }
            } catch (e: Exception) { }
        }
        val trozos = mutableListOf<String>()
        if (citas > 0) trozos.add("🗓️ $citas cita" + (if (citas > 1) "s" else "") + (if (primera.isNotEmpty()) " (primera: $primera)" else ""))
        if (vencidas > 0) trozos.add("⚠️ $vencidas vencida" + if (vencidas > 1) "s" else "")
        if (vencenHoy > 0) trozos.add("🔥 $vencenHoy vence" + (if (vencenHoy > 1) "n" else "") + " hoy")
        if (manana > 0) trozos.add("📅 $manana mañana")
        if (seguimiento > 0) trozos.add("👀 $seguimiento en seguimiento")
        if (correos > 0) trozos.add("📬 $correos correo" + (if (correos > 1) "s" else "") + " sin leer")
        val texto = if (trozos.isEmpty()) "☕ Día tranquilo: ni citas ni tareas que venzan hoy." else trozos.joinToString(" · ")

        val abrir = Intent(ctx, MainActivity::class.java)
            .setData(android.net.Uri.parse("despacho://informe"))
            .putExtra("vista", "informe")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, ID, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        Avisos.crearCanal(ctx)
        val n = NotificationCompat.Builder(ctx, Avisos.CANAL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFFC9973F.toInt())
            .setContentTitle("📰 Tu parte del día")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto + "\nToca para ver el informe."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(ID, n) } catch (e: SecurityException) { }
    }
}

class InformeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pendiente = goAsync()
        Thread {
            try {
                InformeDiario.mostrar(ctx.applicationContext)
            } catch (e: Exception) {
            } finally {
                InformeDiario.programar(ctx.applicationContext)
                pendiente.finish()
            }
        }.start()
    }
}
