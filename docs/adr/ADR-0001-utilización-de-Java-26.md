# ADR-0001 — Utilización de Java 26

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Estado

Aceptada.

## Decisión

AulaFlow 1.0 utilizará Eclipse Temurin JDK 26 como entorno de
compilación y ejecución.

No se permitirán características Preview, Incubator ni Early Access.

## Consecuencias

- IntelliJ IDEA debe configurarse con JDK 26.
- Maven debe compilar con `release 26`.
- GitHub Actions debe utilizar Java 26.
- Las imágenes Docker deberán contener Java 26.
- El proyecto deberá reevaluar su versión del JDK cuando Java 26
  deje de recibir actualizaciones.
