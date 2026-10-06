# ADR-0014: Contrato CSV V1, importación en dos fases, copia nueva y neutralización reversible de fórmulas

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Estado: aceptada
* Fecha: 2026-08-03
* Proyecto: AulaFlow 1.0
* Incremento: 2.0
* Issue: #19

## Contexto

El incremento 2.0 añade interoperabilidad de datos Kanban mediante CSV.
La issue #19 exige un formato versionado, una previsualización o
validación previa, importación atómica, una política determinista de
duplicados, exportación con *round trip* semántico y protección frente
a fórmulas peligrosas. Existen varias formas razonables de resolver
cada una de estas exigencias; este documento fija las decisiones
tomadas y por qué.

El contrato de campos, codificación y comillas está descrito con
detalle en
[`docs/contracts/csv/aulaflow-kanban-csv-v1.md`](../contracts/csv/aulaflow-kanban-csv-v1.md).
Este ADR se centra en las decisiones arquitectónicas, no en repetir la
gramática del formato.

## Decisión 1 — CSV plano de un solo tablero, no un formato jerárquico ni multi-tablero

Se define un único CSV con tres tipos de registro (`BOARD`, `COLUMN`,
`CARD`) en una tabla plana, en vez de:

* **un CSV por tabla** (`boards.csv`, `columns.csv`, `cards.csv`)
  relacionados por identificador — descartado porque exige que la
  persona usuaria gestione varios archivos coherentes entre sí, sin
  aportar ninguna ventaja real en este alcance (un tablero, no una
  base completa);
* **un CSV con varios tableros por archivo** — descartado porque
  complica la previsualización (¿qué se muestra, un resumen por
  tablero?), la política de duplicados (¿qué pasa si dos tableros del
  mismo archivo tienen el mismo nombre?) y el caso de uso real de la
  issue, que es compartir *un* tablero entre docentes o entre
  instalaciones;
* **CSV solo de tarjetas**, asumiendo que las columnas ya existen en
  el tablero destino — descartado porque exigiría un tablero destino
  preexistente y por tanto una semántica de fusión, que el incremento
  2.0 declara explícitamente fuera de alcance.

Un único archivo con tres tipos de registro permite representar el
tablero completo con una gramática simple, comprobable con un parser
de estados y una tabla de validación, sin relaciones entre archivos.

## Decisión 2 — Importación en dos fases: previsualización con token, después confirmación

Se separa la importación en dos peticiones HTTP distintas:

1. `POST .../import/preview` (HTML) o `.../import/validate` (API):
   valida el archivo completo, no escribe nada en la base y, si es
   válido, guarda un `CsvImportPlan` inmutable en un almacén temporal
   en memoria, devolviendo un token opaco de un solo uso.
2. `POST .../import/confirm` (HTML) o `.../import` (API): recibe
   únicamente el token, recupera el plan ya validado y lo escribe de
   forma atómica.

Se descarta una **importación de una sola fase** (subir el archivo y
confirmarlo en la misma petición) porque:

* obliga a repetir la subida completa del archivo si la persona
  usuaria solo quiere revisar avisos antes de decidir, lo que es
  peor experiencia y más coste de red;
* impide mostrar un resumen de validación (columnas, tarjetas,
  avisos, errores) antes de comprometerse a crear un tablero nuevo;
* la issue #19 pide explícitamente previsualización o validación
  previa como criterio de aceptación.

El plan de importación **no** se persiste en disco ni en la base de
datos entre las dos fases: vive en memoria, ligado a la sesión que lo
creó, con caducidad (`PENDING_IMPORT_TTL = 10 minutos`) y un máximo de
planes pendientes por sesión (`MAX_PENDING_IMPORTS_PER_SESSION = 3`).
El token está ligado a **sesión, propietario, token y expiración**, no
solo al identificador del propietario: esto impide que otra sesión
del mismo administrador (por ejemplo, otra pestaña tras cerrar sesión
y volver a entrar) reutilice un plan ajeno a su ciclo de vida, y que un
token filtrado desde otra sesión sea suficiente para confirmar una
importación.

## Decisión 3 — Almacén de planes en memoria, no un archivo temporal ni una tabla nueva

Se descartan dos alternativas para guardar el plan entre la
previsualización y la confirmación:

* **archivo temporal en disco**: exige gestión de limpieza, rutas
  seguras, permisos de sistema de archivos y aumenta la superficie de
  ataque (un archivo con contenido de usuario en un directorio
  compartido). El propio documento de instrucciones del incremento lo
  prohíbe explícitamente ("sin archivos temporales");
* **tabla `pending_imports` en SQLite** con una migración `V013`:
  descartado porque el plan de importación es un dato **de sesión**,
  no un dato persistente de negocio. Persistirlo en la base implicaría
  una migración, una tarea de limpieza periódica de filas caducadas y
  la posibilidad de que un plan sobreviva a un reinicio del servidor,
  lo que contradice la semántica de "sesión en memoria" ya establecida
  para el resto de AulaFlow (sesiones de autenticación, límite de
  intentos de login) y añadiría alcance no pedido por la issue #19.

El almacén (`InMemoryPendingImportStore`) sigue el mismo patrón que
`InMemorySessionStore` e `InMemoryLoginAttemptLimiter`: un
`ConcurrentMap` protegido, limpieza perezosa en el momento de acceso
(no un hilo de limpieza en segundo plano, para no añadir concurrencia
adicional innecesaria en este alcance) y invalidación de un solo uso
tras la confirmación.

## Decisión 4 — Cada importación crea un tablero nuevo; no existe fusión

Confirmar una importación siempre ejecuta `INSERT` de un tablero
nuevo, nunca `UPDATE` sobre uno existente. Repetir la misma
importación produce dos tableros independientes con el mismo
contenido textual.

Se descarta cualquier semántica de **fusión** (*merge*) con un tablero
existente por nombre porque:

* exige decidir qué ocurre con columnas o tarjetas que ya existían y
  no aparecen en el CSV (¿se eliminan? ¿se conservan?), una decisión
  de producto no resuelta por la issue #19, que la declara
  explícitamente fuera de alcance;
* complicaría la atomicidad: una fusión de verdad necesita comparar
  el estado actual con el nuevo antes de decidir qué escribir, lo que
  es un problema distinto (y más complejo) que una inserción atómica
  de un grafo de datos nuevo.

Esta decisión también simplifica la propiedad: el tablero creado
pertenece siempre a quien confirma la importación, sin ambigüedad
sobre si se está modificando un recurso de otra persona.

## Decisión 5 — Importación atómica mediante una única conexión JDBC con `setAutoCommit(false)`

Siguiendo el patrón ya establecido en `SqliteBoardRepository.create`
(ADR-0008, ADR-0010), la importación de un `CsvImportPlan` completo se
implementa en un puerto específico (`CsvImportRepository`) que abre
**una sola conexión**, inserta el tablero, después cada columna, y
después cada tarjeta dentro de esa misma conexión y transacción, hace
`commit()` solo si todas las escrituras tienen éxito y `rollback()`
ante cualquier fallo SQL.

Se descarta insertar llamando en bucle a los repositorios de dominio
ya existentes (`BoardRepository.create`, después
`CardRepository.create` por cada tarjeta) porque cada uno de ellos abre
su propia conexión y su propia transacción: un fallo a mitad de la
importación dejaría el tablero y algunas tarjetas ya confirmadas en
la base, exactamente el estado parcial que la issue #19 prohíbe
explícitamente ("un fallo no deja importaciones parciales"). Por eso
existe un puerto de importación específico, separado del resto de
repositorios de tablero.

## Decisión 6 — Neutralización reversible de fórmulas en vez de eliminación o escape no reversible

Un campo de texto que empieza por `=`, `+`, `-` o `@` puede
interpretarse como una fórmula al abrir el CSV en una hoja de cálculo
(*CSV/Formula Injection*, CWE-1236). Se decide neutralizar ese riesgo
anteponiendo un apóstrofo (`'`) —convención ya usada por Excel y
LibreOffice Calc para forzar texto literal— y invertir la
transformación de forma determinista al reimportar.

Se descartan dos alternativas:

* **eliminar o sustituir el carácter peligroso**: no es reversible
  (pierde información: `=1+1` y `1+1` dejan de distinguirse) y rompe
  el *round trip* semántico exigido por la issue #19;
* **rechazar el archivo completo si contiene un carácter peligroso**:
  descartado porque penaliza contenido legítimo (un título de tarjeta
  que empiece por un signo `-`, por ejemplo una lista con guiones, o
  un correo institucional que use `@`), y porque el objetivo del
  incremento es interoperabilidad segura, no censura de contenido
  válido del dominio.

La neutralización se aplica de forma simétrica: exportar y volver a
importar el mismo tablero reproduce exactamente los mismos valores de
texto, incluidos los apóstrofos originales, mediante la regla de
duplicar el apóstrofo cuando el valor original ya empezaba por uno
(ver contrato, sección 9).

## Decisión 7 — Ningún formato adicional ni biblioteca externa

Se descartan explícitamente en este incremento:

* **XLSX u ODS**: formatos binarios/comprimidos que exigirían una
  biblioteca externa (Apache POI u equivalente) solo para este
  incremento, contradiciendo la política de "Java estándar" del
  proyecto y el alcance definido por la issue #19;
* **Apache Commons CSV u OpenCSV**: bibliotecas de terceros que
  resolverían el trocedado CSV, pero el objetivo pedagógico explícito
  del incremento es que el alumnado implemente y comprenda una máquina
  de estados de trocedado CSV real, con sus casos límite (comillas,
  comas, saltos internos, UTF-8, BOM), no que dependa de una caja
  negra;
* **JSON o ZIP** como formato de intercambio: fuera de alcance de la
  issue #19, que pide específicamente CSV por su legibilidad directa
  en cualquier hoja de cálculo sin herramientas adicionales.

## Consecuencias

* El plan de importación pendiente se pierde si el servidor se
  reinicia entre la previsualización y la confirmación; la persona
  usuaria debe repetir la previsualización. Se documenta como
  comportamiento esperado, coherente con el resto de estado en memoria
  de AulaFlow (sesiones, límite de intentos de login).
* No existe historial de importaciones ni forma de deshacer una
  importación confirmada distinta de eliminar manualmente el tablero
  creado; fuera de alcance de la issue #19.
* Repetir una importación crea tableros duplicados si la persona
  usuaria no lo advierte; se mitiga mostrando siempre el nombre del
  tablero en la previsualización antes de confirmar.
