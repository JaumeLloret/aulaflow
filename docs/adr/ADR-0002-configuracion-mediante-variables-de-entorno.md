# ADR-0002: Configuración mediante variables de entorno

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

- Estado: aceptada
- Fecha: 2026-07-20
- Proyecto: AulaFlow 1.0

## Contexto

AulaFlow debe ejecutarse en diferentes entornos:

- equipos Windows;
- equipos macOS;
- equipos Linux;
- máquinas virtuales del alumnado;
- VPS de Proxmox;
- contenedores Docker;
- servidor personal del profesor.

El host, el puerto y el entorno no deben quedar fijados de forma
permanente dentro del código fuente.

## Opciones consideradas

### Valores escritos directamente en el código

Es la solución más sencilla inicialmente, pero obliga a modificar y
recompilar la aplicación para cambiar su configuración.

### Archivo de configuración personal dentro del repositorio

Permite cambiar valores, pero puede provocar la publicación accidental
de rutas, contraseñas o configuraciones personales.

### Variables de entorno

Permiten proporcionar configuración diferente a cada proceso sin
modificar el código fuente.

## Decisión

AulaFlow utilizará variables de entorno para su configuración externa.

Variables iniciales:

- `AULAFLOW_HTTP_HOST`
- `AULAFLOW_HTTP_PORT`
- `AULAFLOW_ENV`

La aplicación proporcionará valores predeterminados adecuados para el
desarrollo local.

## Consecuencias positivas

- El mismo artefacto puede ejecutarse en distintos entornos.
- No es necesario recompilar para cambiar el puerto.
- La solución es compatible con IntelliJ IDEA, sistemas operativos,
  máquinas virtuales y Docker.
- Se reduce el riesgo de introducir configuración personal en Git.

## Consecuencias negativas

- El usuario debe saber dónde configurar variables de entorno.
- Una variable mal escrita puede provocar que se utilice el valor
  predeterminado.
- Los valores recibidos deben convertirse y validarse.

## Decisiones aplazadas

Todavía no se ha decidido si AulaFlow incorporará un lector de archivos
`.env` para el desarrollo local.

Las credenciales y secretos se abordarán cuando se implemente la
autenticación.
