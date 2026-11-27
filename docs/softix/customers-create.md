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

No existe un endpoint de catálogo para `regime`, `priceList` ni `personType`. Se tomaron de una muestra de 1000 clientes del sandbox (`GET /api/customers/list`). Para los tipos de documento sí existe catálogo (ver más abajo):

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

- `authorization = 1` indica autorización de tratamiento de datos. Debe enviarse
  `1` solo cuando el usuario aceptó la política; la aceptación se registra
  localmente con fecha, versión e IP. No se hereda ni se envía fijo.
- Verificar los valores de producción de `regime` y `priceList` con una
  consulta de solo lectura antes de habilitar escrituras en producción.

## Observación de producción: clientes existentes

Validación de solo lectura realizada el 2026-11-25 sobre los 2.460 registros
devueltos por `GET /api/customers/list?page=...&pageSize=100` en producción.
No se realizaron escrituras.

| Campo | Registros con valor |
|---|---:|
| `documentId`, `documentType`, `firstName`, `birthDate`, `city`, `locality` | 2.460 |
| `lastName` | 2.459 |
| `mobile` | 2.459 |
| `gender` | 2.457 |
| `middleName` | 799 |
| `secondLastName` | 425 |
| `phone` | 2.069 |
| `address` | 780 |
| `email` | 0 en la respuesta de este endpoint |

Los valores operativos observados con mayor frecuencia fueron:

- `documentType = CC`: 2.342 registros;
- `personType = Natural`: 2.457 registros;
- `regime = 49`: 2.458 registros;
- `country = CO`: 2.450 registros;
- `priceList = 1`: 2.460 registros;
- `city = 76`: 2.379 registros;
- `locality = 001`: 2.437 registros.

`authorization` no representa un campo universalmente afirmativo en los datos
existentes: 758 registros tienen `1` y 1.702 tienen `0`. Para clientes creados
desde el ecommerce debe enviarse `1` únicamente después de obtener la
autorización correspondiente.

Esta observación describe clientes ya existentes y no reemplaza el esquema de
creación: la API continúa exigiendo los campos definidos en la sección
anterior. La aplicación debe completar los campos técnicos con valores
operativos, reutilizar los datos de la cuenta local cuando correspondan y
solicitar al usuario solo la información personal que realmente falte.

Actualización 2026-11-26: el mismo endpoint devolvió 2.463 fichas. El análisis
de móviles, los duplicados y la limpieza pendiente están en
[`customer-identity.md`](./customer-identity.md).

## Actualización de clientes (`PUT /api/Customers/{id}`)

Verificado en sandbox el 2026-11-26 con el cliente de prueba `1000000199`
(creado y actualizado sin datos reales).

Contrato observado:

- `PUT /api/Customers/{id}` acepta el mismo body `Client` de la creación.
- Responde `200` con un sobre, no con el objeto `Customer`:
  `{"status":{"code":200,"message":"Customer updated successfully.","success":true},"data":"..."}`.
- Exige el body completo. Los campos que la creación manda en `""` también
  deben ir en el `PUT`.
- Devuelve `400` con `Could not save changes. Please configure your entity
  type accordingly.` cuando falta un campo que la base exige aunque el esquema
  no marque obligatorio.

Hallazgos que afectan el autoservicio:

- **`creationDate` no se puede preservar.** Se envía el valor original, pero
  Softix lo reemplaza por la fecha y hora del `PUT`. Cada edición reescribe la
  fecha de creación. No usar `creationDate` para reglas de negocio ni como
  auditoría; la evidencia local (consentimiento, pedidos) es la fuente.
- **`createdBy` y `creationBranch` sí se respetan** tal como llegan en el body.
  El backend debe releer la ficha y reenviar los valores actuales; nunca los del
  frontend. Si se envían otros, Softix los sobrescribe.
- Los nombres del JSON son `lastName`, `secondLastName`, `locality`,
  `consultationValue`, `blockedSales` y `maxCreditLimit`. No son
  `lastName1`, `lastName2`, `district`, `consultationFee`, `salesBlocked` ni
  `creditLimit`.
- `gender` usa valores en español, por ejemplo `Masculino` y `Femenino`; no
  `M`/`F`.
- **`email` es obligatorio para la API y debe tener formato válido.** Enviar
  `""` devuelve `400` con `The Email is REQUIRED` y `The Email field is not a
  valid e-mail address`. En el formulario del ecommerce es opcional y se
  precarga con el email actual de la ficha: no se usa para login ni
  notificaciones, y si el usuario lo deja vacío se envía un relleno con formato
  válido (`<telefono>@sin-correo.invalid`). El dominio es configurable y no
  recibe correo.
- La creación responde `201` con
  `{"status":{"code":201,"message":"Customer created successfully.","success":true},"data":"..."}`,
  no un booleano.

Campos que el backend debe releer y reenviar sin cambios:

`documentId`, `documentType`, `mobile`, `phone`, `authorization`,
`createdBy`, `creationBranch`, `personType`, `regime`, `priceList`, `country`,
`taxResponsibility`, `taxes`, `blockedSales`, `maxCreditLimit`, `creditDays`,
`consultationValue`, `rural`, `codeClient`, `checkDigit`,
`additionalDiscount`, `bloodType`, `notes`, `membership`, `healthProvider`,
`loyaltyObservation`, `socialNetwork`, `codAffinityHoya`, `photo` y
`creationDate` (aunque Softix lo reescriba).

Reglas para el autoservicio:

- Consultar primero la ficha actual.
- Aplicar solo los campos personales de la lista blanca: nombres, apellidos,
  email opcional, dirección, fecha de nacimiento, género, ciudad, localidad,
  código postal, estado civil y ocupación. Si el email falta, enviar el relleno
  descrito arriba.
- Armar el body completo con los valores actuales y sobrescribir solo la lista
  blanca.
- No confiar en el objeto que manda el frontend.

El detalle del flujo de identidad, el reto de documento y los límites de abuso
está en [`customer-identity.md`](./customer-identity.md).

## Tipos de documento (`GET /api/Customers/DocumentTypes/List`)

Sí existe un catálogo de tipos de documento. Devuelve una lista de
`DocumentTypeSoftix` con `documentTypeId`, `documentName` y `code`.

En producción se observaron al menos estos valores: `CC` (2.345), `TI` (80),
`CE` (12), `PA` (6), `PE` (6), `PEP` (3), `PT` (3), `NT` (3), `RC` (2),
`DE` (2) y `CI` (1). No se debe limitar el formulario a `CC/CE/TI/PAS`; usar el
catálogo.

## Listado y paginación (`GET /api/Customers/List`)

- Solo acepta `page` y `pageSize`.
- No tiene búsqueda ni orden.
- No existe búsqueda por teléfono.
- En una pasada de lectura aparecieron documentos repetidos y en las siguientes
  no. La paginación por `OFFSET` no garantiza un orden estable, así que un
  recorrido completo puede duplicar u omitir filas.

Para sincronizar el índice local de teléfonos: deduplicar por `documentId`,
validar contra `totalRecords` y no asumir que las páginas son consistentes.

## Identidad y vinculación

El flujo de login por teléfono, verificación por WhatsApp, reto de documento y
edición controlada de la ficha está documentado en
[`customer-identity.md`](./customer-identity.md). Ese documento reemplaza el
flujo anterior de vinculación por documento descrito en versiones previas.
