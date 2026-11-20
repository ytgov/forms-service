package ca.yukon.aem.core.servlets;

import ca.yukon.aem.core.directory.DirectoryService;
import ca.yukon.aem.core.directory.DirectoryUnavailableException;
import ca.yukon.aem.core.directory.EmployeeDetail;
import ca.yukon.aem.core.directory.EmployeeSearchResult;
import ca.yukon.aem.core.directory.EmployeeSummary;
import ca.yukon.aem.core.directory.NameList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockRequestPathInfo;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class DirectoryLookupServletTest {

    private static final String KEY = "test-access-key";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DirectoryService directory = mock(DirectoryService.class);

    @Test
    void searchEmployeesReturnsEmployeesCountAndTotal(AemContext context) throws Exception {
        when(directory.searchEmployees("smi")).thenReturn(new EmployeeSearchResult(Collections.singletonList(
                new EmployeeSummary("Zoé Smith", "Zoé", "Smith", "zoe.smith@yukon.ca", "Principal", "Education")), 7));

        JsonNode body = callOk(context, "searchEmployees", "keyword", "smi");

        assertEquals(1, body.get("count").asInt());
        assertEquals(7, body.get("total").asInt());
        JsonNode first = body.get("employees").get(0);
        assertEquals("Zoé Smith", first.get("full_name").asText());
        assertEquals("Zoé", first.get("first_name").asText());
        assertEquals("Smith", first.get("last_name").asText());
        assertEquals("zoe.smith@yukon.ca", first.get("email").asText());
        assertEquals("Principal", first.get("title").asText());
        assertEquals("Education", first.get("department").asText());
    }

    @Test
    void getEmployeeReturnsAFlatRecordOfStrings(AemContext context) throws Exception {
        when(directory.getEmployee("nobody@yukon.ca", "", "")).thenReturn(EmployeeDetail.empty());

        JsonNode body = callOk(context, "getEmployee", "email", "nobody@yukon.ca");

        for (String field : Arrays.asList("first_name", "last_name", "email", "title", "department", "division", "branch",
                "unit", "manager_first_name", "manager_last_name", "manager_email")) {
            assertTrue(body.get(field).isTextual(), field);
            assertEquals("", body.get(field).asText(), field);
        }
    }

    @Test
    void getEmployeePassesTheSelectedTitleAndDepartment(AemContext context) throws Exception {
        when(directory.getEmployee("grace.hopper@yukon.ca", "Acting ADM", "Community Services"))
                .thenReturn(EmployeeDetail.builder().email("grace.hopper@yukon.ca").title("Acting ADM").build());

        JsonNode body = callOk(context, "getEmployee", "email", "grace.hopper@yukon.ca",
                "title", "Acting ADM", "department", "Community Services");

        assertEquals("Acting ADM", body.get("title").asText());
    }

    @Test
    void listOperationsPassTheirParents(AemContext context) throws Exception {
        when(directory.getDepartments()).thenReturn(new NameList(Arrays.asList("A", "B")));
        when(directory.getDivisions("A")).thenReturn(new NameList(Collections.singletonList("A1")));
        when(directory.getBranches("A", "A1")).thenReturn(NameList.empty());
        when(directory.getUnits("A", "A1", "")).thenReturn(NameList.empty());

        assertEquals(2, callOk(context, "getDepartments").get("count").asInt());
        assertEquals("A1", callOk(context, "getDivisions", "department", "A").get("names").get(0).asText());
        JsonNode branches = callOk(context, "getBranches", "department", "A", "division", "A1");
        assertEquals(0, branches.get("count").asInt());
        assertTrue(branches.get("names").isArray());
        assertEquals(0, callOk(context, "getUnits", "department", "A", "division", "A1").get("count").asInt());
    }

    @Test
    void anUnloadedDirectoryReturns503(AemContext context) throws Exception {
        when(directory.searchEmployees(any())).thenThrow(new DirectoryUnavailableException("not loaded"));

        MockSlingHttpServletResponse response = call(context, KEY, KEY, "searchEmployees", "keyword", "smi");

        assertEquals(503, response.getStatus());
        assertEquals("unavailable", MAPPER.readTree(response.getOutputAsString()).get("error").asText());
    }

    @Test
    void refusesAWrongOrMissingKey(AemContext context) throws Exception {
        assertEquals(401, call(context, KEY, "wrong", "getDepartments").getStatus());
        assertEquals(401, call(context, KEY, null, "getDepartments").getStatus());
        verify(directory, never()).getDepartments();
    }

    @Test
    void refusesEverythingWhenNoKeyIsConfigured(AemContext context) throws Exception {
        MockSlingHttpServletResponse response = call(context, "", "", "getDepartments");

        assertEquals(503, response.getStatus());
        verify(directory, never()).getDepartments();
    }

    @Test
    void responsesAreNotCached(AemContext context) throws Exception {
        when(directory.getDepartments()).thenReturn(NameList.empty());

        MockSlingHttpServletResponse response = call(context, KEY, KEY, "getDepartments");

        assertEquals("no-store, private", response.getHeader("Cache-Control"));
        assertEquals("application/json;charset=UTF-8", response.getContentType());
    }

    @Test
    void anUnknownOperationReturns404(AemContext context) throws Exception {
        assertEquals(404, call(context, KEY, KEY, "dropTables").getStatus());
    }

    private JsonNode callOk(AemContext context, String operation, String... params) throws IOException {
        MockSlingHttpServletResponse response = call(context, KEY, KEY, operation, params);
        assertEquals(200, response.getStatus(), response.getOutputAsString());
        return MAPPER.readTree(response.getOutputAsString());
    }

    private MockSlingHttpServletResponse call(AemContext context, String configuredKey, String sentKey,
                                              String operation, String... params) throws IOException {
        if (context.getService(DirectoryService.class) == null) {
            context.registerService(DirectoryService.class, directory);
        }
        DirectoryLookupServlet servlet = context.registerInjectActivateService(new DirectoryLookupServlet(),
                "access.key", configuredKey);
        // A new request and response per call, so outputs don't accumulate.
        MockSlingHttpServletRequest request =
                new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
        ((MockRequestPathInfo) request.getRequestPathInfo())
                .setResourcePath(DirectoryLookupServlet.BASE_PATH + "/" + operation);
        if (sentKey != null) {
            request.setHeader(DirectoryLookupServlet.KEY_HEADER, sentKey);
        }
        Map<String, Object> parameters = new HashMap<>();
        for (int i = 0; i < params.length; i += 2) {
            parameters.put(params[i], params[i + 1]);
        }
        request.setParameterMap(parameters);
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();
        servlet.doGet(request, response);
        return response;
    }
}
