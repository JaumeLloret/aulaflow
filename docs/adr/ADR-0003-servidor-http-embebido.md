# ADR-0003: Servidor HTTP embebido del JDK

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

- Estado: aceptada
- Fecha: 2026-07-20
- Proyecto: AulaFlow 1.0

## Contexto

AulaFlow 1.0 necesita exponer una aplicación web y una API HTTP sin
utilizar frameworks completos como Spring Boot.

El proyecto debe permitir que el alumnado observe directamente:

- cómo arranca un servidor;
- cómo se asocia a una dirección y un puerto;
- cómo se registran rutas;
- cómo se procesa una petición;
- cómo se genera una respuesta;
- cómo se controla el ciclo de vida del proceso.

## Opciones consideradas

### Spring Boot

Proporciona servidor, enrutamiento, configuración e inyección de
dependencias, pero ocultaría gran parte de los mecanismos que AulaFlow
1.0 pretende utilizar con fines educativos.

### Implementar HTTP mediante ServerSocket

Ofrecería control completo, pero obligaría a implementar manualmente
demasiados detalles del protocolo HTTP.

### HttpServer del JDK

Proporciona un servidor HTTP embebido sin incorporar un framework
externo y permite trabajar directamente con rutas, peticiones y
respuestas.

## Decisión

AulaFlow 1.0 utilizará:

`com.sun.net.httpserver.HttpServer`

Las peticiones serán procesadas mediante un
`ExecutorService` basado en hilos virtuales.

## Consecuencias positivas

- No se añade un framework web externo.
- El servidor está incluido en el JDK.
- El alumnado puede observar el ciclo HTTP.
- La aplicación puede ejecutarse como un único proceso Java.
- Se puede controlar directamente el arranque y el cierre.

## Consecuencias negativas

- Debemos implementar enrutamiento y respuestas de forma explícita.
- Debemos controlar manualmente errores y recursos.
- La API es más limitada que un framework web completo.
- Será necesario construir utilidades propias para evitar duplicación.

## Límites de la decisión

Esta decisión corresponde a AulaFlow 1.0.

AulaFlow 2.0 podrá utilizar Spring Boot y comparar ambas
aproximaciones.
