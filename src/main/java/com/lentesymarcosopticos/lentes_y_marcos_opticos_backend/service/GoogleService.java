package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import org.springframework.http.HttpStatus;

@Service
public class GoogleService {

	private final GoogleIdTokenVerifier verifier;

	public GoogleService(@Value("${google.oauth.client-id}") String clientId) {
		this.verifier = new GoogleIdTokenVerifier.Builder(
				new NetHttpTransport(),
				GsonFactory.getDefaultInstance())
				.setAudience(Collections.singletonList(clientId))
				.build();
	}

	public GoogleIdToken.Payload verify(String idTokenString) {
		try {
			GoogleIdToken idToken = verifier.verify(idTokenString);
			if (idToken == null) {
				throw new ApiException(HttpStatus.UNAUTHORIZED, "ID token de Google inválido");
			}
			return idToken.getPayload();
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "ID token de Google inválido");
		}
	}
}
