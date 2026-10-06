# ADR-0006: Recursos web estáticos servidos desde el classpath

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Estado: aceptada
* Fecha: 2026-07-29
* Proyecto: AulaFlow 1.0
* Incremento: 0.2

## Contexto

AulaFlow necesita una interfaz web inicial compuesta por:

* HTML;
* CSS;
* JavaScript;
* futuros catálogos JSON de idioma.

La aplicación utiliza el servidor HTTP embebido incluido en Java y no
incorpora un framework web.

Los recursos deben poder ejecutarse de manera consistente en:

* Windows;
* macOS;
* GNU/Linux;
* IntelliJ IDEA;
* Maven;
* GitHub Actions;
* un archivo JAR;
* futuros despliegues mediante contenedores.

No debe suponerse que el proyecto se ejecutará siempre desde el
directorio raíz del repositorio.

Por tanto, no resulta seguro depender de rutas físicas como:

```text
src/main/resources/web/index.html
```

durante la ejecución.

Esa ruta existe en el proyecto fuente, pero deja de representar la
ubicación real del recurso después de compilar y empaquetar la
aplicación.

## Problema

Es necesario decidir:

* dónde almacenar los recursos web;
* cómo cargarlos durante la ejecución;
* cómo relacionar las URL públicas con los recursos internos;
* cómo determinar su tipo de contenido;
* si debe incorporarse un framework web;
* cómo evitar que una URL permita acceder a archivos no autorizados.

## Objetivos

La solución debe:

* funcionar con Java 26;
* funcionar con Maven y GitHub Actions;
* funcionar independientemente del sistema operativo;
* permitir empaquetar los recursos dentro del JAR;
* evitar rutas absolutas;
* utilizar tipos de contenido correctos;
* preservar UTF-8;
* pasar por la canalización HTTP transversal;
* conservar `X-Request-Id`;
* conservar el logging y el tratamiento de errores;
* impedir el acceso arbitrario a recursos internos;
* evitar dependencias externas innecesarias;
* poder explicarse progresivamente en 1.º DAM.

## Opciones consideradas

### Leer los archivos directamente desde el sistema operativo

La aplicación podría utilizar rutas como:

```java
Path.of(
        "src/main/resources/web/index.html"
);
```

Esta opción se descarta porque:

* depende del directorio desde el que se inicia la aplicación;
* confunde la estructura del proyecto con la estructura de ejecución;
* puede funcionar en IntelliJ y fallar dentro de un JAR;
* dificulta los despliegues;
* introduce diferencias entre sistemas operativos;
* expone innecesariamente el sistema de archivos.

### Utilizar un servidor web o framework externo

Podría incorporarse:

* Spring Boot;
* Jetty;
* Undertow;
* un servidor Node.js;
* un framework de frontend con servidor de desarrollo.

Esta opción se pospone porque:

* aumenta el número de dependencias;
* oculta parte del funcionamiento HTTP que se quiere estudiar;
* incrementa la complejidad inicial;
* no es necesaria para el alcance de AulaFlow 1.0;
* dificulta distinguir qué proporciona Java y qué proporciona el
  framework.

La decisión podrá revisarse en una versión futura cuando las necesidades
del producto lo justifiquen.

### Servir los recursos desde un CDN

CSS, JavaScript o bibliotecas podrían descargarse desde servicios
externos.

Esta opción se descarta para los recursos obligatorios porque:

* requiere conexión a Internet;
* introduce una dependencia externa;
* puede producir problemas de privacidad;
* reduce la reproducibilidad;
* puede dejar de funcionar si cambia el servicio;
* contradice la preferencia del proyecto por soluciones locales y
  autónomas.

### Cargar los recursos desde el classpath

Los archivos se almacenan dentro de:

```text
src/main/resources/web
```

Maven los copia al classpath durante la construcción y los incluye en el
artefacto empaquetado.

La aplicación los recupera mediante:

```java
ClasspathResourceLoader
```

Esta opción funciona tanto desde IntelliJ como desde Maven y desde un
JAR.

## Decisión

AulaFlow almacenará los recursos web estáticos dentro de:

```text
src/main/resources/web
```

La estructura inicial será:

```text
web/
├── index.html
└── assets/
    ├── css/
    │   └── app.css
    └── js/
        └── app.js
```

Los recursos se cargarán desde el classpath mediante:

```java
ClasspathResourceLoader
```

El cargador devolverá:

```java
Optional<byte[]>
```

Se utilizarán bytes porque un recurso estático no tiene por qué ser
siempre texto. Esta decisión permite servir posteriormente:

* imágenes;
* iconos;
* fuentes;
* documentos;
* otros formatos binarios.

## Rutas públicas e internas

Las URL públicas actuales se relacionan con recursos internos de forma
explícita:

```text
/                       → /web/index.html
/assets/css/app.css     → /web/assets/css/app.css
/assets/js/app.js       → /web/assets/js/app.js
/assets/i18n/ca.json    → /web/assets/i18n/ca.json
/assets/i18n/es.json    → /web/assets/i18n/es.json
```

Una URL pública no se utilizará directamente como una ruta de acceso al
classpath sin validación.

Durante esta primera fase se mantendrá una lista explícita de recursos
permitidos.

Cuando se introduzca una resolución general bajo `/assets/`, deberá:

* aceptar únicamente el prefijo previsto;
* normalizar la ruta;
* rechazar segmentos `..`;
* rechazar variantes codificadas de path traversal;
* impedir el acceso fuera de `/web/assets/`;
* devolver `404` para los recursos inexistentes;
* conservar el contrato de errores de AulaFlow.

## Protección actual frente a path traversal

La implementación actual no transforma directamente la URL solicitada en
una ruta del classpath.

Utiliza un mapa inmutable con asociaciones explícitas:

```text
URL pública permitida → recurso interno conocido
```

Cuando una URL no aparece en el mapa, la resolución produce:

```java
Optional.empty()
```

y la respuesta termina mediante el contrato controlado de `404`.

Esta estrategia rechaza, entre otras, las siguientes peticiones:

```text
/web/index.html
/assets/../web/index.html
/assets/%2e%2e/web/index.html
```

La primera intenta acceder directamente a la estructura interna del
classpath.

Las otras dos contienen intentos de navegación mediante `..`, de forma
literal y codificada.

Las respuestas rechazadas deben conservar:

```text
404
application/json; charset=utf-8
RESOURCE_NOT_FOUND
X-Request-Id
```

y no deben contener el recurso interno solicitado.

Esta protección depende actualmente de mantener una lista cerrada.

Cuando el número de recursos obligue a generalizar `/assets/`, deberá
introducirse una resolución segura y volver a ejecutarse todo el contrato
de seguridad.

## Tipos de contenido

El tipo de contenido se resolverá mediante:

```java
StaticContentTypes
```

Asociaciones iniciales:

```text
.html → text/html; charset=utf-8
.css  → text/css; charset=utf-8
.js   → text/javascript; charset=utf-8
.json → application/json; charset=utf-8
```

Las extensiones desconocidas utilizarán:

```text
application/octet-stream
```

Las respuestas incorporarán:

```http
X-Content-Type-Options: nosniff
```

Esta cabecera impide que el navegador intente interpretar el recurso
como un tipo diferente del declarado.

## Política de caché del incremento 0.2

Todas las respuestas estáticas incorporan:

```http
Cache-Control: no-store
```

La política se mantiene deliberadamente durante AulaFlow 1.0 porque:

* los nombres de archivo todavía no contienen versión ni hash;
* HTML, CSS, JavaScript y traducciones cambian frecuentemente;
* el entorno tiene una finalidad docente y de desarrollo;
* una copia antigua podría ocultar cambios correctos;
* todavía no se generan `ETag` ni `Last-Modified`;
* todavía no se procesan peticiones condicionales.

`no-store` indica al navegador y a intermediarios que no deben conservar
la respuesta para reutilizarla posteriormente.

Esta política prioriza previsibilidad y diagnóstico frente al
rendimiento.

No se considera una estrategia definitiva de producción.

Se revisará cuando AulaFlow disponga de:

* nombres de recursos versionados;
* hashes de contenido;
* `ETag`;
* `Last-Modified`;
* respuestas `304 Not Modified`;
* un proceso de construcción frontend;
* despliegues estables.

## Escritura de respuestas

Los cuerpos se enviarán mediante:

```java
HttpResponseWriter.sendBytes(...)
```

La escritura común será responsable de:

* establecer el código de estado;
* establecer `Content-Type`;
* establecer `X-Content-Type-Options`;
* calcular la longitud en bytes;
* escribir el cuerpo;
* cerrar correctamente el flujo.

## Canalización HTTP

`StaticResourceHandler` se registrará mediante:

```java
HttpHandlerPipeline.standard(...)
```

Los recursos web conservarán:

* request ID;
* logging;
* medición de duración;
* tratamiento de excepciones inesperadas;
* respuestas controladas.

No se registrará directamente el manejador sin la canalización.

## Logging de recursos web

Los recursos estáticos pasan por:

```java
HttpHandlerPipeline.standard(...)
```

La canalización aplica:

```text
RequestTracingHandler
→ ExceptionHandlingHandler
→ StaticResourceHandler
```

Cada petición registra:

* método HTTP;
* ruta pública;
* estado enviado;
* duración aproximada;
* request ID.

Ejemplo conceptual:

```text
HTTP GET /assets/i18n/es.json
→ 200
→ duración
→ requestId
```

Los errores `404` y `405` atraviesan la misma trazabilidad.

No se registran:

* cuerpos de los recursos;
* traducciones completas;
* contenido de `localStorage`;
* rutas físicas del sistema operativo;
* rutas internas del classpath;
* trazas técnicas dentro de la respuesta enviada al cliente.

No se ha modificado `RequestTracingHandler` porque ya satisface el
contrato requerido por este incremento.

## CSS y JavaScript

AulaFlow utilizará inicialmente:

* CSS nativo;
* JavaScript nativo;
* HTML semántico;
* APIs estándar del navegador.

No se incorporarán todavía:

* frameworks CSS;
* frameworks JavaScript;
* bibliotecas de componentes;
* gestores de paquetes frontend;
* herramientas de bundling;
* servicios externos.

El JavaScript comunicará estados de la aplicación, mientras que CSS será
responsable de su representación visual.

## Mejora progresiva

La página debe conservar contenido comprensible aunque JavaScript no se
ejecute.

El HTML contiene un estado inicial y JavaScript añade:

```text
data-js="enabled"
```

al elemento raíz.

CSS utiliza ese estado para modificar la representación.

La funcionalidad esencial no dependerá innecesariamente de JavaScript.

## Consecuencias positivas

* Los recursos se incluyen dentro del artefacto de AulaFlow.
* La ejecución no depende del directorio de trabajo.
* La solución funciona en los tres sistemas operativos.
* No se requieren dependencias externas.
* No se requiere conexión a Internet.
* Los tipos MIME están centralizados.
* La escritura HTTP no se duplica.
* Los recursos conservan trazabilidad y logging.
* La lista explícita reduce inicialmente la superficie de ataque.
* El diseño permite añadir posteriormente recursos binarios.
* El alumnado puede observar el funcionamiento HTTP sin que un framework
  lo oculte.

## Consecuencias negativas

* `StaticResourceHandler` debe mantener inicialmente asociaciones
  explícitas.
* Añadir muchos recursos manualmente no sería escalable.
* Será necesario diseñar posteriormente una resolución segura bajo
  `/assets/`.
* La aplicación asume responsabilidades que un framework resolvería
  automáticamente.
* Los tipos de contenido deben mantenerse de forma explícita.
* Las pruebas deben cubrir rutas, cabeceras y errores.
* La solución puede resultar avanzada para alumnado que todavía no
  conozca classpath, flujos o HTTP.

## Riesgos

* Confundir una URL con una ruta del sistema operativo.
* Permitir path traversal al generalizar la resolución.
* devolver un tipo MIME incorrecto;
* olvidar `nosniff`;
* registrar el manejador sin la canalización;
* leer los recursos como texto cuando sean binarios;
* introducir dependencias frontend sin necesidad;
* convertir la lista inicial de rutas en una solución permanente que no
  escale.

## Criterios de revisión

Esta decisión se revisará cuando:

* aumente significativamente el número de recursos;
* se incorporen imágenes, iconos o fuentes;
* se necesite caché de larga duración;
* se utilicen nombres versionados o hashes;
* aparezca un proceso de construcción frontend;
* se adopte un framework web;
* AulaFlow necesite servir archivos generados por usuarios;
* se prepare un despliegue de producción.

## Estado de implementación

Actualmente se sirven:

```http
GET /
GET /assets/css/app.css
GET /assets/js/app.js
GET /assets/i18n/ca.json
GET /assets/i18n/es.json
```

Los recursos utilizan:

* classpath;
* lista explícita de rutas permitidas;
* tipos MIME controlados;
* UTF-8 cuando corresponde;
* `X-Content-Type-Options: nosniff`;
* `Cache-Control: no-store`;
* `X-Request-Id`;
* logging transversal;
* respuestas de error controladas.

La protección actual rechaza el acceso directo a recursos internos y los
intentos de path traversal literales o codificados.

La resolución general de recursos permanece aplazada hasta que el número
de archivos haga insuficiente la lista explícita.
