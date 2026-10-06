# OBS-0007: Seguridad, caché y logging de recursos web

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Fecha: 2026-07-30
* Proyecto: AulaFlow 1.0
* Incremento: 0.2

## Finalidad

Servir un archivo correctamente no completa el trabajo de un servidor
web.

También debe decidirse:

* qué rutas pueden solicitarse;
* qué información debe permanecer inaccesible;
* qué ocurre con una ruta maliciosa;
* si el navegador puede reutilizar respuestas antiguas;
* qué información se registra;
* qué información no debe registrarse;
* cómo se diagnostica un fallo.

Estos aspectos son transversales y pueden quedar ocultos cuando se
utiliza un framework que los resuelve automáticamente.

## Lista cerrada de recursos

AulaFlow utiliza inicialmente asociaciones explícitas:

```text
URL pública → recurso interno
```

Esto significa que una petición solo se acepta cuando su ruta existe
exactamente dentro del mapa de recursos permitidos.

No se realiza:

```text
URL recibida
→ concatenación directa
→ classpath
```

Esta restricción reduce la superficie de ataque.

## Path traversal

Un intento de path traversal pretende abandonar el directorio permitido
mediante segmentos como:

```text
..
```

Ejemplo literal:

```text
/assets/../web/index.html
```

Ejemplo codificado:

```text
/assets/%2e%2e/web/index.html
```

El alumnado debe comprender que codificar caracteres no cambia la
intención de la petición.

La aplicación debe rechazar ambas variantes sin revelar:

* si el archivo interno existe;
* dónde está almacenado;
* su contenido;
* una traza de ejecución.

## Defensa por diseño

La implementación actual no necesita buscar expresamente `..` porque no
convierte rutas arbitrarias.

La defensa se produce por diseño:

```text
ruta no incluida
→ no existe asociación
→ 404 controlado
```

Esto no significa que pueda ignorarse el path traversal.

Las pruebas se mantienen porque una futura generalización podría
introducir accidentalmente la vulnerabilidad.

## Política de caché

AulaFlow devuelve:

```http
Cache-Control: no-store
```

El objetivo actual no es optimizar descargas, sino asegurar que el
navegador solicita la versión vigente.

Esto evita situaciones docentes frecuentes:

* modificar CSS y seguir viendo el diseño anterior;
* corregir JavaScript y ejecutar una copia antigua;
* cambiar una traducción y conservar el catálogo anterior;
* pensar que el servidor no funciona cuando el problema es la caché.

Debe distinguirse entre:

```text
caché HTTP
localStorage
memoria de JavaScript
base de datos
```

`localStorage` conserva deliberadamente la preferencia de idioma.

`Cache-Control` afecta a la reutilización de respuestas HTTP.

Son mecanismos diferentes.

## Logging

La trazabilidad permite relacionar:

```text
petición
respuesta
duración
request ID
```

Ejemplo:

```text
GET /assets/css/app.css
→ 200
→ requestId
```

También permite observar:

```text
GET /assets/../web/index.html
→ 404
→ requestId
```

El log debe ayudar a diagnosticar sin exponer información innecesaria.

## Información que sí debe registrarse

* método;
* ruta pública;
* estado;
* duración;
* request ID;
* excepción técnica cuando corresponda en el servidor.

## Información que no debe registrarse

* contenido completo del archivo;
* valores privados almacenados por el usuario;
* contraseñas o tokens;
* traducciones completas;
* rutas internas innecesarias;
* cuerpos de petición sin justificación.

## Reparto por módulos

### Programación

* mapas;
* `Optional`;
* condiciones de acceso;
* separación entre URL y recurso;
* pruebas de integración;
* códigos de estado;
* diseño seguro por defecto.

### Entornos de Desarrollo

* cabeceras HTTP;
* caché del navegador;
* panel Network;
* logs;
* trazabilidad;
* diagnóstico;
* diferencias entre ruta literal y codificada;
* Maven y CI.

### Proyecto Intermodular

* requisito no funcional de seguridad;
* superficie de ataque;
* privacidad de logs;
* criterios de aceptación;
* gestión de riesgos;
* decisiones de rendimiento;
* documentación arquitectónica.

## Errores previsibles

* concatenar la URL con una ruta interna;
* normalizar después de haber accedido al recurso;
* comprobar únicamente el caso literal;
* devolver el archivo interno con un estado incorrecto;
* incluir una traza en la respuesta;
* registrar el cuerpo completo;
* confundir `no-store` con borrar `localStorage`;
* añadir caché larga sin versionar los nombres;
* considerar que un `404` basta sin revisar el cuerpo.

## Secuencia didáctica propuesta

1. solicitar un recurso permitido;
2. observar el `200`;
3. solicitar una ruta interna;
4. observar el `404`;
5. probar `..`;
6. probar `%2e%2e`;
7. relacionar la respuesta con el log;
8. localizar el request ID;
9. comprobar `Cache-Control`;
10. modificar CSS y recargar;
11. comparar caché HTTP y `localStorage`;
12. ejecutar las pruebas;
13. estudiar qué ocurriría al generalizar `/assets/`.

## Criterio docente

El alumnado no necesita diseñar inicialmente un servidor de archivos
completo.

Sí debe poder:

* reconocer una ruta peligrosa;
* explicar por qué la lista cerrada la rechaza;
* interpretar la respuesta;
* localizar la petición en los logs;
* diferenciar caché y persistencia;
* justificar qué datos no deben registrarse.
