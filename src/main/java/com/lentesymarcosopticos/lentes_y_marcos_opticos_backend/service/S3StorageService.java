package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.List;
import java.util.UUID;

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
 */
@Service
@Slf4j
public class S3StorageService implements StorageService {

	private static final Map<String, String> EXT_BY_CONTENT_TYPE = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp");

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
		String contentType = file.getContentType();
		String ext = contentType == null ? null : EXT_BY_CONTENT_TYPE.get(contentType.toLowerCase());
		if (ext == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"Formato no soportado (jpg, png, webp)");
		}
		String key = folder + "/" + UUID.randomUUID() + "." + ext;
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (java.io.IOException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo");
		}
		client.putObject(
				PutObjectRequest.builder()
						.bucket(bucket)
						.key(key)
						.contentType(contentType)
						.build(),
				RequestBody.fromBytes(bytes));
		return publicUrlBase.isEmpty() ? defaultUrl(key) : publicUrlBase + "/" + key;
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
