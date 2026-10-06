# OBS-0001: Arranque y configuración de una aplicación Java

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

- Fecha: 2026-07-20
- Incremento: 0.1.B

## Conceptos que han aparecido

- Punto de entrada de una aplicación Java.
- Método `main`.
- Métodos y atributos estáticos.
- Constantes.
- Encapsulación.
- Constructor privado.
- Objetos inmutables.
- Variables de entorno.
- Conversión de texto a entero.
- Excepciones.
- Validación.
- Mapas.
- Valores predeterminados.
- Pruebas unitarias.
- Inyección de datos para facilitar pruebas.

## Conocimientos previos necesarios

- Crear paquetes y clases desde IntelliJ IDEA.
- Diferencia básica entre clase, objeto y método.
- Tipos `String` e `int`.
- Condicionales.
- Métodos.
- Concepto básico de excepción.
- Uso inicial de JUnit.

## Posibles dificultades del alumnado

- Confundir una variable de entorno con una variable Java.
- Pensar que `System.getenv()` modifica el sistema.
- No entender por qué el puerto llega como texto.
- Confundir `NumberFormatException` con `IllegalArgumentException`.
- No comprender por qué se utiliza un método `from`.
- Intentar acceder directamente a atributos privados.
- Colocar la prueba en `src/main/java`.
- Importar JUnit 4 en lugar de JUnit Jupiter.
- Ejecutar únicamente desde IntelliJ sin comprobar Maven.
- Dejar una configuración de ejecución con un puerto incorrecto.

## Actividades didácticas posibles

- Añadir una nueva variable con valor predeterminado.
- Probar valores vacíos y valores con espacios.
- Cambiar el puerto desde IntelliJ sin modificar el código.
- Crear una prueba que compruebe el puerto mínimo.
- Mejorar los mensajes de error.
- Comparar código configurado externamente con valores hardcodeados.

## Evidencias evaluables

- Clase de configuración correctamente encapsulada.
- Validación del puerto.
- Pruebas de valores predeterminados.
- Pruebas de valores inválidos.
- Explicación del recorrido desde `main` hasta la configuración.
