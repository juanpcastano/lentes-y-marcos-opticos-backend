package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

/**
 * Respuesta de verificación/completado. Cuando `registrationPending` es
 * verdadero, el OTP está verificado pero todavía no existe cuenta local: el
 * frontend debe vincular o crear la ficha. En ese caso `accessToken` y `user`
 * son nulos y el token de registro viaja en cookie httpOnly.
 */
public record PhoneAuthResponse(
		String accessToken,
		UserResponse user,
		LinkState link,
		boolean registrationPending) {
}
