package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.security.JwtAuthenticationFilter;

import lombok.AllArgsConstructor;

/**
 * SecurityConfig
 */
@Configuration
@AllArgsConstructor
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtFilter;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(@Value("${cors.allowed-origins}") String origins) {
		CorsConfiguration corsConfiguration = new CorsConfiguration();
		for (String origin : origins.split(",")) {
			corsConfiguration.addAllowedOrigin(origin.trim());
		}
		corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		corsConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		corsConfiguration.setAllowCredentials(false);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", corsConfiguration);
		return source;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.authorizeHttpRequests(auth -> auth
					.requestMatchers(HttpMethod.POST,
							"/api/auth/signup",
							"/api/auth/login",
							"/api/auth/login/options",
							"/api/auth/refresh",
							"/api/auth/logout",
							"/api/auth/google",
							"/api/auth/login/code-request",
							"/api/auth/login/code-verify",
							"/api/auth/password/reset/request",
							"/api/auth/password/reset/verify",
							"/api/auth/password/reset/confirm")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**",
								"/api/brands/**")
						.permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.formLogin(form -> form.disable())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(
						(request, response, authException) -> {
							response.setStatus(HttpStatus.UNAUTHORIZED.value());
							response.setContentType(MediaType.APPLICATION_JSON_VALUE);
							response.getWriter().write(
									"{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\",\"path\":\""
											+ request.getRequestURI() + "\",\"details\":null}");
						}))
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}
}
