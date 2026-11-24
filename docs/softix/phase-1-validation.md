# Validación de la Fase 1: integración base con Softix

Última validación: 2026-11-24, ambiente sandbox.

## Smoke test contra Softix

La autenticación se ejecutó con las credenciales de `.sandbox.env`. No se
registran tokens, contraseñas ni datos personales en este documento.

| Operación | Resultado |
|---|---:|
| `POST /api/account/login` | `200` |
| `GET /api/customers/1000000099` | `200` |
| `GET /api/customers/0000000000` | `404` esperado |
| `GET /api/Inventories/available/M628` | `200` |
| `GET /api/Inventories/products/list/MONTURA?page=1&pageSize=1` | `200` |
| `GET /api/Inventarios/monturas/lista/1` | `200` |
| `GET /api/Inventarios/monturas/lista/2` | `200` |
| `GET /api/schedules/1/2026-11-24` | `200` |

La consulta actual de disponibilidad para `M628` devolvió `0`; el valor de
documentos anteriores no se considera evidencia vigente.

La creación de cliente ya fue validada previamente en sandbox y está
documentada en [customers-create.md](./customers-create.md), incluyendo la
consulta posterior del cliente creado.

## Cobertura automatizada

`SoftixClientTest` cubre:

- autenticación Bearer;
- reutilización del token;
- renovación y reintento tras `401`;
- rutas de disponibilidad, catálogo, stock y horarios;
- deserialización de los DTOs principales;
- paginación del catálogo;
- campos obligatorios y normalización del DTO de creación de clientes.

El cliente configura timeout de conexión y lectura mediante:

```properties
SOFTIX_CONNECT_TIMEOUT_MS
SOFTIX_READ_TIMEOUT_MS
```

Los logs del cliente solo registran códigos HTTP o mensajes genéricos y no
incluyen tokens ni credenciales.

## Decisiones cerradas y límites

- Para clientes web, `SOFTIX_CREATION_BRANCH=1` y
  `SOFTIX_CREATED_BY=1`.
- Los valores de régimen, lista de precios y demás catálogos se mantienen
  según la configuración validada en sandbox.
- La validación de creación de clientes continúa exclusivamente en sandbox
  hasta recibir autorización explícita para producción.
