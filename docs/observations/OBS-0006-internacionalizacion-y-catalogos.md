# OBS-0006: Internacionalización y contratos de catálogos

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Fecha: 2026-07-30
* Proyecto: AulaFlow 1.0
* Incremento: 0.2

## Finalidad de esta observación

La internacionalización suele presentarse como un selector que cambia
textos, pero contiene varios problemas distintos:

* separación entre estructura y contenido;
* identificación estable de cada texto;
* formato JSON;
* carga asíncrona de recursos;
* igualdad entre idiomas;
* idioma de reserva;
* accesibilidad;
* persistencia de preferencias;
* pruebas automatizadas.

El alumnado no debe limitarse a copiar dos archivos JSON.

Debe comprender qué contrato existe entre:

```text
HTML
catálogo
JavaScript
servidor HTTP
pruebas
```

## Problema trabajado

AulaFlow dispone inicialmente de textos escritos directamente en el HTML.

Para incorporar castellano sin duplicar la página se crean catálogos:

```text
ca.json
es.json
```

Ambos deben describir los mismos conceptos mediante las mismas claves.

Ejemplo:

```text
home.welcome.title
```

Valor valenciano:

```text
Benvingut a AulaFlow
```

Valor castellano:

```text
Bienvenido a AulaFlow
```

## Conceptos introducidos

### Internacionalización

* internacionalización;
* localización;
* idioma;
* código de idioma;
* idioma predeterminado;
* idioma de reserva;
* preferencia del usuario;
* catálogo de traducciones;
* clave de traducción;
* valor traducido;
* cobertura de un catálogo.

### JSON

* objeto;
* propiedad;
* clave;
* valor;
* texto;
* comas;
* llaves;
* caracteres escapados;
* UTF-8;
* formato válido;
* duplicidad de claves.

### Java

* recursos del classpath;
* `InputStream`;
* `StandardCharsets.UTF_8`;
* listas;
* conjuntos;
* `HashSet`;
* `TreeSet`;
* expresiones regulares;
* `Pattern`;
* `Matcher`;
* métodos auxiliares;
* pruebas de contrato.

### HTTP

* recurso JSON;
* URL pública;
* tipo MIME;
* cabeceras;
* código `200`;
* trazabilidad;
* recurso inexistente;
* prueba de integración.

## Conocimientos previos

Antes de estudiar el bloque en profundidad, el alumnado debería conocer:

* variables;
* cadenas de texto;
* arrays o colecciones básicas;
* clases y métodos;
* archivos y carpetas;
* HTML básico;
* JavaScript básico;
* petición y respuesta HTTP;
* pruebas con JUnit;
* estructura general de JSON;
* Git y commits.

La expresión regular utilizada para validar el catálogo no debe darse por
conocida.

## Nivel pedagógico

### Comprensión inicial

El alumnado puede comprender que:

* existe un archivo por idioma;
* ambos archivos utilizan las mismas claves;
* los valores cambian según el idioma;
* el navegador descargará un catálogo;
* el servidor devuelve JSON;
* una prueba detecta un idioma incompleto.

### Comprensión básica

El alumnado puede:

* añadir una traducción a los dos catálogos;
* localizar una clave desde el HTML;
* identificar una coma incorrecta;
* comprobar el `Content-Type`;
* abrir cada catálogo en el navegador;
* ejecutar la prueba de contrato;
* interpretar una diferencia entre conjuntos.

### Comprensión intermedia

El alumnado puede analizar:

* por qué las claves son técnicas;
* por qué no se duplica el HTML;
* por qué se utiliza un objeto plano;
* cómo se detectan claves duplicadas;
* cómo `Set` elimina duplicados;
* por qué se comparan conjuntos y no listas;
* qué limitaciones tiene la validación actual;
* por qué no se ha añadido todavía una biblioteca JSON.

### Producción autónoma

Diseñar desde cero:

* un formato de catálogos;
* una validación de JSON;
* una estrategia de reserva;
* la aplicación dinámica de traducciones;
* persistencia de idioma;
* pruebas de todos los fallos;

puede superar el nivel inicial de 1.º DAM.

Debe abordarse mediante pequeños incrementos guiados.

## Reparto por módulos

### Programación

Contenidos principales:

* cadenas;
* colecciones;
* listas y conjuntos;
* igualdad;
* expresiones regulares;
* lectura de recursos;
* cierre de flujos;
* excepciones;
* métodos auxiliares;
* pruebas;
* JavaScript y DOM;
* carga de datos;
* promesas y `fetch`, en el siguiente bloque.

Actividades posibles:

* detectar una clave duplicada;
* comparar dos conjuntos;
* validar una clave;
* añadir una traducción;
* provocar un fallo de cobertura;
* explicar por qué una lista conserva duplicados;
* comparar `HashSet` y `TreeSet`.

### Entornos de Desarrollo

Contenidos principales:

* archivos JSON;
* validación desde IntelliJ;
* recursos Maven;
* classpath;
* codificación UTF-8;
* pruebas específicas;
* Maven `verify`;
* CI;
* inspección de respuestas HTTP;
* commits separados para funcionalidad y pruebas.

Actividades posibles:

* introducir deliberadamente JSON inválido;
* observar el fallo local;
* corregirlo antes del commit;
* revisar el catálogo dentro de `target/classes`;
* comprobar la respuesta en el panel Network;
* comparar la ejecución local y GitHub Actions.

### Proyecto Intermodular

Contenidos principales:

* requisito funcional;
* internacionalización;
* accesibilidad;
* idioma de reserva;
* separación de responsabilidades;
* criterio de aceptación;
* decisión arquitectónica;
* contrato entre componentes;
* definición de terminado.

Actividades posibles:

* definir los idiomas soportados;
* justificar el idioma predeterminado;
* redactar criterios de aceptación;
* revisar la cobertura de traducciones;
* analizar el coste de añadir un idioma;
* documentar la decisión mediante ADR.

## Conceptos que requieren explicación especial

### Internacionalización y traducción

Traducir un texto no equivale a internacionalizar una aplicación.

La internacionalización prepara la estructura para soportar idiomas y
formatos regionales.

La traducción concreta forma parte de la localización.

### Clave y valor

En:

```json
{
  "home.status.title": "Estat del projecte"
}
```

la clave:

```text
home.status.title
```

es un identificador técnico.

El valor:

```text
Estat del projecte
```

es contenido localizado.

### Igualdad de claves

No basta con contar entradas.

Dos catálogos pueden tener 15 entradas y representar conceptos
diferentes.

Debe comprobarse la igualdad de los conjuntos completos.

### Claves duplicadas

Aunque algunos analizadores acepten una clave repetida y conserven el
último valor, el resultado es ambiguo.

AulaFlow considera una clave duplicada un error.

### Expresiones regulares

La expresión regular no debe presentarse como una fórmula mágica.

Primero debe explicarse con ejemplos válidos e inválidos.

Su objetivo actual es validar un formato muy limitado, no interpretar
cualquier documento JSON.

### Idioma de reserva

Un idioma de reserva evita que la interfaz quede vacía cuando:

* no existe el catálogo solicitado;
* falla la petición;
* falta una traducción;
* la preferencia es inválida.

El HTML inicial en valenciano ya proporciona una primera forma de
degradación segura.

## Dificultades previsibles

* Confundir clave y traducción.
* Traducir también las claves.
* Añadir una clave a un único catálogo.
* Dejar una coma después de la última entrada.
* Eliminar una coma intermedia.
* Repetir una clave.
* Utilizar comillas tipográficas.
* Guardar el archivo con codificación incorrecta.
* Confundir valenciano con el identificador técnico del archivo.
* Pensar que el número de entradas garantiza igualdad.
* Crear una estructura anidada en un único idioma.
* Añadir una dependencia sin justificarla.
* No comprender qué limita la expresión regular.
* Pensar que la prueba sustituye una futura librería JSON.

## Secuencia didáctica propuesta

1. Identificar los textos existentes en el HTML.
2. Asignar una clave técnica a cada texto.
3. Crear el catálogo valenciano.
4. Copiar únicamente las claves al catálogo castellano.
5. Traducir los valores.
6. Servir ambos archivos mediante HTTP.
7. comprobar el tipo MIME;
8. crear una prueba por catálogo;
9. comparar las claves;
10. introducir deliberadamente una diferencia;
11. observar el fallo;
12. corregirlo;
13. ejecutar `verify`;
14. revisar la CI;
15. implementar posteriormente el selector.

## Evidencias de aprendizaje

El alumnado debería poder:

* explicar la diferencia entre clave y valor;
* localizar los dos catálogos;
* identificar su URL pública;
* demostrar que ambos tienen las mismas claves;
* interpretar una prueba fallida;
* justificar el formato plano;
* explicar por qué no se duplica el HTML;
* identificar las limitaciones del validador;
* describir para qué sirve un idioma de reserva.

## Criterio docente

No se exigirá inicialmente que el alumnado diseñe un sistema completo de
internacionalización.

Sí deberá poder:

* modificar los catálogos sin romper el contrato;
* ejecutar y entender las pruebas;
* seguir el recorrido HTTP del archivo JSON;
* justificar las claves estables;
* distinguir contenido, estructura y comportamiento.

## Ampliación pedagógica: selector, persistencia y reserva

La incorporación del selector añade nuevos conceptos que deben
introducirse progresivamente.

### Carga asíncrona

El alumnado debe comprender la secuencia:

```text
selección
→ petición HTTP
→ espera
→ respuesta
→ interpretación de JSON
→ actualización del DOM
```

`async` y `await` no deben presentarse únicamente como palabras que
permiten utilizar `fetch`.

Deben relacionarse con una operación cuyo resultado no está disponible
inmediatamente.

### Estado temporal del selector

Durante la carga, el selector se desactiva y recibe:

```text
aria-busy="true"
```

Esto evita cambios simultáneos y comunica que la operación está en
curso.

Al terminar se restaura su estado.

### Persistencia local

`localStorage`:

* pertenece al navegador;
* almacena texto;
* no forma parte de la base de datos;
* no identifica a una persona;
* no se comparte automáticamente entre dispositivos;
* puede no estar disponible.

Debe diferenciarse de:

* variables JavaScript;
* cookies;
* sesiones del servidor;
* archivos;
* bases de datos.

### Recuperación ante fallos

La estrategia de reserva permite estudiar que un error no siempre debe
dejar la aplicación inutilizable.

Secuencia didáctica:

1. cargar castellano correctamente;
2. recargar y observar la persistencia;
3. introducir una preferencia inválida;
4. comprobar que se descarta;
5. simular ausencia de red;
6. observar la conservación del contenido;
7. recuperar la conexión;
8. comprobar que el selector vuelve a funcionar.

### Límite de las pruebas actuales

Las pruebas Java verifican contratos de archivos y estructura, pero no
ejecutan un navegador.

Debe explicarse la diferencia entre:

```text
prueba automatizada del servidor
prueba estática de recursos
prueba manual del navegador
```

Una CI verde no demuestra por sí sola que el cambio visual y la
persistencia funcionen en todos los navegadores.
