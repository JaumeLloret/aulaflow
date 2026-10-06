# INC-0005 — `.containerignore` bloquea el JAR de release

```text
Descripción:
Durante la primera CI del release candidate 1.0.0, Maven construyó correctamente el nuevo JAR, pero la fase de imagen Podman no pudo copiarlo al contenedor.

Síntoma:
El job `Maven verify · Java 26` finalizó correctamente con 403 pruebas verdes y generó `target/aulaflow-1.0.0.jar`. El job `Container image · Podman` falló en el paso `COPY target/aulaflow-1.0.0.jar /opt/aulaflow/aulaflow.jar` indicando que el fichero había sido filtrado por `.containerignore`.

Causa:
`.containerignore` excluía `target/*` y conservaba una excepción específica del artefacto anterior: `!target/aulaflow-0.1.0-SNAPSHOT.jar`. Al cambiar la versión Maven a `1.0.0`, el nuevo JAR dejó de estar incluido en el contexto de construcción.

Pruebas realizadas:
- el guard inicial de metadatos pasó;
- `./mvnw clean verify` pasó en CI;
- 403 pruebas finalizaron sin fallos ni errores;
- Maven mostró `Building jar: .../target/aulaflow-1.0.0.jar`;
- Podman falló exactamente al intentar el `COPY` del JAR;
- se revisó `.containerignore` y se localizó la excepción antigua.

Solución:
Actualizar `.containerignore` a `!target/aulaflow-1.0.0.jar` e incorporar ese archivo al guard de release. El guard comprueba también de forma explícita que la excepción del JAR 1.0.0 exista, para que un futuro cambio de versión no deje silenciosamente desalineados Maven, Containerfile y contexto de construcción.

Cómo comprobar la recuperación:
1. ejecutar `bash container/verify-release-metadata.sh`;
2. ejecutar `./mvnw clean verify`;
3. ejecutar `bash container/build-image.sh`;
4. comprobar que `localhost/aulaflow:1.0.0` se construye;
5. confirmar en CI que `Container image · Podman` termina en verde.

Valor docente:
El incidente muestra que una release no depende únicamente del código Java. Los archivos de contexto de contenedor también forman parte del contrato de construcción. Una prueba funcional completamente verde puede coexistir con un artefacto no desplegable. Es un caso claro para explicar integración continua, configuración como código, trazabilidad de versiones y por qué una release debe verificar la cadena completa desde fuentes hasta artefacto ejecutable.
```
