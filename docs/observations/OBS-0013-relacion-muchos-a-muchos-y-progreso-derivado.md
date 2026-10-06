# OBS-0013 — Relación muchos a muchos, valor normalizado y progreso derivado

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Nivel del alumnado

1.º DAM, primer semestre. El alumnado ya conoce claves foráneas simples
(uno a muchos: `board_columns → boards`, `cards → board_columns`) y
transacciones (OBS-0008). Este incremento introduce, por primera vez, una
relación **muchos a muchos** real del proyecto.

## Prerrequisitos

- OBS-0008 (JDBC y transacciones SQLite)
- OBS-0010 (tableros, columnas, CSRF)
- OBS-0011 (tarjetas, movimiento, accesibilidad)
- Conceptos: clave foránea simple, restricción `UNIQUE`

---

## Muchos a muchos: la tabla intermedia

`[P]` Una tarjeta puede tener varias etiquetas, y una etiqueta puede
estar en varias tarjetas del mismo tablero. Esta relación **no cabe** en
una única columna de clave foránea (a diferencia de `column_id` en
`cards`, que solo puede apuntar a una columna).

La solución es una **tabla intermedia**, `card_labels`, con dos claves
foráneas:

```sql
CREATE TABLE card_labels (
    card_id INTEGER NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    label_id INTEGER NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    created_on INTEGER NOT NULL DEFAULT (unixepoch()),
    PRIMARY KEY (card_id, label_id)
);
```

**Para el alumnado:** cada fila de `card_labels` representa **una
asignación concreta**: "esta tarjeta tiene esta etiqueta". Si una tarjeta
tiene tres etiquetas, hay tres filas en `card_labels` con el mismo
`card_id` y distinto `label_id`.

**Para el alumnado más avanzado:** la clave primaria compuesta
`(card_id, label_id)` cumple dos funciones a la vez: identifica de forma
única cada asignación **y** impide, por construcción, que la misma
etiqueta se asigne dos veces a la misma tarjeta. No hace falta ninguna
comprobación adicional en SQL para eso — es el propio motor de base de
datos quien lo garantiza.

---

## Valor visible frente a valor canónico

`[G]` Dos administradores podrían crear, sin querer, las etiquetas
"Urgent" y "urgent " (con un espacio al final) en el mismo tablero,
pensando que son distintas. Para evitarlo, `LabelName` calcula una forma
canónica (`normalized()`): recorta espacios, colapsa espacios internos,
normaliza Unicode y pasa a minúsculas. Esa forma canónica es la que se
compara con `UNIQUE`, pero **nunca** es la que se muestra en pantalla —
el nombre visible (`name`) se guarda tal cual lo escribió la persona
usuaria.

**Para el alumnado:** es la misma idea que un correo electrónico: `Ana@Exemple.com`
y `ana@exemple.com` deberían tratarse como la misma dirección a efectos
de duplicados, aunque se muestren con la capitalización que el usuario
prefiera.

---

## Progreso derivado: no dupliques lo que puedes calcular

`[P]` La tabla `checklist_items` no tiene ninguna columna de
"porcentaje completado". El progreso se calcula siempre a partir de los
elementos actuales:

```java
public record ChecklistProgress(int totalItems, int completedItems) {
    public int percentage() {
        return totalItems == 0 ? 0 : (int) Math.round((completedItems * 100.0) / totalItems);
    }
}
```

**Para el alumnado:** si guardarais el porcentaje como una columna
aparte, tendríais que actualizarlo cada vez que se cree, marque o elimine
un elemento — y tarde o temprano alguna ruta del código se olvidará de
hacerlo, dejando un porcentaje incorrecto ("dato derivado
desincronizado"). Calcularlo siempre a partir de los datos reales elimina
esa clase entera de error.

---

## Reordenación dentro de un límite, no entre tarjetas

`[G]` El checklist reutiliza la misma técnica de "dos pasadas con
posiciones temporales" que ya usasteis para columnas y tarjetas (ver
ADR-0011), pero con una frontera más estricta: un elemento de checklist
solo puede reordenarse **dentro de su propia tarjeta**, nunca moverse a
otra. El repositorio ni siquiera acepta un parámetro de tarjeta de
destino distinto — la operación no existe.

**Para el alumnado:** limitar deliberadamente lo que una operación
puede hacer (en vez de "por si acaso" aceptar más parámetros) hace el
código más fácil de razonar y de probar, y evita tener que documentar
qué pasaría en un caso que el negocio nunca pidió.

---

## Botón con `aria-pressed` en vez de checkbox real

`[A]` Marcar y desmarcar un elemento se implementa como un `<button>`
con `aria-pressed="true"`/`"false"`, no como un `<input type="checkbox">`
real. La razón es puramente de accesibilidad **sin JavaScript**: un
checkbox HTML solo envía su estado dentro de un formulario cuando el
formulario se envía, pero no dispara ningún envío automáticamente al
marcarlo. Como este incremento exige que todo funcione sin JavaScript,
el control debe ser un botón de formulario (que sí puede enviar la
petición al pulsarlo) con el estado comunicado mediante `aria-pressed`,
que los lectores de pantalla anuncian igual que un checkbox marcado o
desmarcado.

**Para el alumnado más avanzado:** esto es un ejemplo de cuándo ARIA
(`aria-pressed`) es la herramienta correcta en vez de HTML semántico
puro — el elemento HTML "obvio" (checkbox) no encaja con la restricción
real (funcionar sin JavaScript mediante POST), así que se elige el
control que sí puede disparar la acción y se comunica su estado de forma
accesible con el atributo apropiado.

---

## Glosario nuevo

| Término | Significado |
| :--- | :--- |
| Relación muchos a muchos | Asociación en la que cada registro de una tabla puede relacionarse con varios de otra, y viceversa |
| Tabla intermedia (tabla puente) | Tabla que materializa una relación muchos a muchos mediante dos claves foráneas |
| Clave primaria compuesta | Clave primaria formada por más de una columna; garantiza unicidad de la combinación |
| Valor canónico | Forma normalizada de un dato, usada para comparar, distinta del valor mostrado al usuario |
| Dato derivado | Valor que se calcula a partir de otros datos en vez de almacenarse por separado |
