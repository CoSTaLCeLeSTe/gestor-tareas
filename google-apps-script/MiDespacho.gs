// Mi Despacho · Gmail y Google Calendar
// Devuelve a la app un resumen de tus correos recientes (sin leer y destacados)
// y tus citas de hoy y mañana. Solo lee: no envía, no borra y no marca nada.
var CLAVE = '__CLAVE__';
var DIAS = 7;

function doGet(e) {
  var p = (e && e.parameter) || {};
  if (p.clave !== CLAVE) return salida({ ok: false, error: 'clave' });
  var r = { ok: true, generado: Date.now(), correos: [], eventos: [], avisos: [] };
  try { r.correos = correos(); r.sinLeerBandeja = GmailApp.getInboxUnreadCount(); }
  catch (err) { r.avisos.push('gmail: ' + err); }
  try { r.eventos = agenda(); }
  catch (err) { r.avisos.push('calendar: ' + err); }
  return salida(r);
}

function correos() {
  var consultas = [
    ['in:inbox is:unread newer_than:' + DIAS + 'd -category:promotions -category:social -category:forums', 30],
    ['is:starred newer_than:30d', 15]
  ];
  var vistos = {};
  var out = [];
  consultas.forEach(function (c) {
    GmailApp.search(c[0], 0, c[1]).forEach(function (hilo) {
      var id = hilo.getId();
      if (vistos[id]) return;
      vistos[id] = true;
      var msgs = hilo.getMessages();
      var m = msgs[msgs.length - 1];
      var cuerpo = (m.getPlainBody() || '').replace(/\s+/g, ' ').trim();
      var de = m.getFrom() || '';
      var email = (de.match(/<([^>]+)>/) || [null, de])[1];
      out.push({
        id: m.getId(),
        hilo: id,
        de: de.replace(/<[^>]*>/, '').replace(/"/g, '').trim() || email,
        email: email,
        asunto: hilo.getFirstMessageSubject() || '(sin asunto)',
        texto: cuerpo.slice(0, 700),
        fecha: m.getDate().getTime(),
        sinLeer: hilo.isUnread(),
        destacado: hilo.hasStarredMessages(),
        importante: hilo.isImportant(),
        mensajes: msgs.length
      });
    });
  });
  return out;
}

function agenda() {
  var ini = new Date();
  ini.setHours(0, 0, 0, 0);
  ini.setDate(ini.getDate() - 1);
  var fin = new Date(ini.getTime());
  fin.setDate(fin.getDate() + 4);
  var out = [];
  CalendarApp.getAllCalendars().forEach(function (cal) {
    try { if (cal.isHidden() || !cal.isSelected()) return; } catch (err) {}
    cal.getEvents(ini, fin).forEach(function (ev) {
      try { if (ev.getMyStatus() === CalendarApp.GuestStatus.NO) return; } catch (err) {}
      out.push({
        titulo: ev.getTitle() || '(sin título)',
        inicio: ev.getStartTime().getTime(),
        fin: ev.getEndTime().getTime(),
        todoElDia: ev.isAllDayEvent(),
        lugar: ev.getLocation() || '',
        calendario: cal.getName(),
        color: cal.getColor()
      });
    });
  });
  out.sort(function (a, b) { return a.inicio - b.inicio; });
  return out;
}

function salida(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

// Ejecuta esta función una vez desde el editor para dar permiso de lectura a tu Gmail y tu Calendar.
function probar() {
  var r = JSON.parse(doGet({ parameter: { clave: CLAVE } }).getContent());
  Logger.log('Correos: ' + r.correos.length + ' · Citas: ' + r.eventos.length + (r.avisos.length ? ' · ' + r.avisos.join(' | ') : ''));
}
