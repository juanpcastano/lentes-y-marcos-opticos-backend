package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixWriteEvent;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixWriteEventRepository;

import lombok.AllArgsConstructor;

/**
 * Limita las escrituras en la ficha de Softix por teléfono. El evento se
 * registra en una transacción independiente para que un fallo posterior de
 * Softix no borre el intento y permita reintentar sin límite.
 */
@Service
@AllArgsConstructor
public class SoftixWriteLimiter {

	public static final String OPERATION_LINK = "LINK";
	public static final String OPERATION_CREATE = "CREATE";
	public static final String OPERATION_UPDATE = "UPDATE";

	public static final int MAX_WRITES_PER_HOUR = 3;

	private final SoftixWriteEventRepository repository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void checkAndRecord(String subjectKey, String operation) {
		LocalDateTime now = LocalDateTime.now();
		repository.lockSubject(subjectKey);
		long recent = repository.countBySubjectKeyAndCreatedAtAfter(
				subjectKey, now.minusHours(1));
		if (recent >= MAX_WRITES_PER_HOUR) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Has hecho demasiados cambios en tu cuenta. Contáctanos.");
		}

		SoftixWriteEvent event = new SoftixWriteEvent();
		event.setSubjectKey(subjectKey);
		event.setOperation(operation);
		event.setCreatedAt(now);
		repository.save(event);
	}

	@Scheduled(cron = "${softix.write-events.cleanup-cron:0 30 3 * * *}")
	@Transactional
	public void cleanupOldEvents() {
		repository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(7));
	}
}
