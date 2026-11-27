# Softix: identidad de clientes y vinculación con el ecommerce

> Última actualización: 2026-11-27.
> Documento de decisiones y referencia del flujo de autenticación y vinculación.
> Complementa [`customers-create.md`](./customers-create.md) y
> [`phase-1-validation.md`](./phase-1-validation.md).

## 1. Principio

- El **teléfono verificado** es la identidad de la cuenta local.
- **Un teléfono es una cuenta.**
- Se recolecta la **menor cantidad de datos posible**. No se piden códigos de
  ciudad, código postal, estado civil ni ocupación en el autoservicio.
- La cuenta local **no existe hasta completar la vinculación** con Softix. El
  OTP solo deja una verificación pendiente.
- Softix sigue siendo la fuente de verdad para la ficha del cliente, ventas y citas.
- El autoservicio solo puede leer o editar la ficha cuyo **móvil** controla el usuario.
- El frontend nunca recibe el `softixClientId` ni documentos que el usuario no
  haya escrito.
- Un documento de Softix solo puede estar vinculado a una cuenta local.

El módulo anterior de email/contraseña y Google se eliminó. No hay login por
email, contraseña ni Google. Las migraciones se unificarán antes de la versión
1.0.

## 2. Flujo de ingreso y registro

Ingreso y registro son el mismo flujo.

1. Una sola pantalla pide el número de teléfono y un captcha.
2. El backend normaliza el número a **E.164** (`+57` + 10 dígitos para móviles
   colombianos) y valida que sea un móvil (`3xx`).
3. Se envía un código de un solo uso por **WhatsApp**.
4. El usuario ingresa el código. Si es válido, el teléfono queda verificado.
5. Se crea una **verificación pendiente** (`phone_verifications`) con un token
   de registro de 15 minutos. Todavía **no se crea la fila en `users`**.
6. Se busca la ficha en el índice local `phone_e164 -> softix_client_id`.
7. Sin ficha: se pide el documento en un paso aparte. Al continuar se
   consulta Softix: si la ficha existe y no tiene teléfono, se precarga el
   formulario para que el usuario la revise y edite; si no existe, se muestra
   el formulario de creación vacío. La cuenta se crea solo al confirmar.
8. Con ficha: se pide el documento para desambiguar y confirmar identidad.
9. Documento correcto: se muestran los datos encontrados en el formulario para
   que el usuario los revise y edite. La ficha se actualiza, se crea la cuenta
   local y se emite la sesión **solo al confirmar ese formulario**. Si el
   usuario abandona antes, no queda usuario.
10. La edición posterior de datos personales ocurre en “Mi cuenta”, únicamente
    sobre una cuenta ya vinculada.

### Regla de decisión

| Teléfono | Documento | Acción |
|---|---|---|
| Sin fichas | No existe en Softix | Formulario vacío; crear ficha al confirmar |
| Sin fichas | Existe sin teléfono | Precargar formulario; vincular al confirmar |
| Sin fichas | Existe con otro teléfono | “Este documento ya está registrado con otro número de teléfono. Contáctanos.” No crear. |
| Con fichas | Coincide con una | Precargar formulario; vincular al confirmar |
| Con fichas | No coincide con ninguna | En los dos primeros intentos: “No pudimos verificar tus datos. Revisa el documento e intenta de nuevo.” Al tercero: “No pudimos verificar tus datos. Contáctanos.” No crear. |
| Más de 3 fichas | — | Remitir a personal, sin reto de documento |

### Precarga del formulario

Cuando el documento ya existe en Softix y puede vincularse, el backend devuelve
solo los campos editables (`documentType`, `documentId`, nombres, email,
fecha de nacimiento y género). El documento queda bloqueado en el formulario y
el usuario revisa el resto antes de confirmar. El `softixClientId` y los datos
internos de la ficha nunca salen del backend.

Una ficha con otro móvil válido no se puede reclamar desde un número distinto:
para ese caso se conserva el mensaje de “documento registrado con otro
teléfono”. Los teléfonos fijos o valores no normalizables no bloquean el
vínculo.

### Reglas de interfaz

- La interfaz no muestra las palabras “Softix”, “ficha”, “tipo de documento” ni
  decisiones internas. Habla de “documento”, “registro” y “cuenta”.
- “Contáctanos” aparece una sola vez por mensaje, como acción del diálogo de
  contacto; el texto del error no repite esa palabra.
- El botón de envío del formulario de registro o vinculación dice siempre
  “Crear cuenta” y ocupa todo el ancho, igual que los demás botones principales.
- La consulta de documento se ejecuta solo al pulsar “Continuar”; no se consulta
  mientras el usuario escribe.

## 3. Reto de documento

- Se exige el **número de documento completo**. No se aceptan los últimos 4
  dígitos ni terminaciones: si el cliente escribe solo una parte, no coincide.
- Softix a veces guarda el documento con un sufijo interno
  (`800233471-10724`). Se compara la parte anterior al guion; el sufijo nunca
  se acepta como documento.
- Si hay **varias fichas** con el mismo teléfono, se muestran los últimos 4
  dígitos del documento base como pista (“tenemos 2 fichas: una termina en 4651
  y otra en 9877”). El usuario debe escribir el documento completo; no lo elige
  de una lista.
- Si hay **una sola ficha**, no se muestra la pista.
- El cliente recibe un `challengeId` opaco por verificación. Las pistas nunca
  revelan más que los últimos 4 dígitos del documento base. El
  `softixClientId` nunca sale del backend.
- Un teléfono con **más de 3 fichas** se considera un caso especial (número
  comodín) y se remite a personal.

Aceptar los últimos 4 dígitos era un bypass: las pistas mostraban esos mismos
cuatro dígitos. Por eso el reto exige el documento completo y el intento fallido
se cuenta; tras 3 fallos el reto se bloquea y se remite a personal.

El OTP prueba control del teléfono; el documento reduce el riesgo de vincular
la ficha equivocada cuando varias personas comparten un número.

## 4. Edición de la ficha

Principio: **recolectar la menor cantidad de datos posible**. El formulario no
pide código de ciudad, código postal, estado civil ni ocupación. El usuario no
debe conocer códigos internos.

El usuario puede editar **solo campos personales**:

- `firstName`, `middleName`, `lastName`, `secondLastName`
- `email` (opcional y precargado con el de la ficha; si se deja vacío, el
  backend envía `<telefono>@sin-correo.invalid`)
- `birthDate`
- `gender`

La dirección **no** se edita ni se crea desde Softix. El ecommerce usa su propio
registro local de direcciones en una fase posterior. Al crear la ficha se envía
`address` vacío; al editar se conserva el valor que ya exista en Softix.

Al crear, el backend fija `city = 76` y `locality = 001` (Cali) y envía
`postalCode`, `maritalStatus` y `occupation` vacíos. Al editar, conserva los
valores actuales de ciudad, localidad, código postal, estado civil y ocupación.

Campos **bloqueados** en autoservicio:

- `documentType`, `documentId`
- `mobile`
- `city`, `locality`, `postalCode`, `maritalStatus`, `occupation`
- `authorization` (solo se envía `1` con consentimiento explícito)
- `creationDate`, `createdBy`, `creationBranch`
- `personType`, `regime`, `priceList`, `country`
- `taxResponsibility`, `taxes`
- `blockedSales`, `maxCreditLimit`, `creditDays`
- `consultationValue`, `rural`
- `codeClient`, `checkDigit`, `additionalDiscount`
- `bloodType` (no se pregunta; se envía `NE` si hay que crear)
- `notes` (interno)

`PUT /api/Customers/{id}` exige el body completo. El backend debe:

1. consultar la ficha actual;
2. aplicar solo los campos de la lista blanca;
3. preservar los campos bloqueados;
4. enviar el body completo;
5. no confiar en el objeto que manda el frontend.

Verificación en sandbox (2026-11-26):

- El `PUT` responde `200` con `{"status":{...},"data":"..."}`; no devuelve el
  objeto `Customer`.
- **`creationDate` se reescribe en cada `PUT`** aunque se envíe el valor
  original. No sirve como auditoría de creación después de la primera edición.
  La evidencia local de consentimiento y los snapshots de pedidos son la fuente.
- `createdBy` y `creationBranch` sí se respetan; el backend debe reenviar los
  valores leídos de Softix, no los del frontend.
- El JSON usa `lastName`, `secondLastName`, `locality`, `consultationValue`,
  `blockedSales` y `maxCreditLimit`.
- `gender` usa `Masculino`/`Femenino`.

Cambiar el teléfono exige re-verificación. Cambiar el documento exige
intervención del personal.

## 5. Modelo local

La cuenta local queda mínima. El perfil vive en Softix.

### `users`

- `id` (UUID)
- `phone_e164` (único, verificado)
- `phone_verified_at` (NOT NULL)
- `softix_client_id` (único, **NOT NULL**)
- `softix_linked_at` (**NOT NULL**)
- `consent_version`, `consent_at`, `consent_ip`
- `is_admin`
- `created_at`, `updated_at`, `last_login_at`

No se guardan nombre, email, contraseña ni `google_sub`. La base impone
`softix_client_id NOT NULL` y `softix_linked_at NOT NULL`: no puede existir una
cuenta sin ficha vinculada.

### `phone_verifications`

- `id` (UUID; es el `sub` del token de registro)
- `phone_e164`
- `user_id` (solo si existía una cuenta sin vincular)
- `consent_version`, `consent_at`, `consent_ip`
- `expires_at`, `consumed`, `created_at`
- `document_lookup_attempts` (contador de consultas de documento; máximo 5)

El OTP crea esta fila, no `users`. Si el usuario no completa la vinculación, la
verificación expira y no queda cuenta.

### `auth_codes`

- `identifier` (teléfono)
- `channel` (`WHATSAPP`)
- `purpose` (`LOGIN`, `VERIFY_PHONE`, ...)
- `code_hash`
- `expires_at`, `attempts`, `consumed`
- `request_ip`, `user_agent`, `device_id`
- `created_at`

### `auth_sessions`

- `id` (UUID, es el `jti` del refresh token)
- `user_id`
- `expires_at`, `revoked_at`, `created_at`

### `softix_customer_index`

- `phone_e164`
- `softix_client_id`
- `synced_at`

Solo el mapeo. Sin nombre, documento ni dirección.

### `softix_link_challenges`

- `id` (UUID opaco para el cliente)
- `phone_verification_id`, `phone_e164`
- `candidate_client_ids` (solo del lado servidor)
- `attempts`, `expires_at`, `consumed`
- `created_at`

### `softix_write_events`

- `id` (UUID)
- `subject_key` (teléfono E.164; también existe antes de crear `users`)
- `operation` (`LINK`, `CREATE`, `UPDATE`)
- `created_at`

Registra cada intento de escritura en la ficha de Softix. Se guarda en una
transacción independiente para que el límite de 3/hora cuente también los
intentos que luego fallan en Softix. Los eventos con más de 7 días se eliminan
con una limpieza diaria.

### `softix_index_runs`

- `started_at`, `finished_at`
- `status` (`RUNNING`, `OK`, `PARTIAL`, `FAILED`)
- `total_records`, `unique_clients`, `indexed_phones`
- `message`

Registro independiente de cada refresco del índice. No comparte transacción ni
bloqueo con la sincronización de inventario.

### Sesiones

Access token con `sub` = UUID del usuario. Refresh token con `sub` = UUID +
`jti` de sesión para poder revocar. El refresh actual por email se elimina.

### Pedidos

Los pedidos conservan snapshots de nombre, teléfono, dirección, SKU, color y
precio. No dependen de que la ficha de Softix no cambie.

## 6. Índice teléfono -> cliente

Softix **no ofrece búsqueda por teléfono**. `GET /api/Customers/List` solo
acepta `page` y `pageSize`, sin filtro ni orden.

Por eso se mantiene un índice local mínimo:

- se construye con una lectura completa de `GET /api/Customers/List`;
- se deduplica por `documentId` y se valida contra `totalRecords`;
- si la lectura trae menos del 90 % de `totalRecords`, se conserva el índice
  anterior y la ejecución queda como fallida;
- se refresca cada hora junto con la sincronización de inventario, con registro
  de ejecución independiente para que un fallo no bloquee al otro;
- también puede refrescarse a pedido del administrador;
- si el índice está desactualizado, el caso se remite a personal.

El listado de Softix pagina sin un orden garantizado. En una pasada de lectura
aparecieron documentos repetidos y en las siguientes no. Un recorrido completo
puede duplicar u omitir filas, así que el sincronizador debe deduplicar y
verificar el total; no debe asumir que las páginas son consistentes.

## 7. Abuso y límites

### Implementado

| Acción | Límite |
|---|---|
| Inicio de OTP | 1 cada 60 s por teléfono; 5/hora; 10/día (ventana móvil de 24 h) |
| Inicio de OTP por IP | 10/hora |
| Inicio de OTP por dispositivo | 10/hora; usa la cookie httpOnly `lm_device` |
| Verificación de código | 5 intentos; expira en 10 min; un solo uso |
| Reto de documento | 3 intentos por reto; cada reto expira en 15 min; 3 fallos acumulados en una hora remiten a personal |
| Búsqueda de documento | 5 consultas por verificación (`document_lookup_attempts`) |
| Crear/actualizar ficha | 3/hora por teléfono/usuario; cuentan los intentos, aunque Softix falle |
| Gasto de WhatsApp | 500 OTP/día por defecto; alerta a 400 y se bloquea la solicitud 501 |

Además:

- captcha en el inicio de OTP (Turnstile en producción);
- el inicio de OTP responde igual exista o no el teléfono;
- códigos hasheados y de un solo uso; en producción no se registran en logs. En
  modo consola de desarrollo el OTP se imprime a propósito para poder probarlo;
- bloqueo del código tras 5 intentos fallidos y remisión a personal cuando el
  reto de documento acumula 3 fallos en una hora;
- el límite por dispositivo es una barrera adicional de mejor esfuerzo: si el
  usuario borra la cookie se genera otro identificador, pero siguen activos los
  límites por teléfono, IP y el captcha;
- la escritura de ficha se registra en `softix_write_events` antes de llamar a
  Softix; un fallo posterior no borra el intento ni permite reintentar sin
  límite.

### Alertas

El sistema emite logs estructurados para que CloudWatch pueda generar alarmas:

- `event=otp.daily_threshold count=... limit=... threshold=...` al pasar el
  umbral diario;
- `event=otp.daily_limit count=... limit=...` al alcanzar el tope diario;
- `event=otp.spike count=... windowMinutes=... threshold=...` cuando se superan
  50 solicitudes en 5 minutos. La alerta de pico tiene un enfriamiento de 15
  minutos para no inundar los logs.

La alarma de CloudWatch y su canal de notificación son configuración de
despliegue; el backend ya emite la señal.

La búsqueda de documento del formulario solo funciona con un token de registro
recién verificado, admite como máximo **5 consultas por verificación** y
únicamente devuelve datos de fichas **sin teléfono**. Es un intercambio
deliberado para permitir la precarga; una ficha con otro móvil válido sigue
protegida por el mensaje genérico de “documento registrado con otro número de
teléfono”.

## 8. Datos de producción (2026-11-25, solo lectura)

Lectura de `GET /api/Customers/List` sobre 2.463 fichas. Sin escrituras.

| Métrica | Valor |
|---|---:|
| Fichas totales | 2.463 |
| Con `mobile` | 2.462 (99,96 %) |
| Sin `mobile` | 1 |
| Formato válido `3xx` de 10 dígitos | 2.381 (96,7 %) |
| Formato inválido | 81 (3,3 %) |
| Móviles distintos (normalizados) | 2.313 |
| Números repetidos | 129 |
| Fichas en un número repetido | 278 (11,3 %) |
| Personas sin cuenta propia con 1 teléfono = 1 cuenta | 149 |

- Los 81 inválidos: 32 de 9 dígitos, 26 de 11, 10 de 10 que no empiezan por 3,
  7 de menos de 9, 5 con separadores y 1 de 12 dígitos. Esos clientes no pueden
  recibir OTP hasta que el personal les corrija el número.
- Ningún número repetido comparte documento: son personas distintas con el
  mismo móvil, no fichas duplicadas de la misma persona.
- El número con más fichas tiene **12 fichas, 12 documentos y 12 nombres
  distintos**, y 10 fueron creadas por el mismo usuario de Softix. Parece un
  número comodín del mostrador, no una familia. Es el caso que obliga a
  bloquear el autoservicio cuando hay más de 3 fichas por teléfono.
- El campo `phone` tiene 2.071 registros y 1.732 están en formato móvil, 0 en
  formato fijo. En la práctica es una copia de `mobile`; no es un canal aparte.
- Tipos de documento observados: `CC` 2.345, `TI` 80, `CE` 12, `PA` 6, `PE` 6,
  `PEP` 3, `PT` 3, `NT` 3, `RC` 2, `DE` 2, `CI` 1. `GET
  /api/Customers/DocumentTypes/List` devuelve 81 entradas, muchas duplicadas y
  con tipos de otros países. El autoservicio las deduplica y muestra solo
  `CC`, `CE`, `TI` y `PA`.

### Limpieza pendiente

Antes de abrir el autoservicio conviene dedicar una jornada a:

- corregir los 81 móviles inválidos;
- resolver los 129 números repetidos;
- identificar y corregir el número comodín;
- revisar las fichas sin móvil.

La limpieza reduce el ruido, pero el código debe manejar igual los casos
inválidos y duplicados, porque los datos vuelven a ensuciarse.

### Cliente de prueba creado en sandbox

El 2026-11-26 se creó el cliente `1000000199` para verificar el `PUT` y el
mapeo de campos. Sandbox no tiene API para borrarlo. No contiene datos reales y
se usó únicamente para pruebas de contrato.

## 9. Endpoints implementados

- `POST /api/auth/phone/start` — teléfono + captcha; envía OTP por WhatsApp y
  deja/renueva la cookie httpOnly `lm_device` para el límite por dispositivo.
- `POST /api/auth/phone/verify` — teléfono + código + consentimiento. Si la
  cuenta ya está vinculada devuelve sesión; si no, deja una verificación
  pendiente, guarda el token de registro en cookie `httpOnly` y devuelve
  `registrationPending = true` con el estado del vínculo.
- `GET /api/auth/phone/document-types` — catálogo de tipos de documento para el
  formulario de registro (público).
- `POST /api/auth/phone/confirm` — `challengeId` + documento; usa la cookie de
  registro, valida la ficha y **devuelve los datos para precargar el
  formulario**. No crea la cuenta todavía.
- `POST /api/auth/phone/lookup` — documento del formulario de creación; usa la
  cookie de registro. Si la ficha existe y es vinculable, devuelve sus datos;
  si no existe, indica que el formulario sigue como creación. Máximo 5
  consultas por verificación.
- `POST /api/auth/phone/link` — datos editados del formulario; usa la cookie de
  registro, actualiza la ficha existente, **crea la cuenta** y emite la sesión.
- `POST /api/auth/phone/create` — datos mínimos; usa la cookie de registro, crea
  la ficha en Softix, **crea la cuenta** y emite la sesión.
- `POST /api/auth/refresh` — refresh por `jti`, no por email.
- `POST /api/auth/logout` — revoca la sesión.
- `GET /api/users/me` — datos mínimos de la cuenta.
- `GET /api/users/me/softix/profile` — ficha editable, sin documento completo
  ni `softixClientId`.
- `PUT /api/users/me/softix/profile` — actualiza solo la lista blanca.
- `POST /api/admin/integration/softix/customers/index/refresh` — refresco
  manual del índice (solo ADMIN).

El OTP se envía por `whatsapp.mode=console` en desarrollo y
`whatsapp.mode=meta` en producción. El captcha usa `captcha.mode=disabled` en
desarrollo y `captcha.mode=turnstile` en producción.

### Configuración

| Variable | Uso |
|---|---|
| `WHATSAPP_MODE` | `console` o `meta` |
| `WHATSAPP_PHONE_NUMBER_ID` | ID del número en Meta Cloud API |
| `WHATSAPP_ACCESS_TOKEN` | Token de Meta |
| `WHATSAPP_TEMPLATE` | Plantilla aprobada de autenticación |
| `WHATSAPP_DAILY_LIMIT` | OTP por día; 500 por defecto |
| `WHATSAPP_DAILY_ALERT_THRESHOLD` | Umbral de alerta diaria; 400 por defecto |
| `WHATSAPP_SPIKE_WINDOW_MINUTES` | Ventana del pico de OTP; 5 por defecto |
| `WHATSAPP_SPIKE_THRESHOLD` | OTP en la ventana para alertar; 50 por defecto |
| `WHATSAPP_SPIKE_ALERT_COOLDOWN_MINUTES` | Enfriamiento de la alerta de pico; 15 por defecto |
| `CAPTCHA_MODE` | `disabled` o `turnstile` |
| `CAPTCHA_SECRET` | Secreto de Turnstile |
| `VITE_TURNSTILE_SITE_KEY` | Site key del widget en el frontend |
| `CONSENT_VERSION` | Versión de la política aceptada |
| `COOKIE_DEVICE_MAX_AGE_SECONDS` | Duración de la cookie de dispositivo; 15552000 (180 días) |
| `SOFTIX_INDEX_SEED_ON_STARTUP` | Construir el índice al arrancar si está vacío |
| `SOFTIX_INDEX_CRON` | Cron del refresco horario (por defecto `0 5 * * * *`) |
| `SOFTIX_WRITE_EVENTS_CLEANUP_CRON` | Limpieza de eventos de escritura; por defecto `0 30 3 * * *` |

El índice también se puede refrescar a mano con
`POST /api/admin/integration/softix/customers/index/refresh`.

## 10. Pendientes

- Aprobar en Meta la plantilla de autenticación y las plantillas utilitarias de
  pedidos y citas. El proveedor es WhatsApp Cloud API de Meta; el modo consola
  sigue disponible para desarrollo.
- Configurar las credenciales de producción de Meta Cloud API y Cloudflare
  Turnstile.
- Configurar la alarma de CloudWatch y su canal de notificación para los eventos
  `otp.daily_threshold`, `otp.daily_limit` y `otp.spike`.
- Política definitiva para teléfonos con más de 3 fichas.
- Jornada de limpieza de fichas.
- Decidir si se acepta que `creationDate` se reescriba en cada edición o si se
  conserva una fecha de alta propia en la base local.
- Unificar migraciones antes de la versión 1.0.
- Envío de notificaciones utilitarias por WhatsApp (Fase 9); por ahora solo se
  implementó el OTP.
