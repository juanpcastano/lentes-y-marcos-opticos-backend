package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryPreviewResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryPreviewRowDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductVariant;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductVariantRepository;

import lombok.AllArgsConstructor;

/**
 * SoftixImportService — lee el Excel de Softix (Apache POI), normaliza cada
 * fila y la clasifica contra la DB sin persistir nada.
 *
 * Reglas (F4): precioventa es valor total → se divide por Cant; Código = sku
 * (upsert por sku, Identificador se ignora); Cant es informativo; trim +
 * uppercase; tipoproducto → product_type; Categoria → categorías N:M;
 * Descripción = nombre; Marca en columna propia (vacía en PRODUCTOS → se usa
 * el Identificador); Tipo de armazón no se guarda.
 */
@Service
@AllArgsConstructor
public class SoftixImportService {

	private final ProductVariantRepository variantRepository;
	private final ProductRepository productRepository;

	private static final List<String> EXPECTED_HEADERS = List.of(
			"código", "identificador", "descripción", "tipoproducto", "marca",
			"categoria", "tipo", "cant", "precioventa", "sede");

	private static final int MAX_ROWS = 20000;

	/** Fila normalizada del Excel, con SKU deduplicado entre hojas. */
	public record ParsedRow(
			String rowKey,
			String sheet,
			int rowNumber,
			String sku,
			String name,
			String brand,
			List<String> categories,
			String productType,
			Integer unitPrice,
			String error,
			String note,
			String duplicateDetail) {
	}

	private record Occurrence(
			String sheet,
			int rowNumber,
			String sku,
			String name,
			String brand,
			List<String> categories,
			String productType,
			Integer unitPrice,
			String error) {
	}

	private record ExistingSnapshot(
			String name,
			Integer basePrice,
			String brand,
			List<String> categories,
			String productType) {
	}

	/** Preview: parsea + clasifica contra la DB (solo lectura). */
	@Transactional(readOnly = true)
	public InventoryPreviewResponse preview(MultipartFile file) {
		List<ParsedRow> rows = parse(file);
		Set<String> skus = rows.stream().map(ParsedRow::sku).collect(Collectors.toSet());
		Map<String, ExistingSnapshot> existing = loadExisting(skus);
		List<InventoryPreviewRowDto> dtos = rows.stream()
				.map(row -> classify(row, existing.get(row.sku())))
				.toList();
		int nuevos = 0, actualizar = 0, sinCambios = 0, conflictos = 0, errores = 0;
		for (var dto : dtos) {
			switch (dto.status()) {
				case "NUEVO" -> nuevos++;
				case "ACTUALIZAR" -> actualizar++;
				case "SIN_CAMBIOS" -> sinCambios++;
				case "CONFLICTO" -> conflictos++;
				default -> errores++;
			}
		}
		return new InventoryPreviewResponse(dtos,
				new InventoryPreviewResponse.Summary(dtos.size(), nuevos, actualizar,
						sinCambios, conflictos, errores));
	}

	/** Parse + normalización + dedup por SKU (sin tocar la DB). */
	public List<ParsedRow> parse(MultipartFile file) {
		Map<String, List<Occurrence>> bySku = new LinkedHashMap<>();
		DataFormatter formatter = new DataFormatter();
		try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
			if (workbook.getNumberOfSheets() == 0) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"El Excel no contiene ninguna hoja");
			}
			for (Sheet sheet : workbook) {
				Row header = sheet.getRow(0);
				if (header == null) {
					continue;
				}
				validateHeaders(sheet.getSheetName(), header, formatter);
				for (int i = 1; i <= sheet.getLastRowNum(); i++) {
					Row row = sheet.getRow(i);
					if (row == null) {
						continue;
					}
					String sku = cell(row, 0, formatter).trim();
					if (sku.isEmpty()) {
						continue; // filas de totales / vacías
					}
					if (bySku.size() >= MAX_ROWS && !bySku.containsKey(sku)) {
						throw new ApiException(HttpStatus.BAD_REQUEST,
								"El archivo supera el máximo de " + MAX_ROWS + " SKUs por importación");
					}
					bySku.computeIfAbsent(sku, k -> new ArrayList<>())
							.add(readOccurrence(sheet.getSheetName(), i + 1, sku, row, formatter));
				}
			}
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"No se pudo leer el archivo Excel: " + messageOf(e));
		}
		if (bySku.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"El Excel no contiene filas de productos para importar");
		}
		return bySku.values().stream().map(this::mergeOccurrences).toList();
	}

	// ---------- lectura ----------

	private void validateHeaders(String sheetName, Row header, DataFormatter formatter) {
		List<String> found = new ArrayList<>();
		for (int c = 0; c < EXPECTED_HEADERS.size(); c++) {
			found.add(cell(header, c, formatter).trim().toLowerCase(Locale.ROOT));
		}
		if (!found.equals(EXPECTED_HEADERS)) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"La hoja \"" + sheetName + "\" no tiene el formato esperado de Softix"
							+ " (headers: Código, Identificador, Descripción, tipoproducto,"
							+ " Marca, Categoria, Tipo, Cant, precioventa, Sede)");
		}
	}

	private Occurrence readOccurrence(String sheet, int rowNumber, String sku, Row row,
			DataFormatter formatter) {
		String identificador = cell(row, 1, formatter).trim();
		String descripcion = cell(row, 2, formatter).trim();
		String tipoRaw = normalize(cell(row, 3, formatter));
		String marcaRaw = cell(row, 4, formatter).trim();
		String categoriaRaw = cell(row, 5, formatter).trim();
		String cantRaw = cell(row, 7, formatter).trim();
		String totalRaw = cell(row, 8, formatter).trim();

		if (descripcion.isEmpty()) {
			return error(sheet, rowNumber, sku, "Sin descripción");
		}

		String productType = mapProductType(tipoRaw, categoriaRaw);
		if (productType == null) {
			return error(sheet, rowNumber, sku,
					"tipoproducto no reconocido: \"" + cell(row, 3, formatter).trim() + "\"");
		}

		String brandSource = marcaRaw.isEmpty() ? identificador : marcaRaw;
		if (brandSource.isEmpty()) {
			return error(sheet, rowNumber, sku, "Sin marca (columna Marca e Identificador vacíos)");
		}

		BigDecimal cant = parseNumber(cantRaw);
		BigDecimal total = parseNumber(totalRaw);
		if (cant == null) {
			return error(sheet, rowNumber, sku, "Cant no numérico: \"" + cantRaw + "\"");
		}
		if (total == null) {
			return error(sheet, rowNumber, sku, "precioventa no numérico: \"" + totalRaw + "\"");
		}
		if (cant.compareTo(BigDecimal.ZERO) <= 0) {
			return error(sheet, rowNumber, sku,
					"Precio desconocido (Cant=0): revísalo manualmente");
		}
		int unitPrice;
		try {
			unitPrice = total.divide(cant, 0, RoundingMode.HALF_UP).intValueExact();
		} catch (ArithmeticException e) {
			return error(sheet, rowNumber, sku, "No se pudo calcular el precio unitario");
		}

		return new Occurrence(sheet, rowNumber, sku, descripcion, normalizeBrand(brandSource),
				mapCategories(tipoRaw, categoriaRaw), productType, unitPrice, null);
	}

	private Occurrence error(String sheet, int rowNumber, String sku, String error) {
		return new Occurrence(sheet, rowNumber, sku, null, null, List.of(), null, null, error);
	}

	/** Fusiona las apariciones del mismo SKU en las distintas hojas/sedes. */
	private ParsedRow mergeOccurrences(List<Occurrence> occurrences) {
		Occurrence first = occurrences.get(0);
		String rowKey = "SKU:" + first.sku();
		List<Occurrence> valid = occurrences.stream().filter(o -> o.error() == null).toList();
		List<Occurrence> failed = occurrences.stream().filter(o -> o.error() != null).toList();
		String skippedNote = failed.isEmpty() ? null
				: failed.stream()
						.map(o -> o.sheet() + " fila " + o.rowNumber() + " omitida (" + o.error()
								+ ")")
						.collect(Collectors.joining("; "));
		if (valid.isEmpty()) {
			return new ParsedRow(rowKey, first.sheet(), first.rowNumber(), first.sku(),
					null, null, List.of(), null, null, first.error(), skippedNote, null);
		}
		Occurrence base = valid.get(0);
		Set<String> signatures = valid.stream().map(this::signature)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		String sheets = valid.stream().map(Occurrence::sheet).distinct()
				.collect(Collectors.joining(", "));
		if (signatures.size() == 1) {
			String note = join(skippedNote,
					valid.size() > 1 ? "Repetido en " + sheets
							+ " con los mismos datos: se importa una vez" : null);
			return new ParsedRow(rowKey, valid.size() > 1 ? sheets : base.sheet(),
					base.rowNumber(), base.sku(), base.name(), base.brand(), base.categories(),
					base.productType(), base.unitPrice(), null, note, null);
		}
		String detail = valid.stream()
				.map(o -> o.sheet() + " fila " + o.rowNumber() + ": precio " + o.unitPrice())
				.collect(Collectors.joining("; "));
		return new ParsedRow(rowKey, base.sheet(), base.rowNumber(), base.sku(), base.name(),
				base.brand(), base.categories(), base.productType(), base.unitPrice(), null,
				skippedNote,
				"El SKU aparece " + valid.size() + " veces con datos distintos (" + detail
						+ "). Se aplican los datos de " + base.sheet() + " fila "
						+ base.rowNumber());
	}

	private String signature(Occurrence occurrence) {
		return occurrence.name() + "|" + occurrence.brand() + "|"
				+ String.join(";", occurrence.categories()) + "|" + occurrence.productType() + "|"
				+ occurrence.unitPrice();
	}

	// ---------- clasificación ----------

	private Map<String, ExistingSnapshot> loadExisting(Set<String> skus) {
		if (skus.isEmpty()) {
			return Map.of();
		}
		List<ProductVariant> variants = variantRepository.findBySkuIn(skus);
		if (variants.isEmpty()) {
			return Map.of();
		}
		List<UUID> productIds = variants.stream().map(v -> v.getProduct().getId()).distinct()
				.toList();
		Map<UUID, Product> products = productRepository.findAllWithDetailsByIdIn(productIds)
				.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
		Map<String, ExistingSnapshot> result = new LinkedHashMap<>();
		for (ProductVariant variant : variants) {
			Product product = products.get(variant.getProduct().getId());
			if (product == null) {
				continue;
			}
			String brand = product.getBrand() != null ? product.getBrand().getName() : null;
			List<String> categories = product.getCategories() == null ? List.of()
					: product.getCategories().stream()
							.map(c -> c.getName()).sorted().toList();
			result.put(variant.getSku(), new ExistingSnapshot(product.getName(),
					variant.getPrice(), brand, categories, product.getProductType()));
		}
		return result;
	}

	private InventoryPreviewRowDto classify(ParsedRow row, ExistingSnapshot existing) {
		if (row.error() != null) {
			return dto(row, "ERROR", row.error(), null, "SKIP");
		}
		if (existing == null) {
			String detail = row.note();
			if (row.categories().isEmpty()) {
				detail = join(detail, "Sin categoría en el Excel: se creará sin categorías");
			}
			return dto(row, "NUEVO", detail, null, "CREATE");
		}
		var snapshot = new InventoryPreviewRowDto.ExistingProductSnapshot(existing.name(),
				existing.basePrice(), existing.brand(), existing.categories(),
				existing.productType());
		boolean sameName = row.name().trim().equalsIgnoreCase(existing.name().trim());
		boolean samePrice = Objects.equals(row.unitPrice(), existing.basePrice());
		boolean sameBrand = equalsNormalized(row.brand(), existing.brand());
		boolean sameCategories = equalsNormalizedSets(row.categories(), existing.categories());
		boolean sameType = equalsNormalized(row.productType(), existing.productType());
		if (sameName && samePrice && sameBrand && sameCategories && sameType
				&& row.duplicateDetail() == null) {
			return dto(row, "SIN_CAMBIOS", row.note(), snapshot, "NONE");
		}
		if (row.duplicateDetail() != null || !sameBrand || !sameCategories || !sameType) {
			List<String> diffs = new ArrayList<>();
			if (!sameBrand) {
				diffs.add("marca (tienda: " + orNull(existing.brand()) + " / Excel: "
						+ orNull(row.brand()) + ")");
			}
			if (!sameCategories) {
				diffs.add("categorías (tienda: " + existing.categories() + " / Excel: "
						+ row.categories() + ")");
			}
			if (!sameType) {
				diffs.add("tipo (tienda: " + orNull(existing.productType()) + " / Excel: "
						+ orNull(row.productType()) + ")");
			}
			if (!sameName) {
				diffs.add("nombre (tienda: \"" + existing.name() + "\" / Excel: \"" + row.name()
						+ "\")");
			}
			if (!samePrice) {
				diffs.add("precio (" + existing.basePrice() + " → " + row.unitPrice() + ")");
			}
			if (row.duplicateDetail() != null) {
				diffs.add(row.duplicateDetail());
			}
			return dto(row, "CONFLICTO",
					"Difiere de la tienda: " + String.join("; ", diffs)
							+ ". Decide: Reemplazar (todo), Actualizar (precio y nombre)"
							+ " o Descartar",
					snapshot, "DECIDE");
		}
		List<String> changes = new ArrayList<>();
		if (!samePrice) {
			changes.add("precio " + existing.basePrice() + " → " + row.unitPrice());
		}
		if (!sameName) {
			changes.add("nombre actualizado");
		}
		return dto(row, "ACTUALIZAR", join(row.note(), "Se actualizará: "
				+ String.join(", ", changes)), snapshot, "UPDATE");
	}

	private InventoryPreviewRowDto dto(ParsedRow row, String status, String detail,
			InventoryPreviewRowDto.ExistingProductSnapshot existing, String action) {
		return new InventoryPreviewRowDto(row.rowKey(), row.sheet(), row.rowNumber(), row.sku(),
				row.name(), row.brand(), row.categories(), row.productType(), row.unitPrice(),
				status, detail, existing, action);
	}

	// ---------- mapeos ----------

	/** tipoproducto → product_type (null si no reconocido). */
	static String mapProductType(String tipoRaw, String categoriaRaw) {
		if ("MONTURA".equals(tipoRaw)) {
			return "marco_optico";
		}
		if ("PRODUCTO".equals(tipoRaw)) {
			return switch (normalize(categoriaRaw)) {
				case "LENTES DE CONTACTO COSMETICOS" -> "lente_contacto";
				case "SOLUCIONES OFTALMICAS" -> "producto_oftalmico";
				case "MERCANCIA" -> "accesorio";
				default -> "producto_oftalmico";
			};
		}
		return null;
	}

	/** Categoria → categorías N:M de la tienda. */
	static List<String> mapCategories(String tipoRaw, String categoriaRaw) {
		String cat = normalize(categoriaRaw);
		if ("MONTURA".equals(tipoRaw)) {
			return switch (cat) {
				case "HOMBRE" -> List.of("Hombre");
				case "MUJER" -> List.of("Mujer");
				case "UNISEX" -> List.of("Unisex");
				case "NIÑO", "NIÑOS" -> List.of("Niño");
				default -> List.of();
			};
		}
		if ("PRODUCTO".equals(tipoRaw)) {
			String trimmed = categoriaRaw.trim().replaceAll("\\s+", " ");
			if (trimmed.isEmpty()) {
				return List.of();
			}
			return List.of(trimmed);
		}
		return List.of();
	}

	// ---------- helpers ----------

	static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
	}

	static String normalizeBrand(String value) {
		return normalize(value);
	}

	private String cell(Row row, int column, DataFormatter formatter) {
		var cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
		if (cell == null) {
			return "";
		}
		return formatter.formatCellValue(cell);
	}

	private BigDecimal parseNumber(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		// Las celdas pueden venir con formato moneda ("$    130,000"): se queda
		// solo con dígitos, punto decimal y signo.
		String cleaned = value.trim().replaceAll("[^0-9.\\-]", "");
		if (cleaned.isEmpty() || "-".equals(cleaned) || ".".equals(cleaned)) {
			return null;
		}
		try {
			return new BigDecimal(cleaned);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private boolean equalsNormalized(String a, String b) {
		return normalize(a).equals(normalize(b));
	}

	private boolean equalsNormalizedSets(List<String> a, List<String> b) {
		Set<String> left = a.stream().map(SoftixImportService::normalize)
				.collect(Collectors.toSet());
		Set<String> right = b.stream().map(SoftixImportService::normalize)
				.collect(Collectors.toSet());
		return left.equals(right);
	}

	private String orNull(String value) {
		return value == null ? "—" : value;
	}

	private String join(String a, String b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return a + ". " + b;
	}

	private String messageOf(Exception e) {
		String message = e.getMessage();
		return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
	}
}
