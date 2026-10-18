# 🗂️ Mi Despacho

Gestor personal de tareas para Android con ambiente de despacho: tablón de corcho, notas tipo post-it y avisos antes del vencimiento.

- **Tareas** con asunto (⚖️ UNED / Derecho, 👨‍👩‍👦 Familia y casa, 📑 Trámites y autónomo, 🏘️ Gestión casas, y los que añadas), criticidad (🔥 crítica, 🔴 alta, 🟡 media, 🟢 baja), fecha, notas y subtareas.
- **Estados**: ⏳ Pendiente, 👀 Seguimiento, ✅ Cerrado, con historial de cambios.
- **Avisos** aunque la app esté cerrada: 1 semana antes, 1 día antes, el mismo día, 2 horas antes o al vencer.
- **📰 Informe del día**: agenda de hoy de Google Calendar, foco del día, lo de mañana, lo que cerraste ayer y los correos de Gmail que piden algo (pagos, plazos, citas, notificaciones…), con botón para convertirlos en tarea.
- **Notificación diaria** a la hora que elijas con citas, tareas que vencen, vencidas y correos sin leer.
- **Escritorio**: pendientes, en seguimiento, cerradas, vencidas, próximos 14 días y avance por asunto.
- **Métricas**: cerradas en 7 y 30 días, % a tiempo, tiempo medio de cierre, racha, récord, creadas vs cerradas por semana, por asunto, por criticidad, por día de la semana y tareas atascadas.
- **Copia de seguridad** en archivo para guardar en Drive o WhatsApp.

## Instalar

Descarga `mi-despacho.apk` desde **Releases → Mi Despacho (última versión)** en el móvil e instálala. Las nuevas versiones se instalan encima sin perder las tareas.

## Conectar Gmail y Google Calendar

En la app: **Ajustes → Gmail y Google Calendar → Conectar** y sigue los 5 pasos. El código que hay que pegar en script.google.com está en `google-apps-script/MiDespacho.gs` (la app lo copia ya con tu clave). El script solo lee: no envía, no borra y no marca nada.

## Cómo está hecha

- `app/src/main/assets/index.html`: toda la interfaz (HTML, CSS y JS, sin conexión).
- `Avisos.kt`: programa las notificaciones con AlarmManager y las restaura al reiniciar el móvil.
- `Informe.kt`: notificación diaria y descarga del resumen de Google.
- `MainActivity.kt`: WebView y puente con Android (permisos, avisos, compartir copia).
- GitHub Actions compila la APK en cada cambio de `main`.
