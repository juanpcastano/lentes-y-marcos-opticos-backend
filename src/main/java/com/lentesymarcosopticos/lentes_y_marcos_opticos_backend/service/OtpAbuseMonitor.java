package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Alertas operativas del gasto de WhatsApp. No bloquea por sí mismo: el tope
 * diario se aplica en {@link OtpService}. Los mensajes usan claves estables
 * para que CloudWatch pueda alertar sobre ellos.
 */
@Service
@Slf4j
public class OtpAbuseMonitor {

	private final int dailyLimit;
	private final int dailyAlertThreshold;
	private final int spikeWindowMinutes;
	private final int spikeThreshold;
	private final int spikeAlertCooldownMinutes;

	private final AtomicReference<LocalDate> dailyThresholdAlert = new AtomicReference<>();
	private final AtomicReference<LocalDate> dailyLimitAlert = new AtomicReference<>();
	private final AtomicReference<LocalDateTime> spikeAlert = new AtomicReference<>();

	public OtpAbuseMonitor(
			@Value("${whatsapp.daily-limit:500}") int dailyLimit,
			@Value("${whatsapp.daily-alert-threshold:400}") int dailyAlertThreshold,
			@Value("${whatsapp.spike-window-minutes:5}") int spikeWindowMinutes,
			@Value("${whatsapp.spike-threshold:50}") int spikeThreshold,
			@Value("${whatsapp.spike-alert-cooldown-minutes:15}") int spikeAlertCooldownMinutes) {
		this.dailyLimit = dailyLimit;
		this.dailyAlertThreshold = dailyAlertThreshold;
		this.spikeWindowMinutes = spikeWindowMinutes;
		this.spikeThreshold = spikeThreshold;
		this.spikeAlertCooldownMinutes = spikeAlertCooldownMinutes;
	}

	public int dailyLimit() {
		return dailyLimit;
	}

	public int spikeWindowMinutes() {
		return spikeWindowMinutes;
	}

	public boolean dailyUsageAlert(long count) {
		if (count < dailyAlertThreshold) {
			return false;
		}
		LocalDate today = LocalDate.now();
		LocalDate previous = dailyThresholdAlert.getAndSet(today);
		if (today.equals(previous)) {
			return false;
		}
		log.warn("event=otp.daily_threshold count={} limit={} threshold={}",
				count, dailyLimit, dailyAlertThreshold);
		return true;
	}

	public boolean dailyLimitReached(long count) {
		LocalDate today = LocalDate.now();
		LocalDate previous = dailyLimitAlert.getAndSet(today);
		if (today.equals(previous)) {
			return false;
		}
		log.error("event=otp.daily_limit count={} limit={}", count, dailyLimit);
		return true;
	}

	public boolean spikeDetected(long count) {
		if (count < spikeThreshold) {
			return false;
		}
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime previous = spikeAlert.get();
		if (previous != null
				&& previous.plusMinutes(spikeAlertCooldownMinutes).isAfter(now)) {
			return false;
		}
		if (!spikeAlert.compareAndSet(previous, now)) {
			return false;
		}
		log.warn("event=otp.spike count={} windowMinutes={} threshold={}",
				count, spikeWindowMinutes, spikeThreshold);
		return true;
	}
}
