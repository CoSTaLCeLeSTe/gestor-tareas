# 🗂️ Mi Despacho

Gestor personal de tareas para Android con ambiente de despacho: tablón de corcho, notas tipo post-it y avisos antes del vencimiento.

- **Tareas** con asunto (⚖️ UNED / Derecho, 👨‍👩‍👦 Familia y casa, 📑 Trámites y autónomo, 🏘️ Gestión casas, y los que añadas), criticidad (🔥 crítica, 🔴 alta, 🟡 media, 🟢 baja), fecha, notas y subtareas.
- **Estados**: ⏳ Pendiente, 👀 Seguimiento, ✅ Cerrado, con historial de cambios.
- **Avisos** aunque la app esté cerrada: 1 semana antes, 1 día antes, el mismo día, 2 horas antes o al vencer.
- **Escritorio**: pendientes, en seguimiento, cerradas, vencidas, próximos 14 días y avance por asunto.
- **Métricas**: cerradas en 7 y 30 días, % a tiempo, tiempo medio de cierre, racha, récord, creadas vs cerradas por semana, por asunto, por criticidad, por día de la semana y tareas atascadas.
- **Copia de seguridad** en archivo para guardar en Drive o WhatsApp.

## Instalar

Descarga `mi-despacho.apk` desde **Releases → Mi Despacho (última versión)** en el móvil e instálala. Las nuevas versiones se instalan encima sin perder las tareas.

## Cómo está hecha

- `app/src/main/assets/index.html`: toda la interfaz (HTML, CSS y JS, sin conexión).
- `Avisos.kt`: programa las notificaciones con AlarmManager y las restaura al reiniciar el móvil.
- `MainActivity.kt`: WebView y puente con Android (permisos, avisos, compartir copia).
- GitHub Actions compila la APK en cada cambio de `main`.
