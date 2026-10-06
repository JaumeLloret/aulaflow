# ADR-0008: SQLite, JDBC y migraciones versionadas

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Estado: aceptada
* Fecha: 2026-07-30
* Proyecto: AulaFlow 1.0
* Incremento: 0.3
* Issue: #5

## Contexto

AulaFlow necesita establecer su primera base de persistencia antes de
incorporar usuarios, tableros, columnas o tarjetas.

El proyecto utiliza:

* Java 26;
* Maven Wrapper;
* servidor HTTP embebido de Java;
* arquitectura por capas sin framework web;
* ninguna base de datos;
* ningún ORM;
* ningún pool de conexiones.

El incremento 0.3 debe introducir SQLite y JDBC de forma explícita,
portable, verificable y comprensible para alumnado de 1.º de DAM.

SQLite será la base de datos del producto AulaFlow 1.0.

SQL Server continuará siendo una tecnología de prácticas de bases de
datos y no formará parte de la arquitectura del producto.

## Problema

Es necesario decidir:

* qué controlador JDBC utilizar;
* cómo localizar el archivo de base de datos;
* cómo abrir y cerrar conexiones;
* cómo activar las claves foráneas;
* cómo crear y evolucionar el esquema;
* cómo ordenar y registrar las migraciones;
* cómo garantizar la atomicidad y la idempotencia;
* cuándo ejecutar la inicialización;
* cómo aislar las bases de datos de las pruebas;
* qué responsabilidades pertenecen a infraestructura;
* qué abstracciones todavía no están justificadas.

## Objetivos

La solución debe:

* utilizar SQLite mediante JDBC;
* funcionar con Java 26 localmente y en integración continua;
* mantener JDBC y SQLite fuera del dominio;
* resolver la ubicación de la base de forma portable;
* crear los directorios necesarios de forma controlada;
* cerrar conexiones y demás recursos correctamente;
* ejecutar migraciones SQL versionadas en un orden determinista;
* registrar únicamente migraciones aplicadas correctamente;
* conservar la versión anterior si una migración falla;
* ser idempotente;
* permitir pruebas con conexiones independientes;
* evitar abstracciones y dependencias que el alcance actual no necesita;
* mantener fuera de Git las bases y archivos auxiliares generados.

## Opciones consideradas

### Utilizar SQL Server como base del producto

Esta opción se descarta porque:

* no corresponde a la arquitectura aprobada para AulaFlow 1.0;
* introduce un servicio externo y una configuración adicional;
* reduce la portabilidad del entorno docente;
* confunde las prácticas de bases de datos con la tecnología del producto.

### Utilizar PostgreSQL en AulaFlow 1.0

Esta opción se descarta para la versión 1.0 porque:

* requiere administrar un servidor de base de datos;
* añade complejidad operativa que el incremento no necesita;
* SQLite satisface el alcance local y docente aprobado.

La decisión podrá revisarse para versiones futuras si aparecen necesidades
que SQLite no pueda cubrir.

### Incorporar un ORM

Esta opción se descarta porque:

* todavía no existen entidades persistentes del dominio;
* ocultaría el ciclo de vida de conexiones, sentencias y resultados;
* reduciría el valor pedagógico del trabajo directo con JDBC;
* añadiría una dependencia y abstracciones sin un caso de uso actual.

### Utilizar Flyway o Liquibase

Esta opción se descarta porque:

* el esquema inicial será pequeño;
* añadiría un framework exclusivamente para las primeras migraciones;
* ocultaría conceptos de orden, historial, transacción e idempotencia;
* un migrador JDBC pequeño y explícito cubre el alcance aprobado.

### Utilizar SQLite exclusivamente en memoria para las pruebas

Esta opción se descarta como estrategia principal porque:

* no verifica la persistencia al cerrar y volver a abrir una conexión;
* una base `:memory:` está vinculada al ciclo de vida de sus conexiones;
* el producto utilizará un archivo real.

Las pruebas utilizarán archivos temporales aislados.

### Mantener una conexión global compartida

Esta opción se descarta porque:

* introduce estado global;
* complica el cierre y la recuperación ante errores;
* favorece el uso concurrente accidental de una misma conexión;
* dificulta el aislamiento de pruebas.

### Incorporar un pool de conexiones

Esta opción se pospone porque:

* no existe todavía una carga observada que lo justifique;
* añadiría configuración y ciclo de vida propios;
* una conexión nueva por operación es suficiente para este incremento.

### Crear puertos, repositorios o una abstracción transaccional

Esta opción se pospone porque:

* todavía no existen casos de uso que persistan entidades del dominio;
* no existe un contrato de aplicación que deba implementarse;
* diseñar esas abstracciones ahora anticiparía incrementos posteriores.

### Crear tablas de usuarios o del tablero Kanban

Esta opción se descarta en este incremento.

Las tablas de usuarios, tableros, columnas y tarjetas pertenecerán a los
incrementos que introduzcan sus correspondientes comportamientos.

### Hacer que `/health` compruebe SQLite

Esta opción se descarta en el incremento 0.3 porque:

* cambiaría un contrato HTTP público ya existente;
* mezclaría la salud básica del proceso con una comprobación nueva;
* el arranque ya fallará si no pueden completarse la conexión y las
  migraciones.

### Activar WAL

Esta opción se pospone porque:

* no existe una necesidad de concurrencia observada;
* genera archivos auxiliares y añade decisiones operativas;
* debe incorporarse únicamente después de medir y documentar su necesidad.

## Decisión

AulaFlow 1.0 utilizará SQLite mediante JDBC.

La dependencia directa será:

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.53.2.1</version>
</dependency>
```

La versión quedará fijada explícitamente en `pom.xml` cuando comience la
implementación.

Este ADR registra la decisión, pero no añade todavía la dependencia.

Se empleará el artefacto estándar multiplataforma de Xerial, que reúne las
clases Java y las bibliotecas nativas soportadas.

Su funcionamiento real con Java 26 deberá verificarse:

* en el entorno local;
* en GitHub Actions.

No se configurarán anticipadamente opciones de acceso nativo de Java.

Cualquier aviso o incompatibilidad se observará primero y se resolverá
mediante una decisión basada en evidencias.

## Configuración y ubicación de la base

La ruta predeterminada será:

```text
data/aulaflow.db
```

La variable de entorno para sustituirla será:

```text
AULAFLOW_DB_PATH
```

La configuración de persistencia se representará separadamente de
`ApplicationConfig`.

La ubicación de la base se resolverá en la composición o infraestructura,
no en el dominio.

Las rutas relativas se resolverán de manera portable desde el directorio de
trabajo.

Antes de abrir la base se crearán los directorios necesarios.

Los errores de ruta, creación de directorios, conexión o migración se
traducirán de forma controlada.

Los siguientes archivos generados permanecerán fuera de Git:

```text
*.db
*.sqlite
*.sqlite3
*-journal
*-wal
*-shm
```

Una base de datos local no se versiona.

Sí se versionan los recursos SQL que definen su evolución.

## Gestión de conexiones

No se utilizará un pool de conexiones.

No existirá una conexión global ni una conexión compartida entre hilos.

Cada operación que necesite acceder a SQLite abrirá una conexión nueva.

Cada conexión activará:

```sql
PRAGMA foreign_keys = ON
```

La activación se realizará por conexión porque SQLite no habilita las
restricciones de claves foráneas globalmente para todas las conexiones.

No se activará WAL en este incremento.

Las conexiones, sentencias y resultados se cerrarán mediante
`try-with-resources`.

Los datos variables se enviarán mediante `PreparedStatement`.

El SQL estático y versionado de una migración es un recurso confiable del
classpath y podrá ejecutarse directamente.

## Estrategia de migraciones

Se implementará un migrador propio, pequeño y explícito mediante JDBC.

No se utilizarán Flyway, Liquibase ni otros frameworks de migraciones.

Las migraciones serán recursos SQL versionados y se ubicarán
previsiblemente bajo:

```text
src/main/resources/db/migration
```

La convención de nombres será:

```text
VNNN__descripcion.sql
```

Existirá un manifiesto explícito y ordenado en el classpath.

El orden no dependerá del listado de un directorio dentro de un JAR, porque
ese listado no constituye un contrato portable del classpath.

Durante este incremento, cada archivo de migración contendrá una única
sentencia SQL ejecutable mediante JDBC.

No se implementará un analizador genérico de scripts SQL.

La primera migración creará la tabla:

```text
schema_migrations
```

Antes de aplicar migraciones, el sistema comprobará mediante SQLite si la
tabla de historial existe.

El migrador comparará el manifiesto ordenado con las migraciones ya
registradas.

Las migraciones pendientes y sus registros de historial se ejecutarán
dentro de una transacción explícita.

Una migración solo se registrará después de ejecutar correctamente su
sentencia.

Si falla cualquier migración:

* se realizará `rollback`;
* no se registrará como aplicada;
* la base conservará la versión anterior.

Reejecutar el migrador no volverá a aplicar las migraciones registradas.

## Arranque y arquitectura

Las migraciones se ejecutarán antes de arrancar el servidor HTTP.

Un fallo de ruta, conexión o migración impedirá iniciar el servidor.

El endpoint:

```http
GET /health
```

no comprobará SQLite en este incremento y conservará su contrato público.

No se crearán todavía:

* puertos de aplicación;
* repositorios de dominio;
* una abstracción transaccional reutilizable.

No existen todavía casos de uso que justifiquen esas abstracciones.

JDBC, SQLite y los tipos:

```text
Connection
PreparedStatement
ResultSet
```

permanecerán exclusivamente en infraestructura.

El dominio no importará:

* JDBC;
* SQLite;
* configuración;
* rutas de archivos;
* detalles de migración.

No se crearán tablas de:

* usuarios;
* tableros;
* columnas;
* tarjetas.

## Estrategia de pruebas

Las pruebas utilizarán bases SQLite basadas en archivos temporales.

Cada prueba tendrá su propia base mediante:

```java
@TempDir
```

No se utilizará `:memory:` como estrategia principal.

Las pruebas cerrarán y abrirán conexiones independientes para verificar
persistencia real.

La estrategia deberá comprobar:

* creación del esquema desde una base vacía;
* aplicación ordenada de migraciones;
* registro del historial;
* idempotencia al ejecutar de nuevo el migrador;
* `commit` de una operación correcta;
* `rollback` de una operación fallida;
* ausencia de cambios parciales tras una migración fallida;
* activación de claves foráneas en cada conexión;
* aislamiento entre bases temporales.

Las pruebas de `commit` y `rollback` podrán crear sus tablas exclusivamente
dentro de la base temporal de prueba.

No se añadirá un componente transaccional de producción únicamente para
satisfacer esas pruebas.

Las 43 pruebas actuales son la línea base previa al incremento.

No constituyen una cantidad permanente ni el total final esperado.

## Herramienta de inspección

Al cerrar el incremento se documentará DB Browser for SQLite como
herramienta gráfica de inspección.

La herramienta es:

* gratuita;
* libre;
* multiplataforma.

Su instalación y uso se desarrollarán en la documentación de cierre del
incremento, no en este ADR.

## Consecuencias positivas

* AulaFlow dispone de una base local portable.
* JDBC permanece visible y permite enseñar sus conceptos fundamentales.
* El esquema puede reconstruirse desde recursos versionados.
* El historial hace observable la versión alcanzada.
* La transacción protege frente a migraciones aplicadas parcialmente.
* La ejecución repetida es idempotente.
* Las pruebas se aproximan al comportamiento real basado en archivos.
* El dominio permanece independiente de SQLite y JDBC.
* No se anticipan repositorios ni abstracciones sin casos de uso.
* No se añade un framework web ni un ORM.

## Consecuencias negativas

* Se añadirá posteriormente una dependencia externa al proyecto.
* El artefacto contiene código nativo que deberá verificarse con Java 26 en
  cada entorno soportado.
* El migrador propio exige pruebas rigurosas de orden, idempotencia,
  historial y rollback.
* La inicialización pasa a formar parte del arranque de la aplicación.
* Un error de persistencia impedirá arrancar el servidor.
* La regla de una sentencia por archivo limita las migraciones compuestas.
* Abrir una conexión por operación puede revisarse si aparecen necesidades
  de rendimiento demostradas.

El README deberá aclarar que «Java 26 puro» significa que AulaFlow no
utiliza un framework web ni un ORM, no que carezca de controladores JDBC.

La restricción de una sentencia por migración evita implementar un parser
SQL incorrecto.

Si el proyecto necesita migraciones compuestas, la restricción deberá
revisarse mediante otro ADR.

## Riesgos

* utilizar una versión del controlador distinta de la documentada;
* encontrar una incompatibilidad entre el controlador nativo y Java 26;
* depender accidentalmente del orden de listado del classpath;
* registrar una migración antes de que termine correctamente;
* ejecutar migraciones e historial fuera de la misma transacción;
* dejar la conexión en modo transaccional incorrecto después de un error;
* olvidar activar claves foráneas en una conexión;
* concatenar datos variables dentro de SQL;
* no cerrar recursos JDBC;
* compartir una base temporal entre pruebas;
* generar una base local dentro del repositorio;
* introducir prematuramente tablas o abstracciones de otros incrementos;
* modificar el contrato de `/health` sin pruebas y documentación.

## Criterios de revisión

Esta decisión se revisará cuando:

* SQLite deje de cubrir las necesidades demostradas del producto;
* exista una necesidad medida de concurrencia que justifique WAL;
* exista una carga medida que justifique un pool;
* aparezcan casos de uso que necesiten puertos, repositorios o una
  abstracción transaccional;
* las migraciones necesiten varias sentencias o capacidades que justifiquen
  un framework especializado;
* una incompatibilidad real con Java 26 requiera opciones adicionales de
  acceso nativo;
* el contrato de salud deba incorporar dependencias externas.

## Referencias

* [Xerial SQLite JDBC Driver](https://github.com/xerial/sqlite-jdbc)
* [Xerial sqlite-jdbc 3.53.2.1](https://github.com/xerial/sqlite-jdbc/releases/tag/3.53.2.1)
* [SQLite: Transaction](https://www.sqlite.org/lang_transaction.html)
* [SQLite: Foreign Key Support](https://www.sqlite.org/foreignkeys.html)
