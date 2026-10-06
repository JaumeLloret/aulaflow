# ADR-0013: Etiquetas con paleta cerrada, normalización de nombre y asignación idempotente

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Estado: aceptada
* Fecha: 2026-08-02
* Proyecto: AulaFlow 1.0
* Incremento: 1.4
* Issue: #15

## Contexto

El incremento 1.4 añade etiquetas reutilizables por tablero y listas de
comprobación (checklist) persistentes a las tarjetas. La issue #15 exige
varias reglas con más de una forma razonable de implementarse:

1. qué color puede tener una etiqueta y cómo se representa;
2. qué se considera un nombre de etiqueta duplicado dentro de un tablero;
3. qué ocurre al asignar dos veces la misma etiqueta a la misma tarjeta.

## Decisión 1 — Paleta cerrada de colores, no CSS libre

Se define `LabelColor` como un enum cerrado de siete claves estables
(`red, orange, yellow, green, blue, purple, gray`), almacenado en SQLite
como texto restringido por `CHECK (color IN (...))`. La interfaz solo
permite elegir un color mediante un `<select>` con esas siete opciones.

Se descarta admitir un color arbitrario (`#RRGGBB` o cualquier cadena
CSS) porque:

- una cadena CSS o un valor `#RRGGBB` proporcionado por el usuario exige
  validación estricta, normalización y, sobre todo, garantías de que
  nunca se interpola sin escapar en HTML — superficie de ataque y de
  error evitable para el valor que aporta a este incremento;
- una paleta cerrada garantiza contraste razonable de forma consistente,
  porque cada clave tiene una regla CSS ya revisada
  (`label-chip--red`, etc.), en vez de depender de que cada persona
  usuaria elija un color legible;
- es más sencilla de enseñar y de probar: siete casos válidos y
  cualquier otra cadena es inválida, sin lógica de parseo de color.

En todos los sitios donde se muestra una etiqueta, el nombre visible
acompaña siempre al indicador de color (`labelChip`, en
`BoardPages.java`); nunca se transmite significado solo mediante color,
tal como exige el criterio de accesibilidad de la issue.

Los botones de asignación y retirada contienen una acción traducible y
un nombre de etiqueta dinámico. El sistema i18n conserva el nombre en
`data-label-name` durante el primer procesamiento y reemplaza únicamente
la acción visible. De ese modo, cambiar de idioma produce nombres
accesibles como «Asignar: Urgent» o «Assignar: Urgent», sin convertir
todos los botones en controles indistinguibles.

## Decisión 2 — Normalización determinista del nombre de etiqueta

Dos etiquetas del mismo tablero no pueden compartir el mismo nombre
"efectivo", pero SQLite no puede usarse para decidir por sí solo qué
cuenta como el mismo nombre en Unicode de forma fiable ni portable entre
plataformas (su colación por defecto es sensible a mayúsculas y no
normaliza espacios).

Se decide que `LabelName` calcule su propia forma canónica mediante un
método `normalized()`:

1. recortar los extremos;
2. colapsar cualquier secuencia de espacios internos a uno solo;
3. pasar a minúsculas con `Locale.ROOT` (invariante de configuración
   regional);
4. aplicar normalización Unicode NFC al resultado final.

Aplicar NFC al final es importante porque una conversión Unicode a
minúsculas puede expandir un punto de código visible en varios puntos de
código internos. Por ejemplo, `İ` puede transformarse en `i` más un punto
combinado. El nombre visible conserva un máximo de 40 puntos de código,
pero la representación canónica admite hasta 120 puntos de código para
que una entrada válida no termine convertida en un error de persistencia.
El dominio verifica ambos límites antes de llegar a SQLite.

La migración `V009` guarda tanto `name` (el valor visible, sin cambios
destructivos) como `normalized_name`, con
`UNIQUE(board_id, normalized_name)`. Esto permite que " Urgent " y
"URGENT" en el mismo tablero se consideren duplicados, pero permite el
mismo nombre "Urgent" en dos tableros distintos.

La normalización es insensible a mayúsculas y a representaciones Unicode
canónicamente equivalentes, pero no elimina acentos: `a` y `á` siguen
siendo nombres diferentes.

Cuando la restricción `UNIQUE` salta, `SqliteLabelRepository` la
traduce a `DuplicateLabelNameException`, mapeada a `400 Bad Request` en
el límite HTTP — no se ha añadido `409 Conflict` porque no aporta valor
frente al `400` ya usado por el resto de validaciones de entrada del
proyecto y evita introducir un código de estado nuevo sin necesidad
real.

## Decisión 3 — Asignación de etiqueta idempotente (PUT)

Asignar una etiqueta ya asignada a una tarjeta no debe ser un error.
`LabelRepository.assign` devuelve un resultado de tres valores
(`ASSIGNED`, `ALREADY_ASSIGNED`, más los casos de "no encontrado"); el
servicio trata `ASSIGNED` y `ALREADY_ASSIGNED` como éxito. La ruta HTTP
correspondiente es `PUT
.../cards/{cardId}/labels/{labelId}`, coherente con la semántica estándar
de PUT (repetir la misma petición dos veces produce el mismo estado
final).

Retirar una etiqueta (`DELETE`), en cambio, sigue el mismo patrón que ya
usaba `CardService.deleteCard`: si no existe la asignación, el
repositorio devuelve `false` y el servicio lanza
`LabelNotFoundException`, traducida a `404`. No se ha hecho idempotente
el `DELETE` para no introducir una segunda semántica de error distinta a
la ya establecida en el proyecto para "eliminar algo que no existe".

## Consecuencia sobre el orden del checklist

La restricción `UNIQUE(card_id, position)` exige que una reordenación o
una compactación no intente ocupar temporalmente una posición todavía
utilizada. Tanto `move` como `delete` reconstruyen el orden mediante la
misma operación transaccional en dos pasadas:

1. posiciones temporales fuera del rango actual;
2. posiciones definitivas contiguas `0..n-1`.

Esto evita que eliminar después de reordenar dependa del orden físico de
las filas de SQLite y garantiza rollback completo ante un fallo.

## Opciones descartadas

- **Color libre (`#RRGGBB`) con validación estricta**: descartado por el
  balance esfuerzo/valor y el riesgo de seguridad frente a una paleta
  cerrada, tal como recomienda el propio documento de instrucciones del
  incremento.
- **Comparación de nombres solo con `COLLATE NOCASE` de SQLite**:
  descartado porque no colapsa espacios ni normaliza Unicode de forma
  portable; se prefiere calcular la forma canónica en Java, donde es
  determinista y fácil de probar unitariamente.
- **Limitar `normalized_name` a 40 caracteres internos**: descartado
  porque una conversión Unicode válida puede expandir la representación
  canónica y provocar una incoherencia entre dominio y persistencia.
- **`409 Conflict` para nombre duplicado**: descartado por no estar ya
  documentado ni probado en el resto del proyecto y no aportar valor
  frente a `400`.
- **Checklist con movimiento entre tarjetas**: fuera de alcance de la
  issue #15; `ChecklistItemRepository.move` solo admite reordenar dentro
  de la misma tarjeta.
