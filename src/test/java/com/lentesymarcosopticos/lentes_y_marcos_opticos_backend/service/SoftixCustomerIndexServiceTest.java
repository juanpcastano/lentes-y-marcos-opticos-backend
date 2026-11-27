package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixCustomerIndex;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerPage;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixCustomerIndexRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixIndexRunRepository;

@ExtendWith(MockitoExtension.class)
class SoftixCustomerIndexServiceTest {

	@Mock
	private SoftixClient softixClient;
	@Mock
	private SoftixCustomerIndexRepository indexRepository;
	@Mock
	private SoftixIndexRunRepository runRepository;
	@Mock
	private TransactionTemplate transactionTemplate;

	private SoftixCustomerIndexService service;

	@BeforeEach
	void setUp() {
		service = new SoftixCustomerIndexService(softixClient, indexRepository, runRepository,
				new PhoneNormalizer(), transactionTemplate, false);
		when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		doAnswer(invocation -> {
			java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action =
					invocation.getArgument(0);
			action.accept(null);
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());
	}

	@Test
	void deduplicaPorDocumentoYNormalizaTelefonos() {
		CustomerPage first = new CustomerPage(3, 1, 2, 2, null, List.of(
				customer("100", "3001234567"),
				customer("200", "+57 300-999-8888")));
		CustomerPage second = new CustomerPage(3, 2, 2, 2, null, List.of(
				customer("100", "3001234567"),
				customer("300", "6011234567")));
		when(softixClient.listCustomers(1, 100)).thenReturn(first);
		when(softixClient.listCustomers(2, 100)).thenReturn(second);

		var result = service.refresh();

		assertThat(result.uniqueClients()).isEqualTo(3);
		assertThat(result.indexedPhones()).isEqualTo(2);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<SoftixCustomerIndex>> rows = ArgumentCaptor.forClass(List.class);
		verify(indexRepository).saveAll(rows.capture());
		assertThat(rows.getValue()).hasSize(2);
		assertThat(rows.getValue()).extracting(SoftixCustomerIndex::getPhoneE164)
				.containsExactlyInAnyOrder("+573001234567", "+573009998888");
	}

	private CustomerResponse customer(String documentId, String mobile) {
		return new CustomerResponse(documentId, "CC", "Ana", null, "Pérez", null,
				mobile, mobile, null, null, null, "76", "001", "Femenino");
	}
}
