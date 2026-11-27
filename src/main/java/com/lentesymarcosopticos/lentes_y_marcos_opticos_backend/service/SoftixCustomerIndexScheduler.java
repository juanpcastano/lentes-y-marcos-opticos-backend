package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Refresco horario del índice de clientes. Corre en su propio registro y con
 * su propio try/catch para no bloquear ni ser bloqueado por la sincronización
 * de inventario.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SoftixCustomerIndexScheduler {

	private final SoftixCustomerIndexService indexService;

	@Scheduled(cron = "${softix.index.cron:0 5 * * * *}")
	public void refreshHourly() {
		try {
			indexService.refresh();
		} catch (RuntimeException ex) {
			log.warn("Refresco horario del índice de clientes fallido: {}", ex.getMessage());
		}
	}
}
