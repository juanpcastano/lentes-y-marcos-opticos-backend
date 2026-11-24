# Softix: inventario, precios y ventas

Verificado el 2026-11-14 y 2026-11-17 con lecturas en producción y pruebas de escritura en sandbox. Fuente de esquema: `https://api.softix.app/swagger/v1/swagger.json`.

## Endpoints

| Uso | Endpoint | Notas |
|---|---|---|
| Catálogo y precio | `GET /api/Inventories/products/list/MONTURA` | 2253 modelos. `salePrice` es el precio unitario de venta. No tiene stock. |
| Stock por sede | `GET /api/Inventarios/monturas/lista/{sede}` | Sedes `1` y `2`. Cada fila tiene `cantidad` y `valor` (unitario). Incluye filas con cantidad 0. |
| Disponibilidad total | `GET /api/Inventories/available/{código}` | Suma sede 1 + sede 2 + B2. Devuelve `200` con `0` también para códigos inexistentes. |
| Movimientos | `GET /api/Inventories/movements/MONTURA/{inicio}/{fin}` | Conceptos `Entrada` y `Salida`. Ubicaciones `1`, `2` y `B2`. |
| Sedes | `GET /api/Sedes/Lista` | Solo sede 1 y sede 2. |
| `frames/list/{sede}` | No usar | Devuelve lista vacía en producción, sin explicación. |

No existe endpoint para listar bodegas o ubicaciones, ni para órdenes de compra a marcas.

## Hallazgos de inventario

- `available` = sede 1 + sede 2 + B2. Verificado para los códigos con stock y en el total.
- **B2 no es una sede.** No aparece en `Sedes/Lista`. Es destino de traslados desde la sede 1 (cada traslado tiene salida en la sede 1 y entrada en B2). Saldo neto: **21 unidades** en 4 códigos:
  - M628: 18 (entrada del 10/11/2025, sin movimientos desde entonces).
  - M1167: 1. M2060: 1. M2086: 1.
- En producción, 1228 de 2253 modelos tienen stock en sede 1 o sede 2.
- Inconsistencia de datos: en un movimiento de B2, `code` es `M2086` pero `identifier` es `M2090` (descripción de otro modelo).
- Las entradas se registran cuando la mercancía ya llegó (cargue, movimiento manual, traslado, importación desde Excel, reingreso al editar una venta). Softix no registra pedidos pendientes a marcas.

## Ventas y pedidos a futuro (sandbox)

- `POST /api/Sales/Create` con `deliveryDate` futuro (formato `dd/MM/yyyy HH:mm:ss`) **descuenta el stock de inmediato**. Probado: disponibilidad 4 → 3, legado sede 1 3 → 2.
- `POST /api/Payments/create` registra abonos (`saleId`, `customerId`, `payment`, etc.). Ajusta el saldo y **no cambia el stock**.
- `GET /api/Sales/{clientId}` lista las ventas del cliente. Se consulta por ID de cliente, no de venta.
- `observation` no se guardó (quedó en `null`). La fecha de entrega no aparece en el listado, así que no se pudo confirmar que se guardó.
- **No hay forma de anular una venta por API.** No existe `DELETE` ni nota crédito para crear. `PATCH /api/Sales/update/Hoya/{id}` es solo para datos de facturación electrónica.

## Precios

- En el Excel `INV MONTURAS Y PRODUCT LMOPTICOS08 Agost.xlsx`, `precioventa` es el **total de la línea**. El unitario es `precioventa / Cant`. Verificado en todas las filas con `Cant > 1`.
- `salePrice` de Softix coincide con el unitario del Excel en 1117 de 1124 modelos (el Excel es de agosto). Las 7 diferencias son cambios de precio posteriores. En esos casos, Softix ya tiene el precio nuevo.
- `valor` del inventario legado también es el precio unitario y coincide con `salePrice`.
- **Decisión:** usar `salePrice` de `products/list` como precio de la página. El Excel no es fuente de precio ni de stock.

## Decisiones provisionales

- `bloodType`: valores oficiales confirmados por Softix: `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+`, `O-`, y `NE` (No especificado) para cuando el cliente no quiere indicarlo. `NE` es la constante `SOFTIX_BLOOD_TYPE_UNKNOWN`. En sandbox Softix también aceptó otros valores (`N/A`, `ND`, `0`, `SIN`, etc.), pero no son válidos según Softix.
- `taxResponsibility = taxes = "ZZ"`. Softix rechaza vacío en ambos.
- `regime = "49"`, `priceList = "1"`, `country = "CO"`, `personType = "Natural"`.
- `creationBranch = 1` y `createdBy = 1`, configurables con `SOFTIX_CREATION_BRANCH` y `SOFTIX_CREATED_BY`. Se mantienen en `1` porque la mayoría de clientes se crea en sede 1.
- Las sedes se tratan como una única tienda: no se muestra stock por sede en la web.

## Decisiones de sincronización y validación

- **Sincronización cada hora:** consulta `products/list/MONTURA` y `monturas/lista/1` y `/2`, y guarda los datos válidos en PostgreSQL. La ejecución horaria usa el mismo registro que las ejecuciones manuales e importaciones.
- **Catálogo:** conserva productos activos sin stock como agotados. El precio guardado es `salePrice` y la sincronización no modifica el precio usando el indicador de IVA.
- **Ficha de producto:** consulta `available` en el momento, así que el detalle está al día.
- **Validación antes de cobrar:** usar `available` para cada código del carrito. Puede haber hasta una hora de desfase en el catálogo, pero el pago nunca se confirma sin validar contra Softix. Ver "Carrito y revalidación" más abajo.
- **Carrito (implementado en frontend):** cada línea del carrito es un producto (un SKU) y tiene un `quantity` cuyo máximo es el stock que devuelve el backend para ese `sku` (`Inventories/available/{sku}`).
  - Si el stock es 0, el `quantity` máximo es 0. Una línea con máximo 0 se trata igual que un producto desactivado: va a "No disponibles" y no suma al total.
  - Si el `quantity` guardado es mayor que el stock actual, se ajusta al stock y el cliente ve el aviso.
  - La ficha de producto muestra la misma cifra: "Disponible" si hay stock, "Agotado" si no.
  - Antes de cobrar, se vuelve a consultar el stock para cada línea.
- **B2:** mientras existan unidades en B2, `available` las incluye. Se planea borrarlas en Softix; al hacerlo, `available` y la suma de sedes coincidirán.

## Configuración

- `APP_ENV` elige el archivo de entorno: `.sandbox.env` por defecto, o `.prod.env` con `APP_ENV=prod`.
- `JWT_SECRET` debe tener 64 caracteres, porque el backend firma con HS512. Se regeneró en sandbox y producción.
- `SOFTIX_BRANCH_IDS` tiene valor `1,2` en ambos ambientes.

## Pendientes

- **B2:** la óptica no sabe qué es. Se van a borrar las unidades en Softix. Confirmar después que `available` coincide con la suma de sedes.
- **Softix:** confirmar cómo se borran las unidades de B2 (ajuste de inventario) y que el movimiento quede registrado.
- **Sandbox, datos de prueba creados (no hay API para borrarlos):**
  - Clientes: `1000000099`, `1000000100`, `1000000111`, `1000000112`, `1000000115` a `1000000124`, `1000000127`.
  - Venta `30042` con abono `46890`.
  - Usuario local `dev.ecommerce.test@example.com`.

## Carrito y revalidación

Aplica al cargar el carrito y otra vez antes de cobrar.

- **Stock:** `Inventories/available/{sku}`, una consulta por línea. Si el stock bajó, la cantidad se ajusta al nuevo máximo. Si llegó a cero, la línea pasa a "No disponibles" por agotado.
- **Precio:** `monturas/lista/1` y `/2` (dos llamadas, sin importar cuántas líneas tenga el carrito), tomando el `valor` del SKU. Antes de cobrar se compara con el precio que muestra el carrito. Si cambió, el paso previo al pago muestra el precio y el total actualizados, y el cliente decide si paga. El botón de pago ya refleja el total nuevo. El servidor vuelve a comparar en el momento de cobrar: si el precio cambió otra vez, no cobra y pide revisar el total.
- **Nombre:** el de nuestra copia, que se actualiza cada hora.
- **Respaldo:** si Softix no responde, no se cobra. El carrito muestra que no se pudo validar.

No se usa `Inventories/product/code/{sku}` como fuente. Para `M628` devuelve `brand` y `color` vacíos, no trae `salePrice` (el precio viene en `price`) y responde `500` con SKUs que no existen.

### Prueba de precio en sandbox (2026-11-20)

- **Body que funciona:** el ejemplo de la documentación oficial de Softix (`POST /api/sales/create`). Requiere `userId`, `branchId` (entero), `clientId`, `total`, `subtotal`, `vatValue`, `discount`, `payments`, `paymentCustomer`, `balance`, `paymentType` (`paymentCode`, `paymentMethod`, `paymentName`), `iva`, `deliveryDate` (`dd/MM/yyyy HH:mm:ss`), `deliveryTime` (`10:00 AM`) y `items`. Las versiones que omitían `balance`, `iva`, `payments` o `paymentCustomer` respondían `400` con "Could not save changes".
- **Precio:** Softix guardó el precio que enviamos, no el `valor` de su inventario. Para `M1`, `valor` es 193277.31 y la venta quedó con `total` 200000.
- **IVA por producto:** en `products/list`, el campo JSON `saleVAT` se mapea explícitamente a `saleVat` y se guarda como booleano informativo. No se usa para calcular ni modificar el precio mostrado; cualquier cálculo de venta deberá definir su propia regla cuando se implemente.
- **Campo `amount`:** en la respuesta de consulta de ventas aparece 231932.77 (= total + IVA), mientras `totalToPay` es 200000. No se usa en nuestro flujo, pero hay que tenerlo en cuenta al leer ventas.
- **Efecto:** la prueba creó la venta `30061` en sandbox, con `M1` (1 unidad). No se puede anular por API.
