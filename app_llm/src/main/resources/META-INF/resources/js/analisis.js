"use strict";
// ---------------------------------------------------------------------
// Sección «Análisis y evolución» (docentes): importación de CSV, evolución
// agregada con alertas tempranas y simulador. Reutiliza las utilidades de
// index.html: estado, $, el, api, fmt, conSigno, css, pintar, vacio, nombreAmbito.
// ---------------------------------------------------------------------

const analisis = { vista: "panel", visible: false, periodos: null, campos: null, previa: null, factores: [], simTimer: null };
const CABECERA_INGESTA = { "X-Hack4Edu": "1" };

// Igual que api(), pero conserva el cuerpo del error (bloqueos de la confirmación)
async function apiIngesta(ruta, opciones = {}) {
  const r = await fetch(ruta, { ...opciones, headers: { ...CABECERA_INGESTA, ...(opciones.headers || {}) } });
  let cuerpo = null;
  try { cuerpo = await r.json(); } catch (_) { /* sin cuerpo */ }
  if (!r.ok) {
    const e = new Error(r.status === 401 ? "Hace falta iniciar sesión como docente." : (cuerpo && cuerpo.error) || `Error ${r.status}`);
    e.cuerpo = cuerpo;
    throw e;
  }
  return cuerpo;
}

const lista = (items) => el("ul", {}, ...items.map((t) => el("li", {}, t)));
const fechaHora = (iso) => iso ? new Date(iso).toLocaleString("es-ES", { dateStyle: "medium", timeStyle: "short" }) : "–";

// ---------------------------------------------------------------------
// Navegación entre pestañas
// ---------------------------------------------------------------------
const VISTAS = {
  panel: ["tabPanel", "vistaPanel"],
  analisis: ["tabAnalisis", "vistaAnalisis"],
  semanal: ["tabSemanal", "vistaSemanal"],
  direccion: ["tabDireccion", "vistaDireccion"],
  medidas: ["tabMedidas", "vistaMedidas"]
};

function mostrarVista(nombre) {
  analisis.vista = nombre;
  analisis.visible = nombre === "analisis";
  for (const [n, [tab, vista]] of Object.entries(VISTAS)) {
    $(tab).setAttribute("aria-selected", String(n === nombre));
    $(vista).hidden = n !== nombre;
  }
  if (nombre === "panel") return;
  // Estas secciones necesitan un centro (dirección) o un grupo (aula): con el rol Administración se cambia de rol
  if (estado.rol === "global") {
    aplicarRol(nombre === "direccion" ? "centro" : "grupo"); // llama a alCambiarAmbito
  } else {
    window.alCambiarAmbito();
  }
}

window.alCambiarAmbito = () => {
  const cargas = { analisis: cargarAnalisis, semanal: window.cargarSemanal, direccion: window.cargarDireccion, medidas: window.cargarMedidas };
  const f = cargas[analisis.vista];
  if (f) f();
};

function cargarAnalisis() {
  actualizarDestino();
  cargarPeriodos();
  cargarEvolucion();
  cargarFactoresSimulador();
}

// ---------------------------------------------------------------------
// A · Importación de datos
// ---------------------------------------------------------------------
function actualizarDestino() {
  const destino = $("importDestino");
  const listo = estado.rol === "grupo" && estado.grupo != null;
  destino.replaceChildren(listo
    ? el("span", {}, "Los datos se importarán en ", el("strong", {}, nombreAmbito()), ". Para otro grupo, cámbialo en la barra superior.")
    : el("span", { class: "error" }, "Para importar, elige el rol «Docente» y un grupo en la barra superior."));
  $("btnValidar").disabled = !listo || !$("archivoCsv").files.length;
}

async function cargarPeriodos() {
  if (analisis.periodos) return;
  const sel = $("selPeriodo");
  try {
    analisis.periodos = await apiIngesta("/api/ingesta/periodos");
    sel.replaceChildren(...analisis.periodos.map((p) => el("option", { value: p.id }, p.nombre + (p.actual ? " (actual)" : ""))));
    const actual = analisis.periodos.find((p) => p.actual);
    if (actual) sel.value = actual.id;
    analisis.campos = await apiIngesta("/api/ingesta/campos");
    cargarHistorial();
  } catch (e) {
    sel.replaceChildren(el("option", { value: "" }, "—"));
    $("estadoImport").textContent = e.message;
  }
}

async function validarArchivo() {
  const archivo = $("archivoCsv").files[0];
  if (!archivo) return;
  if (archivo.size > 2 * 1024 * 1024) {
    $("vistaPrevia").replaceChildren(el("div", { class: "caja bloqueo" }, "El archivo ocupa más de 2 MB. Divídelo por grupos."));
    return;
  }
  await descartarPrevia();
  const datos = new FormData();
  datos.append("archivo", archivo);
  datos.append("centro", estado.centro);
  datos.append("grupo", estado.grupo);
  datos.append("periodo", $("selPeriodo").value);
  ocupado(true, "Validando…");
  try {
    analisis.previa = await apiIngesta("/api/ingesta", { method: "POST", body: datos });
    pintarPrevia();
  } catch (e) {
    $("vistaPrevia").replaceChildren(el("div", { class: "caja bloqueo" }, e.message));
  } finally {
    ocupado(false, "");
    cargarHistorial();
  }
}

function ocupado(si, texto) {
  $("btnValidar").disabled = si || !$("archivoCsv").files.length || estado.rol !== "grupo";
  $("estadoImport").textContent = texto;
}

function pintarPrevia() {
  const p = analisis.previa;
  const r = p.resumen;
  const cont = $("vistaPrevia");
  const partes = [];

  partes.push(el("h3", {}, `Vista previa de «${p.archivo}» · ${p.ambito} · ${p.periodo}`));
  partes.push(el("p", { class: "nota" }, `Codificación ${p.codificacion}, separador ${p.separador}. La vista previa caduca a las ${new Date(p.expira).toLocaleTimeString("es-ES", { timeStyle: "short" })}.`));
  partes.push(el("p", {}, el("strong", {}, p.resumenTexto + ".")));
  partes.push(el("div", { class: "kpis compactos" },
    kpi("Filas detectadas", fmt(r.filasTotal, 0)),
    kpi("Registros válidos", fmt(r.filasValidas, 0)),
    kpi("Con campos vacíos", fmt(r.filasConVacios, 0)),
    kpi("Errores de formato", fmt(r.erroresFormato, 0), null, false),
    kpi("Fuera de rango", fmt(r.fueraDeRango, 0)),
    kpi("Sin nota final", fmt(r.obligatoriosVacios, 0))
  ));

  if (p.bloqueos.length) {
    partes.push(el("div", { class: "caja bloqueo", role: "alert" }, el("strong", {}, "No se puede importar todavía. Cómo corregirlo:"), lista(p.bloqueos)));
  } else {
    partes.push(el("div", { class: "caja ok" }, "El archivo supera la validación. Revisa la muestra y confirma la importación."));
  }
  if (p.avisos.length) partes.push(el("div", { class: "aviso" }, lista(p.avisos)));

  partes.push(tablaMapeo(p));
  if (p.incidencias.length) partes.push(tablaIncidencias(p));
  partes.push(tablaMuestra(p));

  const confirmar = el("button", { class: "boton", type: "button" }, `Confirmar importación de ${fmt(r.filasValidas, 0)} registros`);
  confirmar.disabled = !p.puedeConfirmar;
  confirmar.addEventListener("click", confirmarImportacion);
  const cancelar = el("button", { class: "boton peligro", type: "button" }, "Cancelar");
  cancelar.addEventListener("click", async () => { await descartarPrevia(); cont.replaceChildren(el("p", { class: "nota" }, "Importación cancelada. No se ha guardado nada.")); cargarHistorial(); });
  partes.push(el("div", { class: "fila" }, confirmar, cancelar, el("span", { class: "nota", id: "estadoConfirmar", "aria-live": "polite" })));

  cont.replaceChildren(...partes);
}

function tablaMapeo(p) {
  const campos = analisis.campos || [];
  const obligatorios = campos.filter((c) => c.obligatorio).map((c) => c.etiqueta).join(" y ");
  const selects = [];
  const filas = p.columnas.map((c) => {
    const bloqueada = c.estado === "IDENTIFICATIVA" || c.estado === "SENSIBLE";
    const sel = el("select", { "aria-label": `Campo interno para la columna ${c.nombre}` },
      el("option", { value: "" }, "— Ignorar —"),
      ...campos.map((f) => el("option", { value: f.codigo, title: f.descripcion }, f.etiqueta + (f.obligatorio ? " *" : ""))));
    sel.value = c.campo || "";
    sel.disabled = bloqueada;
    sel.addEventListener("change", () => marcarDuplicados(selects));
    selects.push(sel);
    const estados = { AUTO: "detectada", MANUAL: "manual", SIN_ASIGNAR: "ignorada", IDENTIFICATIVA: "identificativa", SENSIBLE: "sensible", DERIVADA: "no necesaria" };
    return el("tr", {}, el("td", {}, c.nombre), el("td", {}, sel),
      el("td", {}, el("span", { class: "estado-col " + c.estado, title: c.nota || "" }, estados[c.estado] || c.estado)));
  });
  marcarDuplicados(selects);
  const aplicar = el("button", { class: "boton secundario", type: "button" }, "Aplicar correspondencias y volver a validar");
  aplicar.addEventListener("click", () => remapear(selects.map((s) => s.disabled ? null : s.value || null)));
  return el("div", {},
    el("h3", {}, "Correspondencia de columnas"),
    el("p", { class: "nota" }, `Asigna cada columna de tu archivo a un campo interno. * Obligatorios: ${obligatorios}. Las columnas identificativas (nombre, DNI, correo…) y las sensibles nunca se importan.`),
    el("div", { class: "tabla-scroll" }, el("table", {},
      el("thead", {}, el("tr", {}, el("th", {}, "Columna del archivo"), el("th", {}, "Campo interno"), el("th", {}, "Estado"))),
      el("tbody", {}, ...filas))),
    el("div", { class: "fila" }, aplicar));
}

function marcarDuplicados(selects) {
  const cuenta = {};
  selects.forEach((s) => { if (s.value) cuenta[s.value] = (cuenta[s.value] || 0) + 1; });
  selects.forEach((s) => {
    const dup = s.value && cuenta[s.value] > 1;
    s.classList.toggle("duplicado", !!dup);
    s.title = dup ? "Este campo está asignado a más de una columna" : "";
  });
}

function tablaIncidencias(p) {
  const tipos = { FORMATO: "Formato", RANGO: "Fuera de rango", OBLIGATORIO_VACIO: "Obligatorio vacío", ESTRUCTURA: "Estructura" };
  const mostradas = p.incidencias.slice(0, 50);
  const resto = p.incidencias.length - mostradas.length + p.incidenciasOmitidas;
  return el("div", {},
    el("h3", {}, "Incidencias"),
    el("div", { class: "tabla-scroll" }, el("table", {},
      el("thead", {}, el("tr", {}, el("th", { class: "num" }, "Línea"), el("th", {}, "Columna"), el("th", {}, "Tipo"), el("th", {}, "Detalle"))),
      el("tbody", {}, ...mostradas.map((i) => el("tr", {},
        el("td", { class: "num" }, String(i.linea)), el("td", {}, i.columna || "—"), el("td", {}, tipos[i.tipo] || i.tipo), el("td", {}, i.mensaje)))))),
    resto > 0 ? el("p", { class: "nota" }, `… y ${fmt(resto, 0)} incidencias más del mismo tipo.`) : "");
}

function tablaMuestra(p) {
  const m = p.muestra;
  if (!m.filas.length) return el("p", { class: "nota" }, "No hay registros válidos que mostrar.");
  return el("div", {},
    el("h3", {}, `Muestra (${m.filas.length} primeros registros válidos, ya normalizados)`),
    el("div", { class: "tabla-scroll" }, el("table", {},
      el("thead", {}, el("tr", {}, ...m.etiquetas.map((e) => el("th", {}, e)))),
      el("tbody", {}, ...m.filas.map((f) => el("tr", {}, ...f.map((v) => el("td", {}, v == null ? "(vacío)" : v))))))));
}

async function remapear(campos) {
  const p = analisis.previa;
  if (!p) return;
  try {
    analisis.previa = await apiIngesta(`/api/ingesta/${p.token}/mapeo`, {
      method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ campos })
    });
    pintarPrevia();
  } catch (e) {
    $("vistaPrevia").prepend(el("div", { class: "caja bloqueo" }, e.message));
  }
}

async function confirmarImportacion() {
  const p = analisis.previa;
  if (!p) return;
  const estadoTxt = $("estadoConfirmar");
  estadoTxt.textContent = "Importando…";
  document.querySelectorAll("#vistaPrevia button").forEach((b) => { b.disabled = true; });
  try {
    const r = await apiIngesta(`/api/ingesta/${p.token}/confirmar`, { method: "POST" });
    analisis.previa = null;
    $("archivoCsv").value = "";
    $("nombreArchivo").textContent = "Ningún archivo elegido";
    $("vistaPrevia").replaceChildren(el("div", { class: "caja ok", role: "status" },
      el("strong", {}, "Importación completada. "), r.mensaje, ` (carga nº ${r.cargaId}).`));
    cargarHistorial();
    cargarAmbito();   // panel general + evolución + simulador con los datos nuevos
    cargarCalidad();
  } catch (e) {
    const bloqueos = e.cuerpo && e.cuerpo.bloqueos;
    $("vistaPrevia").prepend(el("div", { class: "caja bloqueo", role: "alert" }, e.message, bloqueos ? lista(bloqueos) : ""));
    estadoTxt.textContent = "";
    if (!bloqueos) analisis.previa = null; // la carga quedó registrada como error
  }
}

async function descartarPrevia() {
  const p = analisis.previa;
  analisis.previa = null;
  if (!p) return;
  try { await apiIngesta(`/api/ingesta/${p.token}`, { method: "DELETE" }); } catch (_) { /* ya caducada */ }
}

async function cargarHistorial() {
  const cont = $("historial");
  try {
    const cargas = await apiIngesta("/api/ingesta/historial");
    if (!cargas.length) { cont.replaceChildren(el("p", { class: "nota" }, "Todavía no hay importaciones.")); return; }
    const estados = { completada: "completada", pendiente: "pendiente de confirmar", rechazada: "no válida", sustituida: "sustituida por otra", cancelada: "cancelada", caducada: "caducada", error: "error" };
    cont.replaceChildren(el("div", { class: "tabla-scroll" }, el("table", {},
      el("thead", {}, el("tr", {}, el("th", {}, "Fecha"), el("th", {}, "Usuario"), el("th", {}, "Archivo"), el("th", {}, "Grupo · periodo"),
        el("th", { class: "num" }, "Procesados"), el("th", { class: "num" }, "Válidos"), el("th", { class: "num" }, "Con error"), el("th", {}, "Estado"))),
      el("tbody", {}, ...cargas.map((c) => el("tr", { title: c.resultado || "" },
        el("td", {}, fechaHora(c.fecha)), el("td", {}, c.usuario || "—"), el("td", {}, c.archivo),
        el("td", {}, `${c.centro} · ${c.grupo} · ${c.periodo}`),
        el("td", { class: "num" }, fmt(c.filasProcesadas, 0)), el("td", { class: "num" }, fmt(c.filasValidas, 0)),
        el("td", { class: "num" }, fmt(c.filasError, 0)), el("td", {}, estados[c.estado] || c.estado)))))));
  } catch (e) {
    cont.replaceChildren(el("p", { class: "nota" }, e.message));
  }
}

// ---------------------------------------------------------------------
// B · Evolución y alertas tempranas
// ---------------------------------------------------------------------
async function cargarEvolucion() {
  const t = nuevoTurno("evolucion");
  const esGrupo = estado.grupo != null;
  $("tituloEvolucion").textContent = esGrupo ? "Evolución del grupo" : "Evolución del centro";
  if (estado.centro == null) {
    $("tituloEvolucion").textContent = "Evolución";
    $("notaEvolucion").textContent = "La evolución se calcula por centro o por grupo, no para el conjunto de centros.";
    $("ultimaAct").textContent = "";
    $("avisoEvolucion").textContent = "";
    $("kpisEvolucion").replaceChildren();
    $("tablaEvolucion").replaceChildren();
    $("alertas").replaceChildren();
    $("mensajeDatos").textContent = "Elige el rol «Dirección» (un centro) o «Docente» (un grupo) en la barra superior.";
    vacio("gEvolucion", "Elige el rol Dirección o Docente para ver la evolución.");
    return;
  }
  try {
    const d = await api("/api/evolucion" + consultaAmbito());
    if (!vigente("evolucion", t)) return;
    $("notaEvolucion").textContent = `${d.ambito}: indicadores agregados por periodo.` +
      (d.periodoAnterior ? ` Comparación: «${d.periodoActual}» frente a «${d.periodoAnterior}».` : "");
    $("ultimaAct").textContent = d.ultimaActualizacion ? "Actualizado: " + fechaHora(d.ultimaActualizacion) : "";
    $("avisoEvolucion").textContent = d.aviso;
    pintarComparaciones(d);
    pintarGraficaEvolucion(d);
    pintarAlertas(d);
  } catch (e) {
    if (!vigente("evolucion", t)) return;
    $("mensajeDatos").textContent = "No se pudo cargar la evolución: " + e.message;
    vacio("gEvolucion", "Sin datos.");
  }
}

function pintarComparaciones(d) {
  const unidadVar = (c) => c.unidad === "%" ? " pp" : c.unidad ? " " + c.unidad : "";
  const dec = (c) => c.codigo === "n_alumnos" ? 0 : c.codigo === "tutorias_media" ? 2 : 1;
  $("kpisEvolucion").replaceChildren(...d.comparaciones.filter((c) => c.codigo !== "horas_estudio_media").map((c) =>
    kpi(c.indicador, c.actual == null ? "–" : fmt(c.actual, dec(c)) + (c.unidad === "%" ? " %" : ""),
      c.variacion == null ? { texto: d.periodoActual || "" } : { dif: c.variacion, texto: `${conSigno(c.variacion, dec(c))}${unidadVar(c)} frente al periodo anterior` },
      c.mejorSiSube)));
  const filas = d.serie.slice().reverse();
  $("tablaEvolucion").replaceChildren(filas.length ? el("table", {},
    el("thead", {}, el("tr", {}, el("th", {}, "Periodo"), el("th", { class: "num" }, "Registros"), el("th", { class: "num" }, "Nota media"),
      el("th", { class: "num" }, "Asistencia"), el("th", { class: "num" }, "< 80 %"))),
    el("tbody", {}, ...filas.map((p) => el("tr", {},
      el("td", {}, p.periodo), el("td", { class: "num" }, fmt(p.nAlumnos, 0)), el("td", { class: "num" }, fmt(p.notaMedia, 1)),
      el("td", { class: "num" }, p.asistenciaMedia == null ? "–" : fmt(p.asistenciaMedia, 1) + " %"),
      el("td", { class: "num" }, p.pctAsistenciaBaja == null ? "–" : fmt(p.pctAsistenciaBaja, 1) + " %")))))
    : el("p", { class: "vacio" }, "Sin periodos con al menos 10 registros."));
}

function pintarGraficaEvolucion(d) {
  if (!d.serie.length) { vacio("gEvolucion", "Sin datos publicables para este ámbito."); return; }
  const periodos = new Map();
  [...d.serie, ...d.serieCentro].forEach((p) => periodos.set(p.periodoId, p));
  const ejes = [...periodos.values()].sort((a, b) => a.orden - b.orden);
  const valor = (serie, campo) => ejes.map((p) => { const x = serie.find((s) => s.periodoId === p.periodoId); return x ? x[campo] : null; });
  const datasets = [
    { label: "Nota media", data: valor(d.serie, "notaMedia"), borderColor: css("--c1"), backgroundColor: css("--c1"), yAxisID: "y", tension: .25, spanGaps: true },
    { label: "Asistencia media (%)", data: valor(d.serie, "asistenciaMedia"), borderColor: css("--c3"), backgroundColor: css("--c3"), yAxisID: "y2", tension: .25, spanGaps: true }
  ];
  if (d.serieCentro.length) {
    datasets.push({ label: "Nota media del centro", data: valor(d.serieCentro, "notaMedia"), borderColor: css("--suave"), backgroundColor: css("--suave"), borderDash: [5, 4], yAxisID: "y", tension: .25, spanGaps: true, pointRadius: 2 });
  }
  pintar("gEvolucion", {
    type: "line",
    data: { labels: ejes.map((p) => p.sintetico ? "Línea base" : p.periodo.replace(" · ", " ")), datasets },
    options: {
      plugins: { tooltip: { callbacks: { afterBody: (i) => { const p = d.serie.find((s) => s.periodoId === ejes[i[0].dataIndex].periodoId); return p ? [`${fmt(p.nAlumnos, 0)} registros`] : []; } } } },
      scales: {
        y: { title: { display: true, text: "Nota media" } },
        y2: { position: "right", title: { display: true, text: "Asistencia (%)" }, grid: { drawOnChartArea: false } },
        x: { grid: { display: false } }
      }
    }
  });
}

function pintarAlertas(d) {
  $("mensajeDatos").textContent = d.mensajeDatos || "";
  const niveles = { ATENCION: "Requiere atención", INFORMATIVA: "Informativa", POSITIVA: "Mejora" };
  const tarjetas = d.alertas.map((a) => el("article", { class: "alerta " + a.nivel },
    el("div", { class: "titulo" }, a.mensaje, el("span", { class: "chip" }, niveles[a.nivel] || a.nivel)),
    el("dl", {},
      el("dt", {}, "Indicador"), el("dd", {}, a.indicador),
      el("dt", {}, "Ámbito"), el("dd", {}, a.ambito),
      el("dt", {}, "Periodo analizado"), el("dd", {}, a.periodo),
      el("dt", {}, "Variación"), el("dd", {}, `${conSigno(a.variacion, 1)} ${a.unidad} (de ${fmt(a.valorReferencia, 1)} a ${fmt(a.valorActual, 1)})`),
      el("dt", {}, "Contexto"), el("dd", {}, a.contexto),
      el("dt", {}, "Actualizado"), el("dd", {}, fechaHora(a.actualizado)))));
  if (d.orientacion) tarjetas.push(el("div", { class: "caja info" }, d.orientacion));
  $("alertas").replaceChildren(...tarjetas);
}

// ---------------------------------------------------------------------
// C · Simulador
// ---------------------------------------------------------------------
async function cargarFactoresSimulador() {
  const t = nuevoTurno("simFactores");
  try {
    const factores = await api("/api/simulador/factores" + consultaAmbito());
    if (!vigente("simFactores", t)) return;
    analisis.factores = factores;
    const sel = $("simFactor");
    const previo = sel.value || "tutorias";
    sel.replaceChildren(...factores.map((f) => el("option", { value: f.codigo }, f.nombre + (f.disponibleEnAmbito ? "" : " (sin datos suficientes aquí)"))));
    sel.value = factores.some((f) => f.codigo === previo) ? previo : factores[0].codigo;
    rellenarNivelesSim(previo === "tutorias" && sel.value === "tutorias");
    simular();
  } catch (e) {
    $("simTexto").replaceChildren(el("p", { class: "error" }, e.message));
  }
}

function rellenarNivelesSim(ejemploTutorias) {
  const f = analisis.factores.find((x) => x.codigo === $("simFactor").value);
  if (!f) return;
  const opciones = () => f.niveles.map((n) => el("option", { value: n.orden }, n.etiqueta));
  const desde = $("simDesde"), hacia = $("simHacia");
  const d0 = desde.value, h0 = hacia.value;
  desde.replaceChildren(...opciones());
  hacia.replaceChildren(...opciones());
  if (ejemploTutorias || !f.niveles.some((n) => String(n.orden) === d0)) {
    // Por defecto, el ejemplo del enunciado (tutorías de 1 a 2) o del primer nivel al segundo
    const [a, b] = f.codigo === "tutorias" ? [2, 3] : [f.niveles[0].orden, f.niveles[1].orden];
    desde.value = a;
    hacia.value = b;
  } else {
    desde.value = d0;
    hacia.value = h0;
  }
}

function programarSimulacion() {
  clearTimeout(analisis.simTimer);
  analisis.simTimer = setTimeout(simular, 200);
}

async function simular() {
  const t = nuevoTurno("simulador");
  $("simPctTxt").textContent = $("simPct").value + " %";
  const q = new URLSearchParams(consultaAmbito().slice(1));
  q.set("factor", $("simFactor").value);
  q.set("desde", $("simDesde").value);
  q.set("hacia", $("simHacia").value);
  q.set("porcentaje", $("simPct").value);
  try {
    const r = await api("/api/simulador?" + q);
    if (!vigente("simulador", t)) return;
    pintarSimulacion(r);
  } catch (e) {
    if (!vigente("simulador", t)) return;
    $("simKpis").replaceChildren();
    $("simTexto").replaceChildren(el("div", { class: "caja bloqueo" }, e.message));
    vacio("gSimulador", "Sin simulación.");
  }
}

function pintarSimulacion(r) {
  const fiabilidad = r.fiabilidad ? r.fiabilidad[0].toUpperCase() + r.fiabilidad.slice(1) : "–";
  $("simKpis").replaceChildren(
    kpi("Nota media actual", fmt(r.notaActual, 1), { texto: r.ambito }),
    kpi("Escenario", r.estimable ? fmt(r.notaEscenario, 1) : "No estimable",
      r.estimable ? { texto: `entre ${fmt(r.notaActual + r.variacionMin, 1)} y ${fmt(r.notaActual + r.variacionMax, 1)}` } : { texto: "ver explicación" }),
    kpi("Variación asociada", r.estimable ? conSigno(r.variacion, 2) + " pts" : "–",
      r.estimable ? { dif: r.variacion, texto: "según los datos disponibles" } : null, true),
    kpi("Alumnado que cambia", fmt(r.proporcionAfectada, 1) + " %", { texto: `de «${r.nivelDesde}» a «${r.nivelHacia}»` }),
    kpi("Fiabilidad de la asociación", fiabilidad, { texto: r.diferenciaAsociada == null ? "" : `diferencia entre niveles: ${conSigno(r.diferenciaAsociada, 1)} pts` })
  );

  const texto = [];
  if (r.estimable) texto.push(el("p", {}, r.explicacion));
  else texto.push(el("div", { class: "caja info" }, el("strong", {}, "No se puede estimar. "), r.motivo));
  if (r.palanca) texto.push(el("p", { class: "nota" }, `Línea de actuación a explorar con el equipo: ${r.palanca}. Margen de actuación del centro: ${fmt(r.accionabilidad, 2)}.`));
  texto.push(el("div", { class: "aviso" }, r.aviso));
  $("simTexto").replaceChildren(...texto);

  if (r.niveles.every((n) => n.pctActual == null)) {
    vacio("gSimulador", "La distribución de este factor no se publica en este ámbito (algún nivel tiene menos de 10 registros).");
    return;
  }
  pintar("gSimulador", {
    type: "bar",
    data: {
      labels: r.niveles.map((n) => n.etiqueta),
      datasets: [
        { label: "% del alumnado ahora", data: r.niveles.map((n) => n.pctActual), backgroundColor: css("--c1") + "99", borderRadius: 4, yAxisID: "y" },
        { label: "% en el escenario", data: r.niveles.map((n) => n.pctEscenario), backgroundColor: css("--c2"), borderRadius: 4, yAxisID: "y" },
        { type: "line", label: "Nota media observada en el nivel", data: r.niveles.map((n) => n.notaAsociada), borderColor: css("--c4"), backgroundColor: css("--c4"), yAxisID: "y2", tension: .25 }
      ]
    },
    options: {
      plugins: { tooltip: { callbacks: { afterBody: (i) => { const n = r.niveles[i[0].dataIndex]; return [`Nota media observada: ${fmt(n.notaAsociada, 1)} (${fmt(n.nAsociada, 0)} registros)`]; } } } },
      scales: {
        y: { min: 0, title: { display: true, text: "% del alumnado" } },
        y2: {
          position: "right", title: { display: true, text: "Nota media observada" }, grid: { drawOnChartArea: false },
          suggestedMin: Math.floor(Math.min(...r.niveles.map((n) => n.notaAsociada ?? 100)) - 2),
          suggestedMax: Math.ceil(Math.max(...r.niveles.map((n) => n.notaAsociada ?? 0)) + 2)
        },
        x: { grid: { display: false } }
      }
    }
  });
}

// ---------------------------------------------------------------------
// Eventos
// ---------------------------------------------------------------------
for (const [n, [tab]] of Object.entries(VISTAS)) $(tab).addEventListener("click", () => mostrarVista(n));
$("archivoCsv").addEventListener("change", (e) => {
  const f = e.target.files[0];
  $("nombreArchivo").textContent = f ? `${f.name} (${fmt(f.size / 1024, 0)} KB)` : "Ningún archivo elegido";
  $("vistaPrevia").replaceChildren();
  actualizarDestino();
});
$("btnValidar").addEventListener("click", validarArchivo);
$("simFactor").addEventListener("change", () => { rellenarNivelesSim(false); simular(); });
$("simDesde").addEventListener("change", simular);
$("simHacia").addEventListener("change", simular);
$("simPct").addEventListener("input", programarSimulacion);
window.addEventListener("pagehide", () => {
  // No dejar vistas previas abiertas al salir (si no, caducan solas a los 20 minutos)
  const p = analisis.previa;
  if (p) fetch(`/api/ingesta/${p.token}`, { method: "DELETE", headers: CABECERA_INGESTA, keepalive: true });
});
