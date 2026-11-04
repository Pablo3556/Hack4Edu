#!/usr/bin/env python3
# ---------------------------------------------------------------------
# Genera el profesorado SINTÉTICO de la demo y sus asignaciones a grupos.
#
#   python3 scripts/generar_profesorado.py
#     → datos/profesorado.csv            una fila por docente
#     → datos/asignaciones_docentes.csv  una fila por docente · grupo · asignatura
#
# Es determinista (semilla fija): volver a ejecutarlo produce los mismos archivos.
# Los docentes no tienen nombre (solo un código) y no se generan notas: el progreso
# del alumnado lo calcula la base de datos a partir de los datos de alumnado.
# Después de generarlos, cárgalos con ./scripts/cargar_profesorado.sh
# ---------------------------------------------------------------------
import csv
import random
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
SEMILLA = 2026
CENTROS = range(1, 10)          # analitica.centro (V2)
GRUPOS = ["A", "B", "C", "D"]   # analitica.grupo: 'Grupo A' … 'Grupo D' en cada centro
DOCENTES_POR_CENTRO = 8

# Asignatura → horas semanales por grupo
ASIGNATURAS = {
    "Lengua": 4,
    "Matemáticas": 4,
    "Inglés": 3,
    "Ciencias": 3,
    "Sociales": 3,
    "Educación Física": 2,
}


def generar():
    rnd = random.Random(SEMILLA)
    profesorado, asignaciones = [], []
    for centro in CENTROS:
        codigos = [f"P{centro:02d}{n:02d}" for n in range(1, DOCENTES_POR_CENTRO + 1)]
        for codigo in codigos:
            experiencia = max(0, min(38, round(rnd.gauss(15, 9))))
            profesorado.append({
                "codigo": codigo,
                "centro_id": centro,
                "anios_experiencia": experiencia,
                "nivel_formacion": rnd.choices(["Grado", "Máster", "Doctorado"], [35, 45, 20])[0],
                "horas_formacion_anio": max(0, min(200, round(rnd.gauss(55, 35)))),
                "tipo_contrato": rnd.choices(["Tiempo completo", "Tiempo parcial"], [75, 25])[0],
            })

        # Cada asignatura la reparten uno o dos docentes del centro entre los cuatro grupos.
        # Así cada docente tiene un conjunto distinto de grupos (si todos dieran clase a todos,
        # su resultado sería idéntico y no habría nada que mirar).
        horas = {c: 0 for c in codigos}
        imparte = {c: set() for c in codigos}
        for asignatura, h in ASIGNATURAS.items():
            if rnd.random() < 0.65:
                mezcla = GRUPOS[:]
                rnd.shuffle(mezcla)
                repartos = [sorted(mezcla[:2]), sorted(mezcla[2:])]
            else:
                repartos = [GRUPOS[:]]
            for grupos in repartos:
                # El docente con menos horas asignadas hasta ahora (desempate aleatorio)
                docente = min(codigos, key=lambda c: (horas[c], rnd.random()))
                for g in grupos:
                    asignaciones.append({"codigo_profesor": docente, "centro_id": centro, "grupo": g,
                                         "asignatura": asignatura, "horas_semana": h, "tutor": "no"})
                    horas[docente] += h
                    imparte[docente].add(g)

        # Una tutoría por grupo, cada una a un docente distinto que ya da clase en ese grupo
        tutores = set()
        for g in GRUPOS:
            candidatos = [a for a in asignaciones
                          if a["centro_id"] == centro and a["grupo"] == g and a["codigo_profesor"] not in tutores]
            elegida = rnd.choice(candidatos)
            elegida["tutor"] = "sí"
            tutores.add(elegida["codigo_profesor"])
    return profesorado, asignaciones


def escribir(nombre, filas):
    ruta = RAIZ / "datos" / nombre
    with ruta.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(filas[0].keys()), lineterminator="\n")
        w.writeheader()
        w.writerows(filas)
    print(f"{ruta.relative_to(RAIZ)}: {len(filas)} filas")


if __name__ == "__main__":
    p, a = generar()
    escribir("profesorado.csv", p)
    escribir("asignaciones_docentes.csv", a)
