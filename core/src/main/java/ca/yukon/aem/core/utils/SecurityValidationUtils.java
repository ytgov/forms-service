package ca.yukon.aem.core.utils;

import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utility class for security validation of HTTP requests. Validates requests against same-origin
 * policy while allowing direct navigation (no referer).
 */
public final class SecurityValidationUtils {

    private SecurityValidationUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Validates a request using simple security rules: - Allow same-origin requests - Allow requests
     * with no Referer header (direct navigation, bookmarks, email links) - Block cross-origin
     * requests
     *
     * @param req    the HTTP request to validate
     * @param logger the logger instance for security event logging
     * @return true if the request is valid, false otherwise
     */
    public static boolean isValidRequest(HttpServletRequest req, Logger logger) {
        String referer = req.getHeader("Referer");

        // No referer = direct navigation, bookmarks, email links - allow
        if (referer == null || referer.isEmpty()) {
            return true;
        }

        // Referer present - must be same-origin
        if (isSameOrigin(req, referer)) {
            return true;
        }

        // Cross-origin request - block and log
        logSecurityEvent("BLOCKED", "Cross-origin request rejected: " + referer, req, logger);
        return false;
    }

    /**
     * Checks if the given URL is from the same origin as the request. Compares scheme, host, and
     * port.
     *
     * @param req the HTTP request
     * @param url the URL to check
     * @return true if the URL is from the same origin, false otherwise
     */
    public static boolean isSameOrigin(HttpServletRequest req, String url) {
        try {
            URI uri = new URI(url);
            String reqScheme = req.getScheme();
            String reqHost = req.getServerName();
            int reqPort = req.getServerPort();

            // Compare scheme, host, and port
            return reqScheme.equalsIgnoreCase(uri.getScheme())
                    && reqHost.equalsIgnoreCase(uri.getHost())
                    && reqPort == getPort(uri, reqScheme);
        } catch (URISyntaxException e) {
            // Invalid URI - not same origin
            return false;
        }
    }

    /**
     * Gets the port from URI, using default ports (443 for HTTPS, 80 for HTTP) if not specified.
     *
     * @param uri            the URI
     * @param fallbackScheme the scheme to use for default port if URI scheme is null
     * @return the port number
     */
    public static int getPort(URI uri, String fallbackScheme) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        // Use default ports
        String scheme = uri.getScheme() != null ? uri.getScheme() : fallbackScheme;
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    /**
     * Logs security events as warnings for audit trail.
     *
     * @param action the action taken (e.g., "BLOCKED")
     * @param reason the reason for the action
     * @param req    the HTTP request
     * @param logger the logger instance
     */
    public static void logSecurityEvent(
            String action, String reason, HttpServletRequest req, Logger logger) {
        String uri = req.getRequestURI();
        String queryString = req.getQueryString();
        String referer = req.getHeader("Referer");
        String remoteAddr = req.getRemoteAddr();

        String message =
                String.format(
                        "Security %s: %s | URI: %s | Query: %s | Referer: %s | IP: %s",
                        action, reason, uri, queryString, referer, remoteAddr);

        logger.warn(message);
    }
}
