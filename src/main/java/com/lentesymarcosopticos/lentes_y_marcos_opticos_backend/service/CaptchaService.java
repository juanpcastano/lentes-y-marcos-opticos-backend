package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

/** Verificación de captcha antes de iniciar un OTP. */
public interface CaptchaService {

	void verify(String token, String remoteIp);
}
