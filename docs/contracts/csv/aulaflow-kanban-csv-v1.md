# Contrato: AulaFlow Kanban CSV — versión 1

* Estado: aceptado
* Fecha: 2026-08-03
* Proyecto: AulaFlow 1.0
* Incremento: 2.0
* Issue: #19

## 1. Propósito

Este documento define el formato `AulaFlow Kanban CSV` (`aulaflow_version = 1`),
el único formato de interoperabilidad admitido en el incremento `2.0` para
importar y exportar un tablero Kanban completo (tablero, columnas y
tarjetas) de forma local, reversible y auditable.

No es un formato de backup: no representa identidad, credenciales,
sesiones, etiquetas, checklists ni metadatos internos.

## 2. Un archivo, un tablero

Cada archivo CSV representa exactamente:

```text
1 registro BOARD
1 o más registros COLUMN
0 o más registros CARD
```

Un archivo con cero o más de un `BOARD` es inválido. La importación
**siempre** crea un tablero nuevo, propiedad de la persona autenticada
que confirma la importación. Repetir la importación del mismo archivo
crea una copia independiente adicional; el contrato CSV V1 no fusiona,
actualiza ni elimina tableros existentes.

## 3. Codificación

* El archivo debe estar codificado en **UTF-8 estricto**.
* Un BOM UTF-8 (`EF BB BF`) al principio del archivo es **opcional** en
  la importación: si aparece, se descarta antes de tokenizar.
* La importación decodifica los bytes con `CharsetDecoder` configurado
  con `CodingErrorAction.REPORT`: cualquier secuencia de bytes que no
  sea UTF-8 válido se rechaza como error público `invalid_utf8`, sin
  intentar sustituir ni "reparar" los bytes inválidos.
* La exportación **añade** un BOM UTF-8, para maximizar la
  compatibilidad con hojas de cálculo (LibreOffice Calc, Excel) que
  detectan la codificación a partir de él.

## 4. Delimitador, comillas y saltos de línea

```text
delimitador de campo : coma (,)
carácter de comillas : comilla doble (")
escape de comilla     : comilla doble duplicada ("")
```

Un campo debe entrecomillarse si contiene una coma, una comilla o un
salto de línea (`\n` o `\r\n`). La exportación de AulaFlow aplica
entrecomillado mínimo: solo añade comillas cuando el contenido lo
exige. La importación acepta campos con y sin comillas
indistintamente, según la gramática CSV estándar.

Saltos de línea admitidos:

```text
exportación : siempre CRLF (\r\n), incluida la última línea
importación : admite CRLF y LF indistintamente dentro del mismo archivo
```

El parser de importación se implementa como una **máquina de estados**
carácter a carácter. No se utiliza `String.split(",")` ni ningún
mecanismo equivalente, porque no puede distinguir de forma fiable una
coma delimitadora de una coma dentro de un campo entrecomillado.

## 5. Encabezado exacto

La primera línea lógica del archivo (registro lógico número 1) debe ser
exactamente, en este orden, sin traducir y sin campos adicionales:

```csv
aulaflow_version,record_type,board_name,column_name,column_position,card_title,card_description,card_position
```

* El encabezado no se traduce ni se reordena nunca.
* Un encabezado distinto (campo que falta, campo de más, orden distinto,
  o nombre distinto) se rechaza con `invalid_header`.
* Todas las filas, incluida la del encabezado, deben tener exactamente 8
  campos. Un número de campos distinto se rechaza estructuralmente con
  `invalid_field_count`, indicando el número de registro lógico
  afectado.

## 6. Tipos de registro

`record_type` solo admite estos tres valores exactos, sin traducir:

```text
BOARD
COLUMN
CARD
```

Cualquier otro valor se rechaza con `invalid_record_type`.

### 6.1. `BOARD`

| Campo | Regla |
| :--- | :--- |
| `aulaflow_version` | debe ser `1` |
| `record_type` | `BOARD` |
| `board_name` | obligatorio; validado como `BoardName` del dominio (1–100 puntos de código tras recortar) |
| `column_name`, `column_position`, `card_title`, `card_description`, `card_position` | deben estar vacíos |

Debe existir **exactamente un** registro `BOARD` en todo el archivo.
Cero registros produce `missing_board_record`; dos o más produce
`duplicate_board_record` en el segundo y sucesivos.

### 6.2. `COLUMN`

| Campo | Regla |
| :--- | :--- |
| `aulaflow_version` | `1` |
| `record_type` | `COLUMN` |
| `board_name` | debe coincidir exactamente con el `board_name` del registro `BOARD` |
| `column_name` | obligatorio; validado como `ColumnName` del dominio (1–80 puntos de código tras recortar) |
| `column_position` | entero `>= 0` |
| `card_title`, `card_description`, `card_position` | deben estar vacíos |

Las posiciones de columna dentro del archivo deben ser **únicas y
contiguas desde 0** (`0..n-1`, sin huecos). AulaFlow no compacta ni
corrige posiciones inválidas de forma silenciosa: las rechaza.

### 6.3. `CARD`

| Campo | Regla |
| :--- | :--- |
| `aulaflow_version` | `1` |
| `record_type` | `CARD` |
| `board_name` | debe coincidir con el `BOARD` |
| `column_position` | debe referenciar una `COLUMN` ya declarada en el archivo |
| `column_name` | debe coincidir exactamente con el `column_name` de esa `COLUMN` (verificación cruzada de coherencia, no solo de posición) |
| `card_title` | obligatorio; validado como `CardTitle` del dominio (1–160 puntos de código tras recortar) |
| `card_description` | puede estar vacío; validado como `CardDescription` del dominio (0–4000 puntos de código) |
| `card_position` | entero `>= 0` |

Las posiciones de tarjeta son únicas y contiguas desde 0, **dentro de
cada columna** (no globalmente).

## 7. Orden canónico

El orden canónico de un archivo CSV V1 es:

```text
BOARD
COLUMN 0
  CARD de COLUMN 0 por posición ascendente
COLUMN 1
  CARD de COLUMN 1 por posición ascendente
...
COLUMN n-1
  CARD de COLUMN n-1 por posición ascendente
```

La **exportación** siempre produce el orden canónico. La
**importación** no depende del orden físico de las filas en el
archivo: valida referencias por posición y nombre, no por proximidad
en el archivo. Un archivo válido pero con las filas desordenadas
(por ejemplo, todas las `CARD` antes que su `COLUMN`) se importa
correctamente porque la validación se hace en dos fases (lectura
completa seguida de comprobación de referencias), no de forma
incremental fila a fila.

## 8. Política de duplicados

**Se permiten:**

* nombres de columna repetidos, siempre que sus posiciones sean
  distintas (por ejemplo, dos columnas llamadas "Revisió" en
  posiciones 1 y 3);
* tarjetas con título y descripción idénticos, siempre que sus
  posiciones dentro de la columna sean distintas.

**Se rechazan:**

* un segundo registro `BOARD` (`duplicate_board_record`);
* dos `COLUMN` con la misma `column_position` en el archivo
  (`duplicate_column_position`);
* dos `CARD` con la misma `card_position` dentro de la misma columna
  (`duplicate_card_position`);
* cualquier fila que colisione en identidad contractual según las
  reglas anteriores.

Cada confirmación de importación crea un **tablero nuevo**. El
contrato CSV V1 no ofrece ninguna operación de fusión (*merge*),
actualización ni borrado sobre tableros existentes. Repetir la
importación del mismo archivo dos veces produce dos tableros
independientes con el mismo contenido.

## 9. Protección reversible frente a fórmulas

Un valor textual que empieza por `=`, `+`, `-` o `@` puede
interpretarse como una fórmula al abrir el archivo en una hoja de
cálculo (Excel, LibreOffice Calc, Google Sheets), lo que constituye un
vector de inyección conocido (*CSV/Formula Injection*, CWE-1236).
AulaFlow neutraliza este riesgo de forma **reversible**, sin descartar
ni alterar el significado del dato para el propio AulaFlow.

Se consideran peligrosos los siguientes primeros caracteres
**significativos** (el primer carácter no en blanco de un campo,
ignorando espacios y tabuladores iniciales):

```text
=  +  -  @
```

### 9.1. Exportación (protección)

Para cada campo textual (`board_name`, `column_name`, `card_title`,
`card_description`):

1. si el valor original empieza literalmente por un apóstrofo (`'`),
   se duplica ese apóstrofo (se añade uno más al principio);
2. en caso contrario, si el primer carácter significativo del valor es
   uno de `=`, `+`, `-`, `@`, se añade un apóstrofo (`'`) al
   principio del valor;
3. el resultado de los pasos 1–2 se escapa después según las reglas
   CSV estándar de la sección 4 (comillas si procede).

### 9.2. Importación (reversión)

Para cada campo textual, tras aplicar el descifrado CSV estándar
(desentrecomillado):

1. si el valor empieza por dos apóstrofos (`''`), se elimina **uno**
   de ellos y se conserva el resto tal cual;
2. en caso contrario, si el valor empieza por un apóstrofo (`'`) y el
   resto (a partir del segundo carácter) tiene como primer carácter
   significativo uno de `=`, `+`, `-`, `@`, se elimina ese apóstrofo
   protector;
3. en cualquier otro caso, el valor se conserva sin cambios.

### 9.3. Ejemplos

| Valor original | Exportado (protegido) | Reimportado |
| :--- | :--- | :--- |
| `=1+1` | `'=1+1` | `=1+1` |
| `+SUM(A1:A2)` | `'+SUM(A1:A2)` | `+SUM(A1:A2)` |
| `-1+2` | `'-1+2` | `-1+2` |
| `@cmd` | `'@cmd` | `@cmd` |
| `'=1+1` | `''=1+1` | `'=1+1` |
| `'texto` | `''texto` | `'texto` |
| `texto` | `texto` | `texto` |
| `  =1+1` (espacios iniciales) | `'  =1+1` | `  =1+1` |

La protección es **simétrica**: exportar y volver a importar el mismo
tablero reproduce exactamente los mismos valores de texto, incluidos
apóstrofos y espacios en blanco iniciales.

## 10. Límites

Las constantes están centralizadas en una única clase de la capa de
aplicación (`CsvLimits`). Valores de esta versión:

```text
MAX_UPLOAD_BYTES               = 1 048 576 (1 MiB)
MAX_LOGICAL_RECORDS             = 5 000  (incluyendo el encabezado)
MAX_COLUMNS                     = 100
MAX_CARDS                       = 4 899
MAX_PENDING_IMPORTS_PER_SESSION = 3
PENDING_IMPORT_TTL              = 10 minutos
```

* El cuerpo se lee con un contador de bytes; superar
  `MAX_UPLOAD_BYTES` produce `413` (HTML y API) sin terminar de leer
  ni procesar el resto del archivo.
* Superar `MAX_LOGICAL_RECORDS` durante el trocedado produce el error
  público `too_many_records` (`400`).
* Superar `MAX_COLUMNS` o `MAX_CARDS` durante la validación de negocio
  produce un error de validación (`400`), sin escribir nada.

## 11. Códigos de error e informe

Un intento de importación produce siempre un informe con tres
categorías:

```text
error   : impide la importación
warning : no impide la importación
summary : nombre del tablero, número de columnas y tarjetas, registros totales
```

Cada error y aviso indica el número de registro lógico afectado (el
encabezado es el registro 1), el campo implicado cuando aplica, un
código público estable y un mensaje comprensible sin exponer detalles
internos. Códigos estables definidos por este contrato:

```text
invalid_utf8, file_too_large, too_many_records, invalid_header,
unsupported_version, invalid_record_type, invalid_field_count,
unexpected_field, missing_required_field, invalid_integer,
negative_position, duplicate_board_record, missing_board_record,
duplicate_column_position, missing_column_position,
unknown_column_reference, column_name_mismatch,
duplicate_card_position, missing_card_position,
domain_validation_failed, malformed_quote, unterminated_quote,
nul_character, expired_token, invalid_token,
too_many_pending_imports
```

La implementación añade seis códigos deterministas más allá de la
lista sugerida, necesarios para cubrir casos que la lista anterior no
nombra explícitamente pero que el contrato exige comprobar
(posiciones "únicas y contiguas desde 0", límites de columnas y
tarjetas, y coherencia de `board_name`):

```text
missing_column_record       : el archivo no contiene ningún COLUMN
board_name_mismatch         : un COLUMN o CARD referencia un
                               board_name distinto al del BOARD
non_contiguous_column_positions : las posiciones de columna tienen
                                   huecos
non_contiguous_card_positions   : las posiciones de tarjeta de una
                                   columna tienen huecos
too_many_columns             : se supera MAX_COLUMNS
too_many_cards                : se supera MAX_CARDS
```

Un error de tipo estructural (fallo del parser: comillas sin cerrar,
número de campos incorrecto, carácter NUL, UTF-8 inválido) detiene el
trocedado en el primer problema encontrado y produce un informe con un
único error que identifica el registro afectado. Un error de
**validación de negocio** (posiciones, referencias, duplicados, value
objects del dominio) se **acumula**: el informe puede contener varios
errores de negocio a la vez, para que la persona usuaria pueda
corregir el archivo de una sola vez en vez de una comprobación por
intento.

## 12. Alcance explícito de la versión 1

**Incluido:** tablero, columnas ordenadas, tarjetas ordenadas con
título y descripción.

**Fuera de alcance de V1** (ver ADR asociado para la justificación):

* identificadores internos (`id`) y marcas de tiempo (`created_on`,
  `updated_on`);
* etiquetas y checklists;
* varios tableros por archivo;
* fusión, actualización o borrado de tableros existentes por
  importación;
* formatos distintos de CSV (XLSX, ODS, JSON, ZIP);
* servicios en la nube o sincronización con terceros.

CSV V1 es un formato de **interoperabilidad de datos Kanban**, no un
mecanismo de copia de seguridad de la aplicación.
