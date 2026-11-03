package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SoftixClientTest {

    @Test
    void obtieneTokenYEnvíaAuthorizationBearer() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://softix.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SoftixClient client = newClient(builder);

        expectLogin(server, "token-prueba");
        server.expect(requestTo("http://softix.test/api/customers/123"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-prueba"))
                .andRespond(withSuccess(customerJson("123"), MediaType.APPLICATION_JSON));

        SoftixDtos.CustomerResponse customer = client.findCustomer("123");

        assertThat(customer.documentId()).isEqualTo("123");
        assertThat(customer.firstName()).isEqualTo("Ana");
        server.verify();
    }

    @Test
    void reutilizaTokenMientrasNoExpire() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://softix.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SoftixClient client = newClient(builder);

        expectLogin(server, "token-reutilizado");
        for (String documentId : new String[] { "123", "456" }) {
            server.expect(requestTo("http://softix.test/api/customers/" + documentId))
                    .andExpect(header("Authorization", "Bearer token-reutilizado"))
                    .andRespond(withSuccess(customerJson(documentId), MediaType.APPLICATION_JSON));
        }

        client.findCustomer("123");
        client.findCustomer("456");

        server.verify();
    }

    @Test
    void renuevaTokenYReintentaCuandoSoftixResponde401() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://softix.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SoftixClient client = newClient(builder);

        expectLogin(server, "token-expirado");
        server.expect(requestTo("http://softix.test/api/customers/123"))
                .andExpect(header("Authorization", "Bearer token-expirado"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        expectLogin(server, "token-nuevo");
        server.expect(requestTo("http://softix.test/api/customers/123"))
                .andExpect(header("Authorization", "Bearer token-nuevo"))
                .andRespond(withSuccess(customerJson("123"), MediaType.APPLICATION_JSON));

        SoftixDtos.CustomerResponse customer = client.findCustomer("123");

        assertThat(customer.documentId()).isEqualTo("123");
        server.verify();
    }

    private static SoftixClient newClient(RestClient.Builder builder) {
        return new SoftixClient(builder.build(), "test@softix.com", "password");
    }

    private static void expectLogin(MockRestServiceServer server, String token) {
        server.expect(requestTo("http://softix.test/api/account/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "token": "%s",
                          "expiration": "2099-01-01T00:00:00Z"
                        }
                        """.formatted(token), MediaType.APPLICATION_JSON));
    }

    private static String customerJson(String documentId) {
        return """
                {
                  "message": "Query successful",
                  "customer": {
                    "documentId": "%s",
                    "documentType": "CC",
                    "firstName": "Ana",
                    "lastName1": "Pérez"
                  }
                }
                """.formatted(documentId);
    }
}
