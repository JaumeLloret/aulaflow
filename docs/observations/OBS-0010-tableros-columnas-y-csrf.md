# OBS-0010: Tableros, columnas ordenadas y protección CSRF

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

* Fecha: 2026-07-31
* Proyecto: AulaFlow 1.0
* Incremento: 1.1

## Contexto docente

Este incremento presenta al alumnado de 1.º DAM el primer vertical de
negocio completo. Un tablero atraviesa dominio, aplicación, SQLite,
HTTP, interfaz y pruebas sin permitir que los detalles de una capa
invadan las demás.

El objetivo no es memorizar todas las clases. El alumnado debe poder
seguir un dato, justificar las invariantes y reconocer dónde se valida,
autoriza, persiste y representa.

## Prerrequisitos

Conviene haber trabajado:

* clases, records, interfaces, colecciones y excepciones;
* JDBC, `PreparedStatement`, claves foráneas y `try-with-resources`;
* migraciones, transacciones, commit y rollback;
* petición, respuesta, sesión, cookie y códigos HTTP;
* HTML semántico, formularios e internacionalización;
* pruebas JUnit y bases temporales con `@TempDir`.

## Conceptos nuevos

* Entidad, identificador estable y objeto valor.
* Agregado de tablero con columnas.
* Pertenencia y autorización por recurso.
* Orden explícito frente a orden accidental.
* Posiciones contiguas empezando en cero.
* Operación compuesta atómica.
* Permutación completa sin duplicados.
* Puerto de repositorio nacido de casos de uso reales.
* Token CSRF sincronizado y ligado a sesión.
* Mejora progresiva: operación esencial sin JavaScript.
* API con representación JSON y formularios codificados.

## Mapa arquitectónico

```text
presentación HTTP
        ↓
BoardService
        ↓
BoardRepository
        ↑
SqliteBoardRepository
```

El dominio contiene nombres, identificadores, tablero, columna y sus
invariantes. Aplicación coordina casos de uso y define el puerto que
necesita. Infraestructura implementa SQL y transacciones. Presentación
traduce sesión, formularios, rutas, JSON y HTML.

El dominio no conoce `Connection`, `PreparedStatement`, `ResultSet`,
SQLite, HTTP, cookies, rutas ni tokens CSRF.

## Secuencia didáctica recomendada

### 1. Identidad frente a posición

Mover una columna cambia su posición, no su identidad:

```text
ColumnId(7), posición 2
        ↓ mover
ColumnId(7), posición 0
```

Usar la posición como identificador rompería referencias estables.

### 2. Invariantes de nombres y orden

Los nombres se recortan, no pueden quedar vacíos y tienen un límite de
puntos de código. El orden válido contiene `0..n-1` sin huecos.

Conviene diferenciar caracteres Java (`char`) de puntos de código Unicode
mediante un nombre con un símbolo fuera del plano básico.

### 3. Pertenencia

Toda consulta incorpora el administrador autenticado. Un tablero ajeno y
uno inexistente producen el mismo `404`; la interfaz no es un mecanismo
de autorización.

Aunque AulaFlow tenga ahora un único administrador, modelar la pertenencia
evita que el primer vertical nazca con una frontera incorrecta.

### 4. Creación atómica

Crear un tablero incluye sus tres columnas iniciales. Si falla una parte,
no debe quedar medio tablero:

```text
BEGIN
  INSERT tablero
  INSERT Per fer
  INSERT En curs
  INSERT Fet
COMMIT
```

Ante una excepción se ejecuta rollback.

### 5. Reordenación

La entrada contiene todos los identificadores una vez. El repositorio
asigna primero posiciones temporales fuera del rango final y después
`0..n-1`, todo dentro de una transacción. Así no colisiona con
`UNIQUE(board_id, position)`.

### 6. Token CSRF

La cookie viaja automáticamente con peticiones del navegador. Por eso una
mutación autenticada necesita una prueba adicional que otro sitio no
pueda conocer.

Cada sesión recibe un token aleatorio. Los formularios lo envían oculto y
la API mediante `X-CSRF-Token`. `SameSite=Strict` y el rechazo
`cross-site` siguen siendo defensa en profundidad.

El token nunca debe aparecer en una URL, captura, log o documento.

### 7. API e interfaz

La misma aplicación ofrece HTML utilizable sin JavaScript y JSON para un
cliente HTTP. Esto permite comparar representación, estado y caso de uso:
la ruta cambia, pero la regla de negocio no se duplica.

### 8. Persistencia y reinicio

Tableros, columnas y posiciones permanecen en SQLite. Sesión y token se
pierden al reiniciar. La prueba manual obliga a distinguir ambos ciclos
de vida.

## Coordinación por módulos

### Programación

* Records e invariantes del dominio.
* Colecciones, listas inmutables y permutaciones.
* Interfaces y servicios de aplicación.
* Excepciones de validación y recurso inexistente.
* Parsing controlado y escape HTML/JSON.
* Pruebas unitarias, dobles y de integración.

### Entornos de Desarrollo

* Migraciones `V003` a `V006`.
* Restricciones e índices SQLite.
* `PreparedStatement` y `try-with-resources`.
* Bases aisladas mediante `@TempDir`.
* Cartero, cabeceras, cookies y token CSRF.
* Maven Wrapper, Java 26, CI y revisión del diff.
* Reinicio sobre la misma ruta de base.

### Proyecto Intermodular

* Criterios de aceptación de la issue #9.
* Trazabilidad entre ADR-0010, código, pruebas y manual.
* Alcance del primer vertical Kanban.
* Riesgos de pertenencia y seguridad como requisitos de producto.
* Límites frente a tarjetas, colaboración y roles.

## Errores previsibles

* usar la posición como identificador;
* ordenar por `id` y creer que representa la intención;
* aceptar una lista incompleta o con duplicados;
* actualizar posiciones sin transacción;
* concatenar nombres dentro de SQL;
* consultar por tablero sin añadir el propietario;
* ocultar un enlace y considerarlo autorización;
* guardar el token CSRF en SQLite o en una URL;
* confiar solo en `SameSite`;
* traducir automáticamente datos ya guardados;
* duplicar reglas en cada manejador HTTP;
* utilizar una base compartida entre pruebas;
* confundir la pérdida de sesión con pérdida del tablero.

## Recuperación guiada

1. Confirmar JDK 26 y Maven Wrapper.
2. Revisar sesión y base seleccionada sin mostrar secretos.
3. Volver a cargar la página para obtener el token de la sesión actual.
4. Diferenciar `400`, `401`, `403` y `404`.
5. Comprobar que el orden contiene todos los identificadores una vez.
6. Reproducir con una base temporal.
7. Ejecutar prueba focalizada de dominio, JDBC o HTTP.
8. Ejecutar `./mvnw clean verify`.
9. No editar manualmente posiciones ni historial de migraciones.

## Evidencias evaluables

* Explicar identidad frente a posición.
* Dibujar el recorrido de crear un tablero.
* Justificar el puerto de repositorio.
* Localizar la comprobación de propietario.
* Explicar commit y rollback de creación y reordenación.
* Identificar campo oculto y cabecera CSRF.
* Demostrar `403` sin modificar datos.
* Reiniciar y distinguir estado persistente de estado de sesión.
* Clasificar pruebas como unitarias, JDBC, HTTP o contrato.

## Decisión pedagógica

El alumnado debe poder leer y modificar primero una regla pequeña,
acompañada de su prueba. No se espera que diseñe de forma autónoma el
repositorio transaccional, el protocolo CSRF o todo el vertical HTTP.

Permanecen fuera de esta secuencia tarjetas, arrastrar y soltar, permisos
granulares, concurrencia colaborativa, ORM y frameworks.
