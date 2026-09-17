package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3StorageService — subidas de imágenes de producto a AWS S3.
 * Desactivado si aws.s3.bucket está vacío (503 al intentar subir).
 *
 * <p>
 * Cada subida se normaliza antes de guardarse y el original se descarta:
 * resize al ancho máximo según carpeta y encode a WebP. Un solo archivo
 * por imagen, sin miniaturas ni duplicados en la galería.
 */
@Service
@Slf4j
public class S3StorageService implements StorageService {

	private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
			"image/jpeg", "image/png", "image/webp");

	/** Hero full-bleed: cubre 1920px a DPR 1x (ver hero-slide). */
	private static final int HERO_MAX_WIDTH = 1920;
	/** Cards y resto: se muestran a ~300-600px CSS. */
	private static final int DEFAULT_MAX_WIDTH = 1280;
	private static final float WEBP_QUALITY = 0.8f;
	/** Tope de decodificación (~40MP ≈ 160MB en RAM): evita OOM. */
	private static final long MAX_PIXELS = 40_000_000L;

	private final String bucket;
	private final String region;
	private final String publicUrlBase;
	private final S3Client client;

	public S3StorageService(
				@Value("${aws.s3.bucket}") String bucket,
				@Value("${aws.s3.region}") String region,
				@Value("${aws.s3.public-url-base}") String publicUrlBase) {
		this.bucket = bucket == null ? "" : bucket.trim();
		this.region = region == null ? "us-east-1" : region.trim();
		this.publicUrlBase = publicUrlBase == null ? "" : publicUrlBase.trim();
		if (this.bucket.isEmpty()) {
			this.client = null;
			log.warn("AWS S3 bucket not configured — image upload disabled");
			return;
		}
		S3Client built = null;
		try {
			built = S3Client.builder()
					.region(Region.of(this.region))
					.credentialsProvider(DefaultCredentialsProvider.create())
					.build();
		} catch (RuntimeException e) {
			log.warn("Failed to initialize S3 client: {}", e.getMessage());
		}
		this.client = built;
	}

	@Override
	public boolean enabled() {
		return client != null;
	}

	@Override
	public String store(MultipartFile file, String folder) {
		if (!enabled()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"Storage no configurado (aws.s3.bucket)");
		}
		String contentType = file.getContentType() == null ? ""
				: file.getContentType().toLowerCase();
		if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"Formato no soportado (jpg, png, webp)");
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (java.io.IOException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo");
		}
		byte[] optimized = optimize(bytes, maxWidth(folder));
		// Clave única por subida: el objeto es inmutable y cacheable por un año.
		String key = folder + "/" + UUID.randomUUID() + ".webp";
		client.putObject(
				PutObjectRequest.builder()
						.bucket(bucket)
						.key(key)
						.contentType("image/webp")
						.cacheControl("public, max-age=31536000, immutable")
						.contentLength((long) optimized.length)
						.build(),
				RequestBody.fromBytes(optimized));
		return publicUrlBase.isEmpty() ? defaultUrl(key) : publicUrlBase + "/" + key;
	}

	private static int maxWidth(String folder) {
		return folder != null && (folder.equals("hero") || folder.startsWith("hero/"))
				? HERO_MAX_WIDTH
				: DEFAULT_MAX_WIDTH;
	}

	/**
	 * Decodifica, reduce al ancho máximo (sin ampliar) y codifica a WebP.
	 * El original se descarta: una 4000px de celular (~3MB) queda en ~100KB.
	 */
	static byte[] optimize(byte[] bytes, int maxWidth) {
		BufferedImage src;
		try {
			src = ImageIO.read(new ByteArrayInputStream(bytes));
		} catch (IOException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo leer la imagen");
		}
		if (src == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo leer la imagen");
		}
		if ((long) src.getWidth() * src.getHeight() > MAX_PIXELS) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "La imagen es demasiado grande");
		}
		BufferedImage scaled = src.getWidth() > maxWidth
				? scaleDown(src, maxWidth)
				: toSafeType(src);
		return encodeWebp(scaled);
	}

	/** Reducción por pasos (bilineal progresiva): menos aliasing que un solo paso. */
	private static BufferedImage scaleDown(BufferedImage src, int targetWidth) {
		int type = src.getColorModel().hasAlpha()
				? BufferedImage.TYPE_INT_ARGB
				: BufferedImage.TYPE_INT_RGB;
		BufferedImage current = toType(src, type);
		int currentWidth = current.getWidth();
		while (currentWidth / 2 >= targetWidth && currentWidth / 2 >= 1) {
			currentWidth /= 2;
			int h = Math.max(1, current.getHeight() * currentWidth / src.getWidth());
			current = drawScaled(current, currentWidth, h, type);
		}
		if (currentWidth != targetWidth) {
			int h = Math.max(1, src.getHeight() * targetWidth / src.getWidth());
			current = drawScaled(current, targetWidth, h, type);
		}
		return current;
	}

	private static BufferedImage drawScaled(BufferedImage src, int w, int h, int type) {
		BufferedImage out = new BufferedImage(w, h, type);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING,
					RenderingHints.VALUE_RENDER_QUALITY);
			g.drawImage(src, 0, 0, w, h, null);
		} finally {
			g.dispose();
		}
		return out;
	}

	/** Tipos exóticos (paleta, CMYK, custom) a RGB/ARGB para el encoder. */
	private static BufferedImage toSafeType(BufferedImage src) {
		int type = src.getColorModel().hasAlpha()
				? BufferedImage.TYPE_INT_ARGB
				: BufferedImage.TYPE_INT_RGB;
		if (src.getType() == type) return src;
		return toType(src, type);
	}

	private static BufferedImage toType(BufferedImage src, int type) {
		if (src.getType() == type) return src;
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), type);
		Graphics2D g = out.createGraphics();
		try {
			g.drawImage(src, 0, 0, null);
		} finally {
			g.dispose();
		}
		return out;
	}

	private static byte[] encodeWebp(BufferedImage image) {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
		if (!writers.hasNext()) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
					"Codificador WebP no disponible");
		}
		ImageWriter writer = writers.next();
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
				MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
			writer.setOutput(ios);
			ImageWriteParam param = writer.getDefaultWriteParam();
			if (param.canWriteCompressed()) {
				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				String[] types = param.getCompressionTypes();
				if (types != null && types.length > 0) {
					// "Lossy" en webp-imageio; si cambia, el primero sigue válido.
					param.setCompressionType(types[0]);
				}
				param.setCompressionQuality(WEBP_QUALITY);
			}
			writer.write(null, new IIOImage(image, null, null), param);
			ios.flush();
			return out.toByteArray();
		} catch (IOException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo procesar la imagen");
		} finally {
			writer.dispose();
		}
	}

	@Override
	public List<StoredObject> list(String folder) {
		if (!enabled()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
					"Storage no configurado (aws.s3.bucket)");
		}
		String prefix = folder.endsWith("/") ? folder : folder + "/";
		List<StoredObject> objects = new java.util.ArrayList<>();
		String continuationToken = null;
		do {
			var request = ListObjectsV2Request.builder()
					.bucket(bucket)
					.prefix(prefix);
			if (continuationToken != null) {
				request.continuationToken(continuationToken);
			}
			var response = client.listObjectsV2(request.build());
			response.contents().stream()
					.filter(object -> !object.key().endsWith("/"))
					.map(object -> new StoredObject(object.key(),
							publicUrlBase.isEmpty() ? defaultUrl(object.key()) : publicUrlBase + "/" + object.key()))
					.forEach(objects::add);
			continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
		} while (continuationToken != null);
		return objects;
	}

	@Override
	public String keyForUrl(String url) {
		return extractKey(url);
	}

	@Override
	public void deleteKey(String key) {
		if (!enabled() || key == null || key.isBlank()) {
			return;
		}
		try {
			client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (RuntimeException e) {
			log.warn("Could not delete S3 object {}: {}", key, e.getMessage());
		}
	}

	@Override
	public void delete(String url) {
		if (!enabled()) {
			return;
		}
		deleteKey(extractKey(url));
	}

	private String defaultUrl(String key) {
		return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
	}

	/**
	 * Clave a partir de una URL propia (public-url-base o formato virtual-host/path-style de S3).
	 */
	private String extractKey(String url) {
		if (url == null) {
			return null;
		}
		if (!publicUrlBase.isEmpty() && url.startsWith(publicUrlBase + "/")) {
			return url.substring(publicUrlBase.length() + 1);
		}
		try {
			URI uri = new URI(url);
			String host = uri.getHost();
			String path = uri.getPath();
			if (host == null || path == null || !host.contains("amazonaws.com")) {
				return null;
			}
			String key = path.startsWith("/") ? path.substring(1) : path;
			if (host.startsWith(bucket + ".")) {
				return key;
			}
			if (key.startsWith(bucket + "/")) {
				return key.substring(bucket.length() + 1);
			}
			return null;
		} catch (URISyntaxException e) {
			return null;
		}
	}
}
