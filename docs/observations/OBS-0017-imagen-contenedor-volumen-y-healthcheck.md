# OBS-0017 — Imagen, contenedor, volumen, healthcheck y Compose

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Situación observada

La contenedorización de AulaFlow introduce conceptos que pueden
confundirse si se presentan únicamente como comandos.

Las pruebas realizadas permiten separar cinco ideas:

```text
imagen
contenedor
volumen
healthcheck
Compose
```

Cada una resuelve un problema distinto.

## Imagen y contenedor

Una imagen es una plantilla inmutable utilizada para crear contenedores.

La prueba inicial con Alpine permitió observar:

```text
podman run
    ↓
descarga/usa imagen
    ↓
crea contenedor
    ↓
ejecuta proceso
```

Al utilizar `--rm`, el contenedor desapareció al terminar, pero la imagen
permaneció.

Por tanto:

```text
imagen != contenedor
```

Una misma imagen puede originar múltiples contenedores.

## Capa escribible del contenedor

Un archivo creado dentro de un contenedor permanece mientras ese mismo
contenedor exista, incluso si se detiene y vuelve a arrancar.

Sin embargo, al destruir el contenedor y crear otro desde la misma
imagen, ese archivo desaparece.

Esto permite justificar la necesidad de un volumen antes de explicar su
sintaxis.

## Volumen

Un volumen representa almacenamiento cuya vida no depende de la vida de
un contenedor concreto.

En AulaFlow:

```text
contenedor A
    |
    +-- /var/lib/aulaflow
              |
              v
        aulaflow-data
              |
              +-- aulaflow.db
```

Después:

```text
contenedor A eliminado
volumen conservado
contenedor B creado
mismo volumen montado
datos recuperados
```

La prueba demuestra persistencia real, no solo persistencia después de
reiniciar el mismo proceso.

## Rootless y usuario interno

Podman se ejecuta rootless en Bazzite, pero eso no implica
automáticamente que el proceso del contenedor sea un usuario no root.

Inicialmente se observó:

```text
uid=0(root)
```

dentro del contenedor.

Después de configurar el usuario de aplicación:

```text
uid=10001(aulaflow)
gid=10001(aulaflow)
```

Esto permite diferenciar:

- usuario del host;
- Podman rootless;
- usuario del contenedor;
- UID/GID;
- permisos del filesystem.

## Mínimo privilegio

El usuario AulaFlow puede escribir en:

```text
/var/lib/aulaflow
```

pero no en:

```text
/opt/aulaflow
```

La prueba de `touch` fallando con `Permission denied` no representa un
error: demuestra que la restricción funciona.

## Healthcheck

Un proceso vivo no garantiza que una aplicación esté sana.

Se probaron dos estados:

```text
contenedor vivo + AulaFlow responde
    -> healthy
```

y:

```text
contenedor vivo + AulaFlow no se ejecuta
    -> unhealthy
```

El healthcheck comprueba por tanto el servicio observable y no solo la
existencia del proceso.

## Formato OCI y formato Docker

Durante la construcción con Podman se observó que la instrucción
`HEALTHCHECK` era ignorada al generar la imagen con el formato OCI
predeterminado.

La construcción explícita mediante:

```bash
podman build --format docker ...
```

permitió conservar los metadatos del healthcheck.

Este caso es pedagógicamente útil para mostrar que:

- Podman no es sinónimo de formato OCI exclusivamente;
- motor, formato de imagen y herramienta de orquestación son conceptos
  distintos;
- la compatibilidad real debe verificarse, no suponerse.

## Compose

Compose no sustituye a la imagen, al contenedor ni al volumen. Describe
de forma declarativa cómo combinar esos elementos para ejecutar un
servicio.

Antes de Compose, el arranque requería recordar opciones como la imagen,
las variables de entorno, el puerto y el volumen. Con `compose.yaml`, el
flujo habitual queda reducido a:

```bash
podman compose up -d
podman compose ps
podman compose logs
podman compose down
```

En AulaFlow, Compose no construye la imagen. Consume la imagen producida
por `container/build-image.sh`, lo que permite distinguir claramente:

```text
construcción
    ↓
imagen

configuración de ejecución
    ↓
Compose
```

El volumen se declara externo, por lo que `compose down` destruye el
contenedor pero no los datos SQLite.

## Secuenciación docente

### Entornos de Desarrollo

El alumnado debería dominar:

- imagen;
- contenedor;
- `build`;
- `run`;
- `stop`;
- `rm`;
- publicación de puertos;
- variables de entorno;
- volumen;
- healthcheck;
- Compose como descripción declarativa de la ejecución.

Debe reconocer:

- rootless;
- UID/GID;
- formato OCI frente a formato Docker;
- imagen base;
- capas;
- proveedor Compose.

### Programación

Se relaciona con:

- configuración externa;
- rutas;
- proceso Java;
- permisos;
- separación entre aplicación y persistencia.

No es necesario que el alumnado diseñe inicialmente una imagen segura
completa sin guía.

### Proyecto Intermodular

Permite trabajar el recorrido:

```text
código
  ↓
artefacto
  ↓
imagen
  ↓
contenedor
  ↓
configuración + volumen
  ↓
Compose
  ↓
servicio desplegado
```

## Nivel esperado

Al finalizar el bloque, el alumnado debería ser capaz de explicar por
qué:

- una imagen no es un contenedor;
- reiniciar no es lo mismo que recrear;
- SQLite no debe permanecer en la capa efímera;
- rootless no equivale a ejecutar la aplicación como root;
- un healthcheck aporta información distinta al estado del proceso;
- Compose describe la ejecución pero no sustituye la construcción del
  artefacto o de la imagen.
