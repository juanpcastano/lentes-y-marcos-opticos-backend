package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixLinkResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixLinkService;

@ExtendWith(MockitoExtension.class)
class SoftixLinkControllerTest {
    @Mock
    private SoftixLinkService softixLinkService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SoftixLinkController controller = new SoftixLinkController(softixLinkService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    @Test
    void vinculaClienteYDevuelveDatosMinimos() throws Exception {
        when(softixLinkService.link(any())).thenReturn(
                new SoftixLinkResponse(
                        "123", "CC", "123", "Ana", "Pérez", "ana@test.com", "3001234567"));

        mockMvc.perform(post("/api/users/me/softix/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "documentType": "CC",
                          "documentId": "123"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("123"))
                .andExpect(jsonPath("$.firstName").value("Ana"));

        verify(softixLinkService).link(any());
    }

    @Test
    void rechazaBodySinIdentificacion() throws Exception {
        mockMvc.perform(post("/api/users/me/softix/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "documentType": "",
                          "documentId": ""
                        }
                        """))
                .andExpect(status().isBadRequest());
    }
}
