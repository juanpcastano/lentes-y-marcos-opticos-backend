package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "email.mode", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailService implements EmailService {

	private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

	@Override
	public void sendOtpCode(String email, String code, String purpose) {
		log.info("[DEV EMAIL] OTP code for {} (purpose={}): {}", email, purpose, code);
	}
}
