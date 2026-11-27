package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.JwtService;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain)
			throws ServletException, IOException {

		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}
		String token = header.substring(7);

		try {
			if (!jwtService.isAccessToken(token)) {
				filterChain.doFilter(request, response);
				return;
			}

			UUID userId = jwtService.extractUserId(token);

			if (SecurityContextHolder.getContext().getAuthentication() != null) {
				filterChain.doFilter(request, response);
				return;
			}

			List<GrantedAuthority> authorities = new ArrayList<>();
			if (jwtService.isAdminToken(token)) {
				authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
			}

			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
					userId.toString(), null, authorities);
			SecurityContextHolder.getContext().setAuthentication(authentication);

			filterChain.doFilter(request, response);
		} catch (JwtException | IllegalArgumentException e) {
			filterChain.doFilter(request, response);
		}
	}
}
