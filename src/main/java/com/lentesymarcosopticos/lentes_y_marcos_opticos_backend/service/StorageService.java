package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * StorageService — almacenamiento de archivos de producto
 */
public interface StorageService {

	boolean enabled();

	/**
	 * Guarda el archivo y devuelve su URL pública.
	 */
	String store(MultipartFile file, String folder);

	List<StoredObject> list(String folder);

	/**
	 * Devuelve la clave interna para una URL perteneciente a este storage.
	 */
	String keyForUrl(String url);

	/**
	 * Elimina una clave previamente validada del bucket propio.
	 */
	void deleteKey(String key);

	/**
	 * Elimina el objeto si la URL corresponde al bucket propio (best-effort).
	 */
	void delete(String url);

	record StoredObject(String key, String url) {
	}
}
