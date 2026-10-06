# Documentación de AulaFlow

## Guías para empezar

1. [Primer arranque](alumnado/01-primer-arranque.md): Windows, macOS, Linux, IntelliJ, cuenta inicial y recuperación de errores.
2. [Recorrido para segundo de DAM](alumnado/02-recorrido-para-segundo.md): usar la aplicación y comprenderla sin haberla desarrollado antes.
3. [Mapa del código](alumnado/03-mapa-del-codigo.md): clases, responsabilidades y pruebas.
4. [Evolución del proyecto](alumnado/04-evolucion-del-proyecto.md): por qué se incorporó cada parte.

## Profundizar en una decisión

Un documento de arquitectura explica qué se decidió y por qué. Las observaciones añaden conceptos, dificultades y propuestas docentes. Conservan el contexto de la evolución original; sus números no son tareas que tengas que entregar ni etiquetas disponibles en esta copia.

| Tema | Decisión técnica | Explicación complementaria |
| --- | --- | --- |
| Java 26 y configuración | [Java](adr/ADR-0001-utilización-de-Java-26.md), [variables de entorno](adr/ADR-0002-configuracion-mediante-variables-de-entorno.md) | [Arranque y configuración](observations/OBS-0001-arranque-y-configuracion.md) |
| HTTP | [Servidor embebido](adr/ADR-0003-servidor-http-embebido.md), [contrato inicial](adr/ADR-0004-contrato-http-inicial.md) | [Petición y respuesta](observations/OBS-0003-peticion-y-respuesta-http.md) |
| Errores y trazabilidad | [Errores HTTP](adr/ADR-0005-trazabilidad-y-errores-http.md) | [Infraestructura HTTP](observations/OBS-0004-infraestructura-transversal-http.md) |
| Interfaz e idiomas | [Recursos web](adr/ADR-0006-recursos-web-estaticos-desde-classpath.md), [catálogos](adr/ADR-0007-catalogos-json-de-internacionalizacion.md) | [Internacionalización](observations/OBS-0006-internacionalizacion-y-catalogos.md) |
| SQLite | [JDBC y migraciones](adr/ADR-0008-sqlite-jdbc-y-migraciones-versionadas.md) | [JDBC y transacciones](observations/OBS-0008-jdbc-sqlite-y-transacciones.md) |
| Cuenta y sesión | [Identidad y contraseñas](adr/ADR-0009-identidad-contrasenas-y-sesiones.md) | [Autenticación](observations/OBS-0009-identidad-autenticacion-y-sesiones.md) |
| Tableros y tarjetas | [Columnas y CSRF](adr/ADR-0010-tableros-columnas-orden-y-proteccion-csrf.md), [tarjetas](adr/ADR-0011-tarjetas-orden-y-movimiento.md) | [Movimiento y accesibilidad](observations/OBS-0011-tarjetas-movimiento-y-accesibilidad.md) |
| Reinicio | [Recuperación del esquema](adr/ADR-0012-recuperacion-tras-reinicio-y-version-de-esquema.md) | [Reinicio y migraciones](observations/OBS-0012-reinicio-migraciones-y-fallos-controlados.md) |
| Etiquetas y listas | [Etiquetas y checklist](adr/ADR-0013-etiquetas-paleta-cerrada-y-checklist-idempotente.md) | [Relaciones y progreso](observations/OBS-0013-relacion-muchos-a-muchos-y-progreso-derivado.md) |
| CSV | [Importación en dos fases](adr/ADR-0014-contrato-csv-v1-importacion-en-dos-fases-y-neutralizacion-reversible.md) | [Parser y transacciones](observations/OBS-0015-parser-csv-transacciones-y-seguridad-de-interoperabilidad.md) |
| JAR y contenedor | [JAR ejecutable](adr/ADR-0015-artefacto-ejecutable-y-acceso-nativo-sqlite.md), [contenedor](adr/ADR-0016-contenedor-no-privilegiado-persistencia-y-healthcheck.md) | [Imagen y volumen](observations/OBS-0017-imagen-contenedor-volumen-y-healthcheck.md) |
| Copias de seguridad | [Backup y restore](adr/ADR-0017-backup-restauracion-y-endurecimiento-operativo.md) | [Recuperación y seguridad](observations/OBS-0018-backup-recuperacion-y-seguridad.md) |

## Procedimientos y ejemplos

- [Reproducir y desplegar la referencia actual](release/1.0.0.md).
- [Despliegue con Podman y Compose](deployment/2.1-local-podman-compose.md).
- [Backup y restauración](deployment/2.2-backup-restore-security.md).
- [Contrato CSV](contracts/csv/aulaflow-kanban-csv-v1.md).
- [Peticiones HTTP para Cartero](../http/cartero/README.md).
- [Demostración del recorrido funcional](demos/1.5-stable-vertical-slice.md).
- [Demostración de CSV](demos/2.0-csv-import-export.md).
- [Procedencia y adaptación](REFERENCIA.md).

Los documentos de demostración describen recorridos del proyecto original y sirven como propuestas de observación. Las pruebas que verifican tu copia están en `src/test/`; los informes históricos de producción no forman parte de esta referencia.
