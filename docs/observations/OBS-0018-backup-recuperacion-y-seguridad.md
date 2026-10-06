# OBS-0018 — Backup, recuperación y seguridad

> Documento técnico de la evolución del proyecto. Para ejecutarlo hoy, sigue la [guía de primer arranque](../alumnado/01-primer-arranque.md).

## Observación

El incremento 2.2 contiene conceptos que superan claramente el nivel inicial de 1.º DAM si se presentan como una receta cerrada: consistencia de copias, validación previa a restaurar, movimientos atómicos, sidecars SQLite, defensa HTTP en profundidad y distinción entre error útil y filtración de información.

No se simplifica el diseño correcto. Se secuencia y se explica.

## Idea docente central

Una copia no es un backup fiable solo porque exista otro fichero `.db`.

El alumnado debe poder razonar sobre este flujo:

```text
datos activos
    ↓
copia consistente
    ↓
validación
    ↓
almacenamiento separado
    ↓
ensayo de restauración
    ↓
backup realmente verificable
```

La restauración introduce otra idea fundamental:

```text
validar antes de destruir
```

Una copia corrupta debe fallar antes de modificar los datos activos.

## Programación

Conceptos aprovechables:

- separación de responsabilidades entre configuración, servicio y validador;
- excepciones y recuperación ante fallos;
- `Path`, ficheros temporales y operaciones atómicas;
- JDBC y `try-with-resources`;
- validación defensiva de entradas;
- inyección de `Clock` para pruebas deterministas;
- pruebas de integración con datos reales temporales.

La API nativa de backup de SQLite y los detalles de sidecars pueden mostrarse como infraestructura avanzada, no como requisito de memorización inicial.

## Entornos de Desarrollo

Es el módulo natural para trabajar:

- diferencia entre copia y backup;
- RPO/RTO a nivel introductorio;
- volúmenes y ciclo de vida de datos;
- ensayo de recuperación;
- permisos mínimos;
- logs operativos;
- cabeceras HTTP de seguridad;
- revisión de dependencias;
- evidencia automatizada frente a evidencia manual.

## Proyecto Intermodular

El valor está en cerrar el ciclo de operación:

```text
construir → desplegar → usar → fallar → recuperar → verificar
```

Esto permite que el alumnado entienda que un producto no está terminado cuando “funciona en mi ordenador”, sino cuando puede operarse y recuperarse de forma documentada.

## Seguridad

La secuencia recomendada es explicar primero el riesgo y después el control:

- robo de cookie → `HttpOnly`, `Secure`, `SameSite`;
- petición mutante cruzada → CSRF;
- acceso a recursos ajenos → autorización por propietario;
- framing → CSP `frame-ancestors` y `X-Frame-Options`;
- inyección de recursos → CSP;
- fuga en diagnósticos → logs controlados;
- backup manipulado → validación antes de restore.

Las cabeceras no deben enseñarse como una lista para copiar sin entender. Cada una debe vincularse a un riesgo concreto.

## Qué no exigir inicialmente al alumnado

- implementar la SQLite Online Backup API sin guía;
- diseñar por sí solo un restore atómico;
- memorizar directivas CSP;
- evaluar criptografía aplicada desde cero;
- diseñar retención empresarial, SIEM o HA.

## Evidencia esperada

Al finalizar esta parte, el alumnado debería poder explicar por qué:

1. copiar un fichero de una base activa puede ser una mala política;
2. un backup debe validarse y restaurarse en un ensayo;
3. el restore debe proteger primero la base válida;
4. logs y mensajes de error forman parte de la superficie de seguridad;
5. autenticación, autorización y CSRF resuelven problemas distintos;
6. la seguridad se construye por capas y se verifica sin romper la aplicación.
