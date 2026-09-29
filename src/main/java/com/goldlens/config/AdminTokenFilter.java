package com.goldlens.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Guards /api/admin/** with a shared token sent in the X-Admin-Token header.
 * Fails closed: if no token is configured, every admin request is rejected.
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenFilter.class);

    static final String HEADER = "X-Admin-Token";
    private static final String ADMIN_PREFIX = "/api/admin/";

    private final byte[] expected;

    public AdminTokenFilter(@Value("${admin.token:}") String token) {
        this.expected = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0) {
            log.warn("admin.token is not set - all /api/admin endpoints are disabled");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        boolean ok = expected.length > 0
                && provided != null
                && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8));

        if (!ok) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid " + HEADER);
            return;
        }
        chain.doFilter(request, response);
    }
}
