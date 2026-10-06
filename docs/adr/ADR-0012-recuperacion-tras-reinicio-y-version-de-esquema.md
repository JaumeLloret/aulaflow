# ADR-0012: Recuperación tras reinicio y política ante versión de esquema

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Estado: aceptada
* Fecha: 2026-08-02
* Proyecto: AulaFlow 1.0
* Incremento: 1.3
* Issue: #13

## Contexto

El incremento 1.3 no añade funcionalidad Kanban nueva: demuestra y
endurece que identidad, tableros, columnas, tarjetas y su orden
sobreviven a un reinicio real de AulaFlow sobre el mismo archivo
SQLite, y que los fallos de integridad o de esquema se gestionan de
forma segura en vez de dejar el servidor arrancar en un estado
engañoso.

Existían dos decisiones de diseño con impacto real que no podían
resolverse solo leyendo el código existente:

1. cómo demostrar de forma convincente que un "reinicio" no reutiliza
   por accidente ningún objeto, conexión o caché del ciclo anterior;
2. qué debe ocurrir si el archivo SQLite contiene un historial de
   migraciones (`schema_migrations`) con una versión que esta build no
   reconoce, por ejemplo porque procede de una versión más reciente de
   AulaFlow.

## Decisión 1 — Frontera del reinicio en la prueba de dos ciclos

`SqliteConnectionFactory` ya es sin estado: abre y cierra una conexión
JDBC por operación, no mantiene ningún pool ni caché en memoria. Por
tanto, no existe ningún componente de producción que necesite "cerrarse"
explícitamente entre dos ciclos para poder reconstruirlo con garantías.

Se descarta extraer una clase de composición nueva (`ApplicationRuntime`
de test, `PersistenceBootstrap` o similar) porque no soluciona ninguna
dificultad real de cierre y se convertiría en una abstracción
especulativa no justificada, contraria a las restricciones de
arquitectura del proyecto.

En su lugar, `RestartRecoveryIntegrationTest` fuerza la frontera del
reinicio mediante el propio compilador: el primer ciclo se ejecuta
íntegro dentro de un método privado (`runFirstCycle`) que **devuelve
únicamente un `record` con valores primitivos** (identificadores,
nombres, posiciones). Ninguna variable de ese método — conexión,
`SqliteConnectionFactory`, repositorio o servicio — puede llegar al
segundo ciclo, porque sale de alcance al terminar el método. El segundo
ciclo (`verifySecondCycle`) construye de nuevo, desde cero, exactamente
la misma composición que usa `AulaFlowApplication`.

Esto hace estructuralmente imposible que la prueba pase por reutilizar
por accidente un objeto del primer ciclo, sin necesidad de reiniciar la
JVM ni de ningún mecanismo adicional.

## Decisión 2 — Fallo temprano ante una versión de esquema no reconocida

Antes de este incremento, `SqliteMigrator.migrate()` solo comprobaba
qué migraciones de su propio catálogo **faltaban** por aplicar. Si la
base ya contenía una versión registrada en `schema_migrations` que no
pertenece al catálogo actual (por ejemplo `V009` aplicada por una build
más reciente de AulaFlow que este código no conoce), el migrador la
ignoraba silenciosamente y continuaba el arranque como si la base fuera
compatible. Eso es exactamente el escenario que el criterio de
aceptación de la issue #13 pide evitar: una base incompatible debe
producir un fallo temprano y controlado, no un arranque parcial o una
corrupción silenciosa.

Se decide que `SqliteMigrator.migrate()`, tras leer las versiones ya
aplicadas y **antes** de aplicar ninguna migración pendiente, calcule
la diferencia entre esas versiones y el catálogo conocido por la build
actual. Si existe alguna versión aplicada que el catálogo no reconoce,
se lanza `PersistenceException` inmediatamente, sin abrir ninguna
transacción de escritura.

Consecuencias:

- el arranque falla antes de tocar el servidor HTTP (`migrate()` se
  invoca antes de `AulaFlowHttpServer.create(...)` en
  `AulaFlowApplication.main`), por lo que nunca hay un arranque parcial;
- no se borra ni modifica ningún dato: el chequeo ocurre en modo lectura;
- el mensaje de la excepción incluye las versiones no reconocidas (por
  ejemplo `[V999]`) porque son simples etiquetas internas de esquema, no
  rutas, SQL ni datos de usuario, por lo que no vulneran la restricción
  de no filtrar detalles sensibles;
- no se implementa ninguna migración descendente ni un intento
  automático de "arreglar" el esquema: el profesorado debe usar una
  base creada por la misma versión de AulaFlow o actualizar la
  aplicación.

## Opciones descartadas

- **Ignorar el problema y confiar en `CREATE TABLE IF NOT EXISTS`**:
  descartado porque no protege frente a un esquema que ya cambió de
  forma incompatible; el fallo aparecería tarde, en tiempo de consulta,
  con un mensaje SQL menos claro.
- **Migración descendente automática**: fuera de alcance de la issue
  #13 y del proyecto; los criterios de aceptación piden explícitamente
  que no se implemente.
- **Clase de composición reutilizable (`ApplicationRuntime` de test)**:
  descartada por no aportar valor real dado que
  `SqliteConnectionFactory` ya es sin estado; habría añadido una
  abstracción sin una dificultad de cierre que resolver.
- **Reflection o hooks de producción para forzar un fallo transaccional
  en la prueba de atomicidad**: descartado a favor de reproducir el
  mismo patrón transaccional real (`setAutoCommit(false)` + rollback)
  con una violación genuina de `UNIQUE(column_id, position)`, sin tocar
  la superficie pública de los repositorios.
