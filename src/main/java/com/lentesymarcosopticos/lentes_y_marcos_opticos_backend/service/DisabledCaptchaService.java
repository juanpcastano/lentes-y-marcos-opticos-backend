package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Modo de desarrollo: acepta cualquier solicitud sin captcha. */
@Service
@ConditionalOnProperty(name = "captcha.mode", havingValue = "disabled", matchIfMissing = true)
public class DisabledCaptchaService implements CaptchaService {

	@Override
	public void verify(String token, String remoteIp) {
		// Sin captcha en desarrollo.
	}
}
