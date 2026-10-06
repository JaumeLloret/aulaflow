# OBS-0008: JDBC, SQLite y transacciones

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Fecha: 2026-07-30
* Proyecto: AulaFlow 1.0
* Incremento: 0.3

## Contexto docente

Este incremento introduce la primera persistencia de AulaFlow para
alumnado de 1.º de DAM.

El objetivo no es enseñar todavía repositorios de dominio ni construir
las tablas del Kanban. Se utiliza una infraestructura mínima para hacer
visibles:

* la configuración de una base local;
* el ciclo de vida de una conexión;
* la ejecución segura de SQL;
* la evolución versionada del esquema;
* el significado de commit y rollback;
* la diferencia entre código de dominio e infraestructura.

## Conceptos que han aparecido

* SQLite como base de datos embebida basada en archivo.
* JDBC como API estándar de Java.
* Controlador JDBC externo.
* URL `jdbc:sqlite:`.
* Ruta absoluta y ruta relativa.
* Directorio de trabajo.
* Variable de entorno `AULAFLOW_DB_PATH`.
* Creación de directorios con `Files.createDirectories`.
* `Connection`, `Statement`, `PreparedStatement` y `ResultSet`.
* `AutoCloseable` y `try-with-resources`.
* Parámetros SQL.
* Claves foráneas.
* Configuración por conexión.
* Migración de esquema.
* Recurso SQL del classpath.
* Manifiesto ordenado.
* Historial de migraciones.
* Idempotencia.
* Transacción.
* `commit`.
* `rollback`.
* Base temporal con `@TempDir`.
* Prueba de integración.

## Separación arquitectónica

SQLite y JDBC pertenecen a infraestructura.

Por ese motivo, las clases que importan tipos JDBC se encuentran bajo:

```text
es.aulaflow.infrastructure.persistence.sqlite
```

El dominio no conoce:

* rutas de archivos;
* conexiones;
* SQL;
* el controlador Xerial;
* la tabla de historial;
* el mecanismo de migraciones.

Todavía no existe un caso de uso que necesite un repositorio. Crear una
interfaz de repositorio en este momento obligaría a inventar un contrato
sin comportamiento de negocio.

## Secuenciación recomendada

### 1. Del dato en memoria al dato persistente

Comparar una variable Java con un archivo SQLite:

```text
finaliza el proceso
        ↓
la variable desaparece

finaliza el proceso
        ↓
el archivo SQLite permanece
```

El alumnado debe distinguir persistencia de copia de seguridad. Este
incremento persiste datos; todavía no define una estrategia de backup.

### 2. Configuración de la ruta

Presentar primero el valor predeterminado:

```text
data/aulaflow.db
```

Después introducir:

```text
AULAFLOW_DB_PATH
```

Conviene ejecutar la aplicación desde dos directorios de trabajo
distintos para observar cómo cambia la resolución de una ruta relativa.

### 3. Apertura y cierre

Mostrar una conexión sin cerrar y preguntar:

* quién posee el recurso;
* cuándo debe liberarse;
* qué ocurre si aparece una excepción.

Después comparar con:

```java
try (
        Connection connection = ...
) {
    // uso
}
```

La sintaxis puede resultar avanzada, pero evita fugas y expresa
claramente el ciclo de vida.

### 4. SQL estático y datos variables

Comparar:

```text
SQL versionado y confiable de una migración
```

con:

```text
datos recibidos durante una operación
```

Los datos variables se envían mediante `PreparedStatement`. No deben
concatenarse dentro de la consulta.

### 5. Claves foráneas

Explicar que SQLite requiere activar:

```sql
PRAGMA foreign_keys = ON
```

en cada conexión. No basta con ejecutarlo una vez en el proyecto.

### 6. Migraciones

Una migración describe un cambio reproducible del esquema.

El manifiesto responde a:

```text
qué migraciones existen y en qué orden
```

La tabla `schema_migrations` responde a:

```text
qué migraciones ya se aplicaron a esta base
```

La combinación permite crear una base vacía y volver a ejecutar el
migrador sin repetir cambios.

### 7. Transacciones

Representar la secuencia:

```text
iniciar transacción
        ↓
ejecutar migraciones pendientes
        ↓
registrar cada migración correcta
        ↓
commit
```

Si aparece un fallo:

```text
error
  ↓
rollback
  ↓
versión anterior
```

La fila de historial no se registra antes de ejecutar correctamente su
migración.

### 8. Pruebas con archivos temporales

Cada prueba recibe su propio directorio mediante `@TempDir`.

Esto permite:

* aislamiento;
* repetibilidad;
* limpieza automática;
* conexiones independientes;
* comprobar persistencia real al cerrar y volver a abrir.

No se utiliza `:memory:` como estrategia principal porque ocultaría parte
del comportamiento basado en archivos y conexiones.

## Dificultades previsibles

* Confundir SQLite con el controlador JDBC.
* Pensar que JDBC es una base de datos.
* Resolver una ruta relativa desde el archivo fuente.
* Introducir una ruta personal en el repositorio.
* Olvidar crear el directorio padre.
* Mantener una conexión global abierta.
* Olvidar cerrar `ResultSet` o `PreparedStatement`.
* Concatenar datos variables en SQL.
* Activar claves foráneas en una sola conexión.
* Confundir migración con carga de datos.
* Depender del orden de archivos dentro de un JAR.
* Registrar el historial antes de ejecutar el SQL.
* Ejecutar cada migración en una transacción independiente.
* Confundir `commit` con cerrar la conexión.
* Creer que una prueba con `@TempDir` utiliza memoria.
* Borrar manualmente el historial para solucionar un fallo.
* Interpretar un aviso de acceso nativo como una prueba fallida.

## Tratamiento del aviso de acceso nativo

Durante la verificación con Java 26.0.2, Xerial carga su biblioteca nativa
y la JVM muestra un aviso de acceso restringido.

Las pruebas terminan correctamente. Siguiendo ADR-0008, no se añade una
opción anticipada para ocultarlo.

La actividad docente adecuada es diferenciar:

* aviso;
* error;
* fallo de prueba;
* incompatibilidad real.

Si una versión futura bloquea el acceso, deberá recogerse la evidencia y
revisarse la decisión técnica.

## Coordinación por módulos

### Programación

* Uso de JDBC.
* Encapsulación de configuración.
* Excepciones controladas.
* `try-with-resources`.
* Consultas parametrizadas.
* Colecciones para versiones aplicadas.
* Pruebas JUnit con `@TempDir`.
* Commit, rollback e idempotencia.

### Entornos de Desarrollo

* Dependencia fijada en Maven.
* Maven Wrapper y Java 26.
* Recursos dentro del classpath y del JAR.
* Variables de entorno en IntelliJ IDEA.
* Directorio de trabajo.
* `.gitignore` para datos generados.
* Ejecución local y GitHub Actions.
* Inspección con DB Browser for SQLite.
* Diagnóstico de warnings frente a errores.

### Proyecto Intermodular

* Decisión SQLite para AulaFlow 1.0.
* Separación de infraestructura y dominio.
* Evolución reproducible del producto.
* Criterios de aceptación de la issue.
* Límites frente a autenticación y Kanban.
* Trazabilidad entre ADR, código, pruebas y documentación.

## Actividades didácticas posibles

### Actividad 1 — Resolver una ruta

Dado un directorio de trabajo y tres valores de
`AULAFLOW_DB_PATH`, calcular la ruta resultante antes de ejecutar.

### Actividad 2 — Comprobar recursos cerrados

Identificar todos los recursos cerrables en una consulta y dibujar su
orden de creación y cierre.

### Actividad 3 — Parametrizar una consulta

Transformar una consulta construida por concatenación en un
`PreparedStatement`.

### Actividad 4 — Observar claves foráneas

Abrir dos conexiones y comprobar `PRAGMA foreign_keys` en ambas.

### Actividad 5 — Reejecutar migraciones

Ejecutar el migrador dos veces y explicar por qué la segunda ejecución no
modifica el historial.

### Actividad 6 — Provocar rollback

Añadir una migración inválida únicamente en recursos de prueba y observar
que no queda la tabla creada por la migración pendiente anterior.

### Actividad 7 — Inspeccionar sin modificar

Abrir una base local con DB Browser, localizar `schema_migrations` y
ejecutar una consulta `SELECT`.

## Evidencias evaluables

* Explicación de la diferencia entre SQLite y JDBC.
* Resolución correcta de una ruta relativa.
* Uso de `try-with-resources`.
* Justificación de `PreparedStatement`.
* Verificación de claves foráneas por conexión.
* Lectura del manifiesto y del historial.
* Explicación de idempotencia.
* Prueba de commit.
* Prueba de rollback.
* Base de prueba aislada con `@TempDir`.
* Identificación de infraestructura frente a dominio.
* Interpretación correcta del resultado de Maven.

## Recuperación ante errores frecuentes

1. Leer el primer mensaje controlado, no únicamente la última línea.
2. Comprobar Java y Maven Wrapper.
3. Revisar `AULAFLOW_DB_PATH` y el directorio de trabajo.
4. Confirmar permisos y que la ruta no sea un directorio.
5. Detener procesos o herramientas que mantengan abierta la base.
6. No editar manualmente `schema_migrations`.
7. Reproducir el fallo con una base temporal nueva.
8. Ejecutar la prueba focalizada.
9. Ejecutar `./mvnw clean verify`.
10. Registrar un incidente solo si el problema fue real y aporta
    aprendizaje reutilizable.

## Decisión pedagógica

El alumnado debe poder leer, ejecutar, probar y explicar el flujo
completo.

No se espera inicialmente que diseñe de forma autónoma:

* un migrador genérico;
* un parser SQL;
* un pool de conexiones;
* una abstracción transaccional;
* una arquitectura de repositorios.

Esas extensiones solo deben aparecer cuando exista una necesidad real.
