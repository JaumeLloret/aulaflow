# Comprender AulaFlow al llegar a segundo de DAM

No necesitas haber participado en el proyecto de primero. El punto de partida es una aplicación terminada que puedes utilizar y observar. Este recorrido relaciona lo que ves en pantalla con Java, HTTP, SQL, pruebas y despliegue.

Es una guía de estudio, no una entrega evaluable ni una modificación de las actividades de Aules. Hazla a tu ritmo. Conviene conocer clases, métodos, colecciones, excepciones y SQL básico; las piezas de seguridad se pueden consultar cuando llegues a ellas.

## 1. Entender qué problema resuelve

Sigue la [guía de primer arranque](01-primer-arranque.md). Crea un tablero «Preparar una práctica», con columnas para lo pendiente, lo que estás haciendo y lo que ya has terminado. Añade tres tarjetas, muévelas, pon una etiqueta y completa parte de una lista de comprobación.

Antes de leer Java, explica con tus palabras qué son un tablero, una columna, una tarjeta y un elemento de la lista. Una tarjeta representa una tarea; su columna indica el estado del trabajo.

**Comprueba tu avance:** puedes mostrar el mismo tablero después de reiniciar y explicar por qué necesitas volver a iniciar sesión aunque las tareas permanezcan.

## 2. Reconocer la entrada al programa

Abre [AulaFlowApplication.java](../../src/main/java/es/aulaflow/AulaFlowApplication.java). Localiza `main`, la lectura de configuración, la preparación de SQLite y autenticación, el registro de rutas HTTP y el arranque del servidor.

Identifica qué objetos se crean y cómo se conectan. La clase principal prepara las piezas: no contiene todas las reglas de tarjetas ni todas las consultas SQL.

Consulta [ApplicationConfig.java](../../src/main/java/es/aulaflow/shared/config/ApplicationConfig.java) y [SqliteConfig.java](../../src/main/java/es/aulaflow/infrastructure/persistence/sqlite/SqliteConfig.java). Cambia solo el puerto de tu ejecución local a 8081 y abre la nueva dirección.

**Comprueba tu avance:** puedes explicar de dónde sale el puerto y dónde está tu base, sin buscar una contraseña dentro del código.

## 3. Leer una regla pequeña

Lee [CardTitle.java](../../src/main/java/es/aulaflow/domain/board/CardTitle.java) y [CardModelTest.java](../../src/test/java/es/aulaflow/domain/board/CardModelTest.java). Observa cómo se protege una tarjeta frente a títulos inválidos y cómo las pruebas describen comportamientos válidos e inválidos.

Abre [CardService.java](../../src/main/java/es/aulaflow/application/board/CardService.java). Relaciona `createCard`, `updateCard`, `deleteCard` y `moveCard` con acciones que ya hiciste en el navegador. El servicio recibe un repositorio; no escribe SQL directamente.

**Comprueba tu avance:** puedes distinguir una regla del título de una decisión sobre dónde guardar una tarjeta, y localizar una prueba de una entrada inválida.

## 4. Seguir una tarjeta desde HTTP hasta SQLite

Usa el [mapa del código](03-mapa-del-codigo.md) y sigue la creación de una tarjeta:

1. El navegador envía una petición con título y descripción.
2. Un manejador HTTP interpreta los datos, comprueba la sesión y la protección CSRF.
3. `CardService` transforma los valores y solicita la creación al repositorio.
4. `SqliteCardRepository` comprueba las relaciones y guarda el resultado mediante JDBC.
5. El manejador devuelve la respuesta para actualizar la pantalla.

Hay dos entradas: formularios HTML y API JSON. Para estudiar la API, abre [las peticiones de Cartero](../../http/cartero/README.md). En el navegador también puedes observar **Network/Red**. Identifica método, dirección, estado y cuerpo de una petición; no copies cookies ni tokens en una entrega o captura.

Lee [BoardEndpointTest.java](../../src/test/java/es/aulaflow/infrastructure/http/BoardEndpointTest.java) para ver cómo se inicia un servidor y se comprueban peticiones reales.

**Comprueba tu avance:** puedes contar el viaje de una tarjeta y señalar dónde se recibe HTTP, dónde se aplican reglas y dónde se ejecuta SQL. Distingues 401 (falta una sesión válida) de 403 (una operación rechazada, por ejemplo por CSRF).

## 5. Entender datos persistentes y pruebas

Lee [la migración de tarjetas](../../src/main/resources/db/migration/V007__create_cards.sql) y localiza las relaciones con tablero y columna. En [SqliteCardRepository.java](../../src/main/java/es/aulaflow/infrastructure/persistence/sqlite/SqliteCardRepository.java), busca `PreparedStatement`, los parámetros y las transacciones usadas para mantener el orden.

Mueve una tarjeta y reinicia. Relaciónalo con [RestartRecoveryIntegrationTest.java](../../src/test/java/es/aulaflow/infrastructure/persistence/sqlite/RestartRecoveryIntegrationTest.java).

Ejecuta una prueba concreta desde IntelliJ o la terminal. En Windows:

```powershell
.\mvnw.cmd -Dtest=CardModelTest test
```

En macOS/Linux:

```bash
./mvnw -Dtest=CardModelTest test
```

**Comprueba tu avance:** puedes distinguir una prueba del dominio, una que utiliza SQLite y una que recorre HTTP. Sabes ejecutar una prueba concreta y la suite completa.

## Qué comprender primero

| Base para orientarte | Consulta posterior con más calma |
| --- | --- |
| Clases, métodos, constructores y colecciones | Derivación PBKDF2 y credenciales |
| Responsabilidad de cada capa | Sesiones y protección CSRF |
| Petición y respuesta HTTP | Parser multipart y seguridad del CSV |
| SQL, claves foráneas y consultas parametrizadas | Reordenación transaccional de tarjetas y listas |
| Datos persistentes frente a memoria | Copias consistentes y restauración |
| Ejecutar, leer y depurar una prueba | Construcción del contenedor |

No hace falta escribir todas las piezas avanzadas de memoria para empezar. Sí conviene comprender su propósito y respetar sus contratos al modificar la aplicación. Para una práctica propia, trabaja en una rama, escoge una regla pequeña, comprueba sus pruebas y explica el comportamiento que cambiaste.

Continúa con la [evolución del proyecto](04-evolucion-del-proyecto.md).
