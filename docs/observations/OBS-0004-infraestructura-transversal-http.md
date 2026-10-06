# OBS-0004: Infraestructura transversal HTTP

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Fecha: 2026-07-23
* Proyecto: AulaFlow 1.0
* Incremento: 0.1.E

## Finalidad de esta observación

Este incremento introduce conceptos que superan claramente los
contenidos iniciales habituales de 1.º DAM.

El código no debe simplificarse únicamente por ser avanzado si está
correctamente diseñado y resuelve problemas reales.

Debe:

* explicarse;
* secuenciarse;
* relacionarse con los módulos;
* diferenciarse entre contenido obligatorio y ampliación;
* utilizarse como ejemplo progresivo.

## Problema técnico trabajado

Hasta este incremento, los manejadores HTTP se encargaban directamente
de procesar cada petición.

Ahora aparecen responsabilidades comunes a todas las rutas:

* asignar un identificador;
* registrar el inicio y el final;
* medir la duración;
* registrar el estado;
* capturar errores inesperados;
* devolver respuestas `500`;
* evitar filtrar información interna.

Estas responsabilidades no pertenecen al endpoint de salud.

Son infraestructura transversal.

## Conceptos que han aparecido

* Trazabilidad.
* Request ID.
* UUID.
* Cabeceras HTTP personalizadas.
* Logging.
* Niveles de logging.
* Duración de una operación.
* `System.nanoTime()`.
* Conversión de unidades temporales.
* Delegación.
* Composición de objetos.
* Patrón Decorator.
* Canalización de manejadores.
* Excepciones inesperadas.
* Errores HTTP `500`.
* Mensaje público.
* Traza interna.
* Pila de llamadas.
* Información sensible.
* Seguridad por ocultación de detalles internos.
* Diferencia entre `RuntimeException`, `IOException` y `Error`.
* Estado de una respuesta HTTP.
* Cierre de un intercambio.
* Pruebas de fallos deliberados.
* Integración continua.
* Workflow.
* Runner.
* Maven Wrapper.
* Caché de dependencias.
* Status checks.
* Protección de rama.

## Conocimientos previos necesarios

Antes de estudiar este incremento, el alumnado debería conocer:

* clases y objetos;
* interfaces;
* implementación de interfaces;
* atributos;
* constructores;
* composición básica;
* excepciones;
* bloques `try-catch`;
* métodos estáticos;
* expresiones lambda;
* HTTP básico;
* códigos de estado;
* cabeceras;
* pruebas con JUnit;
* Git y ramas;
* ciclo de vida básico de Maven.

Para comprenderlo en profundidad también se necesita:

* distinguir herencia de composición;
* comprender la delegación;
* conocer la pila de llamadas;
* comprender que varias peticiones pueden procesarse concurrentemente;
* entender la diferencia entre información pública e interna.

## Nivel pedagógico estimado

### Comprensión básica

El alumnado puede comprender inicialmente que:

* cada petición recibe un código identificador;
* el código aparece en la respuesta y en los registros;
* un error inesperado devuelve `500`;
* el cliente no debe recibir la traza Java;
* GitHub vuelve a ejecutar las pruebas automáticamente.

Este nivel es razonable durante 1.º DAM con acompañamiento.

### Comprensión intermedia

El alumnado puede analizar:

* cómo un manejador contiene otro;
* cómo se delega la ejecución;
* por qué el orden de la canalización importa;
* cómo se mide la duración;
* por qué se capturan unas excepciones y otras no;
* cómo una prueba provoca deliberadamente un error.

Este nivel requiere una explicación guiada y ejemplos pequeños.

### Producción autónoma

Pedir al alumnado que diseñe desde cero una canalización de decoradores,
un sistema de trazabilidad y una gestión segura de excepciones puede
resultar excesivo para una fase inicial de 1.º DAM.

Puede plantearse como:

* ampliación;
* actividad guiada;
* análisis de código;
* refactorización con plantilla;
* reto para alumnado avanzado.

## Reparto por módulos

### Programación

Conceptos principales:

* interfaces;
* implementación de `HttpHandler`;
* composición;
* delegación;
* patrón Decorator;
* lambdas;
* excepciones;
* jerarquía de excepciones;
* métodos estáticos;
* UUID;
* encapsulación;
* inmutabilidad;
* medición temporal;
* pruebas automatizadas.

Actividades posibles:

* crear un decorador sencillo;
* envolver una operación con un contador;
* medir el tiempo de un método;
* diferenciar `RuntimeException`, `IOException` y `Error`;
* generar y validar UUID;
* implementar una prueba que espere un `500`;
* comprobar que una excepción interna no aparece en el cuerpo.

### Entornos de Desarrollo

Conceptos principales:

* ejecución de Maven `verify`;
* integración continua;
* GitHub Actions;
* estructura de un workflow;
* runner;
* variables y expresiones YAML;
* caché Maven;
* logs de una ejecución;
* checks de una pull request;
* protección de `main`;
* diagnóstico de fallos en CI;
* diferencia entre entorno local y runner remoto.

Actividades posibles:

* localizar una prueba fallida en GitHub Actions;
* comparar el resultado local con el remoto;
* romper deliberadamente una prueba;
* interpretar cada paso del workflow;
* comprobar las versiones de Java y Maven;
* analizar el efecto de `cancel-in-progress`;
* comprobar que Maven Wrapper evita depender del Maven instalado.

### Proyecto Intermodular

Conceptos principales:

* criterio de aceptación;
* trazabilidad de incidencias;
* contrato de errores;
* seguridad de la información;
* decisión arquitectónica;
* deuda técnica;
* evidencia automática;
* pull request;
* vinculación entre issue y pull request;
* definición de terminado.

Actividades posibles:

* redactar un contrato de error;
* justificar la necesidad del request ID;
* relacionar una respuesta de Cartero con la consola;
* documentar una decisión mediante ADR;
* decidir qué información puede recibir un usuario;
* elaborar un informe de la CI;
* revisar si el incremento cumple todos los criterios.

## Conceptos avanzados que deben documentarse especialmente

### Patrón Decorator

El alumnado puede confundirlo con herencia.

Debe mostrarse primero con un ejemplo sencillo:

```text
Objeto base
    ↓
Objeto que añade comportamiento
    ↓
Otro objeto que añade más comportamiento
```

Después trasladarlo a:

```text
HealthHandler
    ↓
ExceptionHandlingHandler
    ↓
RequestTracingHandler
```

Es importante visualizar que todos implementan `HttpHandler`.

### Delegación

La instrucción:

```java
delegate.handle(exchange);
```

significa que el decorador no sustituye al manejador final.

Ejecuta una responsabilidad adicional y luego cede el trabajo.

### Orden de la canalización

No es equivalente:

```text
Tracing → Exceptions → Endpoint
```

que:

```text
Exceptions → Tracing → Endpoint
```

La trazabilidad debe comenzar antes de que pueda producirse el error
para garantizar que la excepción también tenga request ID.

### Concurrencia

Cada petición puede ejecutarse en un hilo virtual diferente.

No debemos almacenar el request ID en:

* una variable estática mutable;
* un atributo compartido del servidor;
* una única variable global.

El ID pertenece al intercambio HTTP concreto.

### Mensaje público y traza interna

Esta distinción debe tratarse como un principio de diseño y seguridad.

#### Mensaje público

Es el contenido que puede recibir el cliente:

```json
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "Se ha producido un error interno",
  "requestId": "..."
}
```

Debe ser:

* seguro;
* comprensible;
* estable;
* no técnico.

#### Traza interna

Es la información que utiliza el equipo para investigar:

```text
java.lang.IllegalStateException
    at clase.metodo(...)
    at ...
```

Puede incluir detalles técnicos, pero no debe publicarse.

El request ID es el puente entre ambos espacios.

### Diferencia entre tipos de fallo

#### Error esperado de aplicación

Ejemplo:

```text
El recurso solicitado no existe.
```

Puede convertirse directamente en una respuesta conocida, como `404`.

#### Error inesperado de programación

Ejemplo:

```text
NullPointerException
```

Se registra internamente y devuelve un `500` genérico.

#### Error de entrada o salida

Ejemplo:

```text
El cliente cierra la conexión.
```

Puede impedir enviar cualquier respuesta y debe gestionarse como un
problema de comunicación.

#### Error grave de la JVM

Ejemplo:

```text
OutOfMemoryError
```

No debe tratarse como una excepción HTTP ordinaria.

## Dificultades previsibles

* Pensar que el request ID identifica al usuario.
* Creer que un UUID nunca puede repetirse de forma matemática.
* Guardar el request ID en una variable global.
* No entender qué objeto envuelve a cuál.
* Registrar directamente el endpoint sin pasar por la canalización.
* Confundir delegación con recursividad.
* Capturar `Exception` o `Throwable` sin analizar consecuencias.
* Devolver la traza completa al cliente.
* Incluir el mensaje de la excepción en el JSON público.
* Intentar enviar un `500` después de comenzar un `200`.
* Interpretar `System.nanoTime()` como una fecha.
* Comparar directamente valores absolutos de `nanoTime()`.
* Confundir un log con un `System.out.println`.
* Pensar que la CI sustituye las pruebas locales.
* Creer que una CI verde demuestra que no existen errores posibles.
* No entender por qué el runner descarga nuevamente dependencias.
* Modificar el workflow sin comprender YAML.
* Confundir el nombre del workflow con el nombre del job.
* No saber localizar el paso concreto que ha fallado.

## Errores frecuentes esperables

### Registrar el endpoint sin la canalización

Código incorrecto:

```java
httpServer.registerContext(
        HealthHandler.PATH,
        healthHandler
);
```

Consecuencia:

* no existe request ID;
* no existe registro;
* no se controla el `500`.

### Filtrar el mensaje interno

Código inseguro:

```java
"message": exception.getMessage();
```

Consecuencia:

* se revelan detalles técnicos;
* el contrato depende de mensajes internos;
* pueden aparecer datos sensibles.

### Generar varios request IDs

Si cada componente llama directamente a `UUID.randomUUID()`:

```text
Cabecera → ID A
JSON     → ID B
Log      → ID C
```

La trazabilidad queda rota.

### Capturar errores demasiado amplios

Código peligroso:

```java
catch (Throwable throwable)
```

Puede ocultar errores graves de la JVM que no deberían procesarse como
una petición HTTP normal.

### Medir duración con la hora del sistema

Código menos apropiado:

```java
Instant inicio = Instant.now();
```

El reloj puede modificarse.

Para intervalos se utiliza:

```java
System.nanoTime();
```

## GitHub: protección técnica y protección procedimental

El alumnado debe comprender que configurar una regla no implica
necesariamente que la plataforma pueda aplicarla.

Conviene diferenciar:

- protección técnica: GitHub bloquea la acción;
- protección procedimental: el equipo acuerda no realizarla.

La disponibilidad de algunas reglas puede depender del plan y de si el
repositorio es público o privado.

## Actividades didácticas propuestas

### Actividad 1 — Seguir una petición

1. Ejecutar AulaFlow.
2. Enviar una petición con Cartero.
3. copiar `X-Request-Id`.
4. Localizarlo en la consola.
5. Explicar el recorrido completo.

### Actividad 2 — Comparar mensaje y traza

1. Provocar un error interno en una prueba.
2. Leer el cuerpo HTTP.
3. Leer la traza de la consola.
4. Crear una tabla con las diferencias.
5. Decidir qué información nunca debe enviarse al cliente.

### Actividad 3 — Alterar el orden

Cambiar temporalmente el orden de los decoradores y analizar:

* cuándo se crea el request ID;
* qué ocurre si el primer componente falla;
* qué registros se producen.

### Actividad 4 — Decorador sencillo

Crear un manejador que añada una cabecera:

```http
X-AulaFlow-Version: 0.1.0-SNAPSHOT
```

El objetivo es comprender el patrón antes de estudiar toda la
canalización.

### Actividad 5 — Romper la CI

Modificar temporalmente una aserción para que falle.

Después:

* hacer push a la rama;
* localizar el workflow fallido;
* identificar la prueba;
* corregirla;
* comprobar la nueva ejecución.

### Actividad 6 — Analizar un workflow

Identificar en `ci.yml`:

* evento;
* job;
* runner;
* pasos;
* acción reutilizada;
* comando Maven;
* timeout;
* permisos.

## Evidencias evaluables

* Explicación del propósito del request ID.
* Relación entre cabecera, JSON y log.
* Diferenciación entre mensaje público y traza interna.
* Diagnóstico de una respuesta `500`.
* Análisis del patrón Decorator.
* Identificación del orden de la canalización.
* Prueba automatizada que provoca una excepción.
* Interpretación de un workflow de GitHub Actions.
* Localización de un fallo en CI.
* Explicación del uso de Maven Wrapper.
* Justificación de por qué no se devuelve la traza al cliente.

## Secuenciación recomendada

1. Repasar petición y respuesta HTTP.
2. Introducir el problema de varias peticiones similares.
3. Añadir manualmente un request ID a una única respuesta.
4. Detectar la duplicación.
5. Introducir un manejador envolvente.
6. Explicar la delegación.
7. Añadir el registro.
8. Provocar una excepción sin capturar.
9. diferenciar mensaje público y traza.
10. Añadir el manejador de excepciones.
11. Construir la canalización.
12. Crear la prueba de `500`.
13. Ejecutar Maven.
14. Introducir GitHub Actions.
15. Comparar verificación local y remota.

## Decisión pedagógica provisional

El alumnado no tendrá que diseñar autónomamente toda esta
infraestructura durante la primera aproximación.

Sí deberá poder:

* utilizarla;
* leerla;
* describir el recorrido;
* modificar partes guiadas;
* escribir pruebas sencillas;
* detectar los riesgos de seguridad;
* relacionar los registros con las respuestas.

La producción autónoma completa podrá reservarse para:

* una ampliación;
* alumnado avanzado;
* una fase posterior del curso;
* la comparación con AulaFlow 2.0.
