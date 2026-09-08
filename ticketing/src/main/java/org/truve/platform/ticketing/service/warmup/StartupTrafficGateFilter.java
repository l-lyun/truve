package org.truve.platform.ticketing.service.warmup;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnExpression("${ticketing.warmup.enabled:false} or ${ticketing.startup.gate-enabled:false}")
@RequiredArgsConstructor
public class StartupTrafficGateFilter extends OncePerRequestFilter {

	private final ApplicationAvailability availability;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getServletPath();
		return !path.equals("/api") && !path.startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
			response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			response.setHeader("Retry-After", "1");
			return;
		}
		chain.doFilter(request, response);
	}
}
