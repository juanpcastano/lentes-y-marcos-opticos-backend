package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

/** Envío de mensajes por WhatsApp Business. */
public interface WhatsAppService {

	void sendOtp(String phoneE164, String code);
}
