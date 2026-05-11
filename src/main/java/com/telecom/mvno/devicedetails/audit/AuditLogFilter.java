package com.telecom.mvno.devicedetails.audit;

import com.telecom.mvno.devicedetails.domain.audit.AuditEntry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditLogFilter extends OncePerRequestFilter {

    private static final String MDC_CORRELATION_ID = "correlationId";

    private final AuditLogService auditLogService;

    public AuditLogFilter(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String callerIp = request.getRemoteAddr();
        String msisdn = request.getParameter("msisdn");

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String correlationId = MDC.get(MDC_CORRELATION_ID);

            String mvnoId = null;
            String credentialId = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() != null) {
                mvnoId = auth.getPrincipal().toString();
                credentialId = auth.getName();
            }

            AuditEntry.Outcome outcome = resolveOutcome(status);

            AuditEntry entry = new AuditEntry(
                    correlationId,
                    Instant.now(),
                    mvnoId,
                    msisdn,
                    callerIp,
                    credentialId,
                    method,
                    uri,
                    status,
                    durationMs,
                    outcome
            );

            auditLogService.log(entry);
        }
    }

    private AuditEntry.Outcome resolveOutcome(int status) {
        if (status >= 200 && status < 300) {
            return AuditEntry.Outcome.SUCCESS;
        } else if (status == 400) {
            return AuditEntry.Outcome.VALIDATION_ERROR;
        } else if (status == 401 || status == 403) {
            return AuditEntry.Outcome.AUTH_FAILURE;
        } else if (status == 404) {
            return AuditEntry.Outcome.NOT_FOUND;
        } else if (status == 503) {
            return AuditEntry.Outcome.VENDOR_ERROR;
        }
        return AuditEntry.Outcome.VENDOR_ERROR;
    }
}
