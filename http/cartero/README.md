# Peticiones HTTP de AulaFlow para Cartero

Esta carpeta contiene peticiones HTTP reutilizables para comprobar manualmente los contratos HTTP de AulaFlow 1.0.

## Herramienta utilizada

AulaFlow utiliza Cartero como cliente HTTP gráfico oficial para el desarrollo y las demostraciones docentes. Cartero permite ejecutar las peticiones sin utilizar una cuenta, servicios en la nube ni funciones de pago.

Las comprobaciones automáticas no dependen de Cartero; se realizan mediante JUnit, `java.net.http.HttpClient` y Maven.

## Requisitos previos

Antes de ejecutar una petición:

1. Abrir AulaFlow en IntelliJ IDEA.
2. Ejecutar `AulaFlowApplication`.
3. Comprobar que la consola muestra: `Estado: servidor iniciado`.
4. Comprobar que la dirección base es: `http://127.0.0.1:8080`.

## Cómo ejecutar una petición

1. Abrir Cartero.
2. Utilizar el atajo `Ctrl + O`.
3. Seleccionar uno de los archivos `.cartero`.
4. Revisar el método, la URL y las cabeceras.
5. Ejecutar la petición mediante `Ctrl + Enter`.

## Peticiones disponibles

| Archivo | Petición | Estado esperado |
| :--- | :--- | :---: |
| `health/01-health-ok.cartero` | `GET /api/v1/health` | 200 |
| `health/02-health-query.cartero` | `GET /api/v1/health?source=cartero` | 200 |
| `health/03-health-method-not-allowed.cartero` | `POST /api/v1/health` | 405 |
| `health/04-unknown-resource.cartero` | `GET /api/v1/unknown` | 404 |
| `health/05-health-prefix-not-found.cartero` | `GET /api/v1/health/details` | 404 |
| `1.0-authentication/01-login-form.cartero` | `GET /login` | 200 |
| `1.0-authentication/02-invalid-login.cartero` | `POST /login` inválido | 401 |
| `1.0-authentication/03-valid-login-template.cartero` | `POST /login` válido | 303 |
| `1.0-authentication/04-private-account.cartero` | `GET /account` | 200 o 303 |
| `1.0-authentication/05-logout.cartero` | `POST /logout` | 303 |
| `1.0-authentication/06-cross-site-login-rejected.cartero` | `POST /login` cross-site | 403 |
| `1.0-authentication/07-login-method-not-allowed.cartero` | `PUT /login` | 405 |
| `1.1-personal-boards/01-list-boards.cartero` | `GET /api/v1/boards` | 200 o 401 |
| `1.1-personal-boards/02-create-board-template.cartero` | `POST /api/v1/boards` | 201 |
| `1.1-personal-boards/03-rename-board-template.cartero` | `PATCH /api/v1/boards/{id}` | 200 |
| `1.1-personal-boards/04-add-column-template.cartero` | `POST /api/v1/boards/{id}/columns` | 200 |
| `1.1-personal-boards/05-reorder-columns-template.cartero` | `PUT /api/v1/boards/{id}/columns/order` | 200 |
| `1.1-personal-boards/06-invalid-csrf.cartero` | `POST /api/v1/boards` sin token válido | 403 |
| `1.2-cards-workflow/01-create-card.cartero` | `POST /api/v1/boards/{id}/columns/{colId}/cards` | 201 |
| `1.2-cards-workflow/02-get-board-with-cards.cartero` | `GET /api/v1/boards/{id}` con tarjetas | 200 |
| `1.2-cards-workflow/03-edit-card.cartero` | `PATCH /api/v1/boards/{id}/cards/{cardId}` | 200 |
| `1.2-cards-workflow/04-move-card.cartero` | `PUT /api/v1/boards/{id}/cards/{cardId}/position` | 200 |
| `1.2-cards-workflow/05-delete-card.cartero` | `DELETE /api/v1/boards/{id}/cards/{cardId}` | 204 |
| `1.2-cards-workflow/06-invalid-csrf.cartero` | `POST /api/v1/boards/{id}/columns/{colId}/cards` sin token | 403 |
| `1.2-cards-workflow/07-wrong-owner.cartero` | `PATCH /api/v1/boards/999/cards/999` ajeno | 404 |
| `1.2-cards-workflow/08-invalid-position.cartero` | `PUT` con `targetPosition=9999` | 400 |
| `1.2-cards-workflow/09-wrong-method.cartero` | `PUT /api/v1/boards/{id}/columns/{colId}/cards` | 405 |
| `1.4-labels-checklists/01-create-label.cartero` | `POST /api/v1/boards/{id}/labels` | 200 |
| `1.4-labels-checklists/02-edit-label.cartero` | `PATCH /api/v1/boards/{id}/labels/{labelId}` | 200 |
| `1.4-labels-checklists/03-assign-label.cartero` | `PUT /api/v1/boards/{id}/cards/{cardId}/labels/{labelId}` | 200 |
| `1.4-labels-checklists/04-unassign-label.cartero` | `DELETE /api/v1/boards/{id}/cards/{cardId}/labels/{labelId}` | 204 |
| `1.4-labels-checklists/05-delete-label.cartero` | `DELETE /api/v1/boards/{id}/labels/{labelId}` | 204 |
| `1.4-labels-checklists/06-invalid-csrf.cartero` | `POST /api/v1/boards/{id}/labels` sin token | 403 |
| `1.4-labels-checklists/07-create-checklist-item.cartero` | `POST .../cards/{cardId}/checklist-items` | 201 |
| `1.4-labels-checklists/08-toggle-checklist-item.cartero` | `PUT .../checklist-items/{itemId}/completed` | 200 |
| `1.4-labels-checklists/09-move-checklist-item.cartero` | `PUT .../checklist-items/{itemId}/position` | 200 |
| `1.4-labels-checklists/10-delete-checklist-item.cartero` | `DELETE .../checklist-items/{itemId}` | 204 |
| `1.4-labels-checklists/11-edit-checklist-item.cartero` | `PATCH .../checklist-items/{itemId}` | 200 |
| `2.0-csv-import-export/01-validate-valid-csv.cartero` | `POST /api/v1/boards/import/validate` con CSV válido | 200 |
| `2.0-csv-import-export/02-confirm-import.cartero` | `POST /api/v1/boards/import` con token | 201 |
| `2.0-csv-import-export/03-get-imported-board.cartero` | `GET /api/v1/boards/{id}` del tablero importado | 200 |
| `2.0-csv-import-export/04-export-board.cartero` | `GET /api/v1/boards/{id}/export.csv` | 200 |
| `2.0-csv-import-export/05-validate-invalid-csv.cartero` | `POST .../import/validate` con encabezado inválido | 400 |
| `2.0-csv-import-export/06-invalid-csrf.cartero` | `POST .../import/validate` con token CSRF inválido | 403 |
| `2.0-csv-import-export/07-oversized-file.cartero` | `POST .../import/validate` con archivo &gt;1 MiB | 413 |
| `2.0-csv-import-export/08-confirm-invalid-token.cartero` | `POST /api/v1/boards/import` con token inexistente | 400 |

## Recorrido de autenticación

1. Ejecutar la petición `01` para comprobar el formulario.
2. Ejecutar `02` y observar el mensaje genérico.
3. Abrir `03`, reemplazar temporalmente las dos variables por las
   credenciales de la base y ejecutar sin guardar el secreto.
4. Comprobar `Set-Cookie` ocultando su valor. Debe incluir `Path=/`,
   `HttpOnly` y `SameSite=Strict`.
5. Ejecutar `04` conservando la cookie de la sesión. Debe devolver `200`;
   sin cookie devuelve `303` hacia `/login`.
6. Obtener `_csrf` del formulario privado, introducirlo solo en memoria
   en `05`, ejecutar logout y volver a probar `04`.
7. Ejecutar `06` y `07` para observar rechazos controlados.

No deben guardarse contraseñas ni valores de `AULAFLOW_SESSION` en estos
archivos, capturas, documentación o commits. Cartero complementa las
pruebas automáticas; no sustituye la comprobación de expiración ni el
reinicio del proceso.

## Recorrido de tableros

1. Iniciar sesión y conservar la cookie únicamente en Cartero.
2. Abrir `/boards` y tomar el token oculto solo para esta sesión.
3. Introducirlo temporalmente en `CSRF_TOKEN` sin guardar el archivo.
4. Ejecutar `01` y `02`; copiar el identificador creado a `BOARD_ID`.
5. Ejecutar `03` y `04`.
6. Leer el tablero, formar una lista con todos los identificadores de
   columna una sola vez y ejecutar `05`.
7. Ejecutar `06` y comprobar que devuelve `403` sin crear datos.
8. Recargar el tablero y comprobar nombres y orden.

La cookie y el token pertenecen al proceso actual. Después de reiniciar
AulaFlow hay que iniciar sesión y obtener un token nuevo.

## Recorrido de tarjetas

1. Iniciar sesión y conservar la cookie únicamente en Cartero.
2. Abrir `/boards`, crear un tablero y copiar su identificador a `BOARD_ID`.
3. Abrir el tablero y tomar el `id` de una columna; copiarlo a `COLUMN_ID`.
4. Tomar el token CSRF del formulario e introducirlo temporalmente en `CSRF_TOKEN` sin guardar el archivo.
5. Ejecutar `01` y copiar el identificador de la tarjeta creada a `CARD_ID`.
6. Ejecutar `02` y comprobar que la respuesta incluye la tarjeta bajo su columna.
7. Ejecutar `03` con `CARD_ID` obtenido y verificar el título actualizado.
8. Ejecutar `04` con `TARGET_COLUMN_ID` apuntando a otra columna del tablero.
9. Ejecutar `05` y verificar que la respuesta es `204` sin cuerpo.
10. Ejecutar `06` y comprobar que devuelve `403` sin crear datos.
11. Ejecutar `07` y comprobar que devuelve `404`.
12. Ejecutar `08` y comprobar que devuelve `400`.
13. Ejecutar `09` y comprobar que devuelve `405` con cabecera `Allow`.

La cookie y el token pertenecen al proceso actual. Después de reiniciar AulaFlow hay que iniciar sesión y obtener un token nuevo.

## Recorrido de etiquetas y checklist

1. Iniciar sesión, conservar la cookie únicamente en Cartero y crear un tablero con al menos una tarjeta.
2. Copiar `BOARD_ID` y `CARD_ID` desde las respuestas anteriores y el token CSRF del formulario, sin guardarlo.
3. Ejecutar `01` y copiar el `id` de la etiqueta creada a `LABEL_ID`.
4. Ejecutar `02` y comprobar el nuevo nombre y color en la respuesta.
5. Ejecutar `03` y comprobar que la tarjeta devuelta incluye la etiqueta bajo `labels`.
6. Ejecutar `03` una segunda vez y comprobar que sigue devolviendo `200` (asignación idempotente).
7. Ejecutar `04` y comprobar `204` sin cuerpo.
8. Ejecutar `05` y comprobar que la etiqueta desaparece del tablero sin eliminar la tarjeta.
9. Ejecutar `06` y comprobar que devuelve `403` sin crear ninguna etiqueta.
10. Ejecutar `07` y copiar el `id` del elemento creado a `ITEM_ID`.
11. Ejecutar `11` y comprobar el texto actualizado.
12. Ejecutar `08` y comprobar `"completed":true` en la respuesta.
13. Ejecutar `09` con otro elemento existente y comprobar el nuevo orden en el tablero.
14. Ejecutar `10` y comprobar `204`, y que el resto de elementos compacta su posición.

La cookie y el token pertenecen al proceso actual. Después de reiniciar AulaFlow hay que iniciar sesión y obtener un token nuevo.

## Recorrido completo del corte vertical (1.5)

El incremento 1.5 no añade peticiones nuevas: enlaza las colecciones ya existentes en un único recorrido de principio a fin, tal como pediría una demostración docente o una comprobación manual completa.

1. `1.0-authentication/01-login-form.cartero` → formulario de login.
2. `1.0-authentication/03-valid-login-template.cartero` → iniciar sesión con credenciales reales, sin guardarlas; conservar la cookie y el token CSRF de la sesión solo en memoria.
3. `1.0-authentication/04-private-account.cartero` → comprobar que `/account` es accesible.
4. `1.1-personal-boards/02-create-board-template.cartero` → crear un tablero; copiar el `id` a `BOARD_ID`.
5. `1.1-personal-boards/04-add-column-template.cartero` → confirmar que las columnas iniciales y la creación de columnas funcionan.
6. `1.2-cards-workflow/01-create-card.cartero` → crear una tarjeta; copiar el `id` a `CARD_ID`.
7. `1.2-cards-workflow/04-move-card.cartero` → mover la tarjeta.
8. `1.4-labels-checklists/01-create-label.cartero` → crear una etiqueta; copiar el `id` a `LABEL_ID`.
9. `1.4-labels-checklists/03-assign-label.cartero` → asignarla a la tarjeta.
10. `1.4-labels-checklists/07-create-checklist-item.cartero` → crear un elemento de checklist; copiar el `id` a `ITEM_ID`.
11. `1.4-labels-checklists/08-toggle-checklist-item.cartero` → marcarlo.
12. `1.2-cards-workflow/02-get-board-with-cards.cartero` → releer el tablero y comprobar tarjeta, etiqueta y checklist anidados con el progreso correcto.
13. `1.1-personal-boards/06-invalid-csrf.cartero` o `1.4-labels-checklists/06-invalid-csrf.cartero` → comprobar `403` sin cambios en los datos.
14. `1.2-cards-workflow/07-wrong-owner.cartero` → comprobar `404` ante un recurso ajeno o inexistente.
15. `1.0-authentication/05-logout.cartero` → cerrar sesión.
16. `1.0-authentication/04-private-account.cartero` (sin cookie válida) → confirmar que `/account` ya no es accesible.

La cookie y el token CSRF pertenecen al proceso actual; tras reiniciar AulaFlow hay que repetir los pasos 2-3 para obtener una sesión nueva.

## Recorrido de importación y exportación CSV (2.0)

Las fixtures CSV de este recorrido están en
`http/cartero/2.0-csv-import-export/fixtures/`. Cada archivo `.cartero`
incluye su propio cuerpo CSV en línea; las fixtures sirven de
referencia y para pruebas manuales adicionales (por ejemplo, abrirlas
directamente con LibreOffice Calc).

1. `1.0-authentication/03-valid-login-template.cartero` → iniciar
   sesión con credenciales reales, sin guardarlas; conservar la cookie
   solo en memoria.
2. Abrir `/boards/import` en el navegador (o `/boards` con sesión
   activa) y copiar el token CSRF de la sesión actual a `CSRF_TOKEN`
   sin guardar el archivo.
3. Ejecutar `01-validate-valid-csv.cartero` y comprobar `200` con
   `"valid":true` y un `token` en la respuesta.
4. Copiar ese `token` a `IMPORT_TOKEN` en `02-confirm-import.cartero`,
   sin guardarlo.
5. Ejecutar `02-confirm-import.cartero` y comprobar `201` con cabecera
   `Location`; copiar el `id` a `BOARD_ID`.
6. Ejecutar `03-get-imported-board.cartero` y comprobar que el tablero,
   las columnas y las tarjetas coinciden con el CSV importado.
7. Ejecutar `04-export-board.cartero` y comprobar `200`,
   `Content-Type: text/csv; charset=UTF-8`,
   `Content-Disposition: attachment` y `Cache-Control: no-store`.
8. Comparar el CSV exportado con el archivo de fixture original
   `valid-ca.csv`: deben coincidir en tablero, columnas, tarjetas y
   orden (sin comparar IDs ni marcas de tiempo).
9. Ejecutar `05-validate-invalid-csv.cartero` y comprobar `400` con
   errores públicos (encabezado inválido) y sin `token`.
10. Ejecutar `06-invalid-csrf.cartero` y comprobar `403` sin validar
    ningún dato.
11. Ejecutar `07-oversized-file.cartero` y comprobar `413`. Este
    archivo supera 1 MiB a propósito.
12. Ejecutar `08-confirm-invalid-token.cartero` y comprobar `400` con
    un token inexistente.
13. `1.0-authentication/05-logout.cartero` → cerrar sesión.

La cookie y el token CSRF pertenecen al proceso actual. Después de
reiniciar AulaFlow hay que repetir el paso 1 para obtener una sesión
nueva. Ninguna fixture ni archivo `.cartero` de este recorrido
contiene identificadores fijos ni secretos.

## Resultados esperados

### Health correcto
La petición debe devolver:
- Estado HTTP `200`.
- Cabecera `Content-Type: application/json; charset=utf-8`.
- Cabecera `Cache-Control: no-store`.
- Estado de la aplicación.
- Versión.
- Entorno.
- Instante UTC.

### Método no permitido
La petición `POST /api/v1/health` debe devolver:
- Estado HTTP `405`.
- Cabecera `Allow: GET`.
- Código JSON `METHOD_NOT_ALLOWED`.

### Recurso inexistente
Las rutas inexistentes deben devolver:
- Estado HTTP `404`.
- Código JSON `RESOURCE_NOT_FOUND`.

## Trazabilidad de peticiones

Todas las respuestas generadas por AulaFlow incluyen la cabecera:

`X-Request-Id`

Ejemplo:

`X-Request-Id: e19b6898-ecc2-4307-9005-2bfd1d151b5d`

Las respuestas de error incluyen el mismo valor dentro del cuerpo JSON.

Para comprobar la trazabilidad:

1. Ejecutar una petición desde Cartero.
2. Localizar `X-Request-Id` en las cabeceras.
3. En respuestas de error, comprobar el campo `requestId`.
4. Localizar el mismo identificador en la consola de AulaFlow.
5. Repetir la petición y comprobar que recibe otro identificador.

El request ID sirve para relacionar una petición, una respuesta y los
registros producidos durante su procesamiento.

## Papel de Cartero en el proyecto

Cartero sirve para:
- Observar peticiones y respuestas.
- Comprobar cabeceras.
- Comparar métodos HTTP.
- Experimentar manualmente.
- Documentar contratos.
- Realizar demostraciones en el aula.

**Nota:** Cartero no sustituye las pruebas automáticas. La verificación oficial del proyecto continúa siendo: `Maven verify → JUnit → java.net.http.HttpClient`.
