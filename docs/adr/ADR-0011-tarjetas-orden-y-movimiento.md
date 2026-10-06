# ADR-0011: Tarjetas, orden y movimiento

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Estado: aceptada
* Fecha: 2026-07-31
* Proyecto: AulaFlow 1.0
* Incremento: 1.2
* Issue: #11

## Contexto

AulaFlow dispone de tableros con columnas ordenadas. El incremento 1.2 incorpora
tarjetas: la unidad de trabajo del tablero Kanban. Las tarjetas deben poder
crearse, editarse, eliminarse, reordenarse dentro de una columna y moverse entre
columnas del mismo tablero.

Las nuevas operaciones deben preservar:

- la propiedad por administrador (`card → column → board → owner_id`);
- la protección CSRF en todas las mutaciones HTML y API;
- la integridad de posiciones (`UNIQUE(column_id, position)` y posiciones contiguas);
- la arquitectura en capas sin ORM ni frameworks.

## Modelo de dominio

### Entidades y value objects

**`CardId`**: entero positivo estable generado por SQLite.
No se reutiliza la posición visible como identificador.

**`CardTitle`**: texto obligatorio, recortado en sus extremos, entre 1 y 160
puntos de código Unicode. No se impone unicidad porque dos tarjetas pueden
tener el mismo título con significados distintos.

**`CardDescription`**: texto opcional, vacío permitido. Los saltos de línea se
normalizan a `\n`. Máximo 4.000 puntos de código Unicode. El contenido se trata
siempre como texto sin formato; no se convierte Markdown ni se interpreta HTML.

**`Card`**: record inmutable con `CardId`, `ColumnId`, `CardTitle`,
`CardDescription`, `int position >= 0`, `Instant createdAt`, `Instant updatedAt`.
`updatedAt` no puede ser anterior a `createdAt`. No se añade `boardId` al record
porque la pertenencia se verifica mediante joins en el repositorio.

### Posiciones

Dentro de cada columna las posiciones son enteros contiguos sin huecos:
`0, 1, 2, … n-1`.

Después de cada operación correcta (crear, eliminar, mover) las posiciones
de todas las tarjetas de las columnas afectadas quedan compactadas.

## Puerto y servicio de aplicación

Se crea `CardRepository` como puerto separado de `BoardRepository`. No se amplía
`BoardRepository` para evitar convertirlo en un repositorio general del tablero.
Tableros/columnas y tarjetas tienen ciclos de vida y operaciones distintos.

`CardService` recibe `CardRepository` como único colaborador. La comprobación de
propiedad del tablero la realiza el repositorio mediante joins SQL, por lo que
el servicio no necesita `BoardRepository`.

Casos de uso del servicio:

```java
Card createCard(long ownerId, long boardId, long columnId, String rawTitle, String rawDescription)
List<Card> listCards(long ownerId, long boardId)
Card getCard(long ownerId, long boardId, long cardId)
void updateCard(long ownerId, long boardId, long cardId, String rawTitle, String rawDescription)
void deleteCard(long ownerId, long boardId, long cardId)
void moveCard(long ownerId, long boardId, long cardId, long targetColumnId, int targetPosition)
```

`CardMoveResult` es un enum con tres valores: `MOVED`, `NOT_FOUND`,
`INVALID_POSITION`. El servicio traduce estos valores a `CardNotFoundException`
o `IllegalArgumentException` para que la capa de presentación los convierta en
códigos HTTP.

## Persistencia SQLite

### Nuevas migraciones

```
V007__create_cards.sql   → tabla cards
V008__index_cards_by_column.sql → índice por column_id
```

### Tabla

```sql
CREATE TABLE cards (
    id INTEGER PRIMARY KEY,
    column_id INTEGER NOT NULL
        REFERENCES board_columns(id)
        ON DELETE CASCADE,
    title TEXT NOT NULL
        CHECK (length(trim(title)) BETWEEN 1 AND 160),
    description TEXT NOT NULL DEFAULT ''
        CHECK (length(description) <= 4000),
    position INTEGER NOT NULL
        CHECK (position >= 0),
    created_on INTEGER NOT NULL DEFAULT (unixepoch()),
    updated_on INTEGER NOT NULL DEFAULT (unixepoch()),
    UNIQUE (column_id, position)
)
```

`ON DELETE CASCADE` garantiza que al eliminar una columna sus tarjetas desaparecen
sin operaciones adicionales en la capa de aplicación.

### Propiedad

Toda consulta sobre tarjetas verifica la cadena `cards → board_columns → boards →
owner_id` mediante joins. Una tarjeta, columna o tablero de otro propietario
produce el mismo resultado que un recurso inexistente.

### Timestamps

La base de datos es la fuente de verdad. Después de INSERT o UPDATE el repositorio
relee la fila para obtener `created_on` y `updated_on` reales. No se usa
`Instant.now()` para fabricar timestamps.

### Estrategia de posiciones temporales para movimiento atómico

La restricción `UNIQUE(column_id, position)` impide actualizar posiciones en línea
sin violarla temporalmente. La solución, idéntica a la usada para columnas en el
incremento 1.1, es asignar posiciones temporales fuera del rango activo:
`count + 1000`, garantizando que no colisionan con ninguna posición real.

El proceso completo para mover dentro de la misma columna:

1. Asignar posición temporal a la tarjeta origen.
2. Desplazar las tarjetas afectadas.
3. Asignar la posición final.

El proceso para mover entre columnas distintas:

1. Asignar posición temporal a la tarjeta en la columna origen.
2. Compactar la columna origen (decrementar posiciones > srcPos).
3. Abrir espacio en la columna destino (incrementar posiciones >= targetPos).
4. Mover la tarjeta a la columna y posición destino.

La operación completa se ejecuta en una única transacción con `setAutoCommit(false)`
y rollback ante cualquier `SQLException`.

## Contratos HTTP

### HTML

Las rutas HTML siguen el patrón POST/Redirect/GET del resto del proyecto:

```
GET  /boards/{boardId}                           → tablero con tarjetas
POST /boards/{boardId}/columns/{colId}/cards     → crear tarjeta → 303 /boards/{boardId}
POST /boards/{boardId}/cards/{cardId}/edit       → editar tarjeta → 303 /boards/{boardId}
POST /boards/{boardId}/cards/{cardId}/delete     → eliminar tarjeta → 303 /boards/{boardId}
POST /boards/{boardId}/cards/{cardId}/move       → mover tarjeta → 303 /boards/{boardId}
```

Los controles de movimiento (arriba, abajo, a columna anterior, a columna
siguiente) se renderan como formularios accesibles, operable únicamente con
teclado y sin JavaScript.

### API

```
GET    /api/v1/boards/{boardId}                           → tablero con tarjetas anidadas
POST   /api/v1/boards/{boardId}/columns/{colId}/cards     → 201 Created + JSON tarjeta
PATCH  /api/v1/boards/{boardId}/cards/{cardId}            → 200 OK + JSON tarjeta
DELETE /api/v1/boards/{boardId}/cards/{cardId}            → 204 No Content
PUT    /api/v1/boards/{boardId}/cards/{cardId}/position   → 200 OK + JSON tarjeta
```

Las rutas de tarjetas se registran en `BoardApiHandler` (que ya maneja
`/api/v1/boards`) mediante extensión del enrutado basado en segmentos. No se
registra un contexto separado.

El cuerpo de DELETE no se parsea como formulario para evitar requerir
`Content-Type: application/x-www-form-urlencoded` en una petición sin cuerpo.

Se añade `HttpResponseWriter.sendNoContent(exchange)` para enviar 204 sin cuerpo
de respuesta, usando `sendResponseHeaders(204, -1)`.

La respuesta de `GET /api/v1/boards/{boardId}` incluye las tarjetas anidadas en
cada columna:

```json
{
  "id": 1,
  "name": "Aula",
  "columns": [
    {
      "id": 10,
      "name": "Per fer",
      "position": 0,
      "cards": [
        {
          "id": 100,
          "title": "Preparar tema",
          "description": "",
          "position": 0,
          "createdAt": "2026-07-31T17:00:00Z",
          "updatedAt": "2026-07-31T17:00:00Z"
        }
      ]
    }
  ]
}
```

## Drag and drop nativo

Se usa exclusivamente la API nativa del navegador:
`dragstart`, `dragover`, `drop`, `dragend`, `DataTransfer`, `fetch`,
`URLSearchParams`. No se añade ninguna librería.

`draggable="true"` se asigna desde JavaScript para no anunciar una capacidad
que no funciona si el script falla.

Al soltar:
1. Se calcula la columna y posición de destino.
2. Se envía PUT a `/api/v1/boards/{boardId}/cards/{cardId}/position`.
3. Se incluye `X-CSRF-Token` y credenciales same-origin.
4. El DOM se actualiza solo tras respuesta correcta, o se aplica actualización
   optimista con rollback ante error.

Una región `aria-live="polite"` anuncia el resultado del movimiento.

## Accesibilidad

La funcionalidad completa (crear, editar, eliminar, mover) funciona sin JavaScript
mediante formularios HTML nativos. Los controles de movimiento son botones con
texto o `aria-label` traducible.

JavaScript añade drag and drop como mejora progresiva; no suprime los controles
accesibles.

## Opciones descartadas

- **Añadir boardId a Card**: descartado porque la pertenencia ya se verifica
  mediante joins SQL. Añadirlo solo duplicaría datos con riesgo de inconsistencia.
- **Crear CardHandler separado**: descartado porque las rutas de tarjetas comparten
  el prefijo `/boards/` y `/api/v1/boards/` con el handler existente. Extender el
  enrutado existente es más sencillo y pedagógicamente más claro.
- **WebSockets o tiempo real**: fuera de alcance de este incremento.
- **ORM o pool de conexiones**: no permitidos por restricciones del proyecto.
- **Librería de drag and drop**: no permitida; se usan APIs nativas.
