package com.telecom.mvno.devicedetails.service;

import com.telecom.mvno.devicedetails.exception.AccessDeniedException;
import com.telecom.mvno.devicedetails.exception.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class TenantIsolationService {

    public void assertIdentityMatch(String requestedMvno) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // No principal means auth failed entirely — 401, not 403
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AuthenticationException("No authenticated principal found");
        }

        String authenticatedMvno = authentication.getPrincipal().toString();

        if (!authenticatedMvno.equalsIgnoreCase(requestedMvno)) {
            throw new AccessDeniedException(
                    "Authenticated MVNO '" + authenticatedMvno + "' does not match requested MVNO '" + requestedMvno + "'");
        }
    }
}
