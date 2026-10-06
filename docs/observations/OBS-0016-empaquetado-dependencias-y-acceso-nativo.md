# OBS-0016 — Empaquetado, dependencias y acceso nativo

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Situación observada

Un proyecto Java puede compilar y superar todas sus pruebas y, sin embargo, el JAR generado no ser directamente ejecutable.

En AulaFlow se comprobó inicialmente:

```bash
java -jar target/aulaflow-1.0.0.jar
```

Resultado:

```text
no main manifest attribute
```

El JAR contenía las clases y recursos propios de AulaFlow, pero no declaraba el punto de entrada ni incluía todas las dependencias de runtime.

## Conceptos implicados

### Compilar

Transformar código fuente Java en bytecode `.class`.

### Empaquetar

Agrupar clases y recursos en un artefacto distribuible.

### JAR

Archivo ZIP con una estructura y metadatos definidos para aplicaciones y bibliotecas Java.

### Manifiesto

`META-INF/MANIFEST.MF` contiene metadatos del JAR.

Para utilizar:

```bash
java -jar aplicacion.jar
```

el manifiesto debe indicar qué clase contiene el método `main`.

### Dependencias

El código de AulaFlow no contiene la implementación del driver SQLite. Esa funcionalidad procede de una biblioteca externa.

Un JAR convencional no incorpora automáticamente el contenido de todas sus dependencias.

### Fat JAR / Uber JAR / Shaded JAR

Artefacto que combina aplicación y dependencias necesarias para simplificar la distribución.

### Código nativo

No todo lo que utiliza una aplicación Java está necesariamente implementado en Java.

`sqlite-jdbc` carga una biblioteca SQLite nativa correspondiente al sistema operativo.

Esto permite introducir posteriormente:

- JNI;
- código gestionado y código nativo;
- portabilidad de Java y límites de esa portabilidad;
- seguridad asociada a la carga de bibliotecas nativas.

## Secuenciación docente

### Programación

El alumnado debería dominar:

- método `main`;
- compilación;
- bytecode;
- paquetes;
- dependencias;
- JAR;
- classpath.

Debe reconocer:

- manifiesto;
- fat JAR;
- JNI.

No se exige inicialmente que configure de forma autónoma Maven Shade.

### Entornos de Desarrollo

Debe trabajarse:

- ciclo Maven;
- `compile`;
- `test`;
- `package`;
- `verify`;
- dependencias;
- plugins Maven;
- artefactos;
- reproducibilidad;
- ejecución fuera del IDE.

### Proyecto Intermodular

El contexto permite justificar por qué se crea un artefacto:

```text
código fuente
      ↓
construcción
      ↓
artefacto
      ↓
despliegue
```

El alumnado debe comprender que el IDE es una herramienta de desarrollo y no una dependencia del producto desplegado.

## Valor pedagógico del warning de Java 26

El warning de `System.load` no se considera ruido.

Permite mostrar que:

- una biblioteca puede utilizar código nativo;
- las versiones nuevas del JDK pueden endurecer contratos de seguridad;
- solucionar un warning no significa ocultarlo;
- declarar explícitamente una capacidad es distinto de desactivar una protección global.
