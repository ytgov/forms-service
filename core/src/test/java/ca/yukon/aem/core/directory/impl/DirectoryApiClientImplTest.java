package ca.yukon.aem.core.directory.impl;

import ca.yukon.aem.core.directory.DirectoryApiClient;
import ca.yukon.aem.core.directory.DirectoryApiResponse;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class DirectoryApiClientImplTest {

    private final CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

    @BeforeEach
    void setUp(AemContext context) {
        HttpClientBuilder builder = mock(HttpClientBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(httpClient);
        HttpClientBuilderFactory factory = mock(HttpClientBuilderFactory.class);
        when(factory.newBuilder()).thenReturn(builder);
        context.registerService(HttpClientBuilderFactory.class, factory);
    }

    @Test
    void sendsTheSubscriptionKeyToTheEndpointUrl(AemContext context) throws IOException {
        CloseableHttpResponse response = response(200, "{\"count\":25,\"departments\":[]}");
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(response);
        DirectoryApiClient client = context.registerInjectActivateService(new DirectoryApiClientImpl(),
                "base.url", "https://api.example.test/directory/", "api.key", "test-key");

        DirectoryApiResponse result = client.get(DirectoryApiClient.DEPARTMENTS);

        ArgumentCaptor<HttpUriRequest> request = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(httpClient).execute(request.capture());
        assertTrue(request.getValue() instanceof HttpGet);
        assertEquals("https://api.example.test/directory/departments", request.getValue().getURI().toString());
        assertEquals("test-key",
                request.getValue().getFirstHeader(DirectoryApiClientImpl.SUBSCRIPTION_KEY_HEADER).getValue());
        assertEquals(200, result.getStatusCode());
        assertTrue(result.isSuccess());
        assertEquals("{\"count\":25,\"departments\":[]}", result.getBody());
        verify(response).close();
    }

    @Test
    void returnsGatewayErrorsInsteadOfThrowing(AemContext context) throws IOException {
        CloseableHttpResponse response = response(401, "{ \"statusCode\": 401 }");
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(response);
        DirectoryApiClient client = context.registerInjectActivateService(new DirectoryApiClientImpl(),
                "base.url", "https://api.example.test/directory", "api.key", "wrong-key");

        DirectoryApiResponse result = client.get(DirectoryApiClient.EMPLOYEES);

        assertEquals(401, result.getStatusCode());
        assertFalse(result.isSuccess());
    }

    @Test
    void refusesToCallWithoutAnApiKey(AemContext context) throws IOException {
        DirectoryApiClient client = context.registerInjectActivateService(new DirectoryApiClientImpl(),
                "base.url", "https://api.example.test/directory", "api.key", "");

        assertFalse(client.isConfigured());
        assertThrows(IllegalStateException.class, () -> client.get(DirectoryApiClient.DEPARTMENTS));
        verify(httpClient, never()).execute(any(HttpUriRequest.class));
    }

    private static CloseableHttpResponse response(int status, String body) {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(status);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(new StringEntity(body, StandardCharsets.UTF_8));
        return response;
    }
}
