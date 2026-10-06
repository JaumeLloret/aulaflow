# ADR-0018 — Versionado, release y congelación de AulaFlow 1.0

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Estado

Aceptado para el incremento 2.3.

## Contexto

AulaFlow ha completado el alcance funcional y operativo previsto para 1.0. Antes de publicar un baseline estable, el repositorio todavía conservaba metadatos de desarrollo: Maven declaraba `0.1.0-SNAPSHOT`, el `Containerfile` esperaba ese JAR y el despliegue usaba la etiqueta local `2.1-dev`.

Una release identificada mediante `v1.0.0` no sería reproducible ni inequívoca si el artefacto construido continuara presentándose como snapshot o si los procedimientos operativos usaran una etiqueta perteneciente a un incremento previo.

## Decisión

AulaFlow adopta para el cierre de 1.0 las siguientes reglas:

1. la versión Maven del producto es `1.0.0`;
2. el artefacto ejecutable de release es `target/aulaflow-1.0.0.jar`;
3. la imagen local estable de referencia es `localhost/aulaflow:1.0.0`;
4. `Containerfile`, Compose, scripts de mantenimiento y CI deben referirse al mismo baseline estable;
5. la CI comprueba explícitamente la coherencia de esos metadatos;
6. el tag Git `v1.0.0` se crea únicamente sobre `main`, después de fusionar la PR final validada;
7. el tag es inmutable: una corrección posterior requiere otra versión y no mover `v1.0.0`;
8. desde el cierre, el alcance funcional de AulaFlow 1.0 queda congelado. Las nuevas funcionalidades se planifican fuera de la release 1.0.

## Qué significa reproducible en este proyecto

Otra persona debe poder situarse en el commit etiquetado `v1.0.0` y, con JDK 26, Maven Wrapper y Podman, ejecutar las instrucciones documentadas para:

- verificar el código;
- producir `aulaflow-1.0.0.jar`;
- construir `localhost/aulaflow:1.0.0`;
- iniciar el servicio con un volumen persistente;
- comprobar `/api/v1/health`;
- seguir los procedimientos documentados de backup y restore.

No se afirma reproducibilidad bit a bit del JAR o de la imagen. El requisito de 1.0 es reproducibilidad funcional y operativa desde el código y configuración versionados.

## Política de cambios tras la congelación

El tag `v1.0.0` no se modifica. Si se descubre un defecto después de publicarlo:

- se registra la incidencia;
- se evalúa su severidad;
- se corrige en una rama posterior;
- se publica una versión nueva cuando corresponda.

No se incorporan funcionalidades nuevas a la rama de release para “aprovechar” el cierre.

## Consecuencias

### Positivas

- artefacto, contenedor, documentación y tag expresan la misma versión;
- desaparece la ambigüedad entre incremento técnico y versión del producto;
- la CI detecta regresiones en los metadatos de release;
- el baseline docente puede recuperarse de forma inequívoca;
- las evoluciones posteriores no alteran la referencia 1.0.

### Costes y límites

- cualquier corrección posterior debe versionarse expresamente;
- las guías históricas de incrementos pueden conservar nombres anteriores cuando describen el momento en que fueron escritas;
- la etiqueta de imagen `localhost/aulaflow:1.0.0` es una referencia local, no implica publicación en un registry externo.

## Verificación

El cierre exige:

- CI completa en verde con Java 26;
- construcción de la imagen estable;
- comprobación automática de metadatos de release;
- evidencia funcional y operativa consolidada;
- ensayo final del release candidate en el entorno local de referencia;
- creación de `v1.0.0` solo después de fusionar el candidato validado.
