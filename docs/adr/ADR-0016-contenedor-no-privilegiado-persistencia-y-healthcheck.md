# ADR-0016 — Contenedor no privilegiado, persistencia y healthcheck

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Estado

Aceptado.

## Contexto

El incremento 2.1 incorpora la ejecución de AulaFlow mediante contenedores.

El artefacto Java ejecutable ya se había validado fuera de IntelliJ IDEA.
El siguiente objetivo era construir una imagen reproducible que:

- ejecute Java 26;
- no ejecute AulaFlow como `root`;
- separe aplicación y datos;
- conserve SQLite al destruir y recrear contenedores;
- permita comprobar el estado real del servicio mediante `/api/v1/health`;
- siga siendo utilizable con Podman rootless.

Durante las pruebas se comprobó además que Podman construye imágenes en
formato OCI por defecto y que, en ese formato, la instrucción
`HEALTHCHECK` del `Containerfile` era ignorada.

## Decisiones

### Imagen base

Se utiliza una imagen Eclipse Temurin con JRE 26.

El contenedor necesita ejecutar el JAR ya construido, pero no necesita
Maven, IntelliJ IDEA ni un JDK completo durante el runtime.

### Usuario de ejecución

AulaFlow se ejecuta dentro del contenedor con:

```text
UID 10001
GID 10001
usuario: aulaflow
```

El UID/GID se fija explícitamente para que la configuración sea
determinista.

El usuario no dispone de shell de login ni directorio home funcional.

### Separación entre programa y datos

El artefacto se instala en:

```text
/opt/aulaflow/aulaflow.jar
```

y permanece propiedad de `root`.

Los datos variables se almacenan en:

```text
/var/lib/aulaflow
```

cuyo propietario es el usuario `aulaflow`.

La base se configura mediante:

```text
AULAFLOW_DB_PATH=/var/lib/aulaflow/aulaflow.db
```

El usuario de la aplicación puede escribir en el directorio de datos,
pero no puede modificar `/opt/aulaflow`.

### Configuración de red del contenedor

La imagen proporciona como valores predeterminados:

```text
AULAFLOW_HTTP_HOST=0.0.0.0
AULAFLOW_HTTP_PORT=8080
```

El cambio de `127.0.0.1` a `0.0.0.0` se limita al entorno del contenedor.
La ejecución directa del JAR conserva los valores predeterminados de la
aplicación.

La publicación hacia el host continúa realizándose explícitamente, por
ejemplo:

```bash
-p 127.0.0.1:8080:8080
```

### Persistencia

Se utiliza un volumen nombrado de Podman para montar:

```text
aulaflow-data -> /var/lib/aulaflow
```

El volumen pertenece al UID/GID 10001 y sobrevive a la destrucción del
contenedor.

La persistencia se considera demostrada únicamente cuando:

1. un primer contenedor crea administrador y datos;
2. el primer contenedor se destruye;
3. se crea un segundo contenedor distinto;
4. el segundo contenedor usa el mismo volumen;
5. AulaFlow arranca sin volver a proporcionar credenciales de
   aprovisionamiento;
6. los datos anteriores continúan disponibles.

### Healthcheck

El healthcheck utiliza el endpoint existente:

```text
GET /api/v1/health
```

No se añade otro endpoint.

El script:

```text
container/healthcheck.sh
```

realiza una petición HTTP interna y exige:

- código HTTP `200`;
- `"status": "UP"` en la respuesta.

No se instala `curl` únicamente para ejecutar el healthcheck.

### Formato de construcción

Para conservar la instrucción `HEALTHCHECK`, la imagen se construye con:

```bash
podman build --format docker ...
```

Podman continúa siendo el motor de construcción y ejecución.

El uso del formato Docker v2 para la configuración de la imagen responde
a una necesidad concreta de compatibilidad del healthcheck y no implica
utilizar Docker Desktop ni el daemon Docker.

### Orquestación local

`compose.yaml` describe la configuración de ejecución local, pero no
construye la imagen.

La construcción se centraliza en:

```text
container/build-image.sh
```

Esto garantiza que la imagen consumida por Compose se haya construido con
el formato necesario para conservar `HEALTHCHECK`.

El volumen SQLite se declara externo a Compose para que su ciclo de vida
sea independiente de los contenedores y de `podman compose down`.

## Alternativas consideradas

### Ejecutar como root dentro del contenedor

Descartado por mínimo privilegio y por los criterios de aceptación del
incremento.

### Guardar SQLite en la capa escribible del contenedor

Descartado porque destruir el contenedor destruiría también los datos.

### Dar propiedad de todo `/opt/aulaflow` al usuario de aplicación

Descartado porque la aplicación solo necesita leer su artefacto. Los
permisos de escritura se limitan al directorio de datos.

### Instalar `curl` para el healthcheck

Descartado porque aumentaría el runtime únicamente para una comprobación
que puede realizarse con las herramientas ya presentes.

### Mantener el formato OCI predeterminado ignorando `HEALTHCHECK`

Descartado porque incumpliría el criterio de disponer de un healthcheck
real asociado a la imagen.

### Hacer que Compose construya directamente la imagen

Descartado en 2.1 porque ocultaría la necesidad de `--format docker` y
duplicaría la responsabilidad ya expresada en `container/build-image.sh`.
Compose queda centrado en configuración y ejecución.

## Consecuencias

### Positivas

- ejecución con mínimo privilegio;
- separación clara entre código y datos;
- persistencia independiente de la vida del contenedor;
- configuración externa;
- healthcheck funcional;
- imagen ejecutable mediante Podman rootless;
- despliegue local reproducible mediante Compose;
- construcción de imagen validada también en CI;
- ausencia de Docker Desktop como requisito.

### Negativas

- la construcción debe indicar explícitamente `--format docker`;
- el volumen debe mantener permisos compatibles con UID/GID 10001;
- `podman compose` necesita un proveedor Compose externo;
- la configuración de contenedores añade conceptos adicionales que deben
  documentarse para el alumnado.

## Verificación

Se ha comprobado:

- construcción correcta con Podman;
- Java 26 dentro de la imagen;
- ejecución como UID/GID 10001;
- `200 OK` en `/api/v1/health`;
- escritura permitida en `/var/lib/aulaflow`;
- escritura rechazada en `/opt/aulaflow`;
- base SQLite creada en el volumen;
- destrucción y recreación del contenedor conservando administrador y
  datos;
- segundo arranque sin credenciales de aprovisionamiento;
- healthcheck `healthy` con AulaFlow funcionando;
- healthcheck `unhealthy` con el contenedor vivo pero AulaFlow detenido;
- configuración inicial inválida con fallo controlado y logs sin secretos;
- construcción y despliegue desde un checkout limpio;
- recreación mediante Compose conservando los datos;
- job `Container image · Podman` de GitHub Actions en verde.

## Fuera de alcance

Esta decisión no cubre todavía:

- despliegue final en mini-PC;
- proxy inverso;
- HTTPS;
- backup y restore del incremento 2.2;
- publicación de imágenes en un registry.
