package ca.yukon.aem.core.forms.services.impl;

import ca.yukon.aem.core.forms.model.Signer;
import ca.yukon.aem.core.forms.services.AdobeSignService;
import com.adobe.granite.crypto.CryptoException;
import com.adobe.granite.crypto.CryptoSupport;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link AdobeSignService} using Adobe Sign REST API v6.
 *
 * <p>Credentials are read from an AEM Adobe Sign Cloud Service config
 * node (path supplied via the workflow step's {@code cloudConfigPath}
 * argument). The config stores OAuth credentials, not an access token
 * directly:</p>
 *
 * <ul>
 *   <li>{@code client_id}</li>
 *   <li>{@code client_secret} (encrypted)</li>
 *   <li>{@code refresh_token} (encrypted)</li>
 *   <li>{@code access_token_uri} (the {@code /oauth/v2/refresh} endpoint)</li>
 *   <li>{@code api_access_point} (the regional REST API base URL)</li>
 * </ul>
 *
 * <p>This class exchanges the refresh token for an access token on demand
 * and caches it in memory (keyed by config path) until shortly before
 * expiry. Concurrent refreshes for the same config are serialized.</p>
 *
 * <p>API documentation:
 * https://secure.na1.adobesign.com/public/docs/restapi/v6</p>
 */
@Component(service = AdobeSignService.class, immediate = true)
public class AdobeSignServiceImpl implements AdobeSignService {

    private static final Logger LOG = LoggerFactory.getLogger(AdobeSignServiceImpl.class);

    private static final String API_TRANSIENT_DOCS = "/api/rest/v6/transientDocuments";
    private static final String API_AGREEMENTS = "/api/rest/v6/agreements";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int SOCKET_TIMEOUT_MS = 30_000;

    /** Safety buffer subtracted from the OAuth expires_in to avoid races. */
    private static final long TOKEN_EXPIRY_SKEW_MS = 60_000L;

    // Defaults for signature field placement (PDF points).
    static final int DEFAULT_SIGNATURE_PAGE = 1;
    static final int DEFAULT_SIGNATURE_WIDTH = 180;
    static final int DEFAULT_SIGNATURE_HEIGHT = 36;
    static final int DEFAULT_SIGNATURE_X = 50;
    static final int DEFAULT_SIGNATURE_Y_START = 100;
    static final int DEFAULT_SIGNATURE_Y_STEP = DEFAULT_SIGNATURE_HEIGHT + 30;

    // Property name aliases used across AEM versions on the Adobe Sign
    // Cloud Service config node.
    private static final String[] CLOUD_CLIENT_ID_KEYS    = {"client_id", "clientId"};
    private static final String[] CLOUD_CLIENT_SECRET_KEYS = {"client_secret", "clientSecret"};
    private static final String[] CLOUD_REFRESH_TOKEN_KEYS = {"refresh_token", "refreshToken"};
    private static final String[] CLOUD_TOKEN_URI_KEYS     = {"access_token_uri", "accessTokenUri", "tokenEndpoint"};
    private static final String[] CLOUD_BASEURL_KEYS       = {"api_access_point", "apiAccessPoint", "baseUrl", "baseURL"};
    private static final String[] CLOUD_SENDER_KEYS        = {"senderEmail", "sender_email", "userEmail"};

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    private volatile CryptoSupport cryptoSupport;

    /** Cached access tokens keyed by cloudConfigPath. */
    private final ConcurrentHashMap<String, AccessToken> tokenCache = new ConcurrentHashMap<>();

    @Activate
    protected void activate() {
        LOG.info("AdobeSignService activated. Credentials are resolved per-call from " +
                "an AEM Adobe Sign Cloud Service config (cloudConfigPath workflow arg).");
    }

    @Override
    public String uploadTransientDocument(String fileName, String mimeType,
                                          InputStream fileContent,
                                          ResourceResolver resolver,
                                          String cloudConfigPath) throws IOException {
        ApiContext ctx = buildContext(resolver, cloudConfigPath);
        String boundary = "----YukonFormsBoundary" + UUID.randomUUID();
        URL url = new URL(ctx.baseUrl + API_TRANSIENT_DOCS);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(SOCKET_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + ctx.accessToken);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                String header = "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"File\"; filename=\"" + fileName + "\"\r\n" +
                        "Content-Type: " + mimeType + "\r\n\r\n";
                out.write(header.getBytes(StandardCharsets.UTF_8));

                byte[] buf = new byte[8192];
                int read;
                while ((read = fileContent.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
                out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            String response = readResponse(conn, "uploadTransientDocument", cloudConfigPath);
            return extractJsonStringField(response, "transientDocumentId");
        } finally {
            conn.disconnect();
        }
    }

    @Override
    public String createAgreement(String agreementName, String transientDocId,
                                  List<Signer> signers, String message,
                                  ResourceResolver resolver,
                                  String cloudConfigPath) throws IOException {
        if (signers == null || signers.isEmpty()) {
            throw new IllegalArgumentException("At least one signer is required");
        }
        ApiContext ctx = buildContext(resolver, cloudConfigPath);
        String body = buildAgreementPayload(agreementName, transientDocId, signers, message);
        URL url = new URL(ctx.baseUrl + API_AGREEMENTS);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(SOCKET_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Authorization", "Bearer " + ctx.accessToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }

            String response = readResponse(conn, "createAgreement", cloudConfigPath);
            return extractJsonStringField(response, "id");
        } finally {
            conn.disconnect();
        }
    }

    @Override
    public String getAgreementStatus(String agreementId,
                                     ResourceResolver resolver,
                                     String cloudConfigPath) throws IOException {
        ApiContext ctx = buildContext(resolver, cloudConfigPath);
        URL url = new URL(ctx.baseUrl + API_AGREEMENTS + "/" + agreementId);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(SOCKET_TIMEOUT_MS);
            conn.setRequestProperty("Authorization", "Bearer " + ctx.accessToken);
            conn.setRequestProperty("Accept", "application/json");

            String response = readResponse(conn, "getAgreementStatus", cloudConfigPath);
            return extractJsonStringField(response, "status");
        } finally {
            conn.disconnect();
        }
    }

    // ------------------------------------------------------------------
    // OAuth credentials + token cache
    // ------------------------------------------------------------------

    /**
     * What we need on each API call: the base URL of the API + a valid
     * bearer access token.
     */
    static class ApiContext {
        final String baseUrl;
        final String accessToken;
        ApiContext(String baseUrl, String accessToken) {
            this.baseUrl = baseUrl;
            this.accessToken = accessToken;
        }
    }

    /** OAuth refresh credentials read from the cloud config. */
    static class OAuthConfig {
        final String clientId;
        final String clientSecret;
        final String refreshToken;
        final String tokenUri;
        final String baseUrl;
        OAuthConfig(String clientId, String clientSecret, String refreshToken,
                    String tokenUri, String baseUrl) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.refreshToken = refreshToken;
            this.tokenUri = tokenUri;
            this.baseUrl = baseUrl;
        }
    }

    /** Cached access token entry. */
    static class AccessToken {
        final String token;
        final long expiresAtMillis;
        AccessToken(String token, long expiresAtMillis) {
            this.token = token;
            this.expiresAtMillis = expiresAtMillis;
        }
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }

    /**
     * Resolve OAuth config from the cloud config node, get a valid access
     * token (from cache or by refreshing), and return an {@link ApiContext}.
     */
    private ApiContext buildContext(ResourceResolver resolver, String cloudConfigPath) throws IOException {
        if (cloudConfigPath == null || cloudConfigPath.isEmpty()) {
            throw new IllegalStateException(
                    "cloudConfigPath is required. Set it in the workflow step's PROCESS_ARGS, " +
                            "pointing to /conf/.../settings/cloudconfigs/adobesign/<name>/jcr:content");
        }
        if (resolver == null) {
            throw new IllegalStateException("ResourceResolver is required to read the cloud config");
        }

        OAuthConfig oauth = readOAuthConfig(resolver, cloudConfigPath);
        if (oauth == null) {
            throw new IllegalStateException(
                    "Adobe Sign cloud config not readable at " + cloudConfigPath);
        }

        AccessToken cached = tokenCache.get(cloudConfigPath);
        if (cached != null && !cached.isExpired()) {
            return new ApiContext(oauth.baseUrl, cached.token);
        }

        // Single-flight refresh for this config path.
        synchronized (cacheKeyLock(cloudConfigPath)) {
            cached = tokenCache.get(cloudConfigPath);
            if (cached != null && !cached.isExpired()) {
                return new ApiContext(oauth.baseUrl, cached.token);
            }
            AccessToken fresh = refreshAccessToken(oauth);
            tokenCache.put(cloudConfigPath, fresh);
            return new ApiContext(oauth.baseUrl, fresh.token);
        }
    }

    /** Use an interned String as a per-path lock to avoid global contention. */
    private static Object cacheKeyLock(String path) {
        return ("AdobeSignTokenLock::" + path).intern();
    }

    /**
     * Resolve the resource holding the cloud config properties, accepting
     * the path either with or without a trailing {@code /jcr:content}.
     * The cloud config is a {@code cq:Page} so the actual properties live
     * on its {@code jcr:content} child.
     */
    private Resource resolveCloudConfigResource(ResourceResolver resolver, String path) {
        Resource res = resolver.getResource(path);
        if (res != null) {
            // If the supplied path is the page node, descend to jcr:content.
            Resource content = res.getChild("jcr:content");
            if (content != null) {
                return content;
            }
            // The path may already be jcr:content (no child needed).
            return res;
        }
        // Path didn't resolve directly; try appending jcr:content.
        if (!path.endsWith("/jcr:content")) {
            res = resolver.getResource(path + "/jcr:content");
        }
        return res;
    }

    private OAuthConfig readOAuthConfig(ResourceResolver resolver, String path) {
        Resource res = resolveCloudConfigResource(resolver, path);
        if (res == null) {
            LOG.warn("Adobe Sign cloud config node not found at {} (also tried /jcr:content)", path);
            return null;
        }
        ValueMap props = res.getValueMap();

        String clientId = firstNonEmpty(props, CLOUD_CLIENT_ID_KEYS);
        String clientSecret = firstNonEmpty(props, CLOUD_CLIENT_SECRET_KEYS);
        String refreshToken = firstNonEmpty(props, CLOUD_REFRESH_TOKEN_KEYS);
        String tokenUri = firstNonEmpty(props, CLOUD_TOKEN_URI_KEYS);
        String baseUrl = firstNonEmpty(props, CLOUD_BASEURL_KEYS);

        if (clientId == null || clientSecret == null || refreshToken == null
                || tokenUri == null || baseUrl == null) {
            LOG.warn("Cloud config at {} is missing OAuth properties. " +
                            "Found clientId={}, clientSecret={}, refreshToken={}, tokenUri={}, baseUrl={}",
                    path, clientId != null, clientSecret != null,
                    refreshToken != null, tokenUri != null, baseUrl != null);
            return null;
        }
        clientSecret = maybeDecrypt(clientSecret);
        refreshToken = maybeDecrypt(refreshToken);
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        return new OAuthConfig(clientId, clientSecret, refreshToken, tokenUri, baseUrl);
    }

    /**
     * Exchange the refresh token for an access token via the Adobe Sign
     * OAuth refresh endpoint.
     */
    AccessToken refreshAccessToken(OAuthConfig oauth) throws IOException {
        StringBuilder form = new StringBuilder()
                .append("grant_type=").append(urlEncode("refresh_token"))
                .append("&client_id=").append(urlEncode(oauth.clientId))
                .append("&client_secret=").append(urlEncode(oauth.clientSecret))
                .append("&refresh_token=").append(urlEncode(oauth.refreshToken));

        URL url = new URL(oauth.tokenUri);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(SOCKET_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");

            try (OutputStream out = conn.getOutputStream()) {
                out.write(form.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String body = stream == null ? "" : readAll(stream);
            if (code < 200 || code >= 300) {
                LOG.error("Adobe Sign token refresh failed (HTTP {}): {}", code, body);
                throw new IOException("Adobe Sign token refresh failed: HTTP " + code + " - " + body);
            }

            String accessToken = extractJsonStringField(body, "access_token");
            Long expiresIn = extractJsonLongField(body, "expires_in"); // seconds
            if (accessToken == null) {
                throw new IOException("Adobe Sign refresh response did not contain access_token: " + body);
            }
            long ttlMs = (expiresIn != null ? expiresIn : 3600L) * 1000L;
            long expiresAt = System.currentTimeMillis() + Math.max(0L, ttlMs - TOKEN_EXPIRY_SKEW_MS);
            LOG.debug("Refreshed Adobe Sign access token (ttl={}s)", expiresIn);
            return new AccessToken(accessToken, expiresAt);
        } finally {
            conn.disconnect();
        }
    }

    private String maybeDecrypt(String value) {
        if (value == null || value.isEmpty()) return value;
        CryptoSupport cs = cryptoSupport;
        if (cs == null) {
            // No CryptoSupport at hand; assume plaintext. AEM stores
            // encrypted values wrapped in braces, so log a hint.
            if (value.startsWith("{") && value.endsWith("}")) {
                LOG.warn("Value looks encrypted but CryptoSupport is unavailable. " +
                        "The OAuth call will likely fail.");
            }
            return value;
        }
        try {
            if (cs.isProtected(value)) {
                return cs.unprotect(value);
            }
        } catch (CryptoException e) {
            LOG.warn("Failed to decrypt protected value: {}", e.getMessage());
        }
        return value;
    }

    private static String firstNonEmpty(ValueMap props, String[] keys) {
        for (String k : keys) {
            String v = props.get(k, String.class);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    // ------------------------------------------------------------------
    // Payload builder
    // ------------------------------------------------------------------

    String buildAgreementPayload(String agreementName, String transientDocId,
                                 List<Signer> signers, String message) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append('{');

        sb.append("\"fileInfos\":[{\"transientDocumentId\":\"")
                .append(jsonEscape(transientDocId)).append("\"}],");

        sb.append("\"name\":\"").append(jsonEscape(agreementName)).append("\",");
        sb.append("\"signatureType\":\"ESIGN\",");
        sb.append("\"state\":\"IN_PROCESS\",");

        if (message != null && !message.isEmpty()) {
            sb.append("\"message\":\"").append(jsonEscape(message)).append("\",");
        }

        // Parallel signing: same order (1) for all participantSets.
        sb.append("\"participantSetsInfo\":[");
        for (int i = 0; i < signers.size(); i++) {
            Signer s = signers.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            sb.append("\"memberInfos\":[{\"email\":\"").append(jsonEscape(s.getEmail())).append("\"");
            if (s.getName() != null && !s.getName().isEmpty()) {
                sb.append(",\"name\":\"").append(jsonEscape(s.getName())).append("\"");
            }
            sb.append("}],");
            sb.append("\"order\":1,");
            sb.append("\"role\":\"").append(jsonEscape(s.getRole())).append("\"");
            sb.append('}');
        }
        sb.append("],");

        // One signature field per signer.
        sb.append("\"formFieldLayerTemplates\":[],");
        sb.append("\"formFields\":[");
        for (int i = 0; i < signers.size(); i++) {
            Signer s = signers.get(i);
            if (i > 0) sb.append(',');
            int page = s.getSignaturePage() != null ? s.getSignaturePage() : DEFAULT_SIGNATURE_PAGE;
            int x = s.getSignatureX() != null ? s.getSignatureX() : DEFAULT_SIGNATURE_X;
            int y = s.getSignatureY() != null ? s.getSignatureY()
                    : DEFAULT_SIGNATURE_Y_START + i * DEFAULT_SIGNATURE_Y_STEP;
            sb.append('{');
            sb.append("\"name\":\"sig_").append(i + 1).append("\",");
            sb.append("\"contentType\":\"SIGNATURE\",");
            sb.append("\"assignee\":{\"index\":").append(i + 1).append("},");
            sb.append("\"locations\":[{");
            sb.append("\"pageNumber\":").append(page).append(',');
            sb.append("\"left\":").append(x).append(',');
            sb.append("\"top\":").append(y).append(',');
            sb.append("\"width\":").append(DEFAULT_SIGNATURE_WIDTH).append(',');
            sb.append("\"height\":").append(DEFAULT_SIGNATURE_HEIGHT);
            sb.append("}]}");
        }
        sb.append("]");

        sb.append('}');
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // HTTP / JSON helpers
    // ------------------------------------------------------------------

    /**
     * Reads response body and throws IOException on non-2xx. If the failure
     * is HTTP 401, also invalidates the cached access token so the next
     * call refreshes.
     */
    private String readResponse(HttpURLConnection conn, String op, String cacheKey) throws IOException {
        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = stream == null ? "" : readAll(stream);
        if (code == 401 && cacheKey != null) {
            tokenCache.remove(cacheKey);
        }
        if (code < 200 || code >= 300) {
            LOG.error("AdobeSign {} failed (HTTP {}): {}", op, code, body);
            throw new IOException("AdobeSign " + op + " failed: HTTP " + code + " - " + body);
        }
        LOG.debug("AdobeSign {} ok (HTTP {})", op, code);
        return body;
    }

    private static String readAll(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int read;
            StringBuilder out = new StringBuilder();
            while ((read = reader.read(buf)) != -1) {
                out.append(buf, 0, read);
            }
            return out.toString();
        } finally {
            try { is.close(); } catch (IOException ignored) { }
        }
    }

    static String extractJsonStringField(String json, String fieldName) {
        if (json == null) return null;
        String needle = "\"" + fieldName + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return null;
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) return null;
        StringBuilder out = new StringBuilder();
        for (int p = quote + 1; p < json.length(); p++) {
            char c = json.charAt(p);
            if (c == '\\' && p + 1 < json.length()) {
                char next = json.charAt(p + 1);
                switch (next) {
                    case '"':  out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/':  out.append('/'); break;
                    case 'n':  out.append('\n'); break;
                    case 't':  out.append('\t'); break;
                    case 'r':  out.append('\r'); break;
                    default:   out.append(next); break;
                }
                p++;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return null;
    }

    /**
     * Extract a top-level integer/long field value from a JSON response
     * (e.g. "expires_in":3600).
     */
    static Long extractJsonLongField(String json, String fieldName) {
        if (json == null) return null;
        String needle = "\"" + fieldName + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return null;
        int p = colon + 1;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        int start = p;
        while (p < json.length() && (Character.isDigit(json.charAt(p)) || json.charAt(p) == '-')) p++;
        if (p == start) return null;
        try {
            return Long.parseLong(json.substring(start, p));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
