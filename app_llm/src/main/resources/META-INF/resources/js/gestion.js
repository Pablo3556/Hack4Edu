"use strict";
// ---------------------------------------------------------------------
// Resumen semanal (aula), panel de dirección (semáforo y equidad) y seguimiento de medidas.
// Reutiliza las utilidades de index.html y analisis.js: estado, $, el, api, apiIngesta, fmt, conSigno,
// nombreAmbito, mostrarVista. Todo el texto dinámico se inserta con textContent (nunca innerHTML).
// ---------------------------------------------------------------------

const gestion = { resumen: null, medidasListas: false, factores: [], indicadores: [], periodos: [], prefijo: null };

const SEMAFORO = {
  verde: { simbolo: "●", texto: "En línea" },
  ambar: { simbolo: "▲", texto: "Diferencia moderada" },
  rojo: { simbolo: "■", texto: "Diferencia grande" }
};

function chipSemaforo(color, texto) {
  const s = SEMAFORO[color];
  if (!s) return el("span", { class: "sem neutro" }, texto || "Sin dato");
  return el("span", { class: `sem ${color}`, title: s.texto }, `${s.simbolo} ${texto ?? s.texto}`);
}

// ---------------------------------------------------------------------
// 1 · Resumen semanal
// ---------------------------------------------------------------------
async function cargarSemanal() {
  const cont = $("resumenSemanal"), t = nuevoTurno("semanal");
  $("btnCopiarResumen").disabled = true;
  if (estado.centro == null) { cont.replaceChildren(el("div", { class: "vacio" }, "Elige un centro y un grupo.")); return; }
  try {
    const r = await api("/api/resumen-semanal" + consultaAmbito());
    if (!vigente("semanal", t)) return;
    gestion.resumen = r;
    pintarSemanal(r);
    $("btnCopiarResumen").disabled = !r.datosSuficientes;
  } catch (e) {
    if (!vigente("semanal", t)) return;
    cont.replaceChildren(el("div", { class: "error" }, "No se pudo generar el resumen: " + e.message));
  }
}
window.cargarSemanal = cargarSemanal;

function pintarSemanal(r) {
  const cont = $("resumenSemanal");
  $("notaSemanal").textContent = `${r.ambito} · ${r.semana}` + (r.periodo ? ` · datos de ${r.periodo}` : "");
  const partes = [];
  if (r.mensaje) partes.push(el("div", { class: "caja info" }, r.mensaje));

  if (r.prioridades.length) {
    partes.push(el("h3", {}, "Tres prioridades para esta semana"));
    const lista = el("div", { class: "prio-semanal" });
    for (const p of r.prioridades) {
      const tarjeta = el("div", { class: "tarjeta-prio" },
        el("div", { class: "num" }, String(p.puesto)),
        el("div", { class: "titulo" }, p.titulo, el("span", { class: "chip" }, `fiabilidad ${p.fiabilidad}`)),
        el("div", {}, p.detalle));
      if (p.queProbar) tarjeta.append(el("div", { class: "probar" }, p.queProbar));
      if (p.medidasRelacionadas.length) {
        tarjeta.append(el("div", {}, el("span", { class: "chip" }, "Medida en curso"), " " + p.medidasRelacionadas.join(" · ")));
      }
      const pie = el("div", { class: "fila" },
        el("span", { class: "fuente" }, `${p.fuente.vista} · ${p.fuente.ambito} · ${p.fuente.detalle}`));
      const btn = el("button", { class: "boton secundario", type: "button" }, "Registrar una medida");
      btn.addEventListener("click", () => prepararMedida(p));
      pie.append(btn);
      tarjeta.append(pie);
      lista.append(tarjeta);
    }
    partes.push(lista);
  }

  if (r.senales.length) {
    partes.push(el("h3", {}, "Señales de evolución"));
    const alertas = el("div", { class: "alertas" });
    for (const s of r.senales) {
      alertas.append(el("div", { class: `alerta ${s.nivel}` },
        el("div", { class: "titulo" }, s.titulo),
        s.detalle ? el("div", { class: "nota" }, s.detalle) : "",
        el("div", { class: "fuente" }, `${s.fuente.vista} · ${s.fuente.ambito} · ${s.fuente.detalle}`)));
    }
    partes.push(alertas);
  }

  if (r.medidasEnCurso.length) {
    partes.push(el("h3", {}, "Medidas en curso"));
    const ul = el("ul", {});
    for (const m of r.medidasEnCurso) {
      ul.append(el("li", {}, el("strong", {}, m.titulo), ` (${m.grupo || "todo el centro"}, desde ${m.periodoInicio}) `, chipVeredicto(m.veredicto)));
    }
    partes.push(ul);
  }
  partes.push(el("div", { class: "aviso" }, r.aviso));
  cont.replaceChildren(...partes);
}

function textoResumen(r) {
  const l = [`Resumen semanal · ${r.ambito} · ${r.semana}` + (r.periodo ? ` (datos de ${r.periodo})` : ""), ""];
  r.prioridades.forEach((p) => {
    l.push(`${p.puesto}. ${p.titulo}`, `   ${p.detalle}`);
    if (p.queProbar) l.push(`   ${p.queProbar}`);
    l.push(`   Fuente: ${p.fuente.vista} · ${p.fuente.ambito} · ${p.fuente.detalle}`);
  });
  if (r.senales.length) { l.push("", "Señales de evolución:"); r.senales.forEach((s) => l.push(`- ${s.titulo}`)); }
  if (r.medidasEnCurso.length) { l.push("", "Medidas en curso:"); r.medidasEnCurso.forEach((m) => l.push(`- ${m.titulo}`)); }
  l.push("", r.aviso);
  return l.join("\n");
}

async function copiarResumen() {
  if (!gestion.resumen) return;
  const btn = $("btnCopiarResumen"), original = btn.textContent;
  try {
    await navigator.clipboard.writeText(textoResumen(gestion.resumen));
    btn.textContent = "Copiado";
  } catch (_) {
    btn.textContent = "No se pudo copiar";
  }
  setTimeout(() => { btn.textContent = original; }, 1800);
}

// ---------------------------------------------------------------------
// 2 · Panel de dirección
// ---------------------------------------------------------------------
async function cargarDireccion() {
  const t = nuevoTurno("direccion");
  if (estado.centro == null) return;
  try {
    const d = await api("/api/direccion/semaforo?centro=" + estado.centro);
    if (!vigente("direccion", t)) return;
    pintarSemaforo(d);
  } catch (e) {
    if (!vigente("direccion", t)) return;
    $("tablaSemaforo").replaceChildren(el("div", { class: "error" }, "No se pudo cargar el panel: " + e.message));
    $("tablaEquidad").replaceChildren();
  }
  // Las estadísticas del profesorado son solo de administración (el servidor las deniega a los demás)
  if (sesion.rol !== "administracion") return;
  try {
    const p = await api("/api/direccion/progreso-docente?centro=" + estado.centro);
    if (!vigente("direccion", t)) return;
    pintarProgreso(p);
  } catch (e) {
    if (!vigente("direccion", t)) return;
    $("tablaProgresoGrupos").replaceChildren(el("div", { class: "error" }, "No se pudo cargar el progreso: " + e.message));
    $("tablaProgresoDocentes").replaceChildren();
  }
}
window.cargarDireccion = cargarDireccion;

// Progreso frente a lo esperado: "por debajo" se muestra en ámbar, nunca en rojo (orienta, no señala)
const PROGRESO = {
  por_encima: { color: "verde", texto: "Por encima de lo esperado" },
  en_lo_esperado: { color: null, texto: "En lo esperado" },
  por_debajo: { color: "ambar", texto: "Por debajo de lo esperado" }
};

function chipProgreso(estimacion) {
  const v = estimacion && PROGRESO[estimacion.veredicto];
  if (!v) return el("span", { class: "sem neutro" }, "Sin datos suficientes");
  return v.color ? chipSemaforo(v.color, v.texto) : el("span", { class: "sem neutro" }, v.texto);
}

function celdaProgreso(e) {
  const td = el("td", { class: "num" });
  if (!e || e.progreso == null) return td;
  td.append(el("strong", {}, conSigno(e.progreso, 1) + " pts"),
    el("span", { class: "dif" }, `IC 95 %: ${conSigno(e.icInferior, 1)} a ${conSigno(e.icSuperior, 1)}`));
  return td;
}

const FORMACION = { grado: "Grado", master: "Máster", doctorado: "Doctorado" };

function pintarProgreso(p) {
  $("notaProgreso").textContent = `${p.centro}: progreso del alumnado frente a lo esperado por su punto de partida y su contexto.`;
  $("recuentoProgreso").replaceChildren(
    ...Object.entries(PROGRESO).map(([k, v]) => el("div", { class: "kpi" },
      el("div", { class: "nombre" }, v.texto), el("div", { class: "valor" }, String(p.recuentoGrupos[k] ?? 0)),
      el("div", { class: "comp" }, "grupos"))));

  const filas = [...(p.centroFila ? [p.centroFila] : []), ...p.grupos];
  if (!filas.length) {
    $("tablaProgresoGrupos").replaceChildren(el("div", { class: "vacio" }, "Ningún grupo de este centro llega al umbral de 10 alumnos."));
  } else {
    $("tablaProgresoGrupos").replaceChildren(el("table", { class: "semaforo" },
      el("thead", {}, el("tr", {},
        el("th", {}, "Grupo"), el("th", { class: "num" }, "Alumnado"), el("th", { class: "num" }, "Progreso vs esperado"),
        el("th", {}, "Resultado"), el("th", {}, "Tutoría"), el("th", { class: "num" }, "Docentes"),
        el("th", { class: "num" }, "Experiencia media (años)"), el("th", { class: "num" }, "Formación media (h/año)"))),
      el("tbody", {}, ...filas.map((g, i) => el("tr", {},
        el("td", {}, i === 0 && p.centroFila ? el("strong", {}, g.nombre + " (centro)") : g.nombre),
        el("td", { class: "num" }, fmt(g.nAlumnos, 0)),
        celdaProgreso(g.progreso),
        el("td", {}, chipProgreso(g.progreso)),
        el("td", {}, g.tutor || "—"),
        el("td", { class: "num" }, g.nDocentes ? String(g.nDocentes) : "—"),
        el("td", { class: "num" }, g.experienciaMedia == null ? "—" : fmt(g.experienciaMedia, 1)),
        el("td", { class: "num" }, g.formacionMedia == null ? "—" : fmt(g.formacionMedia, 0)))))));
  }

  if (!p.hayProfesorado) {
    $("tablaProgresoDocentes").replaceChildren(el("div", { class: "vacio" },
      "Este centro no tiene profesorado cargado. Cárgalo con ./scripts/cargar_profesorado.sh"));
  } else {
    $("tablaProgresoDocentes").replaceChildren(el("table", { class: "semaforo" },
      el("thead", {}, el("tr", {},
        el("th", {}, "Docente"), el("th", {}, "Grupos"), el("th", {}, "Asignaturas"), el("th", { class: "num" }, "Horas/sem."),
        el("th", { class: "num" }, "Experiencia"), el("th", {}, "Formación"), el("th", { class: "num" }, "Alumnado"),
        el("th", { class: "num" }, "Progreso de sus grupos"), el("th", {}, "Resultado"))),
      el("tbody", {}, ...p.docentes.map((d) => el("tr", {},
        el("td", {}, el("strong", {}, d.codigo), el("span", { class: "dif" }, d.tipoContrato === "parcial" ? "Tiempo parcial" : "Tiempo completo")),
        el("td", {}, d.grupos || "Sin grupos asignados"),
        el("td", {}, d.asignaturas || "—"),
        el("td", { class: "num" }, d.horasSemana == null ? "—" : String(d.horasSemana)),
        el("td", { class: "num" }, `${d.aniosExperiencia} años`),
        el("td", {}, FORMACION[d.nivelFormacion] || d.nivelFormacion, el("span", { class: "dif" }, `${d.horasFormacionAnio} h/año`)),
        el("td", { class: "num" }, d.nAlumnos == null ? "—" : fmt(d.nAlumnos, 0)),
        celdaProgreso(d.progreso),
        el("td", {}, chipProgreso(d.progreso)))))));
  }
  $("avisoProgreso").replaceChildren(el("div", {}, p.metodo), el("div", {}, p.aviso));
}

function celdaSemaforo(c, ind) {
  const td = el("td", { class: "celda" });
  if (c.valor == null) { td.append(chipSemaforo(null, "Sin dato")); return td; }
  const dec = ind.codigo === "tutorias_media" ? 2 : 1;
  td.append(chipSemaforo(c.color, fmt(c.valor, dec) + (ind.unidad === "pp" ? " %" : "")),
    el("span", { class: "dif" }, `${conSigno(c.diferencia, dec)} ${ind.unidad} vs conjunto`));
  return td;
}

function pintarSemaforo(d) {
  $("notaSemaforo").textContent = `${d.centro}: cada grupo frente al conjunto de centros.`;
  $("leyendaSemaforo").replaceChildren(...Object.keys(SEMAFORO).map((c) => chipSemaforo(c)));
  $("recuentoSemaforo").replaceChildren(
    ...["verde", "ambar", "rojo"].map((c) => el("div", { class: "kpi" },
      el("div", { class: "nombre" }, SEMAFORO[c].texto), el("div", { class: "valor" }, String(d.recuento[c] ?? 0)),
      el("div", { class: "comp" }, "grupos"))));

  const filas = [...(d.centroFila ? [d.centroFila] : []), ...d.grupos];
  if (!filas.length) {
    $("tablaSemaforo").replaceChildren(el("div", { class: "vacio" }, "Ningún grupo de este centro llega al umbral de 10 alumnos."));
  } else {
    const tabla = el("table", { class: "semaforo" },
      el("thead", {}, el("tr", {},
        el("th", {}, "Grupo"), el("th", { class: "num" }, "Alumnado"),
        ...d.indicadores.map((i) => el("th", { class: "celda" }, i.nombre)),
        el("th", {}, "Global"), el("th", { class: "num" }, "Medidas"))),
      el("tbody", {}, ...filas.map((f, i) => el("tr", {},
        el("td", {}, i === 0 && d.centroFila ? el("strong", {}, f.nombre + " (centro)") : f.nombre),
        el("td", { class: "num" }, fmt(f.nAlumnos, 0)),
        ...f.celdas.map((c, k) => celdaSemaforo(c, d.indicadores[k])),
        el("td", {}, chipSemaforo(f.color)),
        el("td", { class: "num" }, f.grupoId == null ? "" : String(f.medidasEnCurso))))));
    $("tablaSemaforo").replaceChildren(tabla);
  }
  const extra = [d.criterio, d.aviso];
  if (d.gruposSinDatos.length) extra.unshift(`Sin datos suficientes (menos de 10 alumnos): ${d.gruposSinDatos.join(", ")}.`);
  $("avisoSemaforo").replaceChildren(...extra.map((x) => el("div", {}, x)));

  if (!d.equidad.length) {
    $("tablaEquidad").replaceChildren(el("div", { class: "vacio" }, "Sin brechas calculables: algún nivel no llega al umbral de 10 alumnos."));
    return;
  }
  $("tablaEquidad").replaceChildren(el("table", {},
    el("thead", {}, el("tr", {}, el("th", {}, "Factor de contexto"), el("th", {}, "Peor nivel"), el("th", {}, "Mejor nivel"),
      el("th", { class: "num" }, "Brecha (pts)"), el("th", { class: "num" }, "En desv. típicas"), el("th", {}, "Semáforo"), el("th", {}, "Fiabilidad"))),
    el("tbody", {}, ...d.equidad.map((e) => el("tr", {},
      el("td", {}, e.factorNombre), el("td", {}, `${e.nivelPeor} (${fmt(e.pctPeor, 1)} %)`), el("td", {}, e.nivelMejor),
      el("td", { class: "num" }, fmt(e.brecha, 1)), el("td", { class: "num" }, fmt(e.brechaEstandarizada, 2)),
      el("td", {}, chipSemaforo(e.color)), el("td", {}, e.fiabilidad))))));
}

// ---------------------------------------------------------------------
// 3 · Seguimiento de medidas
// ---------------------------------------------------------------------
const INDICADOR_POR_FACTOR = { asistencia: "asistencia_media", horas_estudio: "horas_estudio_media", tutorias: "tutorias_media" };

async function cargarCatalogosMedidas() {
  if (gestion.medidasListas) return;
  const [factores, indicadores, periodos] = await Promise.all([
    api("/api/datos/factores"), api("/api/medidas/indicadores"), api("/api/medidas/periodos")]);
  gestion.factores = factores.filter((f) => f.tipo === "accionable");
  gestion.indicadores = indicadores;
  gestion.periodos = periodos;
  $("medFactor").replaceChildren(el("option", { value: "" }, "Ninguno en concreto"),
    ...gestion.factores.map((f) => el("option", { value: f.codigo }, f.nombre)));
  $("medIndicador").replaceChildren(...indicadores.map((i) => el("option", { value: i.codigo }, i.nombre)));
  $("medPeriodo").replaceChildren(...periodos.map((p) => el("option", { value: p.id }, p.nombre)));
  gestion.medidasListas = true;
}

function rellenarAmbitoMedida() {
  const c = estado.centros.find((x) => x.id === estado.centro);
  const sel = $("medAmbito");
  if (!c) { sel.replaceChildren(); return; }
  sel.replaceChildren(el("option", { value: "" }, `Todo el centro (${c.nombre})`),
    ...c.grupos.map((g) => el("option", { value: g.id }, g.nombre)));
  sel.value = estado.grupo != null ? String(estado.grupo) : "";
}

async function cargarMedidas() {
  const t = nuevoTurno("medidas");
  if (estado.centro == null) return;
  try {
    await cargarCatalogosMedidas();
    rellenarAmbitoMedida();
    if (gestion.prefijo) aplicarPrefijo();
    const d = await api("/api/medidas" + consultaAmbito());
    if (!vigente("medidas", t)) return;
    pintarMedidas(d);
  } catch (e) {
    if (!vigente("medidas", t)) return;
    $("listaMedidas").replaceChildren(el("div", { class: "error" }, "No se pudieron cargar las medidas: " + e.message));
  }
}
window.cargarMedidas = cargarMedidas;

// Desde una prioridad del resumen semanal: abre el formulario con el factor y un indicador sugeridos
function prepararMedida(p) {
  gestion.prefijo = {
    factor: p.factor,
    titulo: `Medida sobre: ${p.factorNombre}`,
    indicador: INDICADOR_POR_FACTOR[p.factor] || "nota_media"
  };
  mostrarVista("medidas");
}

function aplicarPrefijo() {
  const p = gestion.prefijo;
  gestion.prefijo = null;
  $("medFactor").value = p.factor;
  $("medIndicador").value = p.indicador;
  if (!$("medTitulo").value) $("medTitulo").value = p.titulo;
  $("medTitulo").focus();
}

function chipVeredicto(v) {
  const mapa = { mejora: ["verde", "Evolución favorable"], sin_cambio: ["ambar", "Sin cambio apreciable"], empeora: ["rojo", "Evolución desfavorable"] };
  const m = mapa[v];
  return m ? chipSemaforo(m[0], m[1]) : el("span", { class: "sem neutro" }, "Pendiente de datos");
}

function tarjetaMedida(m) {
  const ev = m.evaluacion;
  const ambito = m.grupo ? `${m.centro} · ${m.grupo}` : `${m.centro} (todo el centro)`;
  const selEstado = el("select", { "aria-label": `Estado de la medida ${m.titulo}` },
    el("option", { value: "en_curso" }, "En curso"), el("option", { value: "finalizada" }, "Finalizada"),
    el("option", { value: "descartada" }, "Descartada"));
  selEstado.value = m.estado;
  selEstado.disabled = !!sesion.permisos.soloConsulta;
  selEstado.addEventListener("change", async () => {
    selEstado.disabled = true;
    try {
      await apiIngesta(`/api/medidas/${m.id}/estado`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ estado: selEstado.value }) });
      cargarMedidas();
    } catch (e) {
      selEstado.value = m.estado;
      $("estadoMedida").textContent = e.message;
    } finally { selEstado.disabled = false; }
  });

  const tarjeta = el("div", { class: "medida" + (m.estado === "en_curso" ? "" : " cerrada") },
    el("header", {},
      el("div", {}, el("div", { class: "titulo" }, m.titulo),
        el("div", { class: "nota" }, `${ambito} · desde ${m.periodoInicioNombre}` + (m.factorNombre ? ` · factor: ${m.factorNombre}` : "") + ` · vigila: ${m.indicadorNombre}`)),
      selEstado));
  if (m.descripcion) tarjeta.append(el("p", {}, m.descripcion));
  if (ev.estado === "evaluada") {
    tarjeta.append(el("section", { class: "kpis compactos" },
      kpi("Antes", fmt(ev.antes, 1) + " " + m.unidad, { texto: ev.periodoBase }, true),
      kpi("Después", fmt(ev.despues, 1) + " " + m.unidad, { texto: ev.periodoSeguimiento }, true),
      kpi("Cambio observado", conSigno(ev.variacion, 1), ev.variacionReferencia == null ? null : { texto: `referencia ${conSigno(ev.variacionReferencia, 1)}` }, true),
      kpi("Cambio descontando la referencia", conSigno(ev.efectoNeto, 1), { texto: "" }, true)),
      el("div", {}, chipVeredicto(ev.veredicto)));
  } else {
    tarjeta.append(el("div", {}, chipVeredicto(null)));
  }
  tarjeta.append(el("p", { class: "nota" }, ev.mensaje));
  return tarjeta;
}

function pintarMedidas(d) {
  $("notaMedidas").textContent = `Ámbito: ${nombreAmbito()}.`;
  $("listaMedidas").replaceChildren(...(d.medidas.length ? d.medidas.map(tarjetaMedida)
    : [el("div", { class: "vacio" }, "Todavía no hay medidas registradas en este ámbito.")]));
  $("avisoMedidas").textContent = d.aviso;
}

async function guardarMedida(ev) {
  ev.preventDefault();
  const btn = $("btnMedida"), estadoTxt = $("estadoMedida");
  btn.disabled = true;
  estadoTxt.textContent = "Guardando…";
  try {
    await apiIngesta("/api/medidas", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        centro: estado.centro,
        grupo: $("medAmbito").value === "" ? null : Number($("medAmbito").value),
        factor: $("medFactor").value || null,
        titulo: $("medTitulo").value,
        descripcion: $("medDescripcion").value,
        indicador: $("medIndicador").value,
        periodoInicio: Number($("medPeriodo").value)
      })
    });
    $("formMedida").reset();
    rellenarAmbitoMedida();
    estadoTxt.textContent = "Medida guardada.";
    await cargarMedidas();
  } catch (e) {
    estadoTxt.textContent = e.message;
  } finally {
    btn.disabled = false;
  }
}

$("btnCopiarResumen").addEventListener("click", copiarResumen);
$("formMedida").addEventListener("submit", guardarMedida);
