package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Envío real por Meta WhatsApp Cloud API. La plantilla debe estar aprobada y
 * recibe el código como único parámetro de cuerpo.
 */
@Service
@ConditionalOnProperty(name = "whatsapp.mode", havingValue = "meta")
@Slf4j
public class MetaWhatsAppService implements WhatsAppService {

	private final RestClient restClient;
	private final String phoneNumberId;
	private final String accessToken;
	private final String templateName;
	private final String languageCode;

	public MetaWhatsAppService(
			@Value("${whatsapp.meta.base-url:https://graph.facebook.com/v21.0}") String baseUrl,
			@Value("${whatsapp.meta.phone-number-id:}") String phoneNumberId,
			@Value("${whatsapp.meta.access-token:}") String accessToken,
			@Value("${whatsapp.meta.template:otp_acceso}") String templateName,
			@Value("${whatsapp.meta.language:es}") String languageCode) {
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
		this.phoneNumberId = phoneNumberId;
		this.accessToken = accessToken;
		this.templateName = templateName;
		this.languageCode = languageCode;
	}

	@Override
	public void sendOtp(String phoneE164, String code) {
		if (phoneNumberId.isBlank() || accessToken.isBlank()) {
			throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
					"WhatsApp no está configurado");
		}
		Map<String, Object> body = Map.of(
				"messaging_product", "whatsapp",
				"to", phoneE164,
				"type", "template",
				"template", Map.of(
						"name", templateName,
						"language", Map.of("code", languageCode),
						"components", List.of(Map.of(
								"type", "body",
								"parameters", List.of(Map.of("type", "text", "text", code))))));
		try {
			restClient.post()
					.uri("/{phoneNumberId}/messages", phoneNumberId)
					.header("Authorization", "Bearer " + accessToken)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientResponseException ex) {
			log.warn("WhatsApp rechazó el envío: {}", ex.getStatusCode());
			throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
					"No fue posible enviar el código por WhatsApp");
		}
	}
}
