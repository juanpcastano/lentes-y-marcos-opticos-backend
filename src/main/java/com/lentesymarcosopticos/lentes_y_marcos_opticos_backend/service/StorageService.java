package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

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

	/**
	 * Elimina el objeto si la URL corresponde al bucket propio (best-effort).
	 */
	void delete(String url);
}
