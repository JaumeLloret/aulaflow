# ADR-0005: Trazabilidad y tratamiento transversal de errores HTTP

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Estado: aceptada
* Fecha: 2026-07-23
* Proyecto: AulaFlow 1.0
* Incremento: 0.1.E

## Contexto

AulaFlow ya dispone de un servidor HTTP embebido y de un primer contrato:

`GET /api/v1/health`

La aplicación puede devolver respuestas correctas y errores controlados
con códigos `404` y `405`.

Sin embargo, todavía existen dos problemas importantes.

### Falta de trazabilidad

Cuando un cliente recibe una respuesta, no existe un identificador que
permita relacionarla con los registros generados durante el
procesamiento de esa petición.

Esto dificulta investigar incidencias, especialmente cuando:

* llegan varias peticiones al mismo tiempo;
* distintos usuarios solicitan la misma ruta;
* existen varios hilos virtuales procesando peticiones;
* un error aparece únicamente en determinados casos;
* el usuario solo puede comunicar la respuesta recibida.

### Falta de protección ante errores inesperados

Un manejador HTTP puede lanzar una excepción no prevista.

Por ejemplo:

```java
throw new IllegalStateException(
        "No se ha podido recuperar la configuración interna."
);
```

Si esta excepción no se controla, el cliente podría recibir:

* una conexión interrumpida;
* una respuesta incompleta;
* un comportamiento diferente según el tipo de error;
* detalles internos de implementación;
* una traza Java;
* rutas o datos que no deberían exponerse.

También se produciría duplicación si cada endpoint tuviera que
implementar manualmente su propio bloque `try-catch`, su request ID y su
registro de actividad.

## Objetivos

La infraestructura HTTP transversal debe garantizar que:

* cada petición reciba un identificador;
* el identificador aparezca en la respuesta;
* los errores JSON contengan el mismo identificador;
* los registros puedan relacionarse con la respuesta;
* los errores inesperados se conviertan en respuestas `500`;
* el cliente no reciba información interna;
* la traza completa permanezca disponible en el servidor;
* los endpoints no repitan esta lógica;
* la solución funcione sin frameworks ni dependencias externas.

## Opciones consideradas

### Implementar la trazabilidad dentro de cada endpoint

Cada manejador podría generar su propio identificador, registrar la
petición y capturar excepciones.

Esta opción se descarta porque produciría:

* duplicación;
* contratos inconsistentes;
* riesgo de olvidar la trazabilidad en una ruta;
* mayor dificultad de mantenimiento;
* endpoints mezclados con responsabilidades técnicas.

### Modificar directamente todos los manejadores existentes

Podríamos crear una clase base común de la que heredaran todos los
manejadores.

Esta opción se descarta porque:

* obligaría a utilizar herencia;
* limitaría la composición;
* acoplaría todos los endpoints a una implementación concreta;
* dificultaría reutilizar cada comportamiento por separado.

### Crear una canalización de manejadores

Cada comportamiento transversal puede envolverse alrededor del
manejador real mediante objetos que implementan `HttpHandler`.

Ejemplo:

```text
RequestTracingHandler
        │
        ▼
ExceptionHandlingHandler
        │
        ▼
HealthHandler
```

Esta solución permite añadir comportamiento sin modificar el endpoint.

## Decisión

AulaFlow utilizará una canalización común de manejadores HTTP.

La canalización estándar se construirá mediante:

```java
HttpHandlerPipeline.standard(endpointHandler)
```

La composición inicial será:

```text
RequestTracingHandler
        │
        ▼
ExceptionHandlingHandler
        │
        ▼
EndpointHandler
```

Cada contexto HTTP de AulaFlow deberá registrar la canalización
completa, no directamente el manejador del endpoint.

## Identificador de petición

Cada petición recibirá un identificador generado mediante:

```java
UUID.randomUUID()
```

El identificador se devolverá en la cabecera:

```http
X-Request-Id: e19b6898-ecc2-4307-9005-2bfd1d151b5d
```

Los errores JSON incluirán el mismo valor:

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "El recurso solicitado no existe",
  "requestId": "e19b6898-ecc2-4307-9005-2bfd1d151b5d"
}
```

El mismo identificador se utilizará también en los registros del
servidor.

Por tanto, debe cumplirse:

```text
X-Request-Id de la cabecera
        =
requestId del cuerpo de error
        =
requestId del registro del servidor
```

El request ID se utilizará únicamente para trazabilidad.

No sustituye a:

* una sesión;
* un token;
* una credencial;
* un identificador de usuario;
* un mecanismo de autenticación.

## Generación del identificador

La clase `RequestIds` será responsable de:

* comprobar si el intercambio ya tiene un request ID;
* reutilizarlo cuando ya exista;
* generar uno nuevo cuando sea necesario;
* añadirlo a las cabeceras de respuesta.

Este comportamiento evita que distintas capas generen identificadores
diferentes para la misma petición.

## Registro de peticiones

`RequestTracingHandler` registrará como mínimo:

* método HTTP;
* ruta solicitada;
* código de respuesta;
* duración;
* request ID.

Ejemplo:

```text
HTTP GET /api/v1/health -> 200 in 3 ms
requestId=e19b6898-ecc2-4307-9005-2bfd1d151b5d
```

Para medir la duración se utilizará:

```java
System.nanoTime()
```

Este método se utilizará para calcular intervalos, no para representar
fechas u horas.

La duración se convertirá a milisegundos mediante:

```java
TimeUnit.NANOSECONDS.toMillis(...)
```

## Sistema de logging

Durante el bootstrap se utilizará:

```java
System.Logger
```

Esta decisión evita introducir todavía dependencias externas como:

* SLF4J;
* Logback;
* Log4j.

Los niveles iniciales serán:

| Nivel     | Uso                                   |
| --------- | ------------------------------------- |
| `INFO`    | Petición procesada                    |
| `WARNING` | Error de entrada o salida             |
| `ERROR`   | Excepción inesperada de la aplicación |

El sistema de logging se revisará antes de desplegar AulaFlow en un
entorno de producción.

## Tratamiento de excepciones inesperadas

`ExceptionHandlingHandler` capturará excepciones de tipo:

```java
RuntimeException
```

Algunos ejemplos son:

* `IllegalStateException`;
* `IllegalArgumentException`;
* `NullPointerException`.

Cuando la respuesta todavía no haya comenzado, la excepción se
convertirá en:

```http
HTTP/1.1 500 Internal Server Error
```

Cuerpo:

```json
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "Se ha producido un error interno",
  "requestId": "e19b6898-ecc2-4307-9005-2bfd1d151b5d"
}
```

## Diferencia entre mensaje público y traza interna

AulaFlow distinguirá expresamente entre la información que recibe el
cliente y la información que conserva el servidor.

### Mensaje público

El mensaje público está destinado al usuario o al cliente HTTP.

Debe ser:

* comprensible;
* estable;
* breve;
* seguro;
* independiente de detalles de implementación.

Ejemplo:

```json
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "Se ha producido un error interno",
  "requestId": "e19b6898-ecc2-4307-9005-2bfd1d151b5d"
}
```

El mensaje público no debe contener:

* nombres de clases internas;
* nombres de tablas;
* consultas SQL;
* rutas del sistema;
* credenciales;
* variables de entorno;
* tokens;
* detalles de infraestructura;
* trazas Java;
* mensajes técnicos sensibles.

### Traza interna

La traza interna está destinada al diagnóstico por parte del equipo de
desarrollo o de administración.

Puede contener:

* tipo de excepción;
* mensaje técnico original;
* pila de llamadas;
* clase y método donde se produjo el fallo;
* excepciones encadenadas;
* request ID;
* contexto técnico necesario para investigar.

Ejemplo conceptual:

```text
ERROR: Error HTTP no controlado.
requestId=e19b6898-ecc2-4307-9005-2bfd1d151b5d

java.lang.IllegalStateException:
No se ha podido recuperar la configuración interna
    at ...
```

La traza se registra únicamente en el servidor.

No se copia dentro de la respuesta HTTP.

### Motivo de la separación

Esta separación permite simultáneamente:

* informar al cliente de que se ha producido un error;
* proporcionar un identificador para comunicar la incidencia;
* conservar suficiente información para investigarla;
* evitar revelar detalles internos;
* mantener un contrato de error estable.

## Respuesta ya iniciada

Antes de enviar un `500`, la infraestructura comprobará:

```java
exchange.getResponseCode()
```

Si devuelve:

```text
-1
```

todavía no se ha enviado un código de respuesta y puede generarse un
`500`.

Si la respuesta ya ha comenzado, no se intentará sustituirla por otra
respuesta completa.

En ese caso se cerrará el intercambio para liberar los recursos.

## Excepciones que no se capturan

No se capturará indiscriminadamente cualquier `Throwable`.

No se convertirán en respuestas HTTP ordinarias errores graves de la
JVM como:

* `OutOfMemoryError`;
* `StackOverflowError`;
* otros descendientes de `Error`.

Tampoco se convertirá automáticamente cualquier `IOException` en un
`500`, porque puede significar que:

* el cliente ha cerrado la conexión;
* se ha perdido la comunicación;
* ya no es posible enviar una respuesta;
* ha fallado la escritura por red.

Los errores de entrada y salida se registrarán y se propagarán cuando
corresponda.

## Patrón utilizado

La solución aplica el patrón estructural **Decorator**.

Cada manejador:

* implementa la misma interfaz;
* contiene otro manejador;
* añade comportamiento;
* delega la operación principal.

Ejemplo:

```text
RequestTracingHandler
        envuelve a
ExceptionHandlingHandler
        envuelve a
HealthHandler
```

Este patrón permite combinar responsabilidades sin modificar la clase
final del endpoint.

## Consecuencias positivas

* Todos los endpoints reciben trazabilidad de forma consistente.
* Se evita duplicar lógica.
* Los errores utilizan un contrato común.
* El cliente obtiene un identificador útil.
* Los registros permiten localizar la petición.
* Los errores internos no se filtran.
* Cada comportamiento puede probarse por separado.
* Los endpoints conservan una responsabilidad clara.
* La solución no depende de frameworks externos.
* La canalización puede ampliarse posteriormente.

## Consecuencias negativas

* La composición de manejadores añade complejidad.
* El patrón Decorator puede superar el nivel inicial de 1.º DAM.
* El orden de los manejadores afecta al comportamiento.
* El logging actual todavía es básico.
* Los UUID hacen que las respuestas de error sean variables.
* Las pruebas deben validar el formato sin depender de un valor fijo.

## Riesgos

* Registrar accidentalmente información sensible.
* Envolver un endpoint en un orden incorrecto.
* Registrar un manejador sin utilizar la canalización.
* Intentar enviar un `500` cuando la respuesta ya ha comenzado.
* Confundir el request ID con un identificador de usuario.
* tratar una excepción de red como un fallo interno ordinario.

## Deuda técnica

* Añadir niveles de logging configurables.
* Incorporar salida estructurada de registros.
* Decidir el destino de los logs en producción.
* Evitar registrar información sensible.
* Incorporar datos adicionales de contexto cuando exista autenticación.
* Añadir un tratamiento explícito de excepciones de negocio.
* Diferenciar errores esperados y errores inesperados.
* Crear pruebas unitarias específicas para cada decorador.
* Evaluar una biblioteca de logging antes del despliegue final.

## Alcance de la decisión

Esta decisión se aplica a la infraestructura HTTP de AulaFlow 1.0.

AulaFlow 2.0 podrá sustituir esta implementación por filtros,
interceptores o mecanismos equivalentes proporcionados por un
framework, pero deberá conservar los mismos principios:

* trazabilidad;
* separación entre mensaje público y traza interna;
* errores seguros;
* contratos consistentes.
