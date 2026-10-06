# ADR-0004: Contrato HTTP inicial y estrategia de comprobación

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

- **Estado:** Aceptada
- **Fecha:** 2026-07-22
- **Proyecto:** AulaFlow 1.0

---

## Contexto

AulaFlow necesita establecer un primer contrato HTTP verificable antes de implementar funcionalidades de negocio. El primer endpoint debe permitir comprobar:
- Que el proceso está activo.
- Que el servidor acepta peticiones.
- Que el enrutamiento funciona.
- Que puede generarse una respuesta JSON.
- Que la configuración del entorno está disponible.
- Que el servidor puede probarse automáticamente.
- Que el contrato puede explorarse manualmente con herramientas libres.

El proyecto se desarrolla en un instituto público sin licencias de software de pago. Ninguna parte obligatoria del flujo de trabajo debe depender de funciones comerciales del IDE o de servicios externos en la nube.

## Decisión sobre el endpoint

El primer endpoint será: `GET /api/v1/health`

La respuesta contendrá:
- Estado de la aplicación.
- Versión.
- Entorno.
- Instante UTC.

Los métodos diferentes de `GET` devolverán: `405 Method Not Allowed`.
Las rutas inexistentes devolverán errores JSON con código y mensaje.

## Coincidencia exacta de rutas

Aunque `HttpServer` utiliza coincidencias de contexto por prefijo, los manejadores de AulaFlow comprobarán la ruta exacta cuando sea necesario. De esta manera, `/api/v1/health/details` no será aceptada como si fuera `/api/v1/health`.

## Escritura de respuestas

La escritura de respuestas JSON se centralizará inicialmente en `HttpResponseWriter`. Este componente será responsable de:
- Codificar mediante UTF-8.
- Establecer `Content-Type`.
- Calcular la longitud en bytes.
- Enviar las cabeceras.
- Escribir el cuerpo.
- Cerrar correctamente el flujo de salida.

## Serialización JSON inicial

Durante el bootstrap no se añadirá una biblioteca JSON externa. Los contratos pequeños de salud y error se generarán mediante código controlado y una utilidad de escape de textos. Esta decisión se revisará antes de implementar las APIs de tableros, columnas y tarjetas.

## Comprobación manual

Cartero será el cliente HTTP gráfico oficial de AulaFlow 1.0. Las peticiones se almacenarán dentro del repositorio como archivos `.cartero` en la ruta: `http/cartero`.

Cartero se utilizará para:
- Explorar los contratos.
- Ejecutar peticiones manuales.
- Observar estados.
- Inspeccionar cabeceras.
- Revisar cuerpos JSON.
- Realizar demostraciones docentes.

## Comprobación automática

La verificación automática se realizará mediante:
- JUnit 5.
- `java.net.http.HttpClient`.
- Puertos asignados dinámicamente por el sistema operativo.
- Maven `verify`.

Las pruebas automáticas serán independientes de Cartero y del IDE.

## Gestión de recursos en las pruebas

En Java 26, `HttpClient` se tratará como un recurso cerrable. Las pruebas que creen un cliente HTTP utilizarán `try-with-resources` para garantizar su cierre incluso cuando una aserción o petición falle. Esto evita mantener recursos asociados al cliente durante más tiempo del necesario y elimina advertencias de posibles fugas de recursos.

## Consecuencias positivas

- El ciclo completo de una respuesta HTTP queda visible.
- No se introduce todavía una dependencia JSON externa.
- Los errores tienen un formato consistente.
- El endpoint puede comprobarse mediante peticiones reales.
- Las pruebas automáticas no dependen del IDE.
- Las peticiones manuales pueden versionarse en Git.
- El flujo obligatorio puede realizarse con software gratuito.
- La misma estrategia funciona en Linux, Windows y macOS.

## Consecuencias negativas

- La generación manual de JSON no es adecuada para objetos complejos.
- Debemos garantizar el escape correcto de los textos.
- Debemos mantener los archivos de Cartero actualizados cuando cambie un contrato.
- Existe duplicación temporal entre la versión de Maven y la constante utilizada por la aplicación.

## Deuda técnica registrada

- Automatizar la versión a partir del artefacto Maven.
- Incorporar un identificador de petición.
- Añadir tratamiento centralizado de excepciones.
- Reevaluar el uso de una biblioteca JSON.
- Estudiar variables compartidas para las peticiones de Cartero cuando existan varios entornos.
