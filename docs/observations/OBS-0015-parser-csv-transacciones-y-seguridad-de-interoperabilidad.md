# OBS-0015 — Parser CSV, transacciones, tokens e inyección de fórmulas

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Nivel del alumnado

1.º DAM, primer semestre. El alumnado ya ha construido dominio,
aplicación, persistencia, HTTP e interfaz para tableros, tarjetas,
etiquetas y checklist (OBS-0008 a OBS-0014). Este incremento es el
primero que exige **interoperabilidad con el exterior**: leer y
escribir un formato de archivo que otras personas o programas pueden
manipular libremente antes de dárselo a AulaFlow.

## Prerrequisitos

- OBS-0008 (transacciones SQLite)
- OBS-0009 (sesiones y tokens)
- OBS-0012 (migraciones y fallos controlados)
- OBS-0014 (diagnosticar antes de corregir)

---

## Por qué un parser CSV no es `String.split(",")`

`[P]` La tentación más común al leer "CSV" por primera vez es pensar
que basta con `linea.split(",")`. Eso funciona en el primer ejemplo
que se prueba y falla exactamente en el caso que un archivo CSV real
necesita resolver: una coma **dentro** de un campo entrecomillado.

```csv
1,BOARD,"Tauler, amb coma",,,,,
```

Con `split(",")` ese campo se rompe en dos. El parser de AulaFlow
(`CsvParser`) se implementa como una **máquina de estados**: recorre
el texto carácter a carácter y mantiene un estado (`dentro de
comillas` o `fuera de comillas`) que decide si una coma es un
delimitador o parte del contenido.

**Para el alumnado:** este es un ejemplo perfecto de por qué "funciona
en mi prueba rápida" no es lo mismo que "es correcto". Una máquina de
estados obliga a enumerar explícitamente los casos límite (comilla
escapada `""`, salto de línea dentro de comillas, comilla mal
colocada, archivo sin salto final) en vez de descubrirlos uno a uno
cuando un archivo real los produce.

---

## UTF-8 estricto y BOM: decodificar no es "adivinar"

`[P][G]` El contrato exige `CharsetDecoder` con
`CodingErrorAction.REPORT`, no `CodingErrorAction.REPLACE`. La
diferencia es pedagógicamente importante: `REPLACE` sustituiría
bytes inválidos por `�` y dejaría pasar un archivo corrupto como si
fuera válido, con datos ya perdidos y sin ninguna señal de error.
`REPORT` lanza una excepción exactamente en el primer byte
problemático, así que el archivo se rechaza *completo*, con una causa
clara, en vez de importarse a medias con texto corrupto.

El BOM (*Byte Order Mark*, `EF BB BF`) es opcional en importación
porque muchas hojas de cálculo (Excel, especialmente en Windows) lo
añaden automáticamente al guardar como "CSV UTF-8", y lo esperan al
volver a abrir un CSV para detectar la codificación sin ambigüedad.
AulaFlow lo añade siempre al exportar, precisamente para maximizar esa
compatibilidad, y lo descarta de forma transparente al importar si
aparece.

**Para el alumnado:** decodificar bytes a texto no es un detalle
menor delegable a la biblioteca por defecto. La política de qué hacer
ante un byte inválido es una decisión de producto (aquí: rechazar,
no adivinar) que hay que tomar y documentar explícitamente.

---

## Por qué la importación tiene dos fases y un token, no una

`[G]` La issue #19 pide previsualización antes de confirmar. La
implementación no guarda el archivo temporalmente en disco (frontera
de seguridad: contenido de terceros en el sistema de archivos, más
limpieza pendiente), ni lo persiste como una fila de base de datos
(sería un dato de sesión disfrazado de dato de negocio). En su lugar,
guarda un `CsvImportPlan` ya validado **en memoria**, ligado a un
`token` aleatorio opaco (32 bytes de `SecureRandom`, igual que el
token CSRF de OBS-0009), a la sesión que lo creó, a su propietario y a
una expiración de 10 minutos.

Esto reproduce exactamente el mismo patrón que ya conocéis de la
autenticación: un identificador aleatorio no adivinable, que actúa
como "llave" de un recurso efímero en memoria, con caducidad y un solo
uso. La lección transferible es que "token opaco con TTL y un solo
uso" es un patrón general para cualquier operación en dos fases
(previsualizar/confirmar, autorizar/ejecutar, reservar/comprar), no
algo exclusivo del login.

**Para el alumnado más avanzado:** fijaos en que el token está ligado
a **sesión + propietario**, no solo a propietario. Si solo se
comprobara el propietario, una sesión antigua filtrada (por ejemplo, en
un historial de navegador compartido) podría confirmar una importación
que otra sesión del mismo administrador preparó, incluso después de
que esa sesión haya cerrado. Ligarlo también a la sesión concreta
cierra esa ventana.

---

## Importación atómica: por qué no reutilizar los repositorios existentes

`[G]` AulaFlow ya tenía `BoardRepository` y `CardRepository`. Podría
parecer razonable importar un CSV llamando en bucle a
`boardRepository.create(...)` y después a `cardRepository.create(...)`
por cada tarjeta. El problema es que cada uno de esos métodos abre
**su propia conexión JDBC y su propia transacción** (ved OBS-0008): si
la tarjeta número 50 de 100 falla, las primeras 49 ya están
confirmadas en la base, y el tablero queda en un estado a medias que
la issue #19 prohíbe explícitamente.

`SqliteCsvImportRepository` existe como puerto específico
precisamente para esto: abre una única conexión con
`setAutoCommit(false)`, inserta el tablero, después cada columna,
después cada tarjeta, y solo hace `commit()` si todo ha ido bien. La
prueba `SqliteCsvImportAtomicityTest` no simula el fallo con un doble
de prueba: instala un *trigger* real de SQLite, exclusivo de la base
temporal del test, que aborta la transacción a mitad de la
importación, y comprueba que no queda ni rastro (ni tablero, ni
columna, ni tarjeta) del intento fallido.

**Para el alumnado:** "atómico" no es un adjetivo que se declare, es
una propiedad que se demuestra forzando un fallo real y observando
qué queda. Una prueba que solo comprueba el camino feliz no demuestra
atomicidad.

---

## *CSV Injection*: cuando abrir un archivo no debería ejecutar nada

`[G][A]` Un CSV es, en apariencia, "solo texto". Pero si una celda
empieza por `=`, `+`, `-` o `@`, muchas hojas de cálculo (Excel,
LibreOffice Calc, Google Sheets) la interpretan como una **fórmula**
al abrirla, no como texto literal. Un archivo CSV con una celda como

```text
=CMD|' /C calc'!A0
```

puede, en determinadas configuraciones, ejecutar un programa externo
simplemente al abrirse en una hoja de cálculo. Esto se conoce como
*CSV/Formula Injection* (catalogado como CWE-1236) y es un vector de
ataque real y documentado, no una preocupación teórica: cualquier
aplicación que exporte datos introducidos por usuarios a CSV (nombres,
comentarios, descripciones) es vulnerable si no neutraliza este
patrón antes de escribir el archivo.

AulaFlow neutraliza el riesgo anteponiendo un apóstrofo (`'`) —la
misma convención que Excel y LibreOffice Calc ya usan para forzar
texto literal— a cualquier valor cuyo primer carácter significativo
sea peligroso, y revierte esa transformación de forma determinista al
reimportar. La prueba `FormulaNeutralizerTest` comprueba explícitamente
que la operación es **reversible**: exportar e importar de nuevo
reproduce el valor original exacto, apóstrofos incluidos.

**Para el alumnado:** comparad esta decisión con la alternativa obvia
de "eliminar el carácter peligroso". Eliminarlo sería más simple de
programar, pero perdería información de forma irreversible y rompería
el round trip que exige la issue #19. La seguridad y la fidelidad de
los datos no siempre están en tensión: aquí, resolver bien el
problema de seguridad (de forma reversible) es también lo que permite
resolver bien el problema de round trip.

---

## Round trip: la prueba que demuestra la interoperabilidad de verdad

`[G]` Ninguna prueba unitaria aislada (parser, validador, serializador
por separado) demuestra que exportar e importar producen el mismo
resultado. `CsvRoundTripIntegrationTest` construye un tablero real
sobre SQLite con Unicode, comas, comillas, saltos de línea, fórmulas y
apóstrofos, más una columna vacía; lo exporta; reimporta ese mismo
CSV; vuelve a exportar el tablero reimportado; y compara los dos
archivos CSV **byte a byte**.

Esa comparación byte a byte es deliberadamente estricta: si el
serializador no fuera determinista (por ejemplo, si el orden de las
columnas dependiera de un `HashMap` sin ordenar), la prueba lo
detectaría inmediatamente, aunque el contenido "pareciera" el mismo a
simple vista.

**Para el alumnado:** es la misma idea de "punta de la pirámide de
pruebas" de OBS-0014, aplicada a interoperabilidad de archivos en vez
de a un recorrido HTTP: la única forma de demostrar de verdad que un
formato de intercambio funciona es completar el ciclo completo,
exportar → importar → exportar, y comparar el resultado final con el
inicial.

---

## Interoperabilidad no es lo mismo que backup

`[M]` Este incremento es un buen momento para distinguir dos conceptos
que el alumnado suele confundir:

- **Interoperabilidad**: exportar e importar un subconjunto de datos
  en un formato legible y portable, para compartirlo entre personas o
  sistemas distintos (aquí: tablero, columnas y tarjetas, sin
  identificadores internos ni marcas de tiempo).
- **Backup**: una copia completa y fiel de todo el estado de una
  aplicación (incluidas identidades, credenciales, sesiones,
  etiquetas, checklists y metadatos internos), pensada para
  restaurar el sistema exactamente como estaba.

El contrato CSV v1 excluye deliberadamente IDs, timestamps, etiquetas
y checklists, y crea siempre un tablero **nuevo** al importar (nunca
actualiza ni fusiona uno existente). Esto no es una limitación
temporal que se completará "en una versión futura": es una decisión
de alcance explícita, documentada en el ADR-0014, porque backup y
interoperabilidad resuelven problemas distintos con requisitos de
seguridad y consistencia distintos.

**Para el proyecto intermodular:** si vuestro proyecto de fin de curso
necesita "exportar datos", preguntaos primero para qué: ¿para que otra
persona o sistema use un subconjunto legible de los datos
(interoperabilidad), o para poder recuperar el sistema completo tras
un desastre (backup)? Diseñar un solo mecanismo que intente resolver
ambos problemas a la vez suele acabar resolviendo mal los dos.

## Glosario nuevo

| Término | Significado |
| :--- | :--- |
| Máquina de estados | Modelo de cómputo que procesa una entrada carácter a carácter (o símbolo a símbolo) manteniendo un estado interno que determina cómo interpretar el siguiente carácter |
| BOM (*Byte Order Mark*) | Secuencia de bytes al principio de un archivo de texto que señala su codificación Unicode |
| Token opaco | Identificador aleatorio sin estructura interpretable, usado como llave de un recurso efímero |
| *CSV/Formula Injection* | Vector de ataque (CWE-1236) en el que una celda CSV con un carácter inicial como `=`, `+`, `-` o `@` se interpreta como fórmula al abrirse en una hoja de cálculo |
| Neutralización reversible | Transformar un valor peligroso en uno seguro de forma que la transformación pueda deshacerse exactamente, sin perder información |
| Round trip | Ciclo completo de transformación y su inversa (aquí: exportar e importar) que debe reproducir el estado original |
