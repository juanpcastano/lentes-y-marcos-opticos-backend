# Softix: importación de productos y reglas de datos

Fuente: `GET /api/Inventories/products/list/MONTURA` para datos del producto y existencia, y `GET /api/Inventarios/monturas/lista/{sede}` para stock y precio. La importación vigente es exclusivamente por API.

## Modelo

- **Producto = un SKU de Softix.** Es la unidad vendible, con su propio color, precio, categoría, stock, imágenes y estado.
- **Identidad:** el SKU. Nunca cambia.
- **Referencia:** un campo del producto (código del modelo). No agrupa nada en la base.
- **"Otros colores":** en la ficha de producto se muestran los demás productos con la misma marca y referencia (normalizadas). Es una consulta de presentación, no una entidad.
- **Categoría:** pertenece a cada producto.
- **Nombre:** el `name` de Softix, tal cual. Se evalúa su formato en el catálogo (ver Pendiente).
- **Migraciones:** la base no tiene datos. Antes de la primera versión, todas las migraciones se unifican en una sola V01.

Se elimina la entidad `ProductVariant` y sus imágenes pasan al producto.

## Normalización

Las marcas, referencias y categorías se usan tal cual vienen de Softix (solo se recortan espacios de los extremos y se colapsan los espacios internos). No se cambia la capitalización. Las diferencias de forma ya no se normalizan.

## Qué pasa en cada caso

| Caso | Nivel | Resultado |
|---|---|---|
| SKU sin categoría | Error | No se importa hasta que Softix le asigne categoría. |
| SKU sin marca | Error | No se importa hasta que Softix le asigne marca. |
| SKU sin precio, o con precio 0 | Error | No se importa ni se actualiza hasta corregirlo en Softix. |
| Referencia repetida en marcas distintas | Error | Esos SKUs no se importan hasta corregirlo en Softix. Solo se afectan esos SKUs. |
| Dos SKUs con misma marca, referencia y color (`VR2411`: `M897` y `M898`) | Error | Ninguno se importa hasta corregirlo en Softix. Son el mismo producto, no pueden existir dos. |
| SKU sin referencia | Error | No se importa hasta que Softix le asigne referencia. |
| Marca, referencia o categoría con espacios internos repetidos | Advertencia | Se importa con los espacios colapsados. Se avisa para corregir en Softix. |
| Marca, referencia o categoría con mayúsculas distintas | — | Es un valor distinto: crea una marca o categoría nueva, no renombra la anterior. |
| Marca o categoría nueva | Advertencia | Se avisa que se va a crear. |
| SKU que cambia de marca, referencia, color, categoría, nombre, precio o stock | Actualización | Se actualiza sin conflicto. |
| Marca ignorada por el administrador | — | Sus SKUs nuevos no se crean. Los productos ya importados se siguen actualizando. |
| SKU ignorado individualmente | — | No se crea si es nuevo. Los productos ya importados se siguen actualizando. |
| SKU que deja de existir en `products/list` | Sin respaldo | Se oculta efectivamente; el estado manual no cambia. Pendiente de confirmar en la aplicación de escritorio de Softix cómo se elimina un producto. |

Los errores bloquean solo el SKU afectado. Los demás SKUs se importan normalmente. Un SKU bloqueado que además está en una marca o SKU ignorado no cuenta como error. Los productos nuevos se crean activos.

## Campos de Softix

Son de solo lectura en el panel de administración y no están en los DTO de escritura del backend: SKU, marca, referencia, nombre, color, categoría, precio y stock. Para cambiarlos se corrige en Softix y se reimporta.

## Estados de un producto

| Estado | Catálogo | Página del producto | Carrito |
|---|---|---|---|
| Activo con stock | Sí | Sí | Sí |
| Activo sin stock | Sí, con el filtro de stock activo por defecto | Sí, como agotado | Sí, en "No disponibles" por agotado |
| No activo | No | Solo por URL directa | Sí, en "No disponibles" |
| Oculto | No, para nadie | No, ni por URL directa | No |
| Sin respaldo en Softix | No, aunque su estado manual sea activo | No, ni por URL directa | No |

El administrador puede asignar cualquiera de los tres estados manuales: activo, no activo u oculto. La sincronización nunca cambia ese estado. Un producto que pierde su respaldo en Softix se marca como sin respaldo, se oculta efectivamente y se muestra con aviso rojo, visible solo para el administrador.

## Motor de sincronización

Un solo proceso actualiza los productos guardados. Lo usan tres disparadores:

- **Automático:** cada hora.
- **Importación:** al confirmar una importación, en el mismo proceso, después de crear los productos nuevos.
- **Manual:** botón "Sincronizar ahora", aparte del flujo de importación.

Cada ejecución consulta `products/list` para saber qué SKUs existen, y `monturas/lista/1` y `/2` para stock y precio (`valor`). Actualiza todos los SKUs guardados en la base de datos, incluidos los de marcas ignoradas. Nunca crea productos. Los SKUs que están en la base pero no en `products/list` quedan marcados como sin respaldo y se ocultan efectivamente; su estado manual no cambia.

Solo se aplican datos válidos. Si un cambio dejaría un SKU con un error (por ejemplo, sin referencia o sin categoría), se conserva el valor anterior y se registra el error.

## Registro de ejecuciones

Cada ejecución guarda dos cosas:

- **Ejecución:** inicio, fin, origen (`automática`, `importación` o `manual`), resultado (`SUCCESS`, `PARTIAL` o `FAILED`) y conteos: creados, actualizados, ocultos, restaurados, con error, marcas nuevas y categorías nuevas.
- **Eventos:** el registro es por evento, no por SKU. Cada evento tiene tipo (`PRODUCT`, `BRAND`, `CATEGORY` o `RUN`), clave (SKU para productos, nombre para marcas y categorías), acción (`CREATED`, `UPDATED`, `HIDDEN`, `RESTORED`, `BLOCKED` o `SUMMARY`), nivel, campo, valor anterior, valor nuevo y mensaje. Los cambios de stock se guardan completos.

No se registran eventos de SKUs ignorados ni un contador de ignorados. Una marca o categoría nueva se registra una sola vez por ejecución, aunque la usen varios SKUs. El detalle se conserva 90 días.

Cada ejecución se crea en `RUNNING` antes de tocar productos. Importación y sincronización corren en una sola transacción junto con sus eventos. Si falla, se revierten los cambios y la ejecución queda `FAILED` con un evento de error. Si la aplicación se reinicia con una ejecución en curso, al arrancar se marca `FAILED`.

En el panel de administración se ve la lista de ejecuciones con fecha, origen, resultado y conteos. Al abrir una, se ven los eventos agrupados.

## Flujo de importación

1. El administrador consulta Softix y ve la vista previa: productos nuevos (con su estado: válido, bloqueado, o ignorado por marca o por SKU), actualizaciones (valor anterior y nuevo) y errores.
2. Al confirmar, se crean los productos válidos, con marcas y categorías nuevas solo si realmente se crean. Se omiten marcas ignoradas, SKUs ignorados y SKUs bloqueados.
3. En la misma transacción se ejecuta el motor de sincronización sobre todos los SKUs guardados, con origen `importación`.
4. El resultado queda registrado en el historial.

## Impacto pendiente en código

- Eliminar `ProductVariant`, sus DTO, endpoints, pantallas del admin y del frontend.
- Imágenes: el bucket S3 usa hoy el prefijo `variants/{variantId}`. Pasa a `products/{sku}` (o al id del producto), y hay que actualizar `infra/s3/README.md` y la política IAM.
- Carrito y pedidos: la línea apunta a un producto, no a una variante.
- Nuevas tablas: ejecuciones de sincronización y su detalle, con retención de 90 días para el detalle. Pantalla de historial en el admin.

## Pendiente

- Formato del título en el catálogo: propuesta "marca + name + referencia + color". Confirmar.
- Cómo se elimina un producto en Softix (revisar la aplicación de escritorio).
- Cómo se buscará por varias categorías, si se necesita. La categoría ahora es de un solo valor por producto.
