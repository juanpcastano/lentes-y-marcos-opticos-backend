# Validación de la Fase 1: integración base con Softix

Última validación: smoke test en sandbox el 2026-11-24; revisión de OpenAPI y
lectura de producción el 2026-11-26.

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

## Revisión del OpenAPI (2026-11-26)

Revisión de `https://api.softix.app/swagger/v1/swagger.json` sin ejecutar
escrituras:

| Ruta | Hallazgo |
|---|---|
| `PUT /api/Customers/{id}` | Existe. Body `Client` con los 24 obligatorios; probado en sandbox el 2026-11-26. |
| `GET /api/Customers/DocumentTypes/List` | Existe catálogo de tipos de documento (`DocumentTypeSoftix`). |
| `GET /api/Customers/List` | Solo `page` y `pageSize`. Sin búsqueda ni orden. |
| `GET /api/Customers/List/code/{nit}` | Devuelve `documentNumber` y `code`; no es búsqueda por móvil. |
| `POST /api/PortalClientes/app/login` | Softix tiene portal de clientes con email/contraseña. No se usa: el email está vacío en producción. |

El listado no garantiza orden estable. En una lectura de producción aparecieron
documentos repetidos y en las siguientes no; para sincronizar hay que
deduplicar por `documentId` y validar contra `totalRecords`.

### Verificación del `PUT` (sandbox, 2026-11-26)

Se creó el cliente de prueba `1000000199` y se actualizó con el body completo:

| Operación | Resultado |
|---|---|
| `POST /api/Customers/Create` | `201` con sobre `{status, data}` |
| `GET /api/customers/1000000199` | `200` con sobre `{message, customer}` |
| `PUT /api/Customers/1000000199` | `200` con sobre `{status, data}` |
| `GET` posterior | `200`; el campo editado cambió |

Hallazgos:

- `creationDate` se reescribe con la hora del `PUT`; no se puede preservar.
- `createdBy` y `creationBranch` sí se respetan; hay que reenviar los valores
  leídos de Softix.
- Los nombres reales son `lastName`, `secondLastName`, `locality`,
  `consultationValue`, `blockedSales` y `maxCreditLimit`.
- `gender` usa `Masculino`/`Femenino`.
- `email` es obligatorio y válido: `""` devuelve `400` (`The Email is
  REQUIRED`); un relleno como `3000000199@sin-correo.invalid` se acepta. No se
  usa para login ni notificaciones.

El detalle está en [customers-create.md](./customers-create.md).

El análisis de móviles de producción y el flujo de identidad están en
[customer-identity.md](./customer-identity.md).

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
