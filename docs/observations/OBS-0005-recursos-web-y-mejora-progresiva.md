# OBS-0005: Recursos web y mejora progresiva

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Fecha: 2026-07-29
* Proyecto: AulaFlow 1.0
* Incremento: 0.2

## Finalidad de esta observación

Este subincremento incorpora simultáneamente conceptos de:

* Java;
* HTTP;
* Maven;
* HTML;
* CSS;
* JavaScript;
* accesibilidad;
* pruebas;
* Git;
* integración continua.

Aunque el resultado visual parece sencillo, el recorrido completo de una
petición atraviesa varias capas técnicas.

El código no debe presentarse al alumnado únicamente como archivos que
debe copiar.

Debe explicarse:

* qué problema resuelve cada archivo;
* dónde se almacena;
* cómo llega al classpath;
* cómo se solicita mediante HTTP;
* cómo se determina su tipo de contenido;
* cómo se ejecuta en el navegador;
* cómo se comprueba automáticamente;
* cómo se diagnostica cuando falla.

## Problema técnico trabajado

La página inicial necesita recursos externos:

```text
index.html
app.css
app.js
```

El navegador no conoce la estructura interna de Maven.

Solo conoce URL:

```text
/
/assets/css/app.css
/assets/js/app.js
```

El servidor debe relacionar esas URL con recursos empaquetados dentro de
la aplicación.

El alumnado debe distinguir entre:

```text
URL pública
ruta del proyecto
ruta del classpath
recurso empaquetado
```

## Conceptos que han aparecido

### Java y HTTP

* `HttpHandler`;
* rutas HTTP;
* código `200`;
* errores `404` y `405`;
* cabeceras HTTP;
* tipos MIME;
* UTF-8;
* cuerpos en bytes;
* `Optional`;
* flujos de entrada;
* classpath;
* delegación;
* resolución de recursos;
* lista explícita de rutas permitidas;
* pruebas de integración;
* request ID;
* logging.

### HTML

* estructura semántica;
* `header`;
* `main`;
* `section`;
* `footer`;
* atributos;
* clases CSS;
* `data-*`;
* `aria-labelledby`;
* `aria-live`;
* enlace de una hoja de estilos;
* carga diferida de JavaScript.

### CSS

* selectores;
* clases;
* variables CSS;
* cascada;
* herencia;
* box model;
* CSS Grid;
* unidades relativas;
* `clamp`;
* media queries;
* diseño responsive;
* selectores de atributos;
* separación entre estructura y presentación.

### JavaScript

* modo estricto;
* constantes;
* objeto `document`;
* elemento raíz;
* propiedad `dataset`;
* modificación de atributos;
* estado de la interfaz;
* ejecución diferida;
* separación entre comportamiento y presentación.

### Entornos

* estructura Maven;
* `src/main/resources`;
* classpath;
* construcción del JAR;
* navegador;
* herramientas de desarrollo;
* panel de red;
* códigos de respuesta;
* Git;
* commits funcionales;
* push;
* pull request en borrador;
* GitHub Actions.

## Conocimientos previos necesarios

Antes de estudiar este bloque, el alumnado debería conocer:

* archivos y carpetas;
* extensiones de archivo;
* conceptos básicos de cliente y servidor;
* petición y respuesta HTTP;
* códigos de estado básicos;
* clases y métodos en Java;
* arrays;
* excepciones;
* pruebas sencillas con JUnit;
* estructura básica de HTML;
* reglas CSS sencillas;
* variables y constantes;
* ramas y commits de Git.

No es necesario que pueda producir toda la solución de forma autónoma.

## Nivel pedagógico estimado

### Nivel inicial

El alumnado puede identificar:

* qué archivo contiene HTML;
* qué archivo contiene CSS;
* qué archivo contiene JavaScript;
* qué URL solicita cada recurso;
* qué respuesta devuelve el servidor;
* qué cambio visual produce el CSS;
* qué indicador demuestra que JavaScript se ha ejecutado.

Este nivel puede trabajarse desde el inicio con una demostración guiada.

### Nivel básico

El alumnado puede:

* añadir una clase CSS;
* cambiar una variable de color;
* localizar una petición en el panel de red;
* comprobar el código `200`;
* identificar el `Content-Type`;
* explicar la diferencia entre `/assets/js/app.js` y
  `/web/assets/js/app.js`;
* ejecutar una prueba existente;
* interpretar un fallo sencillo.

### Nivel intermedio

El alumnado puede analizar:

* cómo `StaticResourceHandler` resuelve una URL;
* por qué se devuelve `Optional`;
* por qué los recursos se leen como bytes;
* cómo `dataset.js` produce `data-js`;
* cómo CSS reacciona al atributo;
* por qué `defer` modifica el momento de ejecución;
* por qué el HTML conserva un estado sin JavaScript;
* cómo una prueba HTTP valida las cabeceras.

### Producción autónoma

Diseñar desde cero:

* un cargador de recursos;
* un manejador HTTP;
* una resolución segura de rutas;
* tipos MIME;
* una interfaz responsive;
* mejora progresiva;
* pruebas de integración;

puede ser excesivo durante una fase inicial de 1.º DAM.

Debe plantearse como:

* código proporcionado por el profesorado;
* análisis guiado;
* modificación incremental;
* práctica de diagnóstico;
* actividad de ampliación;
* reto para alumnado avanzado.

## Reparto por módulos

### Programación

Conceptos principales:

* clases y responsabilidades;
* métodos;
* constantes;
* `Optional`;
* arrays de bytes;
* excepciones;
* separación de responsabilidades;
* JavaScript básico;
* variables y constantes;
* DOM;
* `dataset`;
* pruebas automatizadas.

Actividades posibles:

* relacionar una URL con una constante Java;
* añadir una ruta controlada;
* modificar el estado `data-js`;
* crear una prueba para un recurso;
* comprobar un tipo MIME;
* provocar un `404`;
* comparar texto y bytes;
* analizar qué ocurre cuando falta un recurso.

### Entornos de Desarrollo

Conceptos principales:

* estructura Maven;
* recursos;
* classpath;
* empaquetado;
* ejecución desde IntelliJ;
* ejecución mediante Maven;
* navegador como herramienta de diagnóstico;
* panel Network;
* caché del navegador;
* Git;
* commit;
* push;
* pull request;
* CI.

Actividades posibles:

* localizar el recurso dentro de `target/classes`;
* ejecutar `clean` y observar su desaparición;
* ejecutar `verify`;
* abrir directamente CSS y JavaScript;
* revisar cabeceras en el navegador;
* desactivar JavaScript;
* comparar resultado local y CI;
* interpretar una prueba fallida.

### Proyecto Intermodular

Conceptos principales:

* entrega vertical;
* criterio de aceptación;
* diseño responsive;
* accesibilidad;
* mejora progresiva;
* autonomía tecnológica;
* dependencias externas;
* decisión arquitectónica;
* documentación;
* trazabilidad;
* definición de terminado.

Actividades posibles:

* justificar el uso del classpath;
* comparar una solución local con un CDN;
* definir los criterios de aceptación;
* revisar la página en móvil y escritorio;
* documentar una decisión mediante ADR;
* relacionar issue, commit, prueba y PR;
* revisar el impacto de incorporar un framework.

## Conceptos avanzados que deben explicarse especialmente

### Classpath

El alumnado puede pensar que:

```text
src/main/resources/web/index.html
```

es una ruta disponible durante toda la ejecución.

Debe visualizarse el proceso:

```text
src/main/resources/web/index.html
        ↓ Maven
target/classes/web/index.html
        ↓ empaquetado
aulaflow.jar
        ↓ ClassLoader
/web/index.html
```

La ruta del proyecto y la ruta de ejecución no son lo mismo.

### URL pública y recurso interno

Debe distinguirse:

```text
URL solicitada:
/assets/css/app.css

Recurso interno:
/web/assets/css/app.css
```

La URL es parte del contrato HTTP.

La ruta interna es una decisión de organización de la aplicación.

### Bytes y texto

HTML, CSS y JavaScript son texto, pero el servidor envía bytes.

El flujo conceptual es:

```text
recurso
→ bytes
→ respuesta HTTP
→ navegador
→ interpretación según Content-Type
```

Esto prepara al alumnado para recursos binarios futuros.

### Tipo MIME

El navegador no debería adivinar el tipo de archivo.

Debe recibir:

```http
Content-Type: text/css; charset=utf-8
```

o:

```http
Content-Type: text/javascript; charset=utf-8
```

`X-Content-Type-Options: nosniff` refuerza este contrato.

### Mejora progresiva

La interfaz no debe quedar completamente inutilizada si JavaScript
falla.

Estado inicial:

```html
<html lang="ca">
```

Estado enriquecido:

```html
<html lang="ca" data-js="enabled">
```

JavaScript comunica el estado.

CSS decide qué se muestra.

### `defer`

No significa simplemente «cargar más tarde».

Permite descargar el archivo mientras continúa el análisis del HTML y
aplaza su ejecución hasta que el documento ha sido procesado.

### Diseño responsive

Responsive no significa crear una versión móvil separada.

La misma página adapta:

* número de columnas;
* espaciado;
* tamaño tipográfico;
* anchura disponible.

Debe comprobarse reduciendo progresivamente el ancho del navegador.

### Accesibilidad

`aria-live="polite"` indica que un cambio de contenido puede anunciarse
sin interrumpir inmediatamente al usuario.

No debe añadirse ARIA indiscriminadamente.

Primero se utilizará HTML semántico y después atributos accesibles cuando
exista una necesidad concreta.

## Dificultades previsibles

* Confundir URL y ruta de archivo.
* Buscar los recursos mediante `Path.of`.
* Crear CSS dentro de `src/main/java`.
* Pensar que Maven compila CSS o JavaScript como Java.
* Creer que `Content-Type` depende únicamente de la extensión.
* Confundir `dataset.js` con una variable global.
* Pensar que `data-js` existe antes de ejecutar el script.
* Colocar estilos dentro de JavaScript.
* Colocar comportamiento dentro de CSS.
* No entender por qué existen dos mensajes de estado.
* Eliminar el contenido alternativo sin JavaScript.
* Abrir el HTML directamente con `file://`.
* Probar la página sin ejecutar el servidor.
* Interpretar una CI verde como prueba visual del diseño.
* No comprobar pantallas estrechas.
* Añadir un framework para resolver una necesidad pequeña.
* Servir cualquier ruta sin validación.

## Errores frecuentes esperables

### Abrir directamente `index.html`

Abrir:

```text
file:///.../index.html
```

no reproduce el contrato real de AulaFlow.

Consecuencia:

* las URL absolutas `/assets/...` pueden fallar;
* no existe servidor HTTP;
* no existen cabeceras;
* no existe request ID;
* no se prueba la aplicación real.

Debe utilizarse:

```text
http://127.0.0.1:8080/
```

### Utilizar una ruta relativa incorrecta

Código problemático:

```html
<script src="src/main/resources/web/assets/js/app.js"></script>
```

El navegador no conoce la estructura Maven.

Debe solicitar la URL pública:

```html
<script src="/assets/js/app.js"></script>
```

### Ejecutar JavaScript antes de tiempo

Sin una estrategia adecuada, el script puede buscar elementos que aún
no han sido creados.

En este bloque se utiliza:

```html
defer
```

### Generalizar rutas sin seguridad

Conversión peligrosa:

```text
URL solicitada
→ concatenación directa con /web/
```

Podría permitir rutas como:

```text
/assets/../../otro-recurso
```

La generalización se pospone hasta diseñar y probar la validación.

## Secuencia didáctica propuesta

1. Abrir la página sin CSS.
2. Observar la petición de `index.html`.
3. Añadir el enlace a CSS.
4. observar la segunda petición HTTP;
5. identificar `Content-Type`;
6. modificar una variable CSS;
7. reducir el ancho del navegador;
8. añadir el archivo JavaScript;
9. observar la tercera petición;
10. inspeccionar el elemento `<html>`;
11. localizar `data-js="enabled"`;
12. deshabilitar JavaScript;
13. comprobar la mejora progresiva;
14. ejecutar las pruebas específicas;
15. ejecutar Maven `verify`;
16. revisar la CI;
17. relacionar cambios y commits;
18. documentar la decisión mediante ADR.

## Evidencias de aprendizaje

El alumnado debería poder entregar:

* captura del panel Network;
* identificación de las tres peticiones;
* explicación de cada `Content-Type`;
* captura de la página en ancho grande y pequeño;
* explicación de `data-js`;
* resultado con JavaScript habilitado y deshabilitado;
* resultado de las pruebas;
* breve explicación de classpath;
* relación entre URL pública y recurso interno;
* reflexión sobre mejora progresiva.

## Criterio docente

No se exigirá inicialmente que el alumnado diseñe toda la
infraestructura.

Sí deberá poder:

* localizar cada responsabilidad;
* seguir el recorrido de una petición;
* interpretar las pruebas;
* modificar elementos controlados;
* diagnosticar errores frecuentes;
* justificar las decisiones fundamentales.
