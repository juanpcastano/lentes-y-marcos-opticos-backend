package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Modo de desarrollo: no envía nada, solo registra el código. Nunca usar en
 * producción.
 */
@Service
@ConditionalOnProperty(name = "whatsapp.mode", havingValue = "console", matchIfMissing = true)
public class ConsoleWhatsAppService implements WhatsAppService {

	private static final Logger log = LoggerFactory.getLogger(ConsoleWhatsAppService.class);

	@Override
	public void sendOtp(String phoneE164, String code) {
		log.warn("[DEV WHATSAPP] Código OTP para {}: {}", phoneE164, code);
	}
}
