# Base y adaptación de la referencia para el alumnado

La copia inicial se prepara el **6 de octubre de 2026** desde la rama principal del proyecto AulaFlow, revisión `cdddc93436ee92a007d96b468982cc15064dbbd8`, que incorpora el rediseño final de la interfaz. La revisión registra la procedencia; no necesitas acceso al origen para utilizar esta copia.

## Qué se conserva

El proyecto funcional completo: fuentes Java, interfaz, traducciones, migraciones, pruebas, ejemplos HTTP y CSV —incluida la muestra binaria de UTF-8 inválido—, Maven Wrapper y herramientas de despliegue y mantenimiento. También las decisiones de arquitectura y las observaciones técnicas y docentes.

## Qué se adapta

- README dirigido al alumnado, con descarga por ZIP o Git y requisitos explícitos.
- Primer arranque en Windows, macOS y Linux, IntelliJ y problemas frecuentes.
- Recorrido para segundo de DAM, mapa de clases y etapas del proyecto.
- Índice técnico y procedimiento operativo de la copia actual.
- Retirada de informes de producción, planes cerrados, evidencias históricas de ejecución e incidencias administrativas del repositorio privado.
- Eliminación de enlaces a issues privadas.
- Instrucciones activas de JAR e imagen alineadas con `1.0.0`.
- Versión del arranque y del endpoint de salud corregida a `1.0.0`, coherente con Maven.
- Muestra `.bin` declarada binaria para preservar sus bytes.
- Wrapper y scripts Bash marcados ejecutables en Git para Linux y macOS.
- Licencia Unlicense ya elegida en el destino, conservada sin cambios.

No se importa el historial de producción, sus ramas ni sus etiquetas. Los incrementos de la documentación describen la evolución conceptual. Esta copia se identifica por sus propias revisiones de Git y no presupone una etiqueta `v1.0.0`.

La única modificación inicial de las fuentes Java es el texto de versión comunicado por la aplicación. Sus funcionalidades se conservan.
