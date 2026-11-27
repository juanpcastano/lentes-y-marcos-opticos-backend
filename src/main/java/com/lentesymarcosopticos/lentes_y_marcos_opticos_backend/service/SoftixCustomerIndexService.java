package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixCustomerIndex;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixIndexRun;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerPage;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixCustomerIndexRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixIndexRunRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Refresca el índice teléfono -> cliente Softix. Softix no busca por móvil, así
 * que se recorre GET /api/Customers/List, se deduplica por documento y se
 * reemplaza el índice solo si la lectura fue razonablemente completa.
 */
@Service
@Slf4j
public class SoftixCustomerIndexService {

	private static final int PAGE_SIZE = 100;

	private final SoftixClient softixClient;
	private final SoftixCustomerIndexRepository indexRepository;
	private final SoftixIndexRunRepository runRepository;
	private final PhoneNormalizer phoneNormalizer;
	private final TransactionTemplate transactionTemplate;
	private final boolean seedOnStartup;

	public SoftixCustomerIndexService(SoftixClient softixClient,
			SoftixCustomerIndexRepository indexRepository,
			SoftixIndexRunRepository runRepository,
			PhoneNormalizer phoneNormalizer,
			TransactionTemplate transactionTemplate,
			@Value("${softix.index.seed-on-startup:false}") boolean seedOnStartup) {
		this.softixClient = softixClient;
		this.indexRepository = indexRepository;
		this.runRepository = runRepository;
		this.phoneNormalizer = phoneNormalizer;
		this.transactionTemplate = transactionTemplate;
		this.seedOnStartup = seedOnStartup;
	}

	@Async
	@EventListener(ApplicationReadyEvent.class)
	public void seedIfEmpty() {
		if (!seedOnStartup || indexRepository.count() > 0) {
			return;
		}
		try {
			refresh();
		} catch (RuntimeException ex) {
			log.warn("No se pudo construir el índice inicial de clientes: {}", ex.getMessage());
		}
	}

	public synchronized IndexResult refresh() {
		SoftixIndexRun run = new SoftixIndexRun();
		run.setStartedAt(LocalDateTime.now());
		run.setStatus(SoftixIndexRun.STATUS_RUNNING);
		run = runRepository.save(run);

		try {
			FetchedClients fetched = fetchAllClients();
			Map<String, CustomerResponse> clients = fetched.clients();
			Integer totalRecords = fetched.totalRecords();
			Map<String, Map<String, SoftixCustomerIndex>> rowsByPhone = new LinkedHashMap<>();
			LocalDateTime now = LocalDateTime.now();

			for (CustomerResponse customer : clients.values()) {
				String phone = validPhone(customer);
				if (phone == null || customer.documentId() == null
						|| customer.documentId().isBlank()) {
					continue;
				}
				rowsByPhone.computeIfAbsent(phone, key -> new LinkedHashMap<>())
						.computeIfAbsent(customer.documentId(), key -> indexRow(phone, customer.documentId(), now));
			}

			List<SoftixCustomerIndex> rows = rowsByPhone.values().stream()
					.flatMap(map -> map.values().stream())
					.toList();

			if (rows.isEmpty()) {
				throw new IllegalStateException("No se pudo indexar ningún teléfono válido");
			}
			if (totalRecords != null && totalRecords > 0
					&& clients.size() < totalRecords * 0.9) {
				throw new IllegalStateException("Lectura incompleta: " + clients.size()
						+ " de " + totalRecords + " fichas; se conserva el índice anterior");
			}

			transactionTemplate.executeWithoutResult(status -> {
				indexRepository.deleteAllInBatch();
				indexRepository.saveAll(rows);
			});

			boolean complete = totalRecords == null || clients.size() >= totalRecords;
			run.setFinishedAt(LocalDateTime.now());
			run.setStatus(complete ? SoftixIndexRun.STATUS_OK : SoftixIndexRun.STATUS_PARTIAL);
			run.setTotalRecords(totalRecords);
			run.setUniqueClients(clients.size());
			run.setIndexedPhones(rowsByPhone.size());
			run.setMessage(complete
					? "Índice actualizado"
					: "Lectura incompleta: " + clients.size() + " de " + totalRecords + " fichas");
			runRepository.save(run);
			return new IndexResult(run.getStatus(), totalRecords, clients.size(), rowsByPhone.size());
		} catch (RuntimeException ex) {
			run.setFinishedAt(LocalDateTime.now());
			run.setStatus(SoftixIndexRun.STATUS_FAILED);
			run.setMessage(truncate(ex.getMessage()));
			runRepository.save(run);
			log.warn("Refresco del índice de clientes fallido: {}", ex.getMessage());
			throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
					"No fue posible actualizar el índice de clientes");
		}
	}

	private FetchedClients fetchAllClients() {
		CustomerPage first = softixClient.listCustomers(1, PAGE_SIZE);
		if (first == null) {
			throw new IllegalStateException("Softix no devolvió la primera página");
		}
		Map<String, CustomerResponse> unique = new LinkedHashMap<>();
		addPage(unique, first);

		int totalPages = first.totalPages() != null ? first.totalPages()
				: first.totalRecords() == null ? 0
						: (int) Math.ceil((double) first.totalRecords() / PAGE_SIZE);
		for (int page = 2; page <= totalPages; page++) {
			CustomerPage next = softixClient.listCustomers(page, PAGE_SIZE);
			if (next == null) {
				continue;
			}
			addPage(unique, next);
		}
		return new FetchedClients(unique, first.totalRecords());
	}

	private record FetchedClients(Map<String, CustomerResponse> clients, Integer totalRecords) {
	}

	private void addPage(Map<String, CustomerResponse> unique, CustomerPage page) {
		if (page.client() == null) {
			return;
		}
		for (CustomerResponse customer : page.client()) {
			if (customer.documentId() != null && !customer.documentId().isBlank()) {
				unique.putIfAbsent(customer.documentId(), customer);
			}
		}
	}

	private String validPhone(CustomerResponse customer) {
		for (String value : new String[] { customer.mobile(), customer.phone() }) {
			if (value == null || value.isBlank()) {
				continue;
			}
			try {
				return phoneNormalizer.normalize(value);
			} catch (RuntimeException ignored) {
				// Se prueba el siguiente campo.
			}
		}
		return null;
	}

	private SoftixCustomerIndex indexRow(String phone, String clientId, LocalDateTime now) {
		SoftixCustomerIndex row = new SoftixCustomerIndex();
		row.setPhoneE164(phone);
		row.setSoftixClientId(clientId);
		row.setSyncedAt(now);
		return row;
	}

	private String truncate(String value) {
		if (value == null) {
			return null;
		}
		return value.length() <= 500 ? value : value.substring(0, 500);
	}

	public record IndexResult(String status, Integer totalRecords, int uniqueClients, int indexedPhones) {
	}
}
