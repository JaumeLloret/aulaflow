# INC-0004 — HEALTHCHECK ignorado al construir la imagen en formato OCI

Descripción:
Durante la construcción de la imagen de AulaFlow con Podman se añadió una
instrucción `HEALTHCHECK` al `Containerfile`, pero el healthcheck no quedó
almacenado en la imagen resultante.

Síntoma:
`podman build` mostró el aviso:

```text
HEALTHCHECK is not supported for OCI image format and will be ignored.
Must use `docker` format
```

Posteriormente:

```bash
podman image inspect localhost/aulaflow:2.1-dev   --format '{{json .Config.Healthcheck}}'
```

devolvió:

```text
null
```

Causa:
Podman estaba construyendo la imagen con el formato OCI predeterminado.
La configuración `HEALTHCHECK` utilizada no se conservaba en ese formato
de salida.

Pruebas realizadas:
- reconstrucción inicial con el formato predeterminado;
- inspección de `.Config.Healthcheck`;
- comprobación del warning emitido por Podman;
- reconstrucción con `--format docker`;
- nueva inspección de la configuración;
- ejecución manual del healthcheck;
- prueba positiva con AulaFlow operativo;
- prueba negativa con contenedor vivo y AulaFlow no ejecutado.

Solución:
Construir la imagen mediante:

```bash
podman build   --format docker   -t localhost/aulaflow:2.1-dev   .
```

No fue necesario cambiar de motor de contenedores ni utilizar Docker
Desktop.

Cómo comprobar la recuperación:
La inspección de la imagen debe devolver una configuración de healthcheck
con:

```text
Test
StartPeriod
Interval
Timeout
Retries
```

Además:

```bash
podman healthcheck run <contenedor>
```

debe finalizar correctamente cuando AulaFlow responde y el estado debe
pasar a `unhealthy` cuando el contenedor permanece vivo pero AulaFlow no
está disponible.

Valor docente:
La incidencia permite distinguir entre motor de contenedores, formato de
imagen y metadatos soportados. También muestra la importancia de revisar
los warnings de construcción y verificar el resultado con `inspect` en
lugar de asumir que una instrucción del `Containerfile` se ha aplicado.
