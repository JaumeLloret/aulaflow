# Trabajar con tu copia de AulaFlow

Puedes descargar la referencia sin modificarla, hacer un fork para practicar o crear una rama en una copia local.

## Preparar un cambio pequeño

1. Sigue la [guía de primer arranque](docs/alumnado/01-primer-arranque.md) y ejecuta la verificación completa.
2. Si utilizas Git, crea una rama: `git switch -c practica/mi-cambio`.
3. Define el comportamiento que quieres cambiar y localiza su clase y sus pruebas con el [mapa del código](docs/alumnado/03-mapa-del-codigo.md).
4. Modifica una responsabilidad concreta y ejecuta las pruebas relacionadas. Si cambias comportamiento, comprueba un caso válido y uno inválido que expliquen el cambio.
5. Ejecuta `./mvnw clean verify` o `.\mvnw.cmd clean verify` antes de compartirlo.
6. Revisa `git diff` y `git status`. Explica qué cambió y cómo lo comprobaste.

## Convenciones

- Java 26 sin preview; Maven Wrapper; texto UTF-8.
- Dominio independiente de HTTP y SQLite.
- Servicios con contratos de repositorio; consultas JDBC parametrizadas en la infraestructura.
- Sesión, autorización y CSRF conservados al modificar HTTP.
- Claves equivalentes en los dos catálogos de idioma.
- Documentación de uso actualizada cuando cambie la ejecución o una pantalla.

No compartas datos personales, contraseñas, cookies, tokens, archivos `.env`, configuración privada del IDE, `target/`, `data/` ni backups. Los valores de pruebas y ejemplos son ficticios y no sirven para acceder a una instalación real.

Las prácticas y entregas se rigen por Aules. Una pull request a este repositorio propone una mejora de la referencia y no sustituye una entrega del módulo.
