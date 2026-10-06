# OBS-0002: Servidor HTTP y ciclo de vida

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

- Fecha: 2026-07-20
- Incremento: 0.1.C

## Conceptos que han aparecido

- Cliente y servidor.
- Dirección IP.
- Puerto.
- Protocolo HTTP.
- Binding.
- Backlog.
- Socket de escucha.
- Estado de un servidor.
- ExecutorService.
- Hilos virtuales.
- IOException.
- BindException.
- AutoCloseable.
- Try-with-resources.
- Shutdown hook.
- Cliente HTTP.
- Prueba de integración.

## Dificultades previsibles

- Confundir dirección, puerto y ruta.
- Pensar que el puerto 0 es un puerto normal del servidor.
- Confundir HttpServer con HttpClient.
- No cerrar el servidor después de una prueba.
- Ejecutar dos instancias sobre el mismo puerto.
- No comprender por qué el proceso permanece activo.
- Confundir el hilo de escucha con los hilos que procesan peticiones.
- Interpretar el 404 como que el servidor no funciona.
- Intentar reiniciar una instancia ya detenida.

## Actividades futuras

- Registrar manualmente una primera ruta.
- Comparar un servidor sin contextos con uno que tenga `/health`.
- Provocar una colisión de puertos.
- Analizar una petición desde el depurador.
- Cambiar el host y observar el efecto.
- Comparar el ejecutor predeterminado con hilos virtuales.

## Evidencias evaluables

- Servidor correctamente encapsulado.
- Cierre correcto del recurso.
- Prueba con puerto dinámico.
- Explicación del recorrido cliente-servidor.
- Diagnóstico de una BindException.

## Observación surgida durante el desarrollo

IntelliJ IDEA detectó que `HttpClient.newHttpClient()` se utilizaba sin
un bloque `try-with-resources`.

Desde Java 21, `HttpClient` implementa `AutoCloseable` y puede gestionar
recursos internos como conexiones y tareas asociadas. Aunque el JDK
puede recuperar posteriormente estos recursos cuando el cliente deja de
ser alcanzable, las pruebas deben realizar un cierre determinista.

Se modificó el test para utilizar un bloque `try-with-resources`
específico para `HttpClient`.

## Aprendizaje docente

El alumnado debe aprender a:

- reconocer tipos que implementan `AutoCloseable`;
- distinguir entre liberación eventual por el recolector de basura y
  cierre explícito;
- limitar el alcance de los recursos;
- utilizar `try-with-resources`;
- no ignorar automáticamente los warnings del IDE;
- analizar si un warning representa un problema real o un falso positivo.
