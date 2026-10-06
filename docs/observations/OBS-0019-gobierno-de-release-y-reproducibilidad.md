# OBS-0019 — Gobierno de release y reproducibilidad

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Observación

El cierre de una versión no consiste únicamente en que el código funcione. El repositorio debe contar una historia coherente: versión Maven, nombre del JAR, etiqueta de imagen, instrucciones, CI y tag Git tienen que describir el mismo producto.

La auditoría de AulaFlow antes de 1.0 encontró un ejemplo docente útil: el producto había avanzado hasta el despliegue y la recuperación, pero algunos metadatos seguían anclados a etapas anteriores (`0.1.0-SNAPSHOT` y `2.1-dev`). Ninguno de esos valores impedía por sí solo desarrollar, pero sí impedían considerar el repositorio una release limpia.

## Valor técnico

Los metadatos forman parte del contrato operativo. Si difieren:

- una persona puede ejecutar un JAR distinto del que espera el `Containerfile`;
- Compose puede arrancar una imagen antigua existente en la máquina;
- una guía puede aparentar describir 1.0 mientras ejecuta una etiqueta de desarrollo;
- un tag Git pierde valor como punto de recuperación si no coincide con el nombre/versionado de los artefactos.

Por eso el incremento 2.3 incorpora un guard de CI específico para metadatos de release, además de las pruebas funcionales.

## Valor docente

Este cierre permite explicar que una release profesional incluye al menos cuatro capas de calidad:

1. **calidad funcional**: las historias de usuario siguen funcionando;
2. **calidad técnica**: compilación, pruebas y análisis automatizados pasan;
3. **calidad operativa**: el producto puede desplegarse, persistir y recuperarse;
4. **calidad de configuración y trazabilidad**: artefactos, documentación y control de versiones identifican el mismo baseline.

Es especialmente útil para Proyecto Intermodular y Entornos de Desarrollo porque muestra que “funciona en mi IDE” no equivale a “tenemos una release”.

## Regla para AulaFlow

`v1.0.0` será una fotografía inmutable de `main` después de validar y fusionar el release candidate. Cualquier trabajo funcional posterior pertenece a otra iteración o versión.
