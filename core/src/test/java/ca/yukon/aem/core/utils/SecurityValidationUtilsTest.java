package ca.yukon.aem.core.utils;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AemContextExtension.class)
class SecurityValidationUtilsTest {

    private TestLogger logger;

    @BeforeEach
    void setup() {
        logger = TestLoggerFactory.getTestLogger(SecurityValidationUtils.class);
        TestLoggerFactory.clear();
    }

    // ========== isValidRequest Tests ==========

    @Test
    void testIsValidRequest_NoReferer_Allowed(AemContext context) {
        MockSlingHttpServletRequest request = context.request();
        // No Referer header

        boolean result = SecurityValidationUtils.isValidRequest(request, logger);

        assertTrue(result, "Should allow requests with no referer (direct navigation)");
    }

    @Test
    void testIsValidRequest_SameOrigin_Allowed(AemContext context) {
        MockSlingHttpServletRequest request = context.request();
        request.setHeader("Referer", "http://localhost:4502/some-page");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(4502);

        boolean result = SecurityValidationUtils.isValidRequest(request, logger);

        assertTrue(result, "Should allow same-origin requests");
    }

    @Test
    void testIsValidRequest_CrossOrigin_Blocked(AemContext context) {
        TestLoggerFactory.clear();
        MockSlingHttpServletRequest request = context.request();
        request.setHeader("Referer", "https://malicious-site.com/phishing");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(4502);

        boolean result = SecurityValidationUtils.isValidRequest(request, logger);

        assertFalse(result, "Should block cross-origin requests");
        assertEquals(1, logger.getLoggingEvents().size(), "Should log blocked request");
        assertTrue(
                logger.getLoggingEvents().get(0).getMessage().contains("BLOCKED"),
                "Log should contain BLOCKED");
    }

    @Test
    void testIsValidRequest_EmptyReferer_Allowed(AemContext context) {
        MockSlingHttpServletRequest request = context.request();
        request.setHeader("Referer", "");

        boolean result = SecurityValidationUtils.isValidRequest(request, logger);

        assertTrue(result, "Should allow requests with empty referer");
    }

    // ========== isSameOrigin Tests ==========

    @Test
    void testIsSameOrigin_SameSchemeHostPort(AemContext context) {
        MockSlingHttpServletRequest request = context.request();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(4502);

        boolean result =
                SecurityValidationUtils.isSameOrigin(request, "http://localhost:4502/some-page");

        assertTrue(result, "Should return true for same origin");
    }

    @Test
    void testIsSameOrigin_DifferentScheme(AemContext context) {
        MockSlingHttpServletRequest request = context.request();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(4502);

        boolean result =
                SecurityValidationUtils.isSameOrigin(request, "https://localhost:4502/some-page");

        assertFalse(result, "Should return false for different scheme");
    }

    @Test
    void testIsSameOrigin_DifferentHost(AemContext context) {
        MockSlingHttpServletRequest request = context.request();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(4502);

        boolean result =
                SecurityValidationUtils.isSameOrigin(request, "http://example.com:4502/some-page");

        assertFalse(result, "Should return false for different host");
    }

    @Test
    void testIsSameOrigin_DifferentPort(AemContext context) {
        MockSlingHttpServletRequest request = context.request();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(4502);

        boolean result =
                SecurityValidationUtils.isSameOrigin(request, "http://localhost:8080/some-page");

        assertFalse(result, "Should return false for different port");
    }

    @Test
    void testIsSameOrigin_DefaultHttpsPort(AemContext context) {
        MockSlingHttpServletRequest request = context.request();
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);

        boolean result = SecurityValidationUtils.isSameOrigin(request, "https://example.com/some-page");

        assertTrue(result, "Should return true when both use default HTTPS port");
    }

    @Test
    void testIsSameOrigin_DefaultHttpPort(AemContext context) {
        MockSlingHttpServletRequest request = context.request();
        request.setScheme("http");
        request.setServerName("example.com");
        request.setServerPort(80);

        boolean result = SecurityValidationUtils.isSameOrigin(request, "http://example.com/some-page");

        assertTrue(result, "Should return true when both use default HTTP port");
    }

    @Test
    void testIsSameOrigin_InvalidUri(AemContext context) {
        MockSlingHttpServletRequest request = context.request();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(4502);

        boolean result = SecurityValidationUtils.isSameOrigin(request, "not-a-valid-uri");

        assertFalse(result, "Should return false for invalid URI");
    }

    // ========== getPort Tests ==========

    @Test
    void testGetPort_ExplicitPort() throws Exception {
        java.net.URI uri = new java.net.URI("http://example.com:8080/path");

        int port = SecurityValidationUtils.getPort(uri, "http");

        assertEquals(8080, port, "Should return explicit port");
    }

    @Test
    void testGetPort_DefaultHttpsPort() throws Exception {
        java.net.URI uri = new java.net.URI("https://example.com/path");

        int port = SecurityValidationUtils.getPort(uri, "https");

        assertEquals(443, port, "Should return default HTTPS port");
    }

    @Test
    void testGetPort_DefaultHttpPort() throws Exception {
        java.net.URI uri = new java.net.URI("http://example.com/path");

        int port = SecurityValidationUtils.getPort(uri, "http");

        assertEquals(80, port, "Should return default HTTP port");
    }

    @Test
    void testGetPort_NoSchemeUseFallback() throws Exception {
        java.net.URI uri = new java.net.URI("//example.com/path");

        int port = SecurityValidationUtils.getPort(uri, "https");

        assertEquals(443, port, "Should use fallback scheme for default port");
    }

    // ========== logSecurityEvent Tests ==========

    @Test
    void testLogSecurityEvent_LogsWarning(AemContext context) {
        TestLoggerFactory.clear();
        TestLogger testLogger = TestLoggerFactory.getTestLogger(SecurityValidationUtils.class);
        MockSlingHttpServletRequest request = context.request();
        request.setPathInfo("/test-uri");
        request.setQueryString("param=value");
        request.setHeader("Referer", "http://example.com");

        SecurityValidationUtils.logSecurityEvent("BLOCKED", "Test block", request, testLogger);

        assertEquals(1, testLogger.getLoggingEvents().size(), "Should log security event");
        assertTrue(
                testLogger.getLoggingEvents().get(0).getMessage().contains("BLOCKED"),
                "Log should contain action");
        assertTrue(
                testLogger.getLoggingEvents().get(0).getMessage().contains("Test block"),
                "Log should contain reason");
    }
}
