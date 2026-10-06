# ADR-0015 — Artefacto ejecutable autocontenido y acceso nativo de SQLite

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Estado

Aceptado.

## Contexto

Hasta el incremento 2.0, AulaFlow se ejecutaba principalmente desde IntelliJ IDEA. Maven generaba un JAR convencional con las clases y recursos del proyecto, pero:

- el manifiesto no declaraba una clase principal;
- las dependencias de runtime no estaban incluidas en el artefacto;
- `java -jar` no podía iniciar directamente la aplicación.

El incremento 2.1 necesita separar claramente construcción y ejecución para poder desplegar AulaFlow posteriormente mediante contenedores OCI.

AulaFlow utiliza `sqlite-jdbc`. El driver carga una biblioteca nativa de SQLite mediante JNI. Java 26 advierte cuando un módulo no autorizado utiliza operaciones restringidas de carga nativa.

## Decisión

AulaFlow se distribuirá como un JAR ejecutable autocontenido generado mediante Maven Shade Plugin.

El artefacto principal:

- contiene las clases y recursos de AulaFlow;
- contiene las dependencias necesarias en runtime;
- conserva los recursos `META-INF/services`;
- declara como punto de entrada `es.aulaflow.AulaFlowApplication`;
- declara explícitamente el acceso nativo necesario mediante:

```text
Enable-Native-Access: ALL-UNNAMED
```

El JAR se ejecuta mediante:

```bash
java -jar target/aulaflow-1.0.0.jar
```

La aplicación no depende de IntelliJ IDEA para su ejecución.

## Alternativas consideradas

### JAR fino y directorio de dependencias

Mantener el JAR de AulaFlow separado de las bibliotecas externas y construir manualmente el classpath.

Se descarta para AulaFlow 1.0 porque aumenta la complejidad de distribución y del futuro contenedor sin aportar una ventaja relevante al tamaño actual del proyecto.

### Distribución dependiente del IDE

Continuar ejecutando la aplicación mediante IntelliJ IDEA.

Se descarta porque un entorno de despliegue no debe depender de un IDE.

### Ignorar el aviso de acceso nativo

Se descarta. El aviso representa un requisito real del driver SQLite y Java indica que este tipo de acceso será más restrictivo en futuras versiones.

## Consecuencias

### Positivas

- AulaFlow puede ejecutarse fuera del IDE.
- El despliegue necesita un único artefacto Java.
- Las dependencias de runtime viajan con la aplicación.
- El requisito JNI de SQLite queda declarado explícitamente.
- El futuro contenedor podrá contener únicamente un runtime Java y el artefacto ejecutable.

### Negativas

- El JAR aumenta significativamente de tamaño.
- El proceso de sombreado puede producir avisos cuando varias dependencias contienen recursos con el mismo nombre.
- Debe prestarse atención a recursos especiales como `META-INF/services`.

## Verificación

Se ha comprobado:

- `./mvnw clean verify` con Java 26.0.2;
- presencia de `Main-Class` en el manifiesto;
- presencia de las clases `org.sqlite`;
- presencia de `Enable-Native-Access: ALL-UNNAMED`;
- arranque mediante `java -jar`;
- acceso al endpoint `/api/v1/health`;
- inicio de sesión desde navegador;
- segundo arranque utilizando la misma base SQLite y sin variables de aprovisionamiento;
- arranque correcto con `--illegal-native-access=deny`.

## Relación con otros incrementos

Esta decisión prepara el artefacto que será utilizado por la imagen OCI del incremento 2.1.

No decide todavía:

- imagen base;
- `Containerfile`;
- volúmenes;
- Compose;
- despliegue en mini-PC.
