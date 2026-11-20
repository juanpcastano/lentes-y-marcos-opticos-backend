# Softix: importación de productos y reglas de datos

Fuente: `GET /api/Inventories/products/list/MONTURA` para datos del producto y existencia, y `GET /api/Inventarios/monturas/lista/{sede}` para stock y precio. La importación por Excel queda como flujo legado (ver `PROJECT_CONTEXT.md`, sección 5).

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
| Referencia repetida en marcas distintas | Error | Esos SKUs no se importan hasta corregirlo en Softix. Solo se afectan esos SKUs. |
| Dos SKUs con misma marca, referencia y color (`VR2411`: `M897` y `M898`) | Error | Ninguno se importa hasta corregirlo en Softix. Son el mismo producto, no pueden existir dos. |
| SKU sin referencia | Error | No se importa hasta que Softix le asigne referencia. |
| Marca, referencia o categoría con espacios o mayúsculas distintas | Advertencia | Se importa normalizado. Se avisa para corregir en Softix. |
| Marca o categoría nueva | Advertencia | Se avisa que se va a crear. |
| SKU que cambia de marca, referencia, color, categoría, nombre, precio o stock | Actualización | Se actualiza sin conflicto. |
| Marca ignorada por el administrador | — | Sus SKUs nuevos no se crean. Los productos ya importados se siguen actualizando. |
| SKU que deja de existir en `products/list` | Oculto | Pendiente de confirmar en la aplicación de escritorio de Softix cómo se elimina un producto. |

Los errores bloquean solo el SKU afectado. Los demás SKUs se importan normalmente.

## Campos de Softix

Son de solo lectura en el panel de administración y no están en los DTO de escritura del backend: SKU, marca, referencia, nombre, color, categoría, precio y stock. Para cambiarlos se corrige en Softix y se reimporta.

## Estados de un producto

| Estado | Catálogo | Página del producto | Carrito |
|---|---|---|---|
| Activo con stock | Sí | Sí | Sí |
| Activo sin stock | Sí, con el filtro de stock activo por defecto | Sí, como agotado | Sí, en "No disponibles" por agotado |
| No activo | No | Solo por URL directa | Sí, en "No disponibles" |
| Oculto | No, para nadie | No, ni por URL directa | No |

El administrador puede asignar cualquiera de los tres estados manuales: activo, no activo u oculto. Un producto que pierde su respaldo en Softix se muestra con aviso rojo, visible solo para el administrador.

## Motor de sincronización

Un solo proceso actualiza los productos guardados. Lo usan tres disparadores:

- **Automático:** cada hora.
- **Importación:** al confirmar una importación, en el mismo proceso, después de crear los productos nuevos.
- **Manual:** botón "Sincronizar ahora", aparte del flujo de importación.

Cada ejecución consulta `products/list` para saber qué SKUs existen, y `monturas/lista/1` y `/2` para stock y precio (`valor`). Actualiza todos los SKUs guardados en la base de datos, incluidos los de marcas ignoradas. Nunca crea productos. Los SKUs que están en la base pero no en `products/list` pasan a oculto.

Solo se aplican datos válidos. Si un cambio dejaría un SKU con un error (por ejemplo, sin referencia o sin categoría), se conserva el valor anterior y se registra el error.

## Registro de ejecuciones

Cada ejecución guarda dos cosas:

- **Ejecución:** inicio, fin, origen (`automática`, `importación` o `manual`), resultado (`ok`, `parcial` o `fallida`) y conteos de productos creados, actualizados, ocultados y con error.
- **Detalle:** una fila por cambio o error, con SKU, campo, valor anterior, valor nuevo y nivel (cambio, advertencia o error). Los cambios de stock se guardan completos.

No se guardan los SKUs que no cambiaron. El detalle se conserva 90 días. Las ejecuciones se conservan más tiempo porque ocupan poco espacio.

En el panel de administración se ve la lista de ejecuciones con fecha, origen, resultado y conteos. Al abrir una, se ve el detalle, con filtro por SKU o por marca.

## Flujo de importación

1. El administrador consulta Softix y ve la vista previa: productos nuevos, actualizaciones (valor anterior y nuevo) y errores.
2. Al confirmar, se crean los productos nuevos (omitiendo marcas ignoradas, con sus errores ya bloqueados).
3. Se ejecuta el motor de sincronización sobre todos los SKUs guardados, con origen `importación`.
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
