# ADR-0007: Catálogos JSON de internacionalización

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Estado: aceptada
* Fecha: 2026-07-30
* Proyecto: AulaFlow 1.0
* Incremento: 0.2

## Contexto

AulaFlow debe ofrecer inicialmente su interfaz en:

* valenciano;
* castellano.

Los textos de la página no deben permanecer duplicados dentro del código
JavaScript ni mantenerse mediante dos archivos HTML diferentes.

La aplicación utiliza:

* HTML estático;
* CSS nativo;
* JavaScript nativo;
* servidor HTTP embebido de Java;
* recursos almacenados en el classpath;
* ninguna biblioteca externa de internacionalización.

La solución debe permitir añadir otros idiomas posteriormente sin
modificar la estructura del documento HTML.

## Problema

Es necesario decidir:

* dónde almacenar las traducciones;
* qué formato utilizar;
* cómo identificar cada texto;
* cómo garantizar que todos los idiomas estén completos;
* cómo servir los catálogos mediante HTTP;
* si debe incorporarse una biblioteca JSON;
* qué responsabilidad tendrá el servidor;
* qué responsabilidad tendrá el navegador.

## Objetivos

La solución debe:

* disponer de valenciano y castellano;
* utilizar UTF-8;
* almacenar los textos fuera del JavaScript;
* evitar duplicar el documento HTML;
* utilizar identificadores técnicos estables;
* garantizar que los idiomas tengan las mismas claves;
* permitir servir los catálogos desde el classpath;
* utilizar el tipo MIME correcto;
* conservar trazabilidad y logging;
* evitar dependencias externas innecesarias;
* permitir incorporar un selector de idioma posteriormente;
* incluir un idioma de reserva.

## Opciones consideradas

### Mantener un archivo HTML por idioma

Podrían crearse archivos como:

```text
index-ca.html
index-es.html
```

Esta opción se descarta porque:

* duplicaría la estructura HTML;
* obligaría a modificar varios archivos ante cada cambio;
* aumentaría el riesgo de diferencias entre idiomas;
* mezclaría contenido traducible y estructura;
* dificultaría añadir nuevos idiomas.

### Incluir todas las traducciones dentro de JavaScript

El archivo `app.js` podría contener:

```javascript
const translations = {
    ca: {
        // ...
    },
    es: {
        // ...
    }
};
```

Esta opción se descarta porque:

* haría crecer el archivo JavaScript;
* mezclaría comportamiento y contenido;
* obligaría a descargar todos los idiomas;
* dificultaría revisar las traducciones de forma independiente;
* reduciría la reutilización de los catálogos.

### Utilizar una biblioteca de internacionalización

Podría incorporarse una biblioteca especializada.

Esta opción se pospone porque:

* el alcance actual es reducido;
* existen solo dos idiomas;
* los catálogos son planos;
* añadiría dependencias y configuración;
* ocultaría conceptos que tienen valor docente;
* todavía no se necesitan plurales, interpolaciones complejas o formatos
  regionales avanzados.

La decisión podrá revisarse si crecen las necesidades del producto.

### Utilizar catálogos JSON externos

Cada idioma utiliza un archivo JSON independiente:

```text
ca.json
es.json
```

El navegador podrá solicitar únicamente el idioma necesario mediante
HTTP.

Esta opción separa:

```text
estructura HTML
comportamiento JavaScript
presentación CSS
contenido traducible JSON
```

## Decisión

AulaFlow utilizará catálogos JSON independientes almacenados en:

```text
src/main/resources/web/assets/i18n
```

Estructura inicial:

```text
i18n/
├── ca.json
└── es.json
```

Las rutas públicas serán:

```http
GET /assets/i18n/ca.json
GET /assets/i18n/es.json
```

Los archivos se servirán mediante:

```text
application/json; charset=utf-8
```

y conservarán:

```text
Cache-Control: no-store
X-Content-Type-Options: nosniff
X-Request-Id
```

## Identificadores de traducción

Los catálogos utilizarán claves técnicas estables:

```text
app.name
app.tagline
home.welcome.title
home.status.description
language.selector.label
```

Las claves:

* estarán escritas en inglés técnico;
* utilizarán minúsculas;
* utilizarán puntos para expresar agrupaciones;
* no contendrán el texto traducido;
* no dependerán del idioma del catálogo.

Ejemplo:

```json
{
  "home.welcome.title": "Benvingut a AulaFlow"
}
```

y:

```json
{
  "home.welcome.title": "Bienvenido a AulaFlow"
}
```

La clave identifica el concepto.

El valor contiene la traducción.

## Estructura plana

Los catálogos utilizarán inicialmente un objeto JSON plano.

Se acepta:

```json
{
  "home.welcome.title": "Benvingut a AulaFlow",
  "home.welcome.description": "La base està en funcionament."
}
```

No se utilizará todavía una estructura anidada como:

```json
{
  "home": {
    "welcome": {
      "title": "..."
    }
  }
}
```

La estructura plana se elige porque:

* simplifica la búsqueda mediante una clave;
* evita recorrer objetos anidados;
* facilita las primeras pruebas;
* hace más visible la igualdad de claves;
* resulta suficiente para el alcance inicial.

Esta decisión podrá revisarse si los catálogos crecen considerablemente.

## Contrato de igualdad

Todos los catálogos deberán contener exactamente el mismo conjunto de
claves.

No es suficiente que tengan el mismo número de entradas.

Debe cumplirse:

```text
claves de ca.json = claves de es.json
```

Una prueba automatizada comprobará:

* existencia de ambos recursos;
* formato plano soportado;
* formato de las claves;
* colocación de las comas;
* ausencia de claves duplicadas;
* igualdad exacta de claves.

Una traducción nueva no estará completa hasta incorporarse en todos los
idiomas soportados.

## Dependencias JSON

No se añadirá todavía una biblioteca JSON al proyecto.

La prueba actual valida únicamente el subconjunto controlado que utiliza
AulaFlow:

```text
objeto JSON plano
clave de texto
valor de texto
```

La validación no pretende convertirse en un parser JSON general.

Se incorporará una biblioteca cuando el código de producción necesite:

* deserializar objetos complejos;
* trabajar con arrays;
* procesar números o valores booleanos;
* generar JSON estructurado;
* gestionar escapes complejos;
* validar esquemas;
* reutilizar modelos JSON en varias funcionalidades.

## Responsabilidad del servidor

El servidor será responsable de:

* localizar el catálogo;
* cargarlo desde el classpath;
* devolver sus bytes;
* establecer el tipo de contenido;
* aplicar las cabeceras comunes;
* devolver errores controlados.

El servidor no traducirá el HTML en esta fase.

## Responsabilidad del navegador

El JavaScript será responsable posteriormente de:

* determinar el idioma solicitado;
* descargar el catálogo;
* localizar elementos con `data-i18n`;
* sustituir su contenido;
* actualizar los metadatos traducibles;
* recordar la preferencia del usuario;
* aplicar el idioma de reserva cuando sea necesario.

Estas responsabilidades se implementarán en el siguiente subincremento.

## Idioma inicial y reserva

El contenido HTML inicial permanece en valenciano.

El valenciano será inicialmente el idioma de reserva:

```text
ca
```

Esto permite que la página conserve contenido comprensible incluso si:

* JavaScript está desactivado;
* el catálogo no puede descargarse;
* la preferencia almacenada es inválida;
* falta una traducción.

El comportamiento definitivo se implementará y probará junto al selector
de idioma.

## Consecuencias positivas

* La estructura HTML no se duplica.
* Los textos quedan separados del comportamiento.
* Cada idioma puede revisarse independientemente.
* Se pueden añadir nuevos idiomas.
* Los catálogos se descargan mediante HTTP.
* Las claves técnicas permanecen estables.
* Las pruebas detectan idiomas incompletos.
* No se incorporan dependencias innecesarias.
* La página conserva una versión funcional sin JavaScript.
* La solución permite enseñar el proceso de internacionalización.

## Consecuencias negativas

* El JavaScript deberá descargar y aplicar el catálogo.
* Las claves deben mantenerse cuidadosamente.
* El formato plano puede resultar largo cuando crezcan los textos.
* La prueba actual no es un parser JSON completo.
* No existen todavía plurales ni interpolaciones.
* Los metadatos requieren un tratamiento diferente del texto visible.
* Una clave nueva obliga a modificar todos los catálogos.

## Riesgos

* Añadir una clave a un único idioma.
* Utilizar claves diferentes para el mismo concepto.
* Duplicar claves dentro de un catálogo.
* Introducir JSON inválido.
* utilizar texto traducido como identificador;
* dejar textos visibles sin `data-i18n`;
* aplicar una traducción mediante `innerHTML` sin necesidad;
* no disponer de idioma de reserva;
* confiar en una preferencia de idioma no validada;
* confundir el código de idioma con el texto mostrado al usuario.

## Criterios de revisión

Esta decisión se revisará cuando sea necesario incorporar:

* más idiomas;
* pluralización;
* parámetros dentro de textos;
* números y fechas regionales;
* traducciones cargadas desde una base de datos;
* validación mediante JSON Schema;
* edición de traducciones desde la aplicación;
* traducciones proporcionadas por terceros;
* una biblioteca especializada de internacionalización.

## Estado de implementación del selector

La decisión se ha materializado mediante un selector HTML accesible:

```html
<select
        id="language-selector"
        name="language"
        disabled
>
```

El selector comienza desactivado.

JavaScript solo lo habilita después de cargar correctamente el catálogo
inicial y registrar el manejador del evento `change`.

Los idiomas soportados son:

```text
ca
es
```

Los valores del selector se validan antes de construir la URL del
catálogo.

## Aplicación de traducciones

JavaScript localiza los elementos mediante:

```text
data-i18n
```

Los textos ordinarios se actualizan mediante:

```javascript
textContent
```

No se utiliza `innerHTML` porque las traducciones actuales contienen
texto y no código HTML.

Los atributos traducibles se declaran mediante:

```text
data-i18n-attribute
```

Esto permite traducir la metadescripción sin mezclar ese caso particular
con el resto de los elementos.

También se actualiza:

```html
<html lang="...">
```

para que el idioma real del documento sea reconocible por navegadores,
lectores de pantalla y herramientas automáticas.

## Persistencia

La preferencia se almacena mediante:

```text
localStorage
```

Clave utilizada:

```text
aulaflow.language
```

La preferencia solo se guarda después de aplicar correctamente el
idioma.

Los errores de lectura o escritura de `localStorage` no impiden utilizar
la página.

## Estrategia de reserva implementada

El valenciano actúa como idioma de reserva:

```text
ca
```

La secuencia es:

```text
idioma solicitado
→ validar
→ intentar cargar su catálogo
→ aplicar el catálogo
→ guardar la preferencia
```

Cuando falla el catálogo solicitado:

```text
idioma solicitado
→ fallo
→ intentar valenciano
→ aplicar valenciano
```

Cuando fallan ambos catálogos, se conserva el contenido que ya estaba
presente en el documento.

El HTML inicial en valenciano constituye la última capa de degradación
segura.

## Evidencias

La implementación se protege mediante:

* contrato de igualdad entre catálogos;
* comprobación de claves utilizadas por el HTML;
* contrato estático del selector;
* comprobación de que cada opción dispone de catálogo;
* pruebas HTTP de ambos archivos JSON;
* comprobaciones manuales en navegador;
* Maven `verify`;
* GitHub Actions.

Las pruebas Java actuales no ejecutan un navegador real.

El comportamiento de `fetch`, DOM y `localStorage` se comprueba
manualmente hasta que el proyecto justifique incorporar una herramienta
de pruebas web.
