package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

class CookieServiceTest {

	private CookieService service;

	@BeforeEach
	void setUp() {
		service = new CookieService(false, "lax", "", "/api/auth",
				900000, 900000, 15552000);
	}

	@Test
	void reutilizaElDispositivoCuandoLaCookieEsValida() {
		String deviceId = UUID.randomUUID().toString();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie("lm_device", deviceId));

		assertThat(service.resolveDeviceId(request)).isEqualTo(deviceId);
	}

	@Test
	void creaUnDispositivoNuevoCuandoNoHayCookie() {
		MockHttpServletRequest request = new MockHttpServletRequest();

		String deviceId = service.resolveDeviceId(request);

		assertThat(UUID.fromString(deviceId)).isNotNull();
	}

	@Test
	void reemplazaUnaCookieDeDispositivoInvalida() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie("lm_device", "no-es-uuid"));

		String deviceId = service.resolveDeviceId(request);

		assertThat(deviceId).isNotEqualTo("no-es-uuid");
		assertThat(UUID.fromString(deviceId)).isNotNull();
	}

	@Test
	void escribeLaCookieHttpOnlyConElPathDelFlujoDeTelefono() {
		MockHttpServletResponse response = new MockHttpServletResponse();
		String deviceId = UUID.randomUUID().toString();

		service.setDeviceCookie(response, deviceId);

		String header = response.getHeader("Set-Cookie");
		assertThat(header).contains("lm_device=" + deviceId);
		assertThat(header).contains("Path=/api/auth/phone");
		assertThat(header).contains("HttpOnly");
		assertThat(header).contains("SameSite=lax");
	}
}
