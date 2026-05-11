package com.telecom.mvno.devicedetails.security;

import com.telecom.mvno.devicedetails.exception.AuthenticationException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String MVNO_ID_CLAIM = "mvno_id";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String mvnoId = jwt.getClaimAsString(MVNO_ID_CLAIM);
        if (mvnoId == null || mvnoId.isBlank()) {
            throw new AuthenticationException("JWT does not contain required claim: " + MVNO_ID_CLAIM);
        }
        return new UsernamePasswordAuthenticationToken(mvnoId, jwt, List.of());
    }
}
