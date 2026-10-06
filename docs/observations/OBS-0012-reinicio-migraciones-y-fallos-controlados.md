# OBS-0012 — Reinicio, ciclo de vida de conexiones y fallos controlados

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Nivel del alumnado

1.º DAM, primer semestre. El alumnado ya ha trabajado con conexiones
JDBC, claves foráneas y transacciones (OBS-0008) y con el modelo de
tableros y tarjetas (OBS-0010, OBS-0011). Este incremento no añade
entidades nuevas; profundiza en **qué significa realmente "persistir"**.

## Prerrequisitos

- OBS-0008 (JDBC y transacciones SQLite)
- OBS-0010 (tableros, columnas, CSRF)
- OBS-0011 (tarjetas, movimiento, accesibilidad)
- Conceptos: proceso, memoria de la JVM, archivo en disco

---

## Reiniciar no es lo mismo que volver a leer

Es habitual que el alumnado confunda dos cosas muy distintas:

1. hacer dos consultas seguidas dentro del **mismo programa en
   ejecución** (la JVM nunca se detiene, los objetos siguen en memoria);
2. detener el programa por completo y volver a arrancarlo (la JVM
   termina, toda la memoria desaparece, y el nuevo proceso solo dispone
   de lo que exista **en disco**).

Solo la segunda situación demuestra persistencia real. `[P]` Por eso
`RestartRecoveryIntegrationTest` no reutiliza ninguna variable entre el
"primer ciclo" y el "segundo ciclo": cada ciclo vive en su propio
método privado y el segundo solo recibe datos simples (números, texto),
nunca un objeto Java del primero. Es la forma de demostrar, sin
necesidad de apagar y encender el ordenador, que lo único que sobrevive
es lo que quedó escrito en el archivo `.db`.

**Para el alumnado:** si vuestro código depende de una variable
`static` o de un caché en memoria para "recordar" algo entre peticiones,
esa información desaparecerá en cuanto el proceso se detenga. Solo lo
que está en la base de datos sobrevive a un reinicio.

---

## `SqliteConnectionFactory` no guarda ninguna conexión

`[P]` Fijaos en que `SqliteConnectionFactory.openConnection()` abre una
conexión **nueva** cada vez que se llama, y cada repositorio la cierra
con `try-with-resources` en cuanto termina. La fábrica en sí misma no
"recuerda" nada entre llamadas: solo guarda la ruta del archivo.

Esto es precisamente lo que hace tan simple simular un reinicio en la
prueba: no hay ningún recurso vivo que cerrar de forma especial, porque
la aplicación nunca mantiene una conexión abierta más tiempo del
estrictamente necesario para una operación.

---

## Migraciones idempotentes y el problema de la versión futura

`[G]` Ya sabíais que `SqliteMigrator` no repite una migración ya
aplicada (comprueba `schema_migrations` antes de ejecutar cada script).
Este incremento añade la comprobación complementaria: ¿qué pasa si la
base contiene una versión que el programa **no conoce**?

Imaginad que compartís la base de datos con una compañera que ya
actualizó su AulaFlow a una versión más nueva, con una tabla que
vosotros todavía no tenéis en vuestro código. Si abrierais esa base con
vuestra versión antigua sin comprobar nada, podríais:

- no ver esa tabla nueva y comportaros como si no existiera;
- en el peor caso, escribir datos inconsistentes con el esquema real.

`SqliteMigrator` ahora compara las versiones registradas con las que
conoce **antes** de tocar nada, y si encuentra una versión desconocida,
aborta el arranque con un mensaje claro. Es el mismo principio que
"fallar rápido y de forma visible" en vez de continuar con un estado
que nadie ha verificado.

**Para el alumnado más avanzado:** fijaos en que esta comprobación
ocurre **antes** de `connection.setAutoCommit(false)` — es decir, antes
de que empiece cualquier escritura. Así se garantiza que un rechazo por
versión desconocida nunca deja una migración a medias.

---

## Errores internos frente a errores públicos

`[P]` Todas las pruebas nuevas de fallo (versión no reconocida, archivo
dañado, restricción violada) comprueban dos cosas a la vez:

1. que se lanza una excepción clara y comprensible **dentro** de la
   aplicación (para quien la desarrolla o la mantiene);
2. que esa excepción **no** llega tal cual a un cliente HTTP, porque
   `migrate()` se ejecuta antes de arrancar el servidor — si falla, el
   servidor simplemente no llega a existir.

Esta distinción — causa técnica interna frente a mensaje público — es
la misma que ya trabajasteis con `ExceptionHandlingHandler` en
incrementos anteriores, aplicada ahora al arranque de la aplicación en
vez de a una petición HTTP concreta.

---

## Probar un rollback real sin trucos

`[A]` Para demostrar que un fallo a mitad de una operación de varias
escrituras no deja datos parciales, `SqliteWriteAtomicityTest` no
modifica ningún método de producción ni usa reflexión para forzar un
error. En su lugar, reproduce exactamente el mismo patrón que ya usan
los repositorios (desactivar el autocommit, escribir, y hacer rollback
si algo falla) y provoca una violación **real** de la restricción
`UNIQUE(column_id, position)` que ya existe en la tabla `cards`.

Es un ejemplo de cómo probar el comportamiento de un sistema mediante
sus propias reglas (las restricciones de la base de datos) en vez de
inventar un mecanismo de prueba que no existe en producción.

---

## Glosario nuevo

| Término | Significado |
| :--- | :--- |
| Grafo de objetos | El conjunto de objetos Java conectados entre sí (por ejemplo, un servicio que referencia a un repositorio que referencia a una fábrica de conexiones) |
| Esquema | La estructura de tablas, columnas y restricciones de una base de datos, sin los datos que contiene |
| Fallo temprano (*fail fast*) | Detectar y notificar un problema lo antes posible, en vez de continuar con un estado no verificado |
| Referencia huérfana | Una fila que apunta, mediante clave foránea, a otra fila que ya no existe |
