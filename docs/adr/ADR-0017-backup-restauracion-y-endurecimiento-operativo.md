# ADR-0017 — Backup, restauración y endurecimiento operativo

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Estado

Aceptado para el incremento 2.2.

## Contexto

AulaFlow 1.0 utiliza SQLite y ya puede ejecutarse como JAR o mediante Podman. El producto necesita un procedimiento de recuperación verificable y una revisión de seguridad antes de congelar la versión 1.0.

Una copia directa del fichero de una base activa no constituye por sí sola una política de backup segura. Del mismo modo, una restauración no debe sustituir datos válidos antes de comprobar que la copia candidata pertenece a AulaFlow y es íntegra.

## Decisiones

### Backup de una base activa

La copia se crea mediante la Online Backup API de SQLite expuesta por `sqlite-jdbc`.

El resultado se guarda fuera del fichero activo con nombre UTC fechado:

```text
aulaflow-YYYYMMDDTHHmmssSSSZ.db
```

Una operación se considera correcta únicamente si la copia posterior supera la validación.

### Validación

Toda copia candidata debe superar:

- `PRAGMA integrity_check`;
- `PRAGMA foreign_key_check`;
- presencia del historial `schema_migrations`;
- ausencia de versiones de esquema desconocidas para esta instalación.

La validación se abre en modo de solo lectura y rechaza ficheros inexistentes, vacíos o no regulares.

### Restauración

La restauración es deliberadamente **offline**. AulaFlow debe estar detenido.

El procedimiento:

1. restringe el nombre al directorio de backups configurado;
2. valida la copia antes de tocar la base activa;
3. copia la candidata a un temporal en el mismo filesystem que la base activa;
4. valida el temporal;
5. sustituye la base mediante movimiento atómico cuando el filesystem lo admite;
6. elimina sidecars SQLite obsoletos únicamente después de sustituir con éxito.

Una copia inválida no puede alcanzar el paso de sustitución.

### Contenedores

Los datos y las copias tienen ciclos de vida independientes:

```text
aulaflow-data     -> /var/lib/aulaflow
aulaflow-backups  -> /var/lib/aulaflow-backups
```

El script de restore rechaza operar mientras un contenedor esté utilizando el volumen de datos.

### Endurecimiento HTTP

El pipeline estándar añade transversalmente:

- `Content-Security-Policy` restrictiva y compatible con los recursos actuales;
- `X-Frame-Options: DENY`;
- `Referrer-Policy: strict-origin-when-cross-origin`;
- `Permissions-Policy` desactivando cámara, micrófono y geolocalización.

No se añade HSTS mientras AulaFlow se sirva directamente por HTTP local. Cuando exista terminación TLS, HSTS corresponderá al proxy HTTPS.

### Logs

Los errores HTTP inesperados y los fallos de arranque dejan de imprimir stack traces en el log operativo. Se conserva información suficiente para correlación (`requestId` y tipo de excepción) sin exponer detalles internos por defecto.

## Decisiones conservadas tras la revisión

- contraseñas: política de 15–128 puntos de código y PBKDF2-HMAC-SHA256 con sal aleatoria y 600.000 iteraciones;
- sesiones: identificadores aleatorios, almacenamiento en memoria y expiración;
- cookies: `HttpOnly`, `SameSite=Strict` y `Secure` configurable para HTTPS;
- CSRF: token sincronizado de sesión en operaciones mutables;
- autorización: el `ownerId` autenticado se propaga a servicios y repositorios para limitar recursos del administrador.

No se modifica lo anterior porque la auditoría no ha encontrado un defecto que justifique aumentar el alcance del MVP.

## Consecuencias

### Positivas

- backup consistente incluso con la aplicación activa;
- restore fail-safe frente a copias corruptas o futuras;
- procedimiento reproducible en local y contenedor;
- backups fuera de Git y separados del volumen principal;
- menor exposición de información interna en logs;
- defensa HTTP en profundidad sin framework externo.

### Limitaciones aceptadas

- restore requiere parada de servicio;
- no existe cifrado de backups integrado;
- no se implementa retención automática en 1.0;
- HSTS queda para el despliegue HTTPS;
- no se introduce un gestor centralizado de secretos.

## Verificación

La parte automatizable queda cubierta por pruebas de configuración, backup/restore, corrupción, esquema futuro y cabeceras HTTP. El cierre exige además un ensayo real en Podman y navegador antes del merge.
