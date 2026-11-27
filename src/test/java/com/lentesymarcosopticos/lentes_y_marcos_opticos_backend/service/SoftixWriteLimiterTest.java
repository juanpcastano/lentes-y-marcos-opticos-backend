package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixWriteEvent;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixWriteEventRepository;

@ExtendWith(MockitoExtension.class)
class SoftixWriteLimiterTest {

	@Mock
	private SoftixWriteEventRepository repository;

	private SoftixWriteLimiter limiter() {
		return new SoftixWriteLimiter(repository);
	}

	@Test
	void registraElIntentoCuandoEstaDebajoDelLimite() {
		when(repository.countBySubjectKeyAndCreatedAtAfter(eq("+573001234567"), any()))
				.thenReturn(2L);

		limiter().checkAndRecord("+573001234567", SoftixWriteLimiter.OPERATION_UPDATE);

		verify(repository).lockSubject("+573001234567");
		ArgumentCaptor<SoftixWriteEvent> captor = ArgumentCaptor.forClass(SoftixWriteEvent.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getSubjectKey()).isEqualTo("+573001234567");
		assertThat(captor.getValue().getOperation())
				.isEqualTo(SoftixWriteLimiter.OPERATION_UPDATE);
	}

	@Test
	void bloqueaElCuartoIntentoEnUnaHora() {
		when(repository.countBySubjectKeyAndCreatedAtAfter(eq("+573001234567"), any()))
				.thenReturn(3L);

		assertThatThrownBy(() -> limiter().checkAndRecord("+573001234567",
				SoftixWriteLimiter.OPERATION_LINK))
				.isInstanceOfSatisfying(ApiException.class, ex -> {
					assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
					assertThat(ex.getMessage()).contains("demasiados cambios");
				});

		verify(repository, never()).save(any(SoftixWriteEvent.class));
	}
}
