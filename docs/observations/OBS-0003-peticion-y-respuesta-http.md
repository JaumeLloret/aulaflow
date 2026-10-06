# OBS-0003: Petición, respuesta y comprobación HTTP

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

- Fecha: 2026-07-22
- Incremento: 0.1.D

## Conceptos técnicos que han aparecido

- Contexto HTTP.
- Manejador HTTP.
- Ruta.
- URL.
- Query string.
- Coincidencia por prefijo.
- Método HTTP.
- GET y POST.
- Código 200.
- Código 404.
- Código 405.
- Cabecera `Content-Type`.
- Cabecera `Cache-Control`.
- Cabecera `Allow`.
- Codificación UTF-8.
- Longitud en bytes.
- Flujo de salida.
- JSON.
- Escape de caracteres.
- Reloj inyectable.
- Prueba de integración HTTP.
- Cliente HTTP gráfico.
- Cliente HTTP programático.
- Gestión y cierre de recursos.
- `AutoCloseable`.
- `try-with-resources`.

## Herramientas utilizadas

### IntelliJ IDEA Community

Se utiliza para:

- editar el código;
- ejecutar la aplicación;
- ejecutar pruebas;
- depurar;
- trabajar con Maven;
- trabajar con Git.

No se presupone la disponibilidad de funciones comerciales del IDE.

### Cartero

Se utiliza para:

- crear peticiones manuales;
- observar respuestas;
- inspeccionar cabeceras;
- comparar métodos y rutas;
- guardar peticiones dentro del repositorio.

### JUnit y HttpClient

Se utilizan para:

- automatizar las comprobaciones;
- levantar servidores reales;
- enviar peticiones reales;
- verificar estados, cabeceras y cuerpos;
- repetir las pruebas en cualquier equipo;
- ejecutar las comprobaciones en integración continua.

## Reparto inicial por módulos

### Programación

- Interfaces.
- Implementación de `HttpHandler`.
- Objetos y métodos.
- Excepciones.
- Flujo de salida.
- Arrays de bytes.
- Codificación.
- `Clock`.
- `AutoCloseable`.
- `try-with-resources`.
- Pruebas JUnit.

### Entornos de Desarrollo

- IntelliJ IDEA.
- Maven.
- Ejecución de pruebas.
- Advertencias del IDE.
- Inspección de código.
- Git.
- Cartero.
- Contratos HTTP versionados.
- Diagnóstico de errores.
- Verificación reproducible.

### Proyecto Intermodular

- Contrato `/api/v1/health`.
- Criterios de aceptación.
- Decisiones arquitectónicas.
- Evidencias.
- Pruebas manuales y automáticas.
- Registro de deuda técnica.
- Coordinación entre componentes.

## Dificultades previsibles

- Confundir ruta con URL completa.
- Confundir método HTTP con método Java.
- Pensar que un `404` significa necesariamente que el servidor no
  funciona.
- Utilizar `404` cuando el problema real es `405`.
- Olvidar la cabecera `Allow`.
- Calcular la longitud con `String.length()`.
- No cerrar el `OutputStream`.
- Generar JSON concatenando texto sin escapar.
- No entender la coincidencia por prefijo de `HttpServer`.
- Escribir pruebas contra el puerto fijo `8080`.
- Usar la hora real en una prueba determinista.
- Confundir una prueba manual con una prueba automática.
- Pensar que Cartero sustituye a JUnit.
- Ignorar una advertencia de recursos porque el test termina en verde.
- Crear un `HttpClient` sin cerrarlo.
- Asumir que una función del IDE está disponible en todas sus ediciones.

## Incidencia pedagógica detectada

IntelliJ mostró la advertencia:

`HttpClient used without try-with-resources statement`

Aunque la prueba funcionaba, la advertencia indicaba que el cliente
HTTP podía permanecer abierto más tiempo del necesario.

La solución adoptada fue incluir `HttpClient` dentro de un bloque
`try-with-resources`.

Esta incidencia es útil para explicar que:

- que un programa funcione no significa que esté correctamente
  gestionado;
- las inspecciones del IDE pueden detectar problemas que no hacen
  fallar inmediatamente una prueba;
- una advertencia debe comprenderse antes de silenciarse;
- Clean Code también incluye la correcta liberación de recursos.

## Actividades posibles

- Ejecutar las cinco peticiones desde Cartero.
- Clasificar cada resultado como 200, 404 o 405.
- Localizar la cabecera `Allow`.
- Introducir una query string y explicar por qué la ruta sigue
  coincidiendo.
- Introducir una comilla en `AULAFLOW_ENV`.
- Eliminar el control de ruta exacta y observar el fallo.
- Cambiar la codificación y probar caracteres acentuados.
- Crear una nueva respuesta de error.
- Ejecutar una petición manual y localizar la prueba automática
  equivalente.
- Eliminar temporalmente el cierre de `HttpClient` y analizar la
  advertencia del IDE.
- Provocar el fallo de una aserción y comprobar que los recursos se
  cierran igualmente.

## Evidencias evaluables

- Endpoint `GET` correctamente implementado.
- Respuesta JSON válida.
- Códigos HTTP correctos.
- Cabeceras correctas.
- Pruebas con servidor y cliente reales.
- Archivos `.cartero` correctamente organizados.
- Explicación del recorrido de la petición.
- Diferenciación entre prueba manual y automática.
- Corrección de la advertencia sobre `HttpClient`.
