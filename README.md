# AulaFlow · Proyecto de referencia para DAM

AulaFlow es una aplicación web para organizar tareas en tableros Kanban: columnas como «Pendiente», «En curso» y «Terminado», con tarjetas que se pueden mover entre ellas. Incluye inicio de sesión, etiquetas, listas de comprobación e importación y exportación CSV.

Este repositorio contiene **el proyecto completo**, preparado para descargarlo, ejecutarlo y estudiar cómo funciona. Está pensado especialmente para el alumnado de **segundo de DAM que no desarrolló AulaFlow en primero**. Puedes empezar desde cero siguiendo las guías; no necesitas conocer el historial del proyecto.

El servidor está escrito en **Java 26**, usa el servidor HTTP del JDK y guarda los datos en **SQLite mediante JDBC**. La interfaz utiliza HTML, CSS y JavaScript, con valenciano como idioma inicial y castellano disponible. Las responsabilidades se ven directamente, sin un framework web ni un ORM.

## Por dónde empezar

| Quiero… | Empieza aquí |
| --- | --- |
| Descargarlo y conseguir que funcione en mi ordenador | [Primer arranque en Windows, macOS y Linux](docs/alumnado/01-primer-arranque.md) |
| Entenderlo sin haber hecho el proyecto en primero | [Recorrido para segundo de DAM](docs/alumnado/02-recorrido-para-segundo.md) |
| Encontrar las clases y seguir una operación | [Mapa del código](docs/alumnado/03-mapa-del-codigo.md) |
| Entender cómo se construyó por etapas | [Evolución del proyecto](docs/alumnado/04-evolucion-del-proyecto.md) |
| Consultar decisiones técnicas, contratos o despliegue | [Índice de documentación](docs/README.md) |

## Descargar el proyecto

Desde [JaumeLloret/aulaflow](https://github.com/JaumeLloret/aulaflow), selecciona **Code → Download ZIP** y descomprime el archivo. Abre la carpeta que contiene `pom.xml`.

Si utilizas Git:

```bash
git clone https://github.com/JaumeLloret/aulaflow.git
cd aulaflow
```

El ZIP incluye las mismas fuentes y pruebas que la rama `main`. Git es útil para practicar cambios en tu propia rama, pero no es necesario para descargar y arrancar la aplicación.

## Qué necesitas

| Herramienta | Uso |
| --- | --- |
| JDK 26, sin funciones preview | Compilar y ejecutar Java; Java 17, 21 y 25 no sirven para esta versión |
| Maven Wrapper 3.9.16, incluido | Descargar dependencias, compilar y ejecutar las pruebas |
| Navegador | Utilizar la aplicación |
| IntelliJ IDEA con soporte para Java 26, opcional | Leer, ejecutar y depurar el código |
| Git, opcional | Clonar y trabajar con ramas |
| Podman y un proveedor Compose, opcionales | Estudiar el despliegue en contenedores |

La primera compilación necesita Internet. No necesitas instalar un servidor de bases de datos, SQLite por separado ni Maven global.

## Compilar y arrancar

En Windows, desde PowerShell y dentro de la carpeta del proyecto:

```powershell
.\mvnw.cmd clean verify
```

En macOS o Linux:

```bash
./mvnw clean verify
```

La compilación ejecuta las pruebas y genera `target/aulaflow-1.0.0.jar`. **Antes del primer arranque debes definir tu propio usuario y contraseña**: no hay una cuenta predeterminada. La [guía de primer arranque](docs/alumnado/01-primer-arranque.md) explica cómo hacerlo desde la terminal y desde IntelliJ.

Después, en esa misma terminal:

```bash
java -jar target/aulaflow-1.0.0.jar
```

Abre **http://127.0.0.1:8080/**. La pantalla inicial permite acceder al inicio de sesión. Los datos se guardan en `data/aulaflow.db`, relativa a la carpeta desde la que arrancas el proceso. Al reiniciar, los tableros se conservan y debes iniciar sesión de nuevo.

| Dirección | Qué muestra |
| --- | --- |
| `/` | Portada de la aplicación |
| `/login` | Formulario de inicio de sesión |
| `/boards` | Tableros de la persona autenticada |
| `/api/v1/health` | Estado, versión y entorno del servidor |

## Qué hay dentro

| Carpeta o archivo | Contenido |
| --- | --- |
| `src/main/java/es/aulaflow/` | Aplicación, dominio, servicios, HTTP, persistencia y mantenimiento |
| `src/main/resources/web/` | Páginas, estilos, JavaScript y traducciones |
| `src/main/resources/db/migration/` | Creación y evolución del esquema SQLite |
| `src/test/` | Pruebas unitarias, de integración y de recorrido completo |
| `http/cartero/` | Peticiones HTTP y datos ficticios para estudiar la API |
| `docs/alumnado/` | Guías de entrada y lectura del código |
| `docs/adr/`, `docs/observations/` | Decisiones de arquitectura y explicaciones técnicas y docentes |
| `docs/contracts/` | Contrato de intercambio CSV |
| `container/`, `Containerfile`, `compose.yaml` | Despliegue, copias de seguridad y restauración |

## Funcionalidad y límites

Puedes crear tableros, columnas y tarjetas; mover tarjetas con el ratón o los controles de la interfaz; gestionar etiquetas y listas de comprobación; exportar CSV e importar un archivo tras revisarlo. SQLite conserva los datos y las migraciones se aplican automáticamente al arrancar.

La aplicación incluye sesiones, protección CSRF, consultas parametrizadas y verificadores de contraseña PBKDF2. Es una referencia educativa de instalación local con un administrador inicial. El registro público, la colaboración entre usuarios, la recuperación de contraseña desde la interfaz y la configuración HTTPS de producción quedan fuera de esta versión.

## Pruebas y despliegue opcional

`clean verify` es la comprobación completa; no hace falta definir credenciales de administrador para ejecutarla. Las pruebas crean sus propios datos temporales. [AulaFlow CI](https://github.com/JaumeLloret/aulaflow/actions) verifica Maven con Java 26 y la imagen de contenedor.

Para estudiar Podman, persistencia, backup y restauración, sigue el [procedimiento de reproducción y despliegue](docs/release/1.0.0.md). Empieza por la ejecución local con el JAR.

## Trabajar con tu copia

Consulta [cómo experimentar y proponer cambios](CONTRIBUTING.md). Comprender este proyecto no implica reproducir de memoria todas sus piezas de seguridad o infraestructura. El recorrido para segundo distingue la base que debes comprender de las partes que puedes consultar cuando las necesites.

No subas contraseñas, cookies, tokens, archivos `.env`, datos locales ni copias de bases de datos. Las credenciales de prueba y los CSV incluidos son ejemplos ficticios.

## Procedencia y licencia

Esta referencia parte del proyecto completo de AulaFlow y conserva su interfaz final. Tiene un historial independiente y documentación adaptada al alumnado. La [nota de adaptación](docs/REFERENCIA.md) describe la base y los cambios realizados.

Se conserva la licencia [Unlicense](LICENSE) elegida para este repositorio. Las dependencias mantienen sus propias licencias.
