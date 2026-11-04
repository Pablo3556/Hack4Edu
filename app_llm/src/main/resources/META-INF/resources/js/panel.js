"use strict";

// ---------------------------------------------------------------------
// Estado y utilidades
// ---------------------------------------------------------------------
const estado = { rol: "global", centro: null, grupo: null, factor: "asistencia", centros: [] };
const graficas = {};

// Cada sección descarta respuestas que llegan tarde (si el usuario cambió de ámbito o de factor entretanto)
const turnos = {};
const nuevoTurno = (clave) => (turnos[clave] = (turnos[clave] || 0) + 1);
const vigente = (clave, t) => turnos[clave] === t;

const $ = (id) => document.getElementById(id);
const css = (v) => getComputedStyle(document.documentElement).getPropertyValue(v).trim();
const fmt = (v, dec = 1) => v == null ? "–" : Number(v).toLocaleString("es-ES", { minimumFractionDigits: dec, maximumFractionDigits: dec });
const redondear = (v, dec) => Math.round(v * 10 ** dec) / 10 ** dec;
const conSigno = (v, dec = 1) => {
  if (v == null) return "–";
  const r = redondear(v, dec);
  return r === 0 ? fmt(0, dec) : (r > 0 ? "+" : "") + fmt(r, dec);
};

function el(etiqueta, atributos = {}, ...hijos) {
  const e = document.createElement(etiqueta);
  for (const [k, v] of Object.entries(atributos)) {
    if (k === "class") e.className = v; else e.setAttribute(k, v);
  }
  for (const h of hijos) e.append(h instanceof Node ? h : document.createTextNode(h ?? ""));
  return e;
}

async function api(ruta, opciones) {
  const r = await fetch(ruta, opciones);
  let cuerpo = null;
  try { cuerpo = await r.json(); } catch (_) { /* sin cuerpo JSON */ }
  if (!r.ok) throw new Error((cuerpo && cuerpo.error) || `Error ${r.status} en ${ruta}`);
  return cuerpo;
}

function consultaAmbito() {
  const p = new URLSearchParams();
  if (estado.centro != null) p.set("centro", estado.centro);
  if (estado.grupo != null) p.set("grupo", estado.grupo);
  const q = p.toString();
  return q ? "?" + q : "";
}

function nombreAmbito() {
  if (estado.centro == null) return "Conjunto de centros";
  const c = estado.centros.find((x) => x.id === estado.centro);
  if (estado.grupo == null) return c ? c.nombre : "Centro";
  const g = c && c.grupos.find((x) => x.id === estado.grupo);
  return (c ? c.nombre : "") + " · " + (g ? g.nombre : "Grupo");
}

function pintar(id, config) {
  if (graficas[id]) graficas[id].destroy();
  const canvas = $(id);
  const contenedor = canvas.parentElement;
  contenedor.querySelectorAll(".vacio").forEach((n) => n.remove());
  canvas.style.display = "";
  graficas[id] = new Chart(canvas, config);
}

function vacio(id, mensaje) {
  if (graficas[id]) { graficas[id].destroy(); delete graficas[id]; }
  const canvas = $(id);
  canvas.style.display = "none";
  const contenedor = canvas.parentElement;
  contenedor.querySelectorAll(".vacio").forEach((n) => n.remove());
  contenedor.append(el("div", { class: "vacio" }, mensaje));
}

function estiloGraficas() {
  Chart.defaults.color = css("--suave");
  Chart.defaults.borderColor = css("--linea");
  Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
  Chart.defaults.maintainAspectRatio = false;
  Chart.defaults.plugins.legend.labels.boxWidth = 12;
}

// ---------------------------------------------------------------------
// Selector de rol y ámbito
// ---------------------------------------------------------------------
function rellenarSelectores() {
  const selC = $("selCentro"), selG = $("selGrupo");
  selC.replaceChildren(...estado.centros.map((c) => el("option", { value: c.id }, `${c.nombre} (${c.tipo === "publico" ? "público" : "privado"})`)));
  if (estado.centro == null && estado.centros.length) estado.centro = estado.centros[0].id;
  selC.value = estado.centro;
  const c = estado.centros.find((x) => x.id === Number(selC.value));
  selG.replaceChildren(...(c ? c.grupos : []).map((g) => el("option", { value: g.id }, g.nombre)));
}

function aplicarRol(rol) {
  estado.rol = rol;
  document.querySelectorAll(".roles button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.rol === rol)));
  const selC = $("selCentro"), selG = $("selGrupo");
  selC.disabled = rol === "global";
  selG.disabled = rol !== "grupo";
  if (rol === "global") {
    estado.centro = null; estado.grupo = null;
  } else {
    if (estado.centro == null) estado.centro = Number(selC.value) || (estado.centros[0] && estado.centros[0].id);
    rellenarSelectores();
    estado.grupo = rol === "grupo" ? Number(selG.value) : null;
  }
  cargarAmbito();
}

// ---------------------------------------------------------------------
// Indicadores
// ---------------------------------------------------------------------
function kpi(nombre, valor, comparacion, mejorSiSube) {
  const comp = el("div", { class: "comp" });
  if (comparacion) {
    const dif = comparacion.dif == null ? 0 : redondear(comparacion.dif, 1);
    const clase = dif === 0 ? "" : ((dif > 0) === mejorSiSube ? "sube" : "baja");
    comp.append(el("span", { class: clase }, comparacion.texto));
  }
  return el("div", { class: "kpi" }, el("div", { class: "nombre" }, nombre), el("div", { class: "valor" }, valor), comp);
}

async function cargarResumen() {
  const cont = $("kpis"), t = nuevoTurno("resumen");
  try {
    const { ambito: a, global: g } = await api("/api/datos/resumen" + consultaAmbito());
    if (!vigente("resumen", t)) return;
    const esGlobal = a.ambito === "global";
    const vs = (v, ref, dec, unidad = "") => esGlobal || v == null || ref == null
      ? null : { dif: v - ref, texto: `${conSigno(v - ref, dec)}${unidad} frente al conjunto` };
    cont.replaceChildren(
      kpi("Alumnado", fmt(a.nAlumnos, 0), esGlobal ? { texto: "en el conjunto de centros" } : { texto: `de ${fmt(g.nAlumnos, 0)} en total` }),
      kpi("Nota media", fmt(a.notaMedia, 1), vs(a.notaMedia, g.notaMedia, 1, " pts") || { texto: `mediana ${fmt(a.notaMediana, 0)} · P25–P75 ${fmt(a.notaP25, 0)}–${fmt(a.notaP75, 0)}` }, true),
      kpi("Asistencia media", fmt(a.asistenciaMedia, 1) + " %", vs(a.asistenciaMedia, g.asistenciaMedia, 1, " pp"), true),
      kpi("Asisten < 80 %", fmt(a.pctAsistenciaBaja, 1) + " %", vs(a.pctAsistenciaBaja, g.pctAsistenciaBaja, 1, " pp"), false),
      kpi("Tutorías al mes", fmt(a.tutoriasMedia, 2), vs(a.tutoriasMedia, g.tutoriasMedia, 2), true),
      kpi("Acceso bajo a recursos", fmt(a.pctRecursosBajos, 1) + " %", vs(a.pctRecursosBajos, g.pctRecursosBajos, 1, " pp"), false)
    );
  } catch (e) {
    if (!vigente("resumen", t)) return;
    cont.replaceChildren(el("div", { class: "panel error" }, "No se pudieron cargar los indicadores: " + e.message));
  }
}

// ---------------------------------------------------------------------
// Prioridades
// ---------------------------------------------------------------------
async function cargarPrioridades() {
  const lista = $("listaPrioridades"), t = nuevoTurno("prioridades");
  try {
    const datos = (await api("/api/datos/prioridades" + consultaAmbito())).filter((p) => p.prioridad != null);
    if (!vigente("prioridades", t)) return;
    if (!datos.length) {
      vacio("gPrioridades", "Sin datos suficientes en este ámbito (umbral de 10 alumnos).");
      lista.replaceChildren();
      return;
    }
    const fuerza = { "grande": css("--c1"), "moderada": css("--c1") + "cc", "pequeña": css("--c1") + "88", "muy pequeña": css("--c1") + "44" };
    pintar("gPrioridades", {
      type: "bar",
      data: {
        labels: datos.map((p) => p.factorNombre),
        datasets: [{
          label: "Prioridad",
          data: datos.map((p) => p.prioridad),
          backgroundColor: datos.map((p) => fuerza[p.magnitud] || css("--c1")),
          borderRadius: 4
        }]
      },
      options: {
        indexAxis: "y",
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              title: (i) => datos[i[0].dataIndex].factorNombre,
              label: (i) => {
                const p = datos[i.dataIndex];
                return [
                  `Prioridad: ${fmt(p.prioridad, 2)}`,
                  `Peor nivel: ${p.nivelPeor} (${fmt(p.pctPeor, 1)} % del alumnado), nota ${fmt(p.notaPeor, 1)}`,
                  `Mejor nivel: ${p.nivelMejor}, nota ${fmt(p.notaMejor, 1)}`,
                  `Brecha: ${fmt(p.brecha, 1)} pts (${p.magnitud}) · margen de actuación ${fmt(p.accionabilidad, 2)}`,
                  `Fiabilidad: ${p.fiabilidad}`
                ];
              }
            }
          }
        },
        scales: { x: { title: { display: true, text: "Prioridad (cuanto mayor, antes)" } }, y: { grid: { display: false } } }
      }
    });
    lista.replaceChildren(...datos.slice(0, 5).map((p) => el("li", {},
      el("span", { class: "puesto" }, String(p.puesto)),
      el("div", {},
        el("div", { class: "titulo" }, p.factorNombre, el("span", { class: "chip" }, "brecha " + p.magnitud)),
        el("div", { class: "detalle" },
          `${fmt(p.pctPeor, 1)} % del alumnado está en «${p.nivelPeor}», con nota media ${fmt(p.notaPeor, 1)} frente a ${fmt(p.notaMejor, 1)} en «${p.nivelMejor}». Fiabilidad ${p.fiabilidad}.`),
        el("div", { class: "detalle" }, "Línea a explorar: " + (p.palanca || "—"))
      )
    )));
  } catch (e) {
    if (!vigente("prioridades", t)) return;
    vacio("gPrioridades", "No se pudieron cargar las prioridades: " + e.message);
    lista.replaceChildren();
  }
}

// ---------------------------------------------------------------------
// Explorar un factor
// ---------------------------------------------------------------------
async function cargarFactores() {
  const factores = await api("/api/datos/factores");
  const grupos = { accionable: "Factores sobre los que el centro puede actuar", contextual: "Factores de contexto (solo equidad)" };
  const sel = $("selFactor");
  sel.replaceChildren(...Object.entries(grupos).map(([tipo, titulo]) => {
    const og = el("optgroup", { label: titulo });
    factores.filter((f) => f.tipo === tipo).forEach((f) => og.append(el("option", { value: f.codigo }, f.nombre)));
    return og;
  }));
  sel.value = estado.factor;
  estado.factoresInfo = Object.fromEntries(factores.map((f) => [f.codigo, f]));
}

async function cargarFactor() {
  const info = (estado.factoresInfo || {})[estado.factor], t = nuevoTurno("factor");
  try {
    const niveles = await api(`/api/datos/factor/${encodeURIComponent(estado.factor)}` + consultaAmbito());
    if (!vigente("factor", t)) return;
    $("notaFactor").textContent = (info ? info.descripcion + " " : "") +
      (niveles.length && niveles[0].pctSinDato > 0 ? `Sin dato: ${fmt(niveles[0].pctSinDato, 1)} %.` : "");
    if (!niveles.length) {
      vacio("gFactor", "Factor oculto en este ámbito: algún nivel tiene menos de 10 alumnos.");
      return;
    }
    pintar("gFactor", {
      type: "bar",
      data: {
        labels: niveles.map((n) => n.nivel),
        datasets: [
          { type: "bar", label: "Nota media", data: niveles.map((n) => n.notaMedia), backgroundColor: css("--c1"), borderRadius: 4, yAxisID: "y" },
          { type: "line", label: "% del alumnado", data: niveles.map((n) => n.pctAmbito), borderColor: css("--c2"), backgroundColor: css("--c2"), yAxisID: "y2", tension: .3 }
        ]
      },
      options: {
        plugins: {
          tooltip: {
            callbacks: {
              afterBody: (i) => {
                const n = niveles[i[0].dataIndex];
                return [`${fmt(n.n, 0)} alumnos`, `${conSigno(n.difVsMediaAmbito, 1)} pts frente a la media del ámbito`];
              }
            }
          }
        },
        scales: {
          y: { title: { display: true, text: "Nota media" }, suggestedMin: Math.floor(Math.min(...niveles.map((n) => n.notaMedia)) - 3) },
          y2: { position: "right", title: { display: true, text: "% del alumnado" }, min: 0, grid: { drawOnChartArea: false } },
          x: { grid: { display: false } }
        }
      }
    });
  } catch (e) {
    if (!vigente("factor", t)) return;
    vacio("gFactor", "No se pudo cargar el factor: " + e.message);
  }
}

// ---------------------------------------------------------------------
// Equidad (factores contextuales)
// ---------------------------------------------------------------------
async function cargarEquidad() {
  const t = nuevoTurno("equidad");
  try {
    const datos = (await api("/api/datos/brechas" + consultaAmbito())).filter((b) => b.tipo === "contextual");
    if (!vigente("equidad", t)) return;
    if (!datos.length) { vacio("gEquidad", "Sin datos suficientes en este ámbito."); return; }
    pintar("gEquidad", {
      type: "bar",
      data: {
        labels: datos.map((b) => b.factorNombre),
        datasets: [{ label: "Brecha (desviaciones típicas)", data: datos.map((b) => b.brechaEstandarizada), backgroundColor: css("--c4"), borderRadius: 4 }]
      },
      options: {
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { afterLabel: (i) => { const b = datos[i.dataIndex]; return `${b.nivelMejor} frente a ${b.nivelPeor}: ${fmt(b.brecha, 1)} pts`; } } }
        },
        scales: { y: { min: 0, suggestedMax: 0.5, title: { display: true, text: "Desviaciones típicas (0,2 = pequeña)" } }, x: { grid: { display: false } } }
      }
    });
  } catch (e) {
    if (!vigente("equidad", t)) return;
    vacio("gEquidad", "No se pudo cargar la equidad: " + e.message);
  }
}

// ---------------------------------------------------------------------
// Comparativa de centros
// ---------------------------------------------------------------------
async function cargarCentros() {
  if (sesion.rol !== "administracion") return;
  const t = nuevoTurno("centros");
  try {
    const centros = await api("/api/datos/centros");
    if (!vigente("centros", t)) return;
    if (!centros.length) { vacio("gCentros", "Sin datos de centros. ¿Se ha ejecutado ./scripts/cargar_kaggle.sh?"); return; }
    const resaltado = (c) => estado.centro != null && c.centroId === estado.centro;
    pintar("gCentros", {
      type: "bar",
      data: {
        labels: centros.map((c) => c.centro.replace("Centro Demo ", "Centro ")),
        datasets: [
          { type: "bar", label: "Nota media", data: centros.map((c) => c.notaMedia), yAxisID: "y", borderRadius: 4,
            backgroundColor: centros.map((c) => resaltado(c) ? css("--c2") : css("--c1")) },
          { type: "line", label: "Asistencia media (%)", data: centros.map((c) => c.asistenciaMedia), yAxisID: "y2",
            borderColor: css("--c3"), backgroundColor: css("--c3"), tension: .3 }
        ]
      },
      options: {
        plugins: { tooltip: { callbacks: { afterBody: (i) => { const c = centros[i[0].dataIndex]; return [`${fmt(c.nAlumnos, 0)} alumnos · ${c.tipoCentro === "publico" ? "público" : "privado"}`]; } } } },
        scales: {
          y: { title: { display: true, text: "Nota media" }, suggestedMin: Math.floor(Math.min(...centros.map((c) => c.notaMedia)) - 2) },
          y2: { position: "right", title: { display: true, text: "Asistencia (%)" }, grid: { drawOnChartArea: false } },
          x: { grid: { display: false } }
        }
      }
    });
  } catch (e) {
    if (!vigente("centros", t)) return;
    vacio("gCentros", "No se pudo cargar la comparativa: " + e.message);
  }
}

// ---------------------------------------------------------------------
// Calidad de datos
// ---------------------------------------------------------------------
async function cargarCalidad() {
  const cont = $("calidad");
  try {
    const filas = (await api("/api/datos/calidad")).filter((c) => c.nAusentes > 0);
    if (!filas.length) { cont.replaceChildren(el("p", { class: "nota" }, "Sin valores ausentes.")); return; }
    cont.replaceChildren(el("table", {},
      el("thead", {}, el("tr", {}, el("th", {}, "Columna"), el("th", { class: "num" }, "Ausentes"), el("th", { class: "num" }, "% del total"))),
      el("tbody", {}, ...filas.map((c) => el("tr", {},
        el("td", {}, c.columna.replaceAll("_", " ")), el("td", { class: "num" }, fmt(c.nAusentes, 0)), el("td", { class: "num" }, fmt(c.pctAusentes, 2) + " %"))))
    ));
  } catch (e) {
    cont.replaceChildren(el("p", { class: "error" }, e.message));
  }
}

// ---------------------------------------------------------------------
// Asistente
// ---------------------------------------------------------------------
function sugerencias() {
  const ambito = nombreAmbito();
  const lista = estado.centro == null
    ? ["¿Qué factores deberíamos priorizar en general?", "¿Hay brechas de equidad preocupantes?"]
    : [`¿Qué deberíamos priorizar en ${ambito}?`, `¿Cómo está la asistencia en ${ambito}?`];
  $("sugerencias").replaceChildren(...lista.map((t) => {
    const b = el("button", { type: "button" }, t);
    b.addEventListener("click", () => { $("pregunta").value = t; $("pregunta").focus(); });
    return b;
  }));
}

async function cargarModelos() {
  const sel = $("selModelo");
  try {
    const { ollamaDisponible, modelos } = await api("/api/modelos");
    sel.replaceChildren(...modelos.map((m) => {
      const extra = m.instalado === false ? " · no descargado" : "";
      const o = el("option", { value: m.id, title: m.descripcion }, `${m.nombre} (${m.modeloOllama})${extra}`);
      if (m.instalado === false) o.disabled = true;
      if (m.porDefecto) o.selected = true;
      return o;
    }));
    if (!ollamaDisponible) $("estadoAsistente").textContent = "Ollama no responde: arranca la app de Ollama en este equipo.";
  } catch (e) {
    sel.replaceChildren(el("option", { value: "" }, "Por defecto"));
  }
}

async function preguntar() {
  const texto = $("pregunta").value.trim();
  if (!texto) { $("pregunta").focus(); return; }
  const boton = $("btnPreguntar"), estadoTxt = $("estadoAsistente");
  boton.disabled = true;
  $("respuesta").textContent = "";
  $("fuentes").replaceChildren();
  const t0 = Date.now();
  const reloj = setInterval(() => { estadoTxt.textContent = `Pensando… ${Math.round((Date.now() - t0) / 1000)} s`; }, 500);
  try {
    const r = await api("/api/asistente", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pregunta: texto, modelo: $("selModelo").value || null })
    });
    $("respuesta").textContent = r.respuesta;
    estadoTxt.textContent = `Respondido con ${r.modeloOllama} en ${Math.round((Date.now() - t0) / 1000)} s`;
    if (r.fuentes && r.fuentes.length) {
      const d = el("details", {}, el("summary", {}, `Fichas consultadas (${r.fuentes.length})`));
      r.fuentes.forEach((f) => d.append(el("p", {}, f.extracto)));
      $("fuentes").replaceChildren(d);
    }
  } catch (e) {
    $("respuesta").replaceChildren(el("span", { class: "error" }, e.message));
    estadoTxt.textContent = "";
  } finally {
    clearInterval(reloj);
    boton.disabled = false;
  }
}

// ---------------------------------------------------------------------
// Arranque
// ---------------------------------------------------------------------
function cargarAmbito() {
  $("ambitoActual").textContent = "Viendo: " + nombreAmbito();
  sugerencias();
  if (window.alCambiarAmbito) window.alCambiarAmbito();
  return Promise.all([cargarResumen(), cargarPrioridades(), cargarFactor(), cargarEquidad(), cargarCentros()]);
}

async function iniciar() {
  if (typeof Chart === "undefined") {
    document.querySelector("main").prepend(el("div", { class: "aviso" }, "No se pudo cargar Chart.js (¿sin conexión?). Las gráficas no se mostrarán."));
    window.Chart = function () { return { destroy() {} }; };
    window.Chart.defaults = { font: {}, plugins: { legend: { labels: {} } } };
  }
  estiloGraficas();
  if (!(await cargarSesion())) return;

  document.querySelectorAll(".roles button").forEach((b) => b.addEventListener("click", () => aplicarRol(b.dataset.rol)));
  $("selCentro").addEventListener("change", (e) => {
    estado.centro = Number(e.target.value);
    rellenarSelectores();
    estado.grupo = estado.rol === "grupo" ? Number($("selGrupo").value) : null;
    cargarAmbito();
  });
  $("selGrupo").addEventListener("change", (e) => { estado.grupo = Number(e.target.value); cargarAmbito(); });
  $("selFactor").addEventListener("change", (e) => { estado.factor = e.target.value; cargarFactor(); });
  $("btnPreguntar").addEventListener("click", preguntar);
  $("pregunta").addEventListener("keydown", (e) => { if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) preguntar(); });

  try {
    estado.centros = await api("/api/datos/ambitos");
    rellenarSelectores();
    await cargarFactores();
  } catch (e) {
    $("kpis").replaceChildren(el("div", { class: "panel error" },
      "No se puede conectar con la base de datos: " + e.message + ". Comprueba que el contenedor está levantado (docker compose up -d) y los datos cargados."));
  }
  cargarModelos();
  cargarCalidad();
  aplicarRol(ambitoPara("panel")); // ámbito inicial según el rol (llama a cargarAmbito)
}

iniciar();
