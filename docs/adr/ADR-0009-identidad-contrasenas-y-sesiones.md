# ADR-0009: Identidad, contraseñas y sesiones de servidor

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Estado: aceptada
* Fecha: 2026-07-30
* Proyecto: AulaFlow 1.0
* Incremento: 1.0
* Issue: #7

## Contexto

AulaFlow ya dispone de HTTP, interfaz bilingüe y persistencia SQLite.
El incremento 1.0 debe incorporar un único administrador inicial,
autenticación mediante contraseña, sesiones de servidor y logout sin
añadir un framework web o de seguridad.

La decisión debe proteger secretos, mantener JDBC fuera del dominio,
preservar los contratos HTTP existentes y seguir siendo observable con
fines docentes.

## Objetivos

La solución debe:

* aprovisionar el primer administrador sin credenciales versionadas;
* persistir un verificador de contraseña resistente a ataques offline;
* utilizar sales independientes y parámetros versionados;
* evitar diferencias públicas entre usuario desconocido y contraseña
  incorrecta;
* crear identificadores de sesión impredecibles en el servidor;
* expirar e invalidar sesiones de forma explícita;
* proteger rutas mediante un componente reutilizable;
* funcionar localmente por HTTP y poder endurecer la cookie tras HTTPS;
* permitir pruebas deterministas sin reducir la seguridad de producción;
* no anticipar autorización Kanban, recuperación, roles u OAuth.

## Aprovisionamiento inicial

Una base vacía leerá:

```text
AULAFLOW_ADMIN_USERNAME
AULAFLOW_ADMIN_PASSWORD
```

No habrá usuario ni contraseña predeterminados.

Si no existe administrador, ambas variables serán obligatorias y el
arranque fallará de forma controlada cuando falte alguna o sea inválida.
El mensaje podrá nombrar la variable ausente, pero nunca su valor.

Si el administrador ya existe, el aprovisionamiento será idempotente y
no creará otro registro. Las variables podrán retirarse después del
primer arranque. El administrador persistirá en SQLite.

El nombre se normalizará a minúsculas y se limitará a entre 3 y 64
caracteres ASCII de letras, números, punto, guion y guion bajo.

La contraseña:

* tendrá entre 15 y 128 puntos de código Unicode;
* admitirá espacios y no impondrá reglas de composición;
* se normalizará mediante Unicode NFC antes de derivarla;
* nunca se recortará silenciosamente;
* no aparecerá en logs, excepciones públicas o documentación real.

El límite superior reduce el riesgo de consumo desproporcionado durante
la derivación.

## Derivación de contraseña

Se utilizará:

```text
PBKDF2WithHmacSHA256
```

Parámetros iniciales:

```text
iteraciones: 600000
sal: 16 bytes aleatorios
resultado: 32 bytes
aleatoriedad: SecureRandom
```

`PBKDF2WithHmacSHA256` forma parte de los algoritmos que toda
implementación de Java 26 debe soportar. El factor de trabajo coincide
con la recomendación vigente de OWASP para PBKDF2-HMAC-SHA-256.

El formato persistido será autocontenido y versionado:

```text
pbkdf2-sha256$v1$600000$<sal-base64url>$<derivado-base64url>
```

El formato permite reconocer algoritmo, versión, factor de trabajo, sal
y derivado. No contiene la contraseña.

La comparación utilizará `MessageDigest.isEqual`. Las matrices
temporales de caracteres y bytes se limpiarán cuando sea viable.

Un usuario desconocido realizará una verificación PBKDF2 contra un
verificador ficticio generado en memoria. Así se evita una diferencia
obvia entre “no existe” y “contraseña incorrecta”. La respuesta pública
será siempre genérica.

### Alternativas descartadas

#### SHA-256 directo

Es demasiado rápido para contraseñas y no incorpora por sí solo un factor
de trabajo adecuado.

#### Cifrar la contraseña

La aplicación no necesita recuperar el texto original. Una función de
derivación es la herramienta apropiada.

#### Argon2id mediante una dependencia nueva

Argon2id es una alternativa recomendada, pero Java 26 ya garantiza
PBKDF2-HMAC-SHA-256. Para el alcance local, docente y sin framework,
añadir otra biblioteca y su ciclo de actualización no aporta una ventaja
suficiente frente a la opción estándar configurada correctamente.

#### Credencial predeterminada

Se descarta porque convertiría el repositorio en una fuente de
credenciales reutilizables.

## Persistencia y arquitectura

La migración `V002` creará exclusivamente la tabla del administrador. No
creará usuarios públicos, roles ni tablas Kanban.

El dominio representará la identidad administradora sin conocer:

* el verificador de contraseña;
* JDBC o SQLite;
* cookies;
* sesiones HTTP;
* variables de entorno.

La aplicación definirá puertos para:

* persistir y recuperar las credenciales del administrador;
* derivar y verificar contraseñas;
* almacenar sesiones;
* generar identificadores de sesión;
* limitar intentos fallidos.

La infraestructura implementará:

* el repositorio mediante SQLite y `PreparedStatement`;
* PBKDF2 y `SecureRandom`;
* el almacén de sesiones en memoria;
* el límite temporal de intentos.

La presentación será responsable de formularios, parsing HTTP, cookies,
redirecciones y respuestas.

La dirección continúa siendo:

```text
presentation -> application -> domain
infrastructure -> application/domain
```

## Login e intentos fallidos

El formulario enviará datos mediante `POST` y
`application/x-www-form-urlencoded`. Se limitará el tamaño del cuerpo y
se validarán nombre y contraseña en el límite HTTP antes del caso de uso.

Un login correcto:

1. verifica la contraseña;
2. elimina el registro de fallos de esa identidad;
3. genera una sesión completamente nueva;
4. no acepta ni reutiliza un identificador aportado por el cliente;
5. devuelve la cookie y redirige a la página privada.

Usuario desconocido, contraseña incorrecta y límite temporal alcanzado
comparten un mensaje de credenciales genérico.

Se permitirán cinco fallos por identidad normalizada dentro de una
ventana de un minuto. El límite se conserva únicamente en memoria y se
reinicia con el proceso. Evita intentos rápidos sin introducir bloqueos
persistentes ni recuperación de cuentas fuera de alcance.

## Sesiones

Las sesiones se almacenarán en el servidor mediante una instancia
inyectada y no estática.

El identificador contendrá 32 bytes generados con `SecureRandom` y se
codificará con Base64 URL sin relleno. Solo el identificador viajará en
la cookie.

Cada sesión asociará:

* identidad administradora;
* instante de creación;
* instante de expiración.

La duración absoluta inicial será de 30 minutos. Un `Clock` inyectable
permitirá probar expiración sin esperas reales.

Las sesiones:

* se eliminan al expirar;
* se invalidan durante logout;
* no pueden recuperarse reutilizando la cookie;
* se pierden deliberadamente al reiniciar AulaFlow.

Persistir sesiones se pospone porque no lo exige el producto local y
aumentaría la exposición de identificadores. Reiniciar fuerza un nuevo
login, mientras que el administrador sí permanece.

## Cookie

El nombre será:

```text
AULAFLOW_SESSION
```

La cookie de una sesión válida incluirá:

```text
Path=/
HttpOnly
SameSite=Strict
```

No incluirá `Domain`, datos personales, usuario, hash ni contraseña.
Será una cookie de sesión del navegador: la expiración autoritativa
permanece en el servidor.

La variable:

```text
AULAFLOW_SESSION_COOKIE_SECURE
```

tendrá `false` como valor local predeterminado para permitir el
desarrollo explícito mediante `http://127.0.0.1`. En todo despliegue tras
HTTPS deberá configurarse como `true`, añadiendo `Secure`.

No se utilizará todavía el prefijo `__Host-`, porque exige `Secure` y
haría que la misma cookie no funcionara en el flujo HTTP local aprobado.

El logout enviará la misma cookie con valor vacío, `Max-Age=0` y los
mismos atributos de alcance y seguridad.

## Contrato HTTP

Se incorporan:

```text
GET  /login
POST /login
GET  /account
POST /logout
```

`GET /login` devuelve el formulario HTML.

`POST /login`:

* devuelve `303 See Other` hacia `/account` y crea la cookie cuando las
  credenciales son correctas;
* devuelve `401 Unauthorized` con el formulario y un mensaje genérico
  cuando no lo son;
* devuelve un error controlado para un cuerpo, tipo o campos inválidos.

`GET /account` es la demostración privada mínima del contrato de
autenticación. No incorpora datos Kanban. Sin sesión válida redirige
mediante `303` a `/login`.

`POST /logout` invalida la sesión conocida, expira la cookie y redirige a
`/login`. La repetición produce el mismo resultado seguro.

Los métodos no admitidos devuelven `405` con `Allow`. Las rutas se
comparan exactamente. Todas las respuestas conservan `X-Request-Id`,
`Cache-Control: no-store` y `X-Content-Type-Options: nosniff`.

No se registran cuerpos, cabeceras `Cookie`, valores `Set-Cookie`,
contraseñas, verificadores ni identificadores de sesión.

## Protección reutilizable

Un decorador de presentación:

1. extraerá la cookie;
2. consultará el caso de uso;
3. rechazará una sesión ausente, desconocida o expirada;
4. asociará la identidad autenticada únicamente al `HttpExchange`
   concreto;
5. delegará en el endpoint privado.

No se utilizará estado estático ni se modificará `/health`.

## CSRF

Ninguna petición `GET` modificará estado.

Las cookies utilizarán `SameSite=Strict`. Además, las operaciones `POST`
rechazarán expresamente peticiones cuyo encabezado
`Sec-Fetch-Site` sea `cross-site`.

Para el alcance actual —login y logout, una sola procedencia local y
ninguna operación Kanban— esta defensa limita las peticiones
automáticas cross-site sin introducir un sistema de tokens que todavía
no tiene consumidores adicionales.

`SameSite` se considera defensa en profundidad, no una solución universal.
Los tokens sincronizados y una política de origen completa deberán
revisarse antes de incorporar operaciones autenticadas con efectos de
negocio.

## Interfaz e internacionalización

El formulario y la página privada:

* usarán HTML semántico;
* tendrán etiquetas visibles y foco claro;
* funcionarán por teclado;
* serán mobile-first;
* ofrecerán castellano y valenciano;
* conservarán valenciano como contenido inicial y de reserva;
* no dependerán de JavaScript para enviar login o logout.

Los catálogos mantendrán exactamente las mismas claves.

## Consecuencias positivas

* No se versionan secretos ni credenciales predeterminadas.
* La contraseña queda protegida por una función lenta y parametrizable.
* El formato permite evolucionar parámetros y algoritmo.
* JDBC, criptografía y HTTP permanecen en adaptadores.
* Las sesiones no sobreviven accidentalmente a un reinicio.
* La protección puede reutilizarse en futuros endpoints.
* La interfaz funciona sin un framework ni JavaScript obligatorio.
* El reloj y los generadores pueden sustituirse en pruebas.

## Consecuencias negativas

* PBKDF2 consume tiempo deliberadamente en cada verificación.
* El aprovisionamiento exige preparar variables de entorno una vez.
* Reiniciar la aplicación cierra todas las sesiones.
* El modo local HTTP no puede utilizar una cookie `Secure`.
* El límite de intentos en memoria no se comparte entre procesos.
* La aplicación implementa explícitamente tareas que un framework de
  seguridad proporcionaría.
* La protección CSRF deberá ampliarse cuando crezca la superficie de
  operaciones autenticadas.

## Riesgos

* publicar las variables iniciales en una captura o archivo;
* registrar accidentalmente cuerpos o cookies;
* reducir iteraciones para acelerar pruebas;
* aceptar identificadores de sesión aportados por el cliente;
* olvidar `Secure` tras HTTPS;
* comparar rutas por prefijo;
* revelar si el usuario existe;
* usar esperas reales para probar expiración;
* confundir autenticación con permisos Kanban;
* considerar `SameSite` una protección completa para cualquier alcance.

## Alternativas descartadas

* contraseña, hash o sal dentro del repositorio;
* SHA-256 directo;
* cifrado reversible de contraseñas;
* framework completo de seguridad;
* JWT en el navegador;
* sesión en `localStorage`;
* sesión global estática;
* identificador elegido por el cliente;
* sesión persistente en SQLite;
* cookie sin `HttpOnly` o `SameSite`;
* cookie `Secure` incondicional que impida el desarrollo HTTP local;
* cambios de estado mediante `GET`;
* registro público, recuperación, roles, OAuth o SSO;
* tablas Kanban o autorización por tablero.

## Criterios de revisión

La decisión se revisará cuando:

* se despliegue AulaFlow tras HTTPS;
* aparezcan más operaciones autenticadas con efectos de negocio;
* se necesiten tokens CSRF sincronizados;
* exista más de un proceso o instancia;
* sea necesario persistir o revocar sesiones de forma compartida;
* aparezcan varios usuarios o roles;
* cambien las recomendaciones de coste de la función de derivación;
* una auditoría justifique Argon2id u otro mecanismo;
* se incorpore recuperación de cuenta o segundo factor.

## Referencias

* [Java 26: SecretKeyFactory](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/javax/crypto/SecretKeyFactory.html)
* [Java 26: SecureRandom](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/security/SecureRandom.html)
* [Java 26: MessageDigest](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/security/MessageDigest.html)
* [NIST SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html)
* [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
* [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
* [OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
* [RFC 6265: HTTP State Management Mechanism](https://www.rfc-editor.org/rfc/rfc6265.html)
* [RFC6265bis draft](https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis/)
