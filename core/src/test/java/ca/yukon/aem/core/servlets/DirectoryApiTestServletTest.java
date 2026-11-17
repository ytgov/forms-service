package ca.yukon.aem.core.servlets;

import ca.yukon.aem.core.directory.DirectoryApiClient;
import ca.yukon.aem.core.directory.DirectoryApiResponse;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class DirectoryApiTestServletTest {

    private final DirectoryApiClient client = mock(DirectoryApiClient.class);
    private DirectoryApiTestServlet servlet;

    @BeforeEach
    void setUp(AemContext context) {
        when(client.getBaseUrl()).thenReturn("https://api.example.test/directory");
        context.registerService(DirectoryApiClient.class, client);
        servlet = context.registerInjectActivateService(new DirectoryApiTestServlet());
    }

    @Test
    void reportsCountsAndGatewayErrorsWithoutRecords() throws IOException {
        when(client.isConfigured()).thenReturn(true);
        when(client.get(DirectoryApiClient.DEPARTMENTS)).thenReturn(
                new DirectoryApiResponse(200, "{\"count\":2,\"departments\":[\"A\",\"B\"]}", 120));
        when(client.get(DirectoryApiClient.EMPLOYEES)).thenReturn(
                new DirectoryApiResponse(429, "{ \"statusCode\": 429, \"message\": \"Rate limit\" }", 40));

        Map<String, Object> report = servlet.buildReport(
                Arrays.asList(DirectoryApiClient.DEPARTMENTS, DirectoryApiClient.EMPLOYEES));

        assertEquals(true, report.get("apiKeyConfigured"));
        List<Map<String, Object>> results = results(report);
        assertEquals(2, results.size());
        assertEquals(200, results.get(0).get("status"));
        assertEquals(2, results.get(0).get("count"));
        assertFalse(results.get(0).containsKey("message"), "a successful response must not echo its body");
        assertEquals(429, results.get(1).get("status"));
        assertTrue(((String) results.get(1).get("message")).contains("Rate limit"));
    }

    @Test
    void reportsConnectionFailures() throws IOException {
        when(client.isConfigured()).thenReturn(true);
        when(client.get(DirectoryApiClient.DIVISIONS)).thenThrow(new SocketTimeoutException("connect timed out"));

        Map<String, Object> report = servlet.buildReport(Collections.singletonList(DirectoryApiClient.DIVISIONS));

        Map<String, Object> result = results(report).get(0);
        assertEquals("java.net.SocketTimeoutException: connect timed out", result.get("error"));
    }

    @Test
    void skipsTheCallsWhenTheKeyIsMissing() throws IOException {
        when(client.isConfigured()).thenReturn(false);

        Map<String, Object> report = servlet.buildReport(DirectoryApiTestServlet.ENDPOINTS);

        assertEquals(false, report.get("apiKeyConfigured"));
        assertTrue(results(report).isEmpty());
        verify(client, never()).get(anyString());
    }

    @Test
    void forbidsUsersWhoAreNotAdministrators(AemContext context) throws IOException {
        servlet.doGet(context.request(), context.response());

        assertEquals(HttpServletResponse.SC_FORBIDDEN, context.response().getStatus());
        verify(client, never()).get(anyString());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> results(Map<String, Object> report) {
        return (List<Map<String, Object>>) report.get("results");
    }
}
