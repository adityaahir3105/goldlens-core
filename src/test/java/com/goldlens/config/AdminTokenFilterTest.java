package com.goldlens.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminTokenFilterTest {

    private MockHttpServletResponse run(String configuredToken, String uri, String header) throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter(configuredToken);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        if (header != null) {
            request.addHeader(AdminTokenFilter.HEADER, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // The chain records the request only when the filter let it through.
        response.setHeader("X-Passed", chain.getRequest() != null ? "yes" : "no");
        return response;
    }

    @Test
    void allowsAdminRequestWithCorrectToken() throws Exception {
        MockHttpServletResponse res = run("s3cret", "/api/admin/backfill-macro", "s3cret");
        assertEquals(200, res.getStatus());
        assertEquals("yes", res.getHeader("X-Passed"));
    }

    @Test
    void rejectsWrongOrMissingToken() throws Exception {
        assertEquals(401, run("s3cret", "/api/admin/backfill-macro", "nope").getStatus());
        assertEquals(401, run("s3cret", "/api/admin/backfill-macro", null).getStatus());
    }

    @Test
    void failsClosedWhenNoTokenConfigured() throws Exception {
        assertEquals(401, run("", "/api/admin/backfill-macro", "").getStatus());
        assertEquals(401, run(null, "/api/admin/backfill-macro", "anything").getStatus());
    }

    @Test
    void leavesPublicEndpointsAlone() throws Exception {
        MockHttpServletResponse res = run("s3cret", "/api/gold-risk/latest", null);
        assertEquals(200, res.getStatus());
        assertEquals("yes", res.getHeader("X-Passed"));
    }

    @Test
    void chainIsNotInvokedOnRejection() throws Exception {
        AdminTokenFilter filter = new AdminTokenFilter("s3cret");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/admin/backfill-wgc"), new MockHttpServletResponse(), chain);
        assertNull(chain.getRequest());
        MockFilterChain okChain = new MockFilterChain();
        MockHttpServletRequest ok = new MockHttpServletRequest("POST", "/api/admin/backfill-wgc");
        ok.addHeader(AdminTokenFilter.HEADER, "s3cret");
        filter.doFilter(ok, new MockHttpServletResponse(), okChain);
        assertNotNull(okChain.getRequest());
    }
}
