package es.costalceleste.tareas

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Programa y muestra los avisos de vencimiento.
 * La interfaz envía la lista completa de avisos futuros; aquí se cancelan los anteriores
 * y se programan los nuevos con AlarmManager, para que salten aunque la app esté cerrada.
 */
object Avisos {
    const val CANAL = "vencimientos"
    private const val PREFS = "avisos"
    private const val LISTA = "lista"

    fun crearCanal(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val canal = NotificationChannel(CANAL, "Vencimientos de tareas", NotificationManager.IMPORTANCE_HIGH)
        canal.description = "Avisos antes de que venza una tarea"
        canal.enableVibration(true)
        nm.createNotificationChannel(canal)
    }

    private fun intentAviso(ctx: Context, o: JSONObject): Intent =
        Intent(ctx, AvisoReceiver::class.java)
            .setData(Uri.parse("despacho://aviso/" + o.getInt("id")))
            .putExtra("id", o.getInt("id"))
            .putExtra("titulo", o.optString("titulo"))
            .putExtra("texto", o.optString("texto"))
            .putExtra("tarea", o.optString("tarea"))

    fun sincronizar(ctx: Context, json: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Cancela los avisos programados antes
        val viejos = try { JSONArray(prefs.getString(LISTA, "[]")) } catch (e: Exception) { JSONArray() }
        for (i in 0 until viejos.length()) {
            val o = viejos.getJSONObject(i)
            val pi = PendingIntent.getBroadcast(
                ctx, o.getInt("id"), intentAviso(ctx, o),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) { am.cancel(pi); pi.cancel() }
        }
        val nuevos = try { JSONArray(json) } catch (e: Exception) { JSONArray() }
        prefs.edit().putString(LISTA, nuevos.toString()).apply()
        programarTodos(ctx, nuevos)
    }

    fun reprogramar(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lista = try { JSONArray(prefs.getString(LISTA, "[]")) } catch (e: Exception) { JSONArray() }
        programarTodos(ctx, lista)
    }

    private fun programarTodos(ctx: Context, lista: JSONArray) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val ahora = System.currentTimeMillis()
        val exactas = puedeExactas(ctx)
        for (i in 0 until lista.length()) {
            val o = lista.getJSONObject(i)
            val at = o.optLong("at", 0L)
            if (at <= ahora) continue
            val pi = PendingIntent.getBroadcast(
                ctx, o.getInt("id"), intentAviso(ctx, o),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (exactas) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    fun puedeExactas(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun mostrar(ctx: Context, id: Int, titulo: String, texto: String, tarea: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        crearCanal(ctx)
        val abrir = Intent(ctx, MainActivity::class.java)
            .setData(Uri.parse("despacho://tarea/$tarea"))
            .putExtra("tarea", tarea)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            ctx, id, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFFC9973F.toInt())
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) { }
    }
}

class AvisoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Avisos.mostrar(
            ctx,
            intent.getIntExtra("id", 1),
            intent.getStringExtra("titulo") ?: "Tarea a punto de vencer",
            intent.getStringExtra("texto") ?: "",
            intent.getStringExtra("tarea") ?: ""
        )
    }
}

/** Tras reiniciar el móvil o actualizar la app, Android borra las alarmas: se vuelven a programar. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Avisos.reprogramar(ctx)
    }
}
