# Primer arranque de AulaFlow

El objetivo es conseguir una instalación local que puedas usar, detener y volver a arrancar. Al terminar tendrás tu propio usuario, un tablero de prueba y sus datos conservados en SQLite.

## 1. Descargar y comprobar el entorno

Descarga el ZIP desde **Code → Download ZIP** en [el repositorio](https://github.com/JaumeLloret/aulaflow), o clona con Git. Descomprime antes de abrirlo; debes ver `pom.xml`, `mvnw`, `mvnw.cmd` y `src/` en la carpeta principal.

Instala un **JDK 26** para tu sistema y arquitectura. Tiene que incluir `java` y `javac`. Abre una terminal nueva y comprueba:

```bash
java --version
javac --version
```

Ambos deben indicar la versión 26. Si usas `JAVA_HOME`, debe apuntar a la carpeta del JDK, no a su subcarpeta `bin`. IntelliJ y la terminal pueden estar usando JDK distintos; configura ambos.

## 2. Compilar y ejecutar las pruebas

Abre una terminal **dentro de la carpeta que contiene `pom.xml`**.

Windows, PowerShell:

```powershell
.\mvnw.cmd --version
.\mvnw.cmd clean verify
```

macOS o Linux:

```bash
chmod +x mvnw
./mvnw --version
./mvnw clean verify
```

El wrapper debe indicar Maven 3.9.16 y Java 26. La primera ejecución descarga Maven y las dependencias. Espera a **BUILD SUCCESS**; se genera `target/aulaflow-1.0.0.jar`. Las pruebas no requieren que definas un usuario ni modifican tu instalación local.

## 3. Crear tu administrador y arrancar desde la terminal

Una base nueva necesita dos variables de entorno. Tú eliges los valores; no vienen incluidos en el proyecto.

| Variable | Cuándo hace falta | Valor y restricciones |
| --- | --- | --- |
| `AULAFLOW_ADMIN_USERNAME` | Cuando la base aún no tiene administrador | 3–64 caracteres: letras minúsculas ASCII, números, punto, guion y guion bajo |
| `AULAFLOW_ADMIN_PASSWORD` | En el mismo primer arranque | Una contraseña propia de 15–128 puntos de código Unicode |

El nombre `estudiante` de los ejemplos puede cambiarse. Introduce una contraseña propia, de al menos 15 caracteres; la terminal la pide sin mostrarla ni escribirla literalmente en el historial de comandos.

Windows, en la **misma PowerShell** donde vas a ejecutar Java:

```powershell
$env:AULAFLOW_ADMIN_USERNAME = 'estudiante'
$clave = Read-Host 'Elige tu contraseña de al menos 15 caracteres' -AsSecureString
$env:AULAFLOW_ADMIN_PASSWORD = ([System.Net.NetworkCredential]::new('', $clave)).Password
java -jar target/aulaflow-1.0.0.jar
```

macOS o Linux, en **Bash**:

```bash
export AULAFLOW_ADMIN_USERNAME='estudiante'
read -r -s -p 'Elige tu contraseña de al menos 15 caracteres: ' AULAFLOW_ADMIN_PASSWORD
printf '\n'
export AULAFLOW_ADMIN_PASSWORD
java -jar target/aulaflow-1.0.0.jar
```

Si tu terminal de macOS usa zsh, ejecuta primero `bash` para utilizar ese bloque. El servidor ocupa la terminal mientras funciona. Para detenerlo, pulsa **Ctrl+C**.

Después de detenerlo puedes retirar las variables de esa terminal:

```powershell
Remove-Item Env:AULAFLOW_ADMIN_USERNAME, Env:AULAFLOW_ADMIN_PASSWORD
Remove-Variable clave
```

```bash
unset AULAFLOW_ADMIN_USERNAME AULAFLOW_ADMIN_PASSWORD
```

En el siguiente arranque, basta `java -jar target/aulaflow-1.0.0.jar` si utilizas la misma base. Conserva tu contraseña: cambiar las variables iniciales no modifica la cuenta ya creada.

## 4. Alternativa: ejecutar desde IntelliJ IDEA

1. Selecciona **Open** y abre la carpeta del proyecto. Espera la importación de Maven.
2. En **File → Project Structure → Project**, selecciona **JDK 26** y nivel de lenguaje 26 sin preview.
3. En la configuración de Maven, selecciona el **Maven Wrapper** y JDK 26 para la importación y ejecución.
4. En **Run → Edit Configurations**, crea una configuración **Application**.

| Campo | Valor |
| --- | --- |
| Name | `AulaFlow local` |
| Main class | `es.aulaflow.AulaFlowApplication` |
| JRE / SDK | JDK 26 |
| Use classpath of module | El módulo `aulaflow` importado desde Maven |
| Working directory | `$PROJECT_DIR$`, la carpeta que contiene `pom.xml` |
| VM options | `--enable-native-access=ALL-UNNAMED` para la ejecución desde clases del IDE |
| Program arguments | Vacío |
| Environment variables | `AULAFLOW_ADMIN_USERNAME` y `AULAFLOW_ADMIN_PASSWORD`, con tus valores, si la base está vacía |

Las variables se introducen con el editor de **Environment variables**, cada nombre y valor por separado. Son variables de entorno, no argumentos del programa ni opciones de la JVM. Mantén la configuración local y no selecciones **Store as project file** para guardar credenciales. `.idea/` está excluido de Git.

Pulsa **Run**. Una vez creada la cuenta, puedes quitar las dos variables de esta configuración. La cuenta sigue existiendo en SQLite. El JAR ya declara el acceso nativo en su manifiesto; la opción de la tabla corresponde a la ejecución desde el IDE.

## 5. Comprobar que funciona y conserva los datos

1. Abre **http://127.0.0.1:8080/** y entra en el formulario de inicio de sesión.
2. Accede con el usuario y la contraseña elegidos.
3. Crea un tablero llamado «Mi primera semana» y una tarjeta «Revisar AulaFlow».
4. Mueve la tarjeta a otra columna y añade un elemento a su lista de comprobación.
5. Detén el servidor y arráncalo de nuevo desde la misma carpeta.
6. Inicia sesión otra vez. Comprueba que el tablero, la tarjeta y su posición siguen ahí.

Abre también **http://127.0.0.1:8080/api/v1/health**. Debe devolver JSON con estado `UP` y versión `1.0.0`.

SQLite se crea en **`data/aulaflow.db`**, relativa al directorio de trabajo. El servidor prepara la carpeta y aplica las migraciones automáticamente. Las sesiones viven en memoria y se pierden al reiniciar; los tableros viven en SQLite y permanecen.

## 6. Configuración y problemas frecuentes

Las opciones siguientes se pueden definir en la terminal o en el mismo editor de variables de IntelliJ. Para el primer arranque local conviene dejar sus valores predeterminados.

| Variable | Predeterminado | Qué cambia |
| --- | --- | --- |
| `AULAFLOW_HTTP_HOST` | `127.0.0.1` | Dirección de escucha local |
| `AULAFLOW_HTTP_PORT` | `8080` | Puerto, un entero entre 1 y 65535 |
| `AULAFLOW_DB_PATH` | `data/aulaflow.db` | Archivo SQLite; una ruta relativa se resuelve desde el directorio de trabajo |
| `AULAFLOW_ENV` | `development` | Nombre de entorno mostrado por el servidor |
| `AULAFLOW_SESSION_COOKIE_SECURE` | `false` | Debe seguir en `false` con HTTP local; `true` exige que el navegador acceda mediante HTTPS |

| Problema | Qué comprobar y cómo recuperarte |
| --- | --- |
| Java no aparece o Maven rechaza su versión | Revisa `JAVA_HOME`, `PATH` y el JDK seleccionado en IntelliJ. Abre una terminal nueva y repite las comprobaciones. |
| `mvnw` no tiene permisos en Linux/macOS | Ejecuta `chmod +x mvnw`. En Windows utiliza `mvnw.cmd`. |
| Falla una descarga de Maven | Comprueba Internet o el proxy del centro y vuelve a ejecutar el wrapper. No omitas las pruebas para ocultar el error. |
| «Una base sin administrador requiere…» | Define las dos variables en el proceso que ejecuta Java. Comprueba que no las introdujiste como argumentos. |
| Contraseña o nombre rechazados al arrancar | Corrige sus valores para cumplir las restricciones de la tabla y vuelve a arrancar. |
| El puerto ya está ocupado | Detén la otra instancia o define `AULAFLOW_HTTP_PORT=8081`; usa también ese puerto en el navegador. |
| La cuenta no acepta la nueva contraseña de las variables | La base ya tenía una cuenta; utiliza su contraseña original. Estas variables no restablecen contraseñas. |
| Parece que han desaparecido los tableros | Comprueba la carpeta desde la que arrancaste y `AULAFLOW_DB_PATH`: puede que hayas abierto otra base. Detén el proceso y vuelve a la ruta correcta. |
| El login parece correcto pero vuelve a pedirlo | Usa siempre el mismo host (`127.0.0.1`) y comprueba que `AULAFLOW_SESSION_COOKIE_SECURE` no sea `true` sobre HTTP. |
| Una petición de API devuelve 401 | Requiere una sesión autenticada. Una pestaña o cliente HTTP distinto puede no tener la cookie de sesión. |

Si olvidas la contraseña, conserva la base y consulta al profesor: esta versión no incluye recuperación desde la interfaz. Para ensayar una instalación independiente puedes elegir otra ruta con `AULAFLOW_DB_PATH` y crear una cuenta nueva allí. No borres una base que contiene trabajo que quieres conservar.

Cuando termines, continúa con el [recorrido para segundo de DAM](02-recorrido-para-segundo.md).
