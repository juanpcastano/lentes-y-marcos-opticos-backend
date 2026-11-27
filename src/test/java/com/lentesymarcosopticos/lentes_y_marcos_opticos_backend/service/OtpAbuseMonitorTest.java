package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OtpAbuseMonitorTest {

	@Test
	void alertaUnaSolaVezPorDiaAlPasarElUmbralDiario() {
		OtpAbuseMonitor monitor = new OtpAbuseMonitor(500, 400, 5, 50, 15);

		assertThat(monitor.dailyUsageAlert(399)).isFalse();
		assertThat(monitor.dailyUsageAlert(400)).isTrue();
		assertThat(monitor.dailyUsageAlert(401)).isFalse();
	}

	@Test
	void alertaUnaSolaVezPorDiaAlAlcanzarElLimiteDiario() {
		OtpAbuseMonitor monitor = new OtpAbuseMonitor(500, 400, 5, 50, 15);

		assertThat(monitor.dailyLimitReached(500)).isTrue();
		assertThat(monitor.dailyLimitReached(501)).isFalse();
	}

	@Test
	void alertaUnaSolaVezPorVentanaDePico() {
		OtpAbuseMonitor monitor = new OtpAbuseMonitor(500, 400, 5, 50, 15);

		assertThat(monitor.spikeDetected(49)).isFalse();
		assertThat(monitor.spikeDetected(50)).isTrue();
		assertThat(monitor.spikeDetected(60)).isFalse();
	}
}
