"use strict";

// ---------------------------------------------------------------------
// Sesión y permisos por rol
//   Qué ve cada rol en la web. El servidor comprueba lo mismo en cada endpoint:
//   esconder una pestaña aquí es solo comodidad, no seguridad.
// ---------------------------------------------------------------------
const PERMISOS = {
  docente: {
    nombre: "Docente",
    vistas: ["panel", "analisis", "semanal", "medidas"],
    ambitos: ["grupo"]
  },
  direccion: {
    nombre: "Dirección",
    vistas: ["panel", "analisis", "semanal", "direccion", "medidas"],
    ambitos: ["centro", "grupo"]
  },
  administracion: {
    nombre: "Administración",
    vistas: ["panel", "direccion", "medidas"],
    ambitos: ["global", "centro"],
    soloConsulta: true
  }
};

const sesion = { usuario: null, rol: null, permisos: null, centro: null, grupo: null };

const irALogin = () => { location.href = "/login.html"; };

// Si la sesión caduca, cualquier petición a la API vuelve a la pantalla de login
const fetchOriginal = window.fetch.bind(window);
window.fetch = async (...args) => {
  const r = await fetchOriginal(...args);
  if (r.status === 401 || (r.redirected && new URL(r.url).pathname === "/login.html")) {
    irALogin();
    throw new Error("La sesión ha caducado.");
  }
  return r;
};

/** Pide al servidor quién es el usuario y adapta la página a su rol. Devuelve false si no hay sesión. */
async function cargarSesion() {
  let datos;
  try {
    const r = await fetch("/api/sesion", { headers: { Accept: "application/json" } });
    datos = await r.json();
  } catch (_) {
    irALogin();
    return false;
  }
  sesion.usuario = datos.usuario;
  sesion.rol = datos.rol;
  sesion.centro = datos.centro ?? null; // centro y grupo asignados (null = todos)
  sesion.grupo = datos.grupo ?? null;
  sesion.permisos = PERMISOS[datos.rol];
  if (!sesion.permisos) {
    document.querySelector("main").replaceChildren(
      el("div", { class: "panel error" }, "Tu usuario no tiene ningún rol asignado. Pide acceso al equipo directivo."));
    return false;
  }

  $("sesionUsuario").textContent = sesion.usuario;
  $("sesionRol").textContent = sesion.permisos.nombre;
  $("sesion").hidden = false;
  // La comparativa entre centros es solo de administración (el servidor la deniega a los demás)
  $("panelCentros").hidden = sesion.rol !== "administracion";
  // Las estadísticas del profesorado también son solo de administración
  $("panelProgreso").hidden = sesion.rol !== "administracion";
  document.body.classList.toggle("solo-consulta", !!sesion.permisos.soloConsulta);

  // Pestañas: solo las del rol
  for (const [vista, [tab, panel]] of Object.entries({
    panel: ["tabPanel", "vistaPanel"], analisis: ["tabAnalisis", "vistaAnalisis"], semanal: ["tabSemanal", "vistaSemanal"],
    direccion: ["tabDireccion", "vistaDireccion"], medidas: ["tabMedidas", "vistaMedidas"]
  })) {
    $(tab).hidden = !sesion.permisos.vistas.includes(vista);
    if ($(tab).hidden) $(panel).hidden = true;
  }

  // Ámbitos: solo los del rol (si solo hay uno, el selector sobra)
  const botones = document.querySelectorAll(".roles button");
  botones.forEach((b) => { b.hidden = !sesion.permisos.ambitos.includes(b.dataset.rol); });
  document.querySelector(".roles").hidden = sesion.permisos.ambitos.length < 2;
  $("btnSalir").addEventListener("click", async () => {
    try { await fetchOriginal("/api/sesion/salir", { method: "POST" }); } finally { irALogin(); }
  });
  return true;
}

/** Ámbito con el que se abre cada pestaña, entre los que permite el rol. */
function ambitoPara(vista) {
  const preferidos = vista === "direccion" ? ["centro"] : vista === "panel" ? ["global", "centro", "grupo"] : ["grupo", "centro"];
  return preferidos.find((a) => sesion.permisos.ambitos.includes(a));
}
