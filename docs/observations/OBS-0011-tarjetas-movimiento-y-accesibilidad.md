# OBS-0011 — Tarjetas: movimiento, transacciones y accesibilidad

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Nivel del alumnado

1.º DAM, primer semestre. El alumnado ya comprende tablas CRUD, claves
foráneas, clases Java con constructor y getters, y ha utilizado `ArrayList`.
El OOP con `record` y la programación reactiva del navegador son conceptos
nuevos que aparecen por primera vez.

## Prerrequisitos

- OBS-0008 (JDBC y transacciones SQLite)
- OBS-0010 (tableros, columnas, CSRF)
- Conceptos: entidad con identidad, value object, posición explícita, índice
  compuesto

---

## Entidad y value objects de tarjeta

```java
// Entidad: identidad estable, puede cambiar su estado
public record Card(
        CardId id,
        ColumnId columnId,
        CardTitle title,
        CardDescription description,
        int position,
        Instant createdAt,
        Instant updatedAt
) { ... }

// Value objects: validan en el constructor, son inmutables
public record CardId(long value) { /* value >= 1 */ }
public record CardTitle(String value) { /* trim, 1-160 puntos de código */ }
public record CardDescription(String value) { /* vacía ok, máx 4000 */ }
```

**Para el alumnado:** un `record` en Java es como una clase con todos los
campos `final`, un constructor que los recibe todos y métodos `equals`,
`hashCode` y `toString` generados automáticamente. La validación en el
cuerpo del constructor canonico garantiza que nunca existe un objeto en
estado inválido.

**Para el alumnado más avanzado:** `CardId` envuelve un `long` para
distinguirlo en el sistema de tipos. Así el compilador evita confundir
`cardId` con `columnId` o `boardId`.

---

## Repositorio y servicio

`CardRepository` es el puerto (interfaz) que la capa de aplicación define.
`SqliteCardRepository` es el adaptador (implementación) que lo cumple con
JDBC y SQL.

`CardService` es el caso de uso: recibe tipos simples (String, long, int),
construye los value objects, delega en el repositorio y traduce errores.

La separación respeta la dirección de dependencias:
```
presentación → aplicación → dominio
infraestructura → aplicación/dominio
```

**Para el alumnado:** el servicio no sabe nada de SQL. El repositorio no
sabe nada de HTTP. Así se pueden probar por separado.

---

## Transacciones y posiciones

La tabla `cards` tiene la restricción `UNIQUE(column_id, position)`.
Esto garantiza que nunca hay dos tarjetas en la misma posición dentro de
una columna.

Cuando hay que mover una tarjeta, actualizar las posiciones en un solo
`UPDATE` puede violar temporalmente esa restricción, aunque el resultado
final sea correcto. SQLite comprueba la restricción fila a fila.

### Solución de dos pasadas

La solución es hacer el reordenamiento en dos pasadas dentro de una
transacción:

```
Pasada 1: asignar posiciones temporales seguras (p.ej. 1000, 1001, 1002)
Pasada 2: asignar las posiciones definitivas (0, 1, 2)
```

La clave es que entre pasadas no hay commit, de modo que si algo falla,
el rollback devuelve todo al estado inicial.

**Para el alumnado:** una transacción es como un "borrador" de cambios que
solo se hace efectivo al hacer `commit`. Si algo va mal, `rollback` borra
el borrador completo.

```java
connection.setAutoCommit(false);
try {
    // operaciones...
    connection.commit();
} catch (SQLException e) {
    connection.rollback();
    throw e;
}
```

---

## Drag and drop nativo del navegador

El arrastrar y soltar se implementa con las APIs nativas del navegador,
sin librerías:

```
dragstart   — el usuario empieza a arrastrar
dragover    — el cursor pasa sobre un objetivo
drop        — el usuario suelta la tarjeta
dragend     — limpieza después del gesto
DataTransfer — objeto que transporta datos entre dragstart y drop
```

El código añade `draggable="true"` mediante JavaScript para que, si el
script falla o está desactivado, las tarjetas no muestren una capacidad
que no funciona.

**Para el alumnado:** `draggable="true"` en el HTML hace que el navegador
active el comportamiento visual de arrastre. `preventDefault()` en
`dragover` indica al navegador que aceptamos el drop.

---

## Mejora progresiva

Toda la funcionalidad de tarjetas funciona sin JavaScript mediante
formularios HTML estándar:

- Crear: `<form method="post" action="...">` con `<input name="title">`
- Editar: `<details><summary>Editar</summary><form>...</form></details>`
- Eliminar: `<form>` con botón de confirmación
- Mover: botones "Pujar", "Baixar", "Columna anterior", "Columna següent"

JavaScript añade la capa de drag and drop encima, sin quitar ningún control.
La región `<div role="status" aria-live="polite">` anuncia el resultado del
movimiento sin recargar la página cuando el DnD funciona.

**Para el alumnado:** "mejora progresiva" significa que la capa base (HTML)
funciona sola, y las capas superiores (CSS, JavaScript) añaden comodidad
sin ser imprescindibles.

---

## Accesibilidad

Los controles de movimiento con teclado son `<button>` nativos que:
- reciben foco con Tab;
- se activan con Enter o Espacio;
- tienen texto visible traducible con `data-i18n`.

El texto del título y la descripción se renderiza con `escape()` y nunca
se inserta con `innerHTML`. El JavaScript usa `textContent` para la región
de estado.

---

## Reparto entre módulos del ciclo

| Concepto | Módulo |
|:---|:---|
| `record`, value objects, dominio | Programación |
| JDBC, transacciones, SQL | Bases de Datos |
| HTTP, formularios HTML, APIs | Entornos de Desarrollo |
| Accesibilidad, i18n | Proyecto Intermodular |
| DnD, JS | Aplicaciones Web (módulo posterior) |

---

## Qué código debe proporcionar el profesor

- La configuración de UNIQUE en la migración SQL
- La clase `SqliteConnectionFactory` y el patrón try-with-resources
- La plantilla del handler HTTP con el routing de segmentos
- El test de integración con `@TempDir`

El alumnado puede implementar guiado:
- Los value objects con sus invariantes
- `CardService` con las validaciones básicas
- Las consultas SELECT/INSERT/UPDATE/DELETE para las operaciones CRUD
- Los formularios HTML en `BoardPages`

El alumnado avanzado puede investigar:
- La lógica de dos pasadas para reordenamiento
- El cálculo de posición durante el dragover
- El manejo de errores en `onDrop` con aria-live
