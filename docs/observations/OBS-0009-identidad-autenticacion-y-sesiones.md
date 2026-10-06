# OBS-0009: Identidad, autenticación y sesiones

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Fecha: 2026-07-31
* Proyecto: AulaFlow 1.0
* Incremento: 1.0

## Contexto docente

Este incremento introduce la primera frontera de seguridad de AulaFlow
para alumnado de 1.º de DAM. El objetivo no es que el alumnado diseñe
criptografía ni un sistema completo de identidades, sino que pueda leer,
ejecutar, probar y explicar un flujo de autenticación construido con las
API del JDK.

La secuencia parte de una única identidad administradora. El
administrador persiste en SQLite, mientras que las sesiones viven en
memoria. Esta diferencia permite separar con claridad identidad,
credencial y sesión antes de incorporar el dominio Kanban.

## Prerrequisitos

Antes de trabajar este incremento conviene dominar:

* clases, records, interfaces y paquetes;
* excepciones y validación;
* colecciones;
* `Optional`;
* JDBC, `PreparedStatement` y `try-with-resources`;
* migraciones, commit y rollback;
* petición, respuesta, cabeceras, métodos y códigos HTTP;
* variables de entorno y pruebas JUnit.

No es necesario conocer previamente PBKDF2 ni el formato detallado de una
cookie. Esos conceptos se presentan mediante contratos ya decididos en
ADR-0009.

## Conceptos nuevos

* Identidad, credencial y autenticación.
* Normalización de un identificador.
* Política de contraseña.
* Contraseña en texto plano frente a verificador derivado.
* Sal aleatoria, iteraciones y formato criptográfico versionado.
* `PBKDF2WithHmacSHA256`, `SecureRandom` y comparación sin salida temprana.
* Puerto de aplicación y adaptador de infraestructura.
* Trabajo ficticio para no distinguir un usuario desconocido.
* Limitación temporal de intentos.
* Identificador opaco de sesión.
* Expiración absoluta y reloj sustituible.
* Cookie `HttpOnly`, `SameSite` y `Secure`.
* Fijación de sesión, logout e invalidación.
* Protección de una ruta.
* Operación segura mediante método HTTP.
* Peticiones `same-site` y `cross-site`.
* Persistencia del administrador frente a volatilidad de la sesión.

## Separación arquitectónica

El dominio contiene la identidad y sus invariantes:

```text
Administrator
AdministratorUsername
```

La aplicación coordina los casos de uso y define los contratos que
necesita:

```text
ProvisionInitialAdministrator
AuthenticationService
AdministratorRepository
PasswordHasher
SessionStore
SessionIdGenerator
LoginAttemptLimiter
```

La infraestructura implementa SQLite, PBKDF2, aleatoriedad y los
almacenes en memoria. La presentación traduce HTTP, formularios, cookies
y páginas. Por tanto, el dominio no importa JDBC, SQLite, HTTP, cookies,
rutas ni algoritmos criptográficos.

El alumnado puede seguir esta dirección:

```text
presentación → aplicación → dominio
infraestructura → aplicación / dominio
```

## Secuencia didáctica recomendada

### 1. Separar identidad, contraseña y sesión

Utilizar tres preguntas:

```text
¿Quién dice ser?       → identidad
¿Cómo lo demuestra?   → credencial
¿Cómo se recuerda?    → sesión
```

Una sesión no sustituye al administrador persistido. Al reiniciar,
desaparece la sesión, pero no la identidad.

### 2. Aprovisionar sin credenciales predeterminadas

Presentar `AULAFLOW_ADMIN_USERNAME` y `AULAFLOW_ADMIN_PASSWORD` como
entradas exclusivas del primer arranque de una base vacía. Después,
retirarlas.

No existe una respuesta a «¿cuál es la contraseña de AulaFlow?». La
persona que instala el proyecto elige el primer nombre y contraseña.
`admin` es un ejemplo válido de nombre local, pero no debe ofrecerse una
contraseña predeterminada para copiar.

Antes de ejecutar, el alumnado debe comprobar:

```text
nombre       3..64
caracteres   a-z 0-9 . _ -
contraseña   15..128 puntos de código Unicode
```

La configuración se enseña con un recorrido observable:

1. `Run → Edit Configurations`;
2. seleccionar `AulaFlowApplication`;
3. comprobar la raíz del proyecto en `Working directory`;
4. abrir la tabla de `Environment variables`;
5. añadir las dos variables iniciales;
6. mantener `AULAFLOW_SESSION_COOKIE_SECURE=false` en HTTP local;
7. dejar desactivado `Store as project file`;
8. ejecutar y localizar `Estado: servidor iniciado`.

Conviene contrastar visualmente `Environment variables` con
`Program arguments` y `VM options`. Los tres campos no son
intercambiables: la aplicación solo consulta el entorno.

Debe quedar claro que una variable de entorno evita versionar un secreto,
pero no autoriza a mostrar el entorno completo ni a registrar su valor.
Tampoco justifica crear un `.env` versionado o incluir toda la tabla en
una captura.

La ruta predeterminada `data/aulaflow.db` se resuelve desde
`Working directory`. Modificar ese directorio o `AULAFLOW_DB_PATH` puede
seleccionar otra base vacía. Este detalle explica por qué una aplicación
que «ya tenía administrador» puede volver a solicitar las variables.

SQLite conserva el nombre, el verificador PBKDF2 y la fecha de creación,
nunca la contraseña en texto plano. Cuando ya existe el administrador,
las variables pueden retirarse. Cambiarlas no modifica la credencial
persistida.

### 3. Derivar, no cifrar, la contraseña

La contraseña no necesita recuperarse. Se transforma con:

```text
contraseña + sal + 600 000 iteraciones
                    ↓
          verificador PBKDF2
```

La sal no es secreta y debe ser distinta. El formato versionado permite
reconocer algoritmo y parámetros. No se pide al alumnado que elija esos
parámetros: aplica la decisión documentada.

### 4. Ocultar diferencias observables

Un nombre desconocido y una contraseña incorrecta comparten respuesta y
realizan trabajo criptográfico comparable. La práctica permite hablar de
enumeración sin exponer detalles ofensivos.

El límite de cinco fallos durante un minuto se entiende como protección
local básica, no como un sistema distribuido definitivo.

### 5. Crear una sesión opaca

Tras un login correcto, el servidor genera 32 bytes aleatorios y los
codifica como Base64 URL sin relleno. El navegador solo recibe un
identificador; los datos de la sesión permanecen en el servidor.

El uso de `Clock` en las pruebas muestra cómo sustituir una dependencia
temporal sin esperar realmente 30 minutos.

### 6. Observar la cookie

Estudiar cada atributo por separado:

* `Path=/`: se aplica a toda la aplicación;
* `HttpOnly`: JavaScript no puede leerla;
* `SameSite=Strict`: restringe su envío entre sitios;
* `Secure`: se activa cuando el despliegue usa HTTPS.

`Secure=false` es una concesión limitada al desarrollo HTTP local, no una
recomendación de despliegue.

### 7. Proteger y cerrar

`GET /account` demuestra una frontera privada mínima. `POST /logout`
invalida la sesión y expira la cookie. Repetir el logout no produce un
estado inconsistente.

El alumnado debe justificar por qué login y logout no cambian estado
mediante `GET`.

### 8. Probar reinicio y recuperación

Una prueba manual completa diferencia dos evidencias:

```text
administrador después del reinicio → persiste
cookie anterior después del reinicio → deja de autenticar
```

Esto hace visible una decisión arquitectónica que sería difícil observar
solo con pruebas unitarias.

## Coordinación por módulos

### Programación

* Modelado de invariantes con tipos.
* Interfaces y adaptadores.
* Arrays de caracteres y limpieza defensiva.
* PBKDF2, sales y aleatoriedad segura como API ya configurada.
* Colecciones concurrentes para estado efímero.
* `Clock`, `Duration` e `Instant`.
* Parsing controlado de formularios y cookies.
* Pruebas unitarias, JDBC y HTTP.

### Entornos de Desarrollo

* Variables de entorno en IntelliJ IDEA y diferencia frente a argumentos.
* Configuración local no compartida mediante `Store as project file`.
* Relación entre `Working directory`, rutas relativas y base seleccionada.
* Maven Wrapper y JDK 26.
* Migraciones del esquema.
* Secretos fuera de Git.
* Cartero y herramientas del navegador para observar HTTP.
* Pruebas con bases temporales.
* CI, trazabilidad y diagnóstico de respuestas.
* Diferencia entre configuración local HTTP y despliegue HTTPS.

### Proyecto Intermodular

* Criterios de aceptación de la issue #7.
* Decisiones y consecuencias de ADR-0009.
* Límites entre capas.
* Riesgos de seguridad como requisitos del producto.
* Continuidad hacia el Kanban sin anticiparlo.
* Evidencias reproducibles: código, pruebas, documentación y devlog.

## Errores previsibles

* Guardar o comparar una contraseña en texto plano.
* Confundir hash rápido con derivación de contraseña.
* Reutilizar la misma sal.
* Inventar parámetros criptográficos en cada clase.
* Concatenar el nombre en una consulta SQL.
* Revelar si un usuario existe.
* Registrar contraseña, cookie o verificador.
* Usar un identificador de sesión predecible.
* Guardar sesiones en una variable `static`.
* Renovar accidentalmente la expiración absoluta.
* Compartir una conexión SQLite global.
* Omitir `HttpOnly` o `SameSite`.
* Activar `Secure` en HTTP local y pensar que el login ha fallado.
* Implementar logout con `GET`.
* Confiar únicamente en ocultar un enlace para proteger una ruta.
* Esperar que la sesión sobreviva a un reinicio.
* Buscar credenciales predeterminadas dentro del repositorio.
* Inventar una contraseña común para toda la clase.
* Escribir variables de entorno como argumentos de programa.
* Activar `Store as project file` con una contraseña.
* Cambiar de directorio de trabajo sin advertir que cambia la base.
* Esperar que modificar las variables cambie un administrador persistido.

## Recuperación guiada

1. Confirmar JDK 26 y `./mvnw`.
2. Confirmar que las variables están en `Environment variables`.
3. Comprobar la ruta de base y el directorio de trabajo.
4. Verificar si la base está vacía antes de exigir las variables iniciales.
5. Revisar los límites sin imprimir la contraseña.
6. Mantener desactivado `Store as project file`.
7. Distinguir `401`, `403`, `303` y `405`.
8. Comprobar los atributos de `Set-Cookie` sin copiar su valor.
9. Esperar un minuto si el limitador está activo.
10. Desactivar `AULAFLOW_SESSION_COOKIE_SECURE` solo para HTTP local.
11. Reproducir el problema con una base temporal y credenciales ficticias.
12. Ejecutar primero la prueba focalizada y después `clean verify`.

No debe «recuperarse» el acceso editando manualmente el verificador,
mostrando secretos o borrando el historial de migraciones.

## Actividades y evidencias

1. Dibujar las capas recorridas por un login.
2. Comparar dos verificadores de la misma contraseña y explicar sus sales.
3. Avanzar un reloj de prueba hasta expirar una sesión.
4. Identificar los atributos de `Set-Cookie`.
5. Probar un login válido, uno inválido, logout y reutilización de cookie.
6. Reiniciar y explicar qué estado se conserva.
7. Clasificar cada prueba como unitaria, JDBC, HTTP o contrato.
8. Relacionar cada criterio de la issue con una evidencia.
9. Configurar un primer administrador sin guardar ni mostrar su
   contraseña.
10. Cambiar temporalmente `Working directory` y explicar por qué aparece
    otra base.

Son evidencias evaluables la explicación del flujo, una prueba
reproducible, la ausencia de secretos, la correcta separación de capas y
la interpretación de códigos y cabeceras.

## Decisión pedagógica

El código avanzado bien diseñado se conserva porque hace visibles
decisiones reales. Se presenta de forma gradual y con pruebas antes de
pedir modificaciones.

El alumnado no debe diseñar autónomamente en esta fase:

* algoritmos o parámetros criptográficos;
* autenticación distribuida;
* rotación o recuperación de credenciales;
* tokens CSRF genéricos;
* autorización por roles;
* repositorios Kanban;
* persistencia de sesiones.

Esos temas requieren nuevos casos de uso y, cuando corresponda, nuevas
decisiones documentadas.
