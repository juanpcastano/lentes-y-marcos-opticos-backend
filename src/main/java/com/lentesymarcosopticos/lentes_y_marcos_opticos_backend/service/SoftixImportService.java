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
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductVariant;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.BrandRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.CategoryRepository;
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
	private final BrandRepository brandRepository;
	private final CategoryRepository categoryRepository;

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
			String duplicateDetail,
			String productName,
			String color,
			boolean needsColorReview,
			String groupKey) {
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
			String productType,
			UUID productId) {
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
						sinCambios, conflictos, errores),
				brandCandidates(dtos), categoryCandidates(dtos),
				existingBrands(), existingCategories());
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
		if (cant == null || total == null || cant.compareTo(BigDecimal.ZERO) <= 0) {
			// Sin precio utilizable (Cant=0, no numérico o total no numérico):
			// la fila conserva sus datos y llega al wizard con precio nulo
			// para que el admin lo ponga a mano (la validación bloquea el
			// avance mientras siga en 0). No es un error descartable.
			return new Occurrence(sheet, rowNumber, sku, descripcion,
					normalizeBrand(brandSource), mapCategories(tipoRaw, categoriaRaw),
					productType, null, null);
		}
		int unitPrice;
		try {
			unitPrice = total.divide(cant, 0, RoundingMode.HALF_UP).intValueExact();
		} catch (ArithmeticException e) {
			return new Occurrence(sheet, rowNumber, sku, descripcion,
					normalizeBrand(brandSource), mapCategories(tipoRaw, categoriaRaw),
					productType, null, null);
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
					null, null, List.of(), null, null, first.error(), skippedNote, null,
					null, "ÚNICO", false, null);
		}
		Occurrence base = valid.get(0);
		Set<String> signatures = valid.stream().map(this::signature)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		String sheets = valid.stream().map(Occurrence::sheet).distinct()
				.collect(Collectors.joining(", "));
		ColorSplit split = splitColor(base.name());
		String groupKey = "B:" + normalize(base.brand()) + "|P:" + fuzzyKey(split.productName());
		if (signatures.size() == 1) {
			String note = join(skippedNote,
					valid.size() > 1 ? "Repetido en " + sheets
							+ " con los mismos datos: se importa una vez" : null);
			note = join(note,
					base.unitPrice() == null
							? "Sin precio en el Excel: ponlo a mano en el paso de productos"
							: null);
			return new ParsedRow(rowKey, valid.size() > 1 ? sheets : base.sheet(),
					base.rowNumber(), base.sku(), base.name(), base.brand(), base.categories(),
					base.productType(), base.unitPrice(), null, note, null,
					split.productName(), split.color(), split.needsReview(), groupKey);
		}
		String detail = valid.stream()
				.map(o -> o.sheet() + " fila " + o.rowNumber() + ": precio " + o.unitPrice())
				.collect(Collectors.joining("; "));
		return new ParsedRow(rowKey, base.sheet(), base.rowNumber(), base.sku(), base.name(),
				base.brand(), base.categories(), base.productType(), base.unitPrice(), null,
				skippedNote,
				"El SKU aparece " + valid.size() + " veces con datos distintos (" + detail
						+ "). Se aplican los datos de " + base.sheet() + " fila "
						+ base.rowNumber(),
				split.productName(), split.color(), split.needsReview(), groupKey);
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
					variant.getPrice(), brand, categories, product.getProductType(),
					product.getId()));
		}
		return result;
	}

	private InventoryPreviewRowDto classify(ParsedRow row, ExistingSnapshot existing) {
		if (row.error() != null) {
			return dto(row, "ERROR", row.error(), null, "SKIP");
		}
		if (existing == null) {
			String detail = join(row.note(), row.duplicateDetail());
			if (row.categories().isEmpty()) {
				detail = join(detail, "Sin categoría en el Excel: se creará sin categorías");
			}
			return dto(row, "NUEVO", detail, null, "CREATE");
		}
		var snapshot = new InventoryPreviewRowDto.ExistingProductSnapshot(existing.name(),
				existing.basePrice(), existing.brand(), existing.categories(),
				existing.productType(),
				existing.productId() == null ? null : existing.productId().toString());
		// El nombre registrado en tienda no lleva el color (el color vive en la
		// variante): se compara la base sin color, no la descripción completa.
		String excelBaseName = row.productName() == null ? row.name() : row.productName();
		boolean sameName = excelBaseName.trim().equalsIgnoreCase(existing.name().trim());
		boolean samePrice = Objects.equals(row.unitPrice(), existing.basePrice());
		boolean sameBrand = equalsNormalized(row.brand(), existing.brand());
		// Las categorías se suman (unión), no se reemplazan: solo importan
		// las que la tienda aún no tiene.
		List<String> addedCategories = row.categories().stream()
				.filter(category -> existing.categories().stream()
						.noneMatch(known -> normalize(known).equals(normalize(category))))
				.toList();
		boolean sameType = equalsNormalized(row.productType(), existing.productType());
		if (sameName && samePrice && sameBrand && addedCategories.isEmpty() && sameType
				&& row.duplicateDetail() == null) {
			return dto(row, "SIN_CAMBIOS", row.note(), snapshot, "NONE");
		}
		// Un renglón por aspecto con el mismo formato "Se actualizará".
		// Marca, nombre y duplicados frenan en el paso 1 del wizard, pero se
		// listan igual para que el preview sea honesto.
		List<String> changes = new ArrayList<>();
		if (!samePrice) {
			changes.add("precio " + existing.basePrice() + " → " + row.unitPrice());
		}
		if (!addedCategories.isEmpty()) {
			changes.add("categorías + " + addedCategories);
		}
		if (!sameType) {
			changes.add("tipo " + orNull(existing.productType()) + " → "
					+ orNull(row.productType()));
		}
		if (!sameBrand) {
			changes.add("marca \"" + orNull(existing.brand()) + "\" → \""
					+ orNull(row.brand()) + "\"");
		}
		if (!sameName) {
			changes.add("nombre \"" + existing.name().trim() + "\" → \"" + excelBaseName.trim()
					+ "\"");
		}
		if (row.duplicateDetail() != null) {
			changes.add(row.duplicateDetail());
		}
		return dto(row, "ACTUALIZAR", join(row.note(), "Se actualizará: "
				+ String.join("; ", changes)), snapshot, "UPDATE");
	}

	private InventoryPreviewRowDto dto(ParsedRow row, String status, String detail,
			InventoryPreviewRowDto.ExistingProductSnapshot existing, String action) {
		return new InventoryPreviewRowDto(row.rowKey(), row.sheet(), row.rowNumber(), row.sku(),
				row.name(), row.brand(), row.categories(), row.productType(), row.unitPrice(),
				status, detail, existing, action, row.productName(), row.color(),
				row.needsColorReview(), row.groupKey());
	}

	// ---------- color + agrupación ----------

	/** Parte de la Descripción: nombre base del producto + color sugerido. */
	private record ColorSplit(String productName, String color, boolean needsReview) {
	}

	/**
	 * Marcadores que no son un color sino "varios colores en una referencia":
	 * se conserva VARIOS como color y se marca para revisión en el paso de
	 * productos. No se divide en variantes (el SKU es único y es la llave
	 * contra Softix); si hay que separar colores, se hace en Softix y se
	 * reimporta, o se agrega la variante a mano con su SKU.
	 */
	private static final Set<String> NON_COLORS = Set.of(
			"VARIOS", "VARIADO", "VARIADA", "VARIADOS", "VARIADAS", "VARIOS.");

	/** Colores conocidos (mayúsculas, con y sin tilde). */
	private static final Set<String> KNOWN_COLORS = Set.of(
			"NEGRO", "NEGRA", "NEGROS", "NEGRAS",
			"BLANCO", "BLANCA", "BLANCOS", "BLANCAS",
			"AZUL", "AZULES",
			"ROJO", "ROJA", "ROJOS", "ROJAS",
			"VERDE", "VERDES",
			"GRIS", "GRISES",
			"ROSA", "ROSADA", "ROSADO", "ROSADAS", "ROSADOS",
			"MORADO", "MORADA", "MORADOS", "MORADAS",
			"LILA", "LILAS", "LILAC",
			"BEIGE", "CAFÉ", "CAFE", "CAFES", "HABANA", "HABANO", "CAREY",
			"DORADO", "DORADA", "DORADOS", "DORADAS",
			"PLATEADO", "PLATEADA", "PLATEADOS", "PLATEADAS", "PLATA",
			"TRANSPARENTE", "TRANSPARENTES", "TRASPARENTE", "TRASLUCIDA", "TRASLUCIDO",
			"TRASLUCIDAS", "TRASLUCIDOS",
			"VINOTINTO", "FUCSIA", "NARANJA", "NARANJAS", "BURGUNDY",
			"AMARILLO", "AMARILLA", "CELESTE", "TURQUESA", "VIOLETA",
			"MARRON", "MARRÓN", "MIEL", "OLIVO", "OLIVA", "CREMA",
			"CORAL", "TERRACOTA", "MOCA", "CHOCOLATE", "ARENA",
			"GUNMETAL", "GREY", "GRAY", "BLACK", "BLUE", "GREEN", "BROWN",
			"PINK", "RED", "WHITE", "SILVER", "GOLD", "TORTOISE",
			"HAVANA", "CRISTAL", "HUMO", "PETROLEO", "PETRÓLEO", "BURDEOS",
			"BORDO", "LAVANDA", "UVA", "MENTA", "AQUA", "CAMEL", "COGNAC", "TABACO");

	/** Segundas palabras válidas de un color compuesto ("AZUL OSCURO"). */
	private static final Set<String> COMPOUND_TAILS = Set.of(
			"OSCURO", "OSCURA", "OSCUROS", "OSCURAS", "CLARO", "CLARA",
			"CLAROS", "CLARAS", "MATE", "BRILLANTE", "PASTEL", "ELECTRICO", "ELÉCTRICO");

	private ColorSplit splitColor(String description) {
		if (description == null || description.isBlank()) {
			return new ColorSplit(description, "ÚNICO", true);
		}
		String trimmed = description.trim().replaceAll("\\s+", " ");
		String[] tokens = trimmed.split(" ");
		if (tokens.length < 2) {
			return new ColorSplit(trimmed, "ÚNICO", true);
		}
		String last = normalize(tokens[tokens.length - 1]);
		if (NON_COLORS.contains(last)) {
			return new ColorSplit(joinTokens(tokens, tokens.length - 1), "VARIOS", true);
		}
		if (tokens.length >= 3) {
			String prev = normalize(tokens[tokens.length - 2]);
			if (COMPOUND_TAILS.contains(last) && KNOWN_COLORS.contains(prev)) {
				String color = capitalize(tokens[tokens.length - 2]) + " " + capitalize(tokens[tokens.length - 1]);
				return new ColorSplit(joinTokens(tokens, tokens.length - 2), color, false);
			}
		}
		if (KNOWN_COLORS.contains(last)) {
			return new ColorSplit(joinTokens(tokens, tokens.length - 1),
					capitalize(tokens[tokens.length - 1]), false);
		}
		String rawLast = tokens[tokens.length - 1];
		if (rawLast.contains("/")) {
			// "NEGRA/GRIS", "CAFE/NEGRO": se conserva tal cual pero se marca
			// para revisión.
			return new ColorSplit(joinTokens(tokens, tokens.length - 1), rawLast, true);
		}
		if (tokens.length >= 3 && tokens[tokens.length - 2].contains("/")) {
			// "darkpink/light purple": el color ocupa las dos últimas palabras.
			String color = tokens[tokens.length - 2] + " " + rawLast;
			return new ColorSplit(joinTokens(tokens, tokens.length - 2), color, true);
		}
		// Última palabra desconocida: color no determinable -> ÚNICO con revisión.
		return new ColorSplit(trimmed, "ÚNICO", true);
	}

	private String joinTokens(String[] tokens, int endExclusive) {
		if (endExclusive <= 0) {
			return String.join(" ", tokens);
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < endExclusive; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(tokens[i]);
		}
		return sb.toString();
	}

	private String capitalize(String token) {
		if (token == null || token.isEmpty()) {
			return token;
		}
		String lower = token.toLowerCase(Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	/**
	 * Clave difusa: mayúsculas alfanuméricas sin separadores, así "RAY-BAN",
	 * "RAY BAN" y "RAYBAN" colapsan al mismo grupo. Solo se usa para agrupar
	 * variantes de producto dentro del Excel y para las validaciones de
	 * duplicados; NUNCA para sugerir marcas/categorías existentes (eso usa
	 * {@link #normalize}, que conserva guiones y separadores: una marca
	 * parecida es nueva con aviso, no un MAP).
	 */
	static String fuzzyKey(String value) {
		if (value == null) {
			return "";
		}
		return normalize(value).replaceAll("[^A-Z0-9]", "");
	}

	// ---------- candidatos de marcas / categorías ----------

	private List<InventoryPreviewResponse.BrandCandidate> brandCandidates(
			List<InventoryPreviewRowDto> dtos) {
		Map<String, Long> counts = new LinkedHashMap<>();
		Map<String, String> display = new LinkedHashMap<>();
		for (var dto : dtos) {
			if (dto.brand() == null || dto.brand().isBlank() || dto.status().equals("ERROR")) {
				continue;
			}
			// Clave exacta (mayúsculas, espacios simples): "MARCA LIMPIA-1" y
			// "MARCA LIMPIA 1" son candidatas distintas; la parecida queda como
			// nueva y el frontend la avisa con findNearMiss.
			String key = normalize(dto.brand());
			counts.merge(key, 1L, Long::sum);
			display.putIfAbsent(key, dto.brand().trim());
		}
		Map<String, Brand> byKey = brandRepository.findAll().stream()
				.collect(Collectors.toMap(b -> normalize(b.getName()), Function.identity(),
						(a, b) -> a, LinkedHashMap::new));
		return counts.entrySet().stream()
				.map(e -> {
					Brand match = byKey.get(e.getKey());
					return new InventoryPreviewResponse.BrandCandidate(display.get(e.getKey()),
							e.getValue().intValue(),
							match == null ? null : match.getId(),
							match == null ? null : match.getName());
				})
				.sorted((a, b) -> Integer.compare(b.count(), a.count()))
				.toList();
	}

	private List<InventoryPreviewResponse.CategoryCandidate> categoryCandidates(
			List<InventoryPreviewRowDto> dtos) {
		Map<String, Long> counts = new LinkedHashMap<>();
		Map<String, String> display = new LinkedHashMap<>();
		for (var dto : dtos) {
			for (String category : dto.categories()) {
				if (category == null || category.isBlank()) {
					continue;
				}
				// Igual que marcas: coincidencia exacta, lo parecido es nuevo.
				String key = normalize(category);
				counts.merge(key, 1L, Long::sum);
				display.putIfAbsent(key, category.trim());
			}
		}
		Map<String, Category> byKey = categoryRepository.findAll().stream()
				.collect(Collectors.toMap(c -> normalize(c.getName()), Function.identity(),
						(a, b) -> a, LinkedHashMap::new));
		return counts.entrySet().stream()
				.map(e -> {
					Category match = byKey.get(e.getKey());
					return new InventoryPreviewResponse.CategoryCandidate(display.get(e.getKey()),
							e.getValue().intValue(),
							match == null ? null : match.getId(),
							match == null ? null : match.getName());
				})
				.sorted((a, b) -> Integer.compare(b.count(), a.count()))
				.toList();
	}

	private List<InventoryPreviewResponse.ExistingBrand> existingBrands() {
		return brandRepository.findAll().stream()
				.map(b -> new InventoryPreviewResponse.ExistingBrand(b.getId(), b.getName()))
				.sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
				.toList();
	}

	private List<InventoryPreviewResponse.ExistingCategory> existingCategories() {
		return categoryRepository.findAll().stream()
				.map(c -> new InventoryPreviewResponse.ExistingCategory(c.getId(), c.getName()))
				.sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
				.toList();
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

	/** Categoria → categorías N:M de la tienda. Cualquier texto puede ser una
	 * categoría nueva: se ofrece como candidata y se crea al importar (el
	 * wizard ya no corrige, solo muestra/omite). */
	static List<String> mapCategories(String tipoRaw, String categoriaRaw) {
		String cat = normalize(categoriaRaw);
		if ("MONTURA".equals(tipoRaw)) {
			String trimmed = categoriaRaw.trim().replaceAll("\\s+", " ");
			if (trimmed.isEmpty()) {
				return List.of();
			}
			return switch (cat) {
				case "HOMBRE" -> List.of("Hombre");
				case "MUJER" -> List.of("Mujer");
				case "UNISEX" -> List.of("Unisex");
				case "NIÑO", "NIÑOS" -> List.of("Niño");
				default -> List.of(trimmed);
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
