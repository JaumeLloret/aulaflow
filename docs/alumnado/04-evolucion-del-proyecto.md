# Cómo se construyó AulaFlow por etapas

Esta secuencia explica cómo se relacionan conceptos de distintos módulos. Los números identifican **incrementos del proyecto original**, no cursos que tengas que realizar ni versiones de código disponibles aquí.

| Etapa | Necesidad resuelta | Conceptos |
| --- | --- | --- |
| Arranque y HTTP, 0.1 | Responder peticiones con un programa configurable | Objetos, configuración, recursos y ciclo de vida |
| Interfaz e idiomas, 0.2 | Ofrecer pantallas en dos idiomas | HTML, CSS, JavaScript y JSON |
| Persistencia, 0.3 | Conservar información fuera de memoria | JDBC, SQLite, migraciones y transacciones |
| Identidad y sesiones, 1.0 | Limitar el acceso a una persona autenticada | Cuenta inicial, contraseña, cookie y sesión |
| Tableros y columnas, 1.1 | Organizar trabajo en estados ordenados | Entidades, relaciones y autorización |
| Tarjetas, 1.2 | Crear, editar y mover tareas | Validación, orden y movimiento |
| Reinicio, 1.3 | Recuperar el trabajo al volver a arrancar | Memoria frente a persistencia |
| Etiquetas y listas, 1.4 | Clasificar y descomponer tareas | Relaciones muchos a muchos y progreso calculado |
| Recorrido completo, 1.5 | Comprobar las piezas juntas | Integración y demostración de un caso real |
| CSV, 2.0 | Intercambiar datos fuera de la aplicación | Contrato, parser e importación atómica |
| Despliegue, 2.1 | Ejecutar en un entorno independiente | JAR, imagen, contenedor, volumen y healthcheck |
| Recuperación, 2.2 | Crear copias consistentes y restaurarlas | Integridad y restauración offline |
| Cierre, 2.3 | Identificar una referencia coherente | Versión 1.0.0, pruebas y reproducción |

El incremento `2.0` de CSV no significa que el JAR actual sea versión 2.0: esta referencia genera `aulaflow-1.0.0.jar`.

## Cómo aprovecharlo en segundo

Al estudiar interfaces móviles, puedes observar qué recibe un servidor, cómo responde y por qué una sesión o una escritura necesitan protección. Puedes distinguir la API de la interfaz HTML que utiliza la aplicación.

En un proyecto intermodular puedes seguir cómo una necesidad termina en reglas, datos, pantallas, pruebas y despliegue. La referencia ayuda a comparar decisiones y justificar las tuyas; las instrucciones del módulo determinan qué debes entregar.

Aquí no hay una aplicación Android ni Flutter: la aplicación actual es web. La colaboración multiusuario y PostgreSQL serían cambios futuros.

## Lecturas por necesidad

Empieza con el [mapa del código](03-mapa-del-codigo.md). Consulta después el [índice técnico](../README.md) para profundizar en HTTP, SQLite, sesiones, tarjetas, CSV y contenedores.

Los documentos técnicos conservan el contexto de decisiones anteriores. Las alternativas descartadas y ejemplos históricos explican cómo se llegó al resultado. Los pasos vigentes para ejecutar el proyecto son [primer arranque](01-primer-arranque.md) y [reproducción y despliegue](../release/1.0.0.md).
