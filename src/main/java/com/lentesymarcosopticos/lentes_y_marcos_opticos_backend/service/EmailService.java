package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

public interface EmailService {

	void sendOtpCode(String email, String code, String purpose);
}
