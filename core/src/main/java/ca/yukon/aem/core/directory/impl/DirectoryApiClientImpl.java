package ca.yukon.aem.core.directory.impl;

import ca.yukon.aem.core.config.DirectoryApiConfig;
import ca.yukon.aem.core.directory.DirectoryApiClient;
import ca.yukon.aem.core.directory.DirectoryApiResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.http.util.EntityUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Directory API client built on AEM's {@link HttpClientBuilderFactory}. On AEM as a Cloud Service,
 * clients from this factory pick up the platform's egress proxy settings, so the same code works
 * with or without Advanced Networking.
 */
@Component(service = DirectoryApiClient.class)
@Designate(ocd = DirectoryApiConfig.class)
public class DirectoryApiClientImpl implements DirectoryApiClient {

    static final String SUBSCRIPTION_KEY_HEADER = "Ocp-Apim-Subscription-Key";

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryApiClientImpl.class);

    @Reference
    private HttpClientBuilderFactory httpClientBuilderFactory;

    private volatile CloseableHttpClient httpClient;
    private volatile String baseUrl;
    private volatile String apiKey;

    @Activate
    @Modified
    protected void activate(DirectoryApiConfig config) {
        closeClient();
        baseUrl = StringUtils.removeEnd(StringUtils.trimToEmpty(config.base_url()), "/");
        apiKey = StringUtils.trimToEmpty(config.api_key());
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(config.connect_timeout_ms())
                .setConnectionRequestTimeout(config.connect_timeout_ms())
                .setSocketTimeout(config.read_timeout_ms())
                .build();
        httpClient = httpClientBuilderFactory.newBuilder()
                .setDefaultRequestConfig(requestConfig)
                .build();
        if (!isConfigured()) {
            LOG.warn("Directory API is not configured: set the base URL and the DIRECTORY_API_KEY secret");
        }
    }

    @Deactivate
    protected void deactivate() {
        closeClient();
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.isNotBlank(baseUrl) && StringUtils.isNotBlank(apiKey);
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public DirectoryApiResponse get(String endpoint) throws IOException {
        if (!isConfigured()) {
            throw new IllegalStateException("Directory API base URL or API key is not configured");
        }
        HttpGet request = new HttpGet(baseUrl + "/" + StringUtils.removeStart(endpoint, "/"));
        request.setHeader(SUBSCRIPTION_KEY_HEADER, apiKey);
        request.setHeader("Accept", "application/json");
        long start = System.currentTimeMillis();
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
            long elapsed = System.currentTimeMillis() - start;
            int status = response.getStatusLine().getStatusCode();
            LOG.debug("Directory API GET {} -> {} ({} chars, {} ms)", endpoint, status, body.length(), elapsed);
            return new DirectoryApiResponse(status, body, elapsed);
        }
    }

    private void closeClient() {
        CloseableHttpClient client = httpClient;
        httpClient = null;
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                LOG.debug("Could not close the directory API HTTP client", e);
            }
        }
    }
}
