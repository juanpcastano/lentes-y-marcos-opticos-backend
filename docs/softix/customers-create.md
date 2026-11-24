# Softix: creación de clientes (`POST /api/Customers/Create`)

Referencia verificada el 2026-11-10 contra el sandbox de Softix.

## Fuentes

- Esquema oficial (OpenAPI): `https://api.softix.app/swagger/v1/swagger.json`, schema `Client`.
- Ruta oficial: `/api/Customers/Create` (con mayúsculas). La documentación en `https://softix.site/docs` muestra un ejemplo, pero no lista los obligatorios.

## Campos obligatorios

Según el esquema, son 24:

`authorization`, `birthDate`, `blockedSales`, `bloodType`, `city`, `consultationValue`, `country`, `createdBy`, `creationBranch`, `creditDays`, `documentId`, `documentType`, `email`, `firstName`, `gender`, `locality`, `maxCreditLimit`, `personType`, `priceList`, `regime`, `rural`, `taxes`, `taxResponsibility`.

Restricciones del esquema:

- `mobile`: exactamente 10 caracteres.
- `documentType`: máximo 2 caracteres.

## Lo que el esquema no dice

- **Los nulos no se aceptan en campos de texto opcionales**, aunque el esquema los marque como `nullable: true`. Al enviar `null` en `middleName`, `phone`, etc., Softix responde `400 Could not save changes`. Enviar `""` funciona. Por eso el DTO `CustomerCreateRequest` convierte esos nulos en cadena vacía.
- Si faltan obligatorios, el mensaje sí los nombra (`"Regime is REQUIRED"`), pero si faltan valores que no son obligatorios en el esquema pero sí en la base de datos, el error es genérico.

## Valores de catálogo observados

No existe un endpoint de catálogo para estos valores. Se tomaron de una muestra de 1000 clientes del sandbox (`GET /api/customers/list`):

| Campo | Valor usado | Frecuencia en la muestra |
|---|---|---|
| `regime` | `49` | 999 de 1000 |
| `priceList` | `1` | 977 de 1000 |
| `taxResponsibility` / `taxes` | `ZZ` | 709 de 1000 (288 vacíos) |
| `personType` | `Natural` | Para compradores individuales |
| `country` | `CO` | 991 de 1000 |
| `creationBranch` | `1` (configurable) | 2 es el más común (708), seguido de 1 (289) |
| `createdBy` | `1` (configurable) | ID de usuario de Softix; `1` es el más común |

`creationBranch` y `createdBy` se configuran con `SOFTIX_CREATION_BRANCH` y
`SOFTIX_CREATED_BY`. Para clientes web, los valores acordados son `1` y `1`.

## Decisiones cerradas

- `bloodType = "NE"` es el valor acordado para clientes web. Softix acepta
  `NE` como "No especificado". En sandbox rechaza `No especificado`,
  `No aplica`, `Desconocido` y textos de 6 o más caracteres; acepta cualquier
  texto de hasta 5 caracteres.

## Pendientes operativos

- `authorization = 1` indica autorización de tratamiento de datos. Hoy se
  envía fijo; el consentimiento real debe definirse en el flujo de registro.
- Verificar los valores de producción de `regime` y `priceList` con una
  consulta de solo lectura antes de habilitar escrituras en producción.
