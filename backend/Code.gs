// ===================== KosherTech Control Backend =====================
// ONE script = data store (Google Sheet) + device sync API + admin dashboard.
// Your email is already filled in below (mechelr362@gmail.com).
// 1) Deploy as a NEW VERSION of your existing deployment (keeps the same URL).
// 2) Open the URL in a browser to use the admin dashboard; the first password
//    you type there becomes your admin password.

var TO = "mechelr362@gmail.com";

// ---------- storage ----------
function ss_() {
  var id = PropertiesService.getScriptProperties().getProperty("SHEET_ID");
  var s;
  if (id) { s = SpreadsheetApp.openById(id); }
  else { s = SpreadsheetApp.create("KosherTech Control Data");
         PropertiesService.getScriptProperties().setProperty("SHEET_ID", s.getId()); }
  return s;
}
function tab_(name, headers) {
  var s = ss_(); var sh = s.getSheetByName(name);
  if (!sh) { sh = s.insertSheet(name); sh.appendRow(headers); }
  return sh;
}
function rows_(name, headers) {
  var sh = tab_(name, headers); var v = sh.getDataRange().getValues(); var out = [];
  var tz = Session.getScriptTimeZone();
  for (var i = 1; i < v.length; i++) {
    var o = {};
    for (var c = 0; c < headers.length; c++) {
      var val = v[i][c];
      if (val instanceof Date) val = Utilities.formatDate(val, tz, "MMM d, h:mm a");  // keep it a plain string
      else if (val === null || val === undefined) val = "";
      else val = String(val);
      o[headers[c]] = val;
    }
    o._row = i + 1; out.push(o);
  }
  return out;
}
var H_DEV = ["deviceId","name","model","plan","lastSeen"];
var H_REQ = ["id","deviceId","appName","reason","status","created"];
var H_APP = ["deviceId","appName","category","packageName","apkUrl","color"];
var H_CHAT= ["created","deviceId","sender","text"];
var H_ANN = ["deviceId","message","active","created"];

// ---------- admin password ----------
function checkKey_(key) {
  var p = PropertiesService.getScriptProperties();
  var k = p.getProperty("ADMIN_KEY");
  if (!k) { if (!key) return false; p.setProperty("ADMIN_KEY", String(key)); return true; }
  return String(key) === String(k);
}

// ===================== device-facing POST =====================
function doPost(e) {
  var out = { ok: false };
  try {
    var d = {};
    if (e && e.postData && e.postData.contents) { try { d = JSON.parse(e.postData.contents); } catch (x) { d = e.parameter || {}; } }
    else if (e && e.parameter) d = e.parameter;
    var action = d.action || inferAction_(d);
    if (action === "register") return json_(registerDevice_(d));
    if (action === "request")  return json_(newRequest_(d));
    if (action === "chat")     return json_(newChat_(d));
    if (action === "feedback") return json_(feedback_(d));
    out.error = "unknown action";
  } catch (err) { out.error = String(err); }
  return json_(out);
}
function inferAction_(d) {
  var t = (d.type || "").toLowerCase();
  if (t === "live chat") return "chat";
  return "feedback";
}
function registerDevice_(d) {
  var sh = tab_("Devices", H_DEV); var list = rows_("Devices", H_DEV);
  for (var i = 0; i < list.length; i++) if (list[i].deviceId === d.deviceId) {
    sh.getRange(list[i]._row, 1, 1, H_DEV.length)
      .setValues([[d.deviceId, list[i].name || d.name, d.model || list[i].model, list[i].plan, new Date()]]);
    return { ok: true };
  }
  sh.appendRow([d.deviceId, d.name || "", d.model || "", "", new Date()]);
  return { ok: true };
}
function newRequest_(d) {
  var id = "R" + Date.now();
  tab_("Requests", H_REQ).appendRow([id, d.deviceId || "", d.appName || d.message || "", d.reason || "", "Pending", new Date()]);
  emailSafe_("App request", "Device: " + (d.deviceId || "?") + "\nApp: " + (d.appName || d.message || "") + "\nReason: " + (d.reason || ""));
  return { ok: true, id: id };
}
function feedback_(d) {
  var type = d.type || "Message";
  emailSafe_(type, d.message || "");
  if (type.toLowerCase().indexOf("request") >= 0 && d.deviceId)
    tab_("Requests", H_REQ).appendRow(["R" + Date.now(), d.deviceId, d.message || "", "", "Pending", new Date()]);
  return { ok: true };
}
function newChat_(d) {
  var dev = d.deviceId || d.chatId || "";
  tab_("Chats", H_CHAT).appendRow([new Date(), dev, "user", d.message || ""]);
  emailSafe_("Live chat", "Device " + dev + ": " + (d.message || ""));
  return { ok: true };
}
function emailSafe_(subject, body) {
  try {
    var dest = TO; if (!dest || dest.indexOf("@") < 0) dest = Session.getEffectiveUser().getEmail();
    MailApp.sendEmail(dest, "KosherTech Hub: " + subject, body + "\n\n(KosherTech)");
  } catch (x) {}
}

// ===================== device-facing GET (sync) + dashboard =====================
function doGet(e) {
  var a = e && e.parameter ? e.parameter.action : null;
  if (a === "sync") return syncDevice_(e.parameter);
  if (a === "chat") return chatPoll_(e.parameter);
  return HtmlService.createHtmlOutput(ADMIN_HTML())
    .setTitle("KosherTech Admin").addMetaTag("viewport", "width=device-width, initial-scale=1");
}
function syncDevice_(p) {
  var dev = p.deviceId || "";
  try { if (dev) registerDevice_({ deviceId: dev }); } catch (x) {}
  var apps = rows_("Apps", H_APP).filter(function (r) { return r.deviceId === dev || r.deviceId === "*"; })
    .map(function (r) { return { name: r.appName, category: r.category, packageName: r.packageName, apkUrl: r.apkUrl, color: r.color }; });
  var reqs = rows_("Requests", H_REQ).filter(function (r) { return r.deviceId === dev; })
    .map(function (r) { return { appName: r.appName, status: r.status }; });
  var ann = rows_("Announcements", H_ANN).filter(function (r) { return (r.deviceId === dev || r.deviceId === "*") && r.active; })
    .map(function (r) { return r.message; });
  var chat = rows_("Chats", H_CHAT).filter(function (r) { return r.deviceId === dev; })
    .map(function (r) { return { from: r.sender, text: r.text }; });
  return json_({ ok: true, apps: apps, requests: reqs, announcements: ann, chat: chat });
}
function chatPoll_(p) {
  var dev = p.chatId || "";
  var msgs = rows_("Chats", H_CHAT).filter(function (r) { return r.deviceId === dev && r.sender === "admin"; })
    .map(function (r) { return { from: "admin", text: r.text }; });
  return json_({ ok: true, messages: msgs });
}
function json_(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }

// ===================== admin functions (called by the dashboard) =====================
function uiData(key) {
  try {
    if (!checkKey_(key)) return { ok: false, error: "Wrong password" };
    return { ok: true, devices: rows_("Devices", H_DEV), requests: rows_("Requests", H_REQ),
             apps: rows_("Apps", H_APP), chats: rows_("Chats", H_CHAT), announcements: rows_("Announcements", H_ANN) };
  } catch (err) { return { ok: false, error: String(err) }; }
}
function uiSetStatus(key, id, status) { if (!checkKey_(key)) return { ok: false };
  var sh = tab_("Requests", H_REQ), list = rows_("Requests", H_REQ);
  for (var i = 0; i < list.length; i++) if (list[i].id === id) { sh.getRange(list[i]._row, 5).setValue(status); return { ok: true }; }
  return { ok: false }; }
function uiPushApp(key, deviceId, appName, category, packageName, apkUrl, color) { if (!checkKey_(key)) return { ok: false };
  tab_("Apps", H_APP).appendRow([deviceId || "*", appName || "", category || "", packageName || "", apkUrl || "", color || "#22D3EE"]); return { ok: true }; }
function uiRemoveApp(key, deviceId, packageName) { if (!checkKey_(key)) return { ok: false };
  var sh = tab_("Apps", H_APP), list = rows_("Apps", H_APP);
  for (var i = list.length - 1; i >= 0; i--) if (list[i].packageName === packageName && list[i].deviceId === deviceId) sh.deleteRow(list[i]._row);
  return { ok: true }; }
function uiReply(key, deviceId, text) { if (!checkKey_(key)) return { ok: false };
  tab_("Chats", H_CHAT).appendRow([new Date(), deviceId || "", "admin", text || ""]); return { ok: true }; }
function uiAnnounce(key, deviceId, message) { if (!checkKey_(key)) return { ok: false };
  tab_("Announcements", H_ANN).appendRow([deviceId || "*", message || "", true, new Date()]); return { ok: true }; }
function uiDeleteRequest(key, id) { if (!checkKey_(key)) return { ok: false };
  var sh = tab_("Requests", H_REQ), list = rows_("Requests", H_REQ);
  for (var i = list.length - 1; i >= 0; i--) if (list[i].id === id) sh.deleteRow(list[i]._row);
  return { ok: true }; }
function uiDeleteChat(key, deviceId) { if (!checkKey_(key)) return { ok: false };
  var sh = tab_("Chats", H_CHAT), list = rows_("Chats", H_CHAT);
  for (var i = list.length - 1; i >= 0; i--) if (list[i].deviceId === deviceId) sh.deleteRow(list[i]._row);
  return { ok: true }; }
function uiRename(key, deviceId, name) { if (!checkKey_(key)) return { ok: false };
  var sh = tab_("Devices", H_DEV), list = rows_("Devices", H_DEV);
  for (var i = 0; i < list.length; i++) if (list[i].deviceId === deviceId) { sh.getRange(list[i]._row, 2).setValue(name); return { ok: true }; }
  return { ok: false }; }

// ===================== admin dashboard page =====================
function ADMIN_HTML() { return HTML_; }
var HTML_ =
'<!doctype html><html><head><meta charset="utf-8">' +
'<style>' +
'*{box-sizing:border-box}body{margin:0;font-family:-apple-system,Segoe UI,Roboto,sans-serif;background:#0B1330;color:#F3F7FF}' +
'.wrap{max-width:720px;margin:0 auto;padding:16px}' +
'h1{font-size:20px;margin:6px 0}h2{font-size:15px;color:#9fb0d8;margin:18px 0 8px;text-transform:uppercase;letter-spacing:.08em}' +
'.card{background:#0C1838;border:1px solid #1C2A52;border-radius:12px;padding:12px;margin-bottom:10px}' +
'input,select,textarea{width:100%;background:#0B1330;border:1px solid #2A3A63;color:#fff;border-radius:8px;padding:10px;margin:4px 0;font-size:14px}' +
'button{background:#22D3EE;color:#04121C;border:0;border-radius:8px;padding:9px 14px;font-weight:700;font-size:13px;cursor:pointer;margin:3px 4px 3px 0}' +
'button.ghost{background:transparent;color:#9fb0d8;border:1px solid #2A3A63}button.red{background:#E04F5F;color:#fff}button.green{background:#34D399;color:#04121C}' +
'.pill{display:inline-block;padding:2px 9px;border-radius:20px;font-size:12px;font-weight:700}' +
'.Pending{background:#3a2f12;color:#E0A52B}.Approved{background:#12331f;color:#34D399}.Declined{background:#331216;color:#E04F5F}' +
'.muted{color:#6E80A8;font-size:12px}.row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}' +
'.tab{display:inline-block;padding:8px 12px;margin:0 4px 8px 0;border-radius:8px;background:#0C1838;border:1px solid #1C2A52;cursor:pointer;font-weight:700;font-size:13px}' +
'.tab.on{background:#22D3EE;color:#04121C}.bubble{padding:8px 10px;border-radius:10px;margin:4px 0;max-width:80%}' +
'.bu{background:#182a52;margin-right:auto}.ba{background:#22D3EE;color:#04121C;margin-left:auto}' +
'</style></head><body><div class="wrap">' +
'<h1>🛡️ KosherTech Admin</h1>' +
'<div id="login"><div class="card"><div class="muted">Enter your admin password. The first password you ever enter here becomes the password.</div>' +
'<input id="key" type="password" placeholder="Admin password"><button onclick="login()">Open dashboard</button><div id="lerr" class="muted"></div></div></div>' +
'<div id="app" style="display:none">' +
'<div class="row"><span class="tab on" id="t_req" onclick="tab(\'req\')">Requests</span>' +
'<span class="tab" id="t_dev" onclick="tab(\'dev\')">Devices</span>' +
'<span class="tab" id="t_push" onclick="tab(\'push\')">Push app</span>' +
'<span class="tab" id="t_chat" onclick="tab(\'chat\')">Chats</span>' +
'<span class="tab" id="t_ann" onclick="tab(\'ann\')">Announce</span>' +
'<button class="ghost" onclick="load()">↻ Refresh</button></div>' +
'<input id="q" placeholder="🔍 Search by device name (shows that device\\\'s chats & requests)" oninput="if(DATA&&DATA.ok)render(DATA)">' +
'<div id="v_req"></div><div id="v_dev" style="display:none"></div><div id="v_push" style="display:none"></div>' +
'<div id="v_chat" style="display:none"></div><div id="v_ann" style="display:none"></div>' +
'</div>' +
'<script>' +
'var KEY="",DATA={};' +
'function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;");}' +
'function login(){KEY=document.getElementById("key").value;google.script.run.withSuccessHandler(function(r){' +
'if(r&&r.ok){try{localStorage.setItem("ktkey",KEY)}catch(e){}document.getElementById("login").style.display="none";document.getElementById("app").style.display="block";render(r);}' +
'else{document.getElementById("lerr").textContent=(r&&r.error)||"Error";}})' +
'.withFailureHandler(function(e){document.getElementById("lerr").textContent="Server error: "+(e&&e.message?e.message:e);}).uiData(KEY);}' +
'function load(){google.script.run.withSuccessHandler(function(r){if(r&&r.ok){render(r);}else{alert((r&&r.error)||"Could not load");}})' +
'.withFailureHandler(function(e){alert("Server error: "+(e&&e.message?e.message:e));}).uiData(KEY);}' +
'function render(r){if(!r||!r.ok)return;DATA=r;drawReq();drawDev();drawPush();drawChat();drawAnn();}' +
'function tab(n){["req","dev","push","chat","ann"].forEach(function(x){' +
'document.getElementById("v_"+x).style.display=(x===n?"block":"none");document.getElementById("t_"+x).className="tab"+(x===n?" on":"");});}' +
'function devName(id){var d=DATA.devices||[];for(var i=0;i<d.length;i++)if(d[i].deviceId===id)return d[i].name||id;return id;}' +
'function sq(){var e=document.getElementById("q");return e?e.value.trim().toLowerCase():"";}' +
'function match(dev){var s=sq();if(!s)return true;return (devName(dev)+" "+dev).toLowerCase().indexOf(s)>=0;}' +
'function drawReq(){var h="<h2>App requests</h2>";var arr=(DATA.requests||[]).slice().reverse();var n=0;' +
'arr.forEach(function(r){if(!match(r.deviceId))return;n++;h+=\'<div class="card"><div class="row"><b>\'+esc(r.appName)+\'</b> <span class="pill \'+esc(r.status)+\'">\'+esc(r.status)+\'</span></div>\'+' +
'\'<div class="muted">\'+esc(devName(r.deviceId))+(r.reason?" — "+esc(r.reason):"")+\'</div>\'+' +
'\'<button class="green" onclick="setStatus(\\\'\'+r.id+\'\\\',\\\'Approved\\\')">Approve</button>\'+' +
'\'<button class="red" onclick="setStatus(\\\'\'+r.id+\'\\\',\\\'Declined\\\')">Decline</button>\'+' +
'\'<button class="ghost" onclick="delReq(\\\'\'+r.id+\'\\\')">Delete</button></div>\';});' +
'if(!n)h+=\'<div class="muted">No requests.</div>\';document.getElementById("v_req").innerHTML=h;}' +
'function setStatus(id,s){google.script.run.withSuccessHandler(load).uiSetStatus(KEY,id,s);}' +
'function delReq(id){google.script.run.withSuccessHandler(load).uiDeleteRequest(KEY,id);}' +
'function drawDev(){var h="<h2>Devices</h2>";var n=0;(DATA.devices||[]).forEach(function(d){if(!match(d.deviceId))return;n++;' +
'h+=\'<div class="card"><div class="muted">\'+esc(d.model)+\' · \'+esc(d.deviceId)+\'</div>\'+' +
'\'<input id="n_\'+esc(d.deviceId)+\'" value="\'+esc(d.name||"")+\'">\'+' +
'\'<button onclick="rename(\\\'\'+esc(d.deviceId)+\'\\\')">Save name</button></div>\';});' +
'if(!n)h+=\'<div class="muted">No devices.</div>\';document.getElementById("v_dev").innerHTML=h;}' +
'function rename(dev){var v=document.getElementById("n_"+dev).value;google.script.run.withSuccessHandler(load).uiRename(KEY,dev,v);}' +
'function drawPush(){var opts=\'<option value="*">All devices</option>\';(DATA.devices||[]).forEach(function(d){opts+=\'<option value="\'+esc(d.deviceId)+\'">\'+esc(d.name||d.deviceId)+\'</option>\';});' +
'var h=\'<h2>Push an app to a device</h2><div class="card">\'+' +
'\'<select id="p_dev">\'+opts+\'</select>\'+' +
'\'<input id="p_name" placeholder="App name (e.g. Zemer)">\'+' +
'\'<input id="p_cat" placeholder="Category (e.g. Music · Jewish songs)">\'+' +
'\'<input id="p_pkg" placeholder="Package name (e.g. com.jtech.zemer)">\'+' +
'\'<input id="p_url" placeholder="APK link (https://...) — leave blank to send a request only">\'+' +
'\'<input id="p_color" placeholder="Color hex (e.g. #E0A52B)" value="#22D3EE">\'+' +
'\'<button onclick="pushApp()">Push app</button></div>\'+' +
'\'<h2>Apps currently pushed</h2>\';(DATA.apps||[]).forEach(function(a){' +
'h+=\'<div class="card"><div class="row"><b>\'+esc(a.appName)+\'</b> <span class="muted">\'+esc(devName(a.deviceId))+\'</span></div>\'+' +
'\'<div class="muted">\'+esc(a.packageName)+\'</div><button class="red" onclick="rmApp(\\\'\'+esc(a.deviceId)+\'\\\',\\\'\'+esc(a.packageName)+\'\\\')">Remove</button></div>\';});' +
'document.getElementById("v_push").innerHTML=h;}' +
'function pushApp(){google.script.run.withSuccessHandler(function(){alert("Pushed");load();}).uiPushApp(KEY,' +
'document.getElementById("p_dev").value,document.getElementById("p_name").value,document.getElementById("p_cat").value,' +
'document.getElementById("p_pkg").value,document.getElementById("p_url").value,document.getElementById("p_color").value);}' +
'function rmApp(dev,pkg){google.script.run.withSuccessHandler(load).uiRemoveApp(KEY,dev,pkg);}' +
'function drawChat(){var byDev={};(DATA.chats||[]).forEach(function(c){(byDev[c.deviceId]=byDev[c.deviceId]||[]).push(c);});' +
'var h="<h2>Live chats</h2>";var any=false;for(var dev in byDev){if(!match(dev))continue;any=true;h+=\'<div class="card"><b>\'+esc(devName(dev))+\'</b>\';' +
'byDev[dev].forEach(function(c){h+=\'<div class="bubble \'+(c.sender==="admin"?"ba":"bu")+\'">\'+esc(c.text)+\'</div>\';});' +
'h+=\'<input id="r_\'+esc(dev)+\'" placeholder="Type a reply...">\'+' +
'\'<button onclick="reply(\\\'\'+esc(dev)+\'\\\')">Send reply</button>\'+' +
'\'<button class="red" onclick="delChat(\\\'\'+esc(dev)+\'\\\')">Delete chat</button></div>\';}' +
'if(!any)h+=\'<div class="muted">No chats.</div>\';document.getElementById("v_chat").innerHTML=h;}' +
'function reply(dev){var t=document.getElementById("r_"+dev).value;if(!t)return;' +
'google.script.run.withSuccessHandler(load).uiReply(KEY,dev,t);}' +
'function delChat(dev){google.script.run.withSuccessHandler(load).uiDeleteChat(KEY,dev);}' +
'function drawAnn(){var opts=\'<option value="*">All devices</option>\';(DATA.devices||[]).forEach(function(d){opts+=\'<option value="\'+esc(d.deviceId)+\'">\'+esc(d.name||d.deviceId)+\'</option>\';});' +
'var h=\'<h2>Send an announcement</h2><div class="card"><select id="a_dev">\'+opts+\'</select>\'+' +
'\'<textarea id="a_msg" placeholder="Message to show on the home screen"></textarea>\'+' +
'\'<button onclick="announce()">Send announcement</button></div>\';document.getElementById("v_ann").innerHTML=h;}' +
'function announce(){var m=document.getElementById("a_msg").value;if(!m)return;' +
'google.script.run.withSuccessHandler(function(){alert("Sent");load();}).uiAnnounce(KEY,document.getElementById("a_dev").value,m);}' +
'try{var k=localStorage.getItem("ktkey");if(k){document.getElementById("key").value=k;}}catch(e){}' +
'</script></div></body></html>';
