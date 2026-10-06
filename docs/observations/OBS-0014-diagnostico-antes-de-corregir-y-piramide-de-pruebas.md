# OBS-0014 — Diagnosticar antes de corregir y pirámide de pruebas

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Nivel del alumnado

1.º DAM, primer semestre. El alumnado ya ha construido dominio,
aplicación, persistencia, HTTP e interfaz para tableros, tarjetas,
etiquetas y checklist (OBS-0008 a OBS-0013). Este incremento no añade
ninguna función nueva: es la primera vez que el proyecto dedica un
incremento completo a **estabilizar** en vez de **construir**.

## Prerrequisitos

- OBS-0008 (transacciones SQLite)
- OBS-0011 (accesibilidad)
- OBS-0012 (reinicio y migraciones)
- OBS-0013 (relación muchos a muchos)

---

## Por qué diagnosticar antes de corregir

`[P]` Este incremento empezó **sin tocar ni una línea de código de
producción**. El primer paso fue construir, desde el router real (no
desde el README, que puede quedar desactualizado), un mapa completo
del recorrido y una tabla de defectos con evidencia:

```
| ID | Evidencia | Criterio afectado | Severidad | Corrección | Regresión |
```

**Para el alumnado:** es tentador, al encontrar un error, corregirlo
inmediatamente. El riesgo es corregir un síntoma sin entender la causa,
o "arreglar" algo que en realidad no está roto. Exigir una **prueba que
falle primero** (roja) antes de tocar el código de producción obliga a
demostrar que el defecto existe de verdad, y deja una prueba de
regresión que impide que vuelva a aparecer.

---

## Un defecto encontrado por analogía con otro ya corregido

`[G]` El defecto más interesante de este incremento (`DEF-1`) no se
encontró probando código nuevo, sino **leyendo la historia reciente
del proyecto**: al revisar `SqliteChecklistItemRepository`, se detectó
que ya existía una prueba (`SqliteChecklistDeleteAfterReorderTest`)
que documentaba un defecto ya corregido: compactar posiciones con una
única sentencia

```sql
UPDATE checklist_items
SET position = position - 1
WHERE card_id = ? AND position > ?
```

puede violar `UNIQUE(card_id, position)` si el orden en que SQLite
recorre las filas no coincide con el orden de `position` (por ejemplo,
después de una reordenación previa, donde el orden de `rowid` —el de
creación— ya no coincide con el orden visual).

Ese mismo patrón de código, casi idéntico, seguía presente **sin
corregir** en `SqliteCardRepository.delete()`. La lección es doble:

1. cuando encontráis y corregís un error, preguntaos si el mismo patrón
   de código aparece en otro sitio;
2. las pruebas de regresión no son solo para el código que las motivó:
   son documentación viva de un riesgo real que puede repetirse.

**Para el alumnado más avanzado:** este tipo de error de compactación
solo aparece de forma intermitente y depende del plan de consultas de
SQLite (qué fila procesa primero un `UPDATE` con varias filas
afectadas). Es un buen ejemplo de por qué una prueba automatizada que
fuerza el escenario exacto vale más que "probarlo a mano varias veces y
no verlo fallar": el orden de recorrido no es aleatorio, pero tampoco
es el que intuitivamente esperaríais.

---

## Un defecto de accesibilidad que solo aparece sin JavaScript

`[A]` El segundo defecto (`DEF-2`) no rompía ninguna prueba automática
existente: los botones de asignar/retirar etiqueta funcionaban
correctamente (el formulario enviaba la petición correcta), y con
JavaScript activo se veían bien (`app.js` añadía el verbo de la acción
dinámicamente). El problema solo era visible si os preguntabais: **¿qué
ve exactamente una persona usuaria que no tiene JavaScript, o cuyo
script todavía no ha terminado de cargar?**

La respuesta era: un botón con un único sustantivo ("Urgent"), sin
ningún verbo que indique si pulsarlo asigna o retira la etiqueta. La
funcionalidad "sin JavaScript" mencionada en los criterios de
aceptación no es solo "que el formulario funcione": también es "que el
texto visible tenga sentido" sin depender de un script.

**Para el alumnado:** un nombre accesible incompleto no lanza ninguna
excepción ni aparece en ningún log. Solo se detecta revisando
deliberadamente el HTML tal como llegaría al navegador antes de que se
ejecute cualquier script — exactamente lo que hace un test que usa
`java.net.http.HttpClient` en vez de un navegador real.

---

## La pirámide de pruebas en un proyecto real

`[G]` Antes de este incremento, el proyecto ya tenía:

- pruebas de dominio (`LabelModelTest`, ...) — rápidas, sin JDBC;
- pruebas de aplicación con dobles de prueba (`LabelServiceTest`, ...);
- pruebas de persistencia con SQLite real (`SqliteLabelRepositoryTest`, ...);
- pruebas HTTP con una sesión inyectada directamente en el almacén de
  sesiones (`BoardEndpointTest`, `LabelChecklistEndpointTest`).

Lo que faltaba era la punta de la pirámide: una prueba que recorriera
el sistema **de verdad**, de principio a fin, exactamente como lo haría
un cliente real: `POST /login` con credenciales reales, extraer la
cookie de sesión de la cabecera `Set-Cookie`, extraer el token CSRF del
HTML recibido (no inventarlo), y encadenar tablero → columna → tarjeta
→ etiqueta → checklist → logout → reinicio → nuevo login, todo por HTTP
real sobre un servidor real.

**Para el alumnado:** las pruebas de la base de la pirámide son
rápidas y numerosas porque prueban una sola pieza aislada; las de la
cúpula son pocas, más lentas, pero son las únicas que demuestran que
todas las piezas encajan **de verdad** cuando se combinan, tal como las
usaría una persona real. Ninguna prueba unitaria podría haber detectado
que faltaba extraer el token CSRF del HTML en vez de suponer su valor.

---

## Glosario nuevo

| Término | Significado |
| :--- | :--- |
| Corte vertical (*vertical slice*) | Un recorrido completo de la aplicación, de principio a fin, que atraviesa todas las capas para una funcionalidad concreta |
| Prueba de regresión | Prueba añadida específicamente para que un defecto ya corregido no vuelva a aparecer sin que alguien se dé cuenta |
| Pirámide de pruebas | Modelo que recomienda muchas pruebas rápidas y aisladas en la base, y pocas pruebas lentas y completas en la cúpula |
| Nombre accesible | El texto que un lector de pantalla anuncia para un control; debe bastar por sí solo para entender la acción |
