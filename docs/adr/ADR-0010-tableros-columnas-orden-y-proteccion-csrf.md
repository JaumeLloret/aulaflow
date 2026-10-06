# ADR-0010: Tableros personales, columnas ordenadas y protección CSRF

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Estado: aceptada
* Fecha: 2026-07-31
* Proyecto: AulaFlow 1.0
* Incremento: 1.1
* Issue: #9

## Contexto

AulaFlow ya dispone de un administrador persistente, autenticación y
sesiones de servidor. El incremento 1.1 incorpora el primer comportamiento
Kanban: tableros personales con columnas ordenadas.

Las nuevas operaciones modifican estado desde una sesión basada en cookie.
ADR-0009 dejó expresamente pendiente revisar los tokens CSRF sincronizados
cuando aparecieran operaciones autenticadas con efectos de negocio.

La solución debe conservar JDBC y SQLite en infraestructura, comprobar la
pertenencia de los recursos, ser observable para alumnado de 1.º DAM y no
anticipar tarjetas, colaboración, roles ni un framework.

## Modelo de dominio

El dominio representará:

```text
BoardId
BoardName
Board
ColumnId
ColumnName
BoardColumn
```

Los identificadores serán enteros positivos estables generados por SQLite.
No se reutilizará la posición visible como identificador.

Los nombres se recortarán en sus extremos, admitirán Unicode y no podrán
estar vacíos. El nombre de tablero tendrá entre 1 y 100 puntos de código;
el de columna, entre 1 y 80. No se impondrá unicidad a los nombres porque
dos tableros o columnas pueden compartir una etiqueta con significados
distintos para la persona usuaria.

Cada tablero pertenecerá al identificador del administrador que lo creó.
Cada columna pertenecerá a un único tablero.

La posición de columna será un entero mayor o igual que cero. Dentro de un
tablero las posiciones serán únicas y contiguas:

```text
0, 1, 2, ... n - 1
```

El dominio no importará JDBC, SQLite, HTTP, cookies, rutas ni SQL.

## Columnas iniciales

Crear un tablero añadirá, dentro de la misma transacción:

```text
Per fer
En curs
Fet
```

Las etiquetas pasan a ser datos editables del tablero. No cambian cuando
se cambia el idioma de la interfaz. El valenciano se utiliza como valor
inicial porque es el idioma inicial y de reserva de AulaFlow.

No se incorpora todavía un sistema de plantillas.

## Aplicación y persistencia

La aplicación definirá un único puerto de repositorio para los casos de
uso reales de tableros y columnas. El servicio de aplicación coordinará
validación, pertenencia y operaciones; no conocerá JDBC.

La infraestructura SQLite implementará el puerto mediante conexiones
nuevas por operación, `try-with-resources` y `PreparedStatement` para
todos los datos variables.

Se añadirán migraciones SQL versionadas, con una sentencia por recurso,
para:

* la tabla `boards`;
* la tabla `board_columns`;
* los índices necesarios para buscar por propietario y tablero.

`boards.owner_id` referenciará al administrador y
`board_columns.board_id` referenciará al tablero. Las restricciones de
SQLite reforzarán nombres no vacíos, posiciones no negativas y unicidad
de posición dentro de cada tablero.

Todas las lecturas y mutaciones incluirán el propietario autenticado en
su criterio. Un identificador existente de otro propietario se tratará
igual que uno inexistente y producirá `404`, para no revelar recursos.

Crear un tablero y sus columnas será atómico. Reordenar recibirá la
permutación completa de identificadores actuales, sin duplicados ni
ausencias, y se ejecutará dentro de una transacción. Primero asignará
posiciones temporales posteriores al rango final y después las posiciones
definitivas para no colisionar con la restricción única. Un fallo realizará
rollback.

## Sesión y token CSRF

Cada sesión autenticada contendrá un token CSRF independiente de su
identificador. El token tendrá 32 bytes generados mediante `SecureRandom`
y se codificará en Base64 URL sin relleno.

El token:

* se generará en el servidor al iniciar sesión;
* permanecerá ligado a esa sesión hasta logout o expiración;
* no se guardará en SQLite;
* no se incluirá en URL, logs ni mensajes de error;
* se comparará sin salida temprana;
* se incluirá como campo oculto `_csrf` en formularios;
* se enviará mediante `X-CSRF-Token` en mutaciones de la API.

Toda mutación autenticada exigirá un token válido y rechazará además un
`Sec-Fetch-Site: cross-site`. `SameSite=Strict` se conserva como defensa
en profundidad. La ausencia o alteración del token producirá un `403`
controlado y no modificará estado.

El token no protege login porque todavía no existe una sesión
autenticada. Logout pasa a exigir el token sincronizado al ser una
mutación autenticada.

## Contrato HTTP

La interfaz HTML utilizará:

```text
GET  /boards
POST /boards
GET  /boards/{boardId}
POST /boards/{boardId}/rename
POST /boards/{boardId}/columns
POST /boards/{boardId}/columns/{columnId}/rename
POST /boards/{boardId}/columns/reorder
```

Los formularios usarán `application/x-www-form-urlencoded`, realizarán
redirección `303` después del éxito e incluirán `_csrf`.

La API utilizará:

```text
GET   /api/v1/boards
POST  /api/v1/boards
GET   /api/v1/boards/{boardId}
PATCH /api/v1/boards/{boardId}
POST  /api/v1/boards/{boardId}/columns
PATCH /api/v1/boards/{boardId}/columns/{columnId}
PUT   /api/v1/boards/{boardId}/columns/order
```

Las respuestas de la API serán JSON. Para mantener un parser pequeño y
común sin añadir dependencias, las mutaciones recibirán cuerpos
`application/x-www-form-urlencoded`; el reordenado enviará una lista de
identificadores separada por comas. La API exigirá el token mediante la
cabecera `X-CSRF-Token`.

Los métodos no admitidos devolverán `405` con `Allow`. Entradas inválidas
devolverán `400`; falta de autenticación en la API, `401`; token inválido,
`403`; recurso inexistente o ajeno, `404`. Los errores conservarán el
contrato JSON y `X-Request-Id`.

La interfaz seguirá siendo bilingüe, accesible, mobile-first y funcional
sin JavaScript para las operaciones esenciales.

## Transacciones y concurrencia

SQLite continuará sin WAL, pool ni conexión global. Cada operación abrirá
su conexión y activará claves foráneas.

Las operaciones compuestas usarán una transacción explícita con commit y
rollback. No se introducirá una abstracción transaccional genérica porque
los límites atómicos pertenecen al adaptador concreto del repositorio.

La unicidad de posición en base de datos protege frente a estados
inconsistentes. Este incremento no intenta resolver edición colaborativa
concurrente; no existe más de un administrador activo como requisito del
producto.

## Alternativas descartadas

### Posición como identificador

Se descarta porque reordenar cambiaría la identidad de la columna y
rompería enlaces estables.

### Orden implícito por identificador o fecha

Se descarta porque no permite expresar una reordenación y depende de un
detalle de almacenamiento.

### Nombres únicos

Se descarta porque no es una regla de negocio solicitada y añade fricción
sin evitar ambigüedad suficiente.

### Traducir automáticamente columnas existentes

Se descarta porque modificaría datos elegidos por la persona usuaria al
cambiar una preferencia de interfaz.

### Enviar el token CSRF en la URL

Se descarta porque las URL pueden aparecer en historial, referencias y
registros.

### Confiar solo en `SameSite`

Se descarta porque es una defensa complementaria y las nuevas mutaciones
justifican un token sincronizado.

### Token CSRF global o persistido

Se descarta porque compartiría protección entre sesiones o ampliaría
innecesariamente los datos sensibles persistidos.

### JSON mediante una dependencia nueva

Se descarta en este incremento. Los formularios codificados ofrecen un
contrato suficiente y un parser acotado; las respuestas siguen utilizando
JSON.

### Repositorios de tarjetas, equipos o permisos

Se descartan porque no existe todavía el caso de uso correspondiente.

## Consecuencias

* Aparece el primer modelo Kanban y el primer repositorio de negocio.
* La pertenencia se comprueba en cada operación, aunque actualmente exista
  un solo administrador.
* Las posiciones explícitas hacen determinista el orden y requieren
  transacciones rigurosas al reordenar.
* El token sincronizado amplía el modelo de sesión y protege todas las
  nuevas mutaciones.
* Logout cambia su formulario para incluir el token de la sesión.
* El contrato de cuerpos codificados evita otra dependencia, pero debe
  documentarse claramente para clientes HTTP.
* Las tablas y migraciones persisten; las sesiones y tokens continúan
  siendo efímeros.
* El valor pedagógico aumenta al mostrar invariantes, autorización por
  pertenencia, transacciones y seguridad web en un vertical completo.

## Criterios de revisión

La decisión se revisará cuando:

* aparezcan varios usuarios, tableros compartidos o roles;
* exista edición concurrente que necesite control de versión;
* las columnas requieran borrado o reglas adicionales;
* la API necesite cuerpos JSON complejos;
* se incorpore una política de origen o despliegue diferente;
* las sesiones se persistan o distribuyan;
* se añadan tarjetas o movimiento mediante arrastrar y soltar.

## Referencias

* [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
* [MDN: Cross-site request forgery](https://developer.mozilla.org/en-US/docs/Web/Security/Attacks/CSRF)
* [MDN: Fetch metadata request headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Fetch_metadata)
* [SQLite: Transactions](https://www.sqlite.org/lang_transaction.html)
* [SQLite: CREATE TABLE](https://www.sqlite.org/lang_createtable.html)
* [SQLite: Foreign Key Support](https://sqlite.org/foreignkeys.html)
* [SQLite: CREATE INDEX](https://www.sqlite.org/lang_createindex.html)
