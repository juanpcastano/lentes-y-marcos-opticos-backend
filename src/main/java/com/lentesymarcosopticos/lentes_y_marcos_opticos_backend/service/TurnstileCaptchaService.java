package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;

/** Verificación con Cloudflare Turnstile. */
@Service
@ConditionalOnProperty(name = "captcha.mode", havingValue = "turnstile")
public class TurnstileCaptchaService implements CaptchaService {

	private final RestClient restClient;
	private final String secret;

	public TurnstileCaptchaService(
			@Value("${captcha.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}") String verifyUrl,
			@Value("${captcha.turnstile.secret:}") String secret) {
		this.restClient = RestClient.builder().baseUrl(verifyUrl).build();
		this.secret = secret;
	}

	@Override
	public void verify(String token, String remoteIp) {
		if (secret.isBlank()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Captcha no está configurado");
		}
		if (token == null || token.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Completa el captcha para continuar");
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("secret", secret);
		form.add("response", token);
		if (remoteIp != null && !remoteIp.isBlank()) {
			form.add("remoteip", remoteIp);
		}
		try {
			Map<?, ?> response = restClient.post()
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(Map.class);
			if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Captcha inválido. Intenta de nuevo.");
			}
		} catch (RestClientResponseException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "No fue posible validar el captcha");
		}
	}
}
