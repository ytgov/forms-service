package ca.yukon.aem.core.servlets;

import ca.yukon.aem.core.directory.DirectoryApiClient;
import ca.yukon.aem.core.directory.DirectoryApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TEMPORARY connectivity check for the directory API spike (YUKONAEMF-88). Remove it once the
 * sync job is in place.
 *
 * <p>{@code GET /bin/yukon-forms/directory-api-test} calls each directory endpoint from this AEM
 * instance and reports the status code, record count, size and time. It never returns directory
 * records. Add {@code ?endpoint=departments} (or {@code divisions}, {@code employees}) to test one
 * endpoint. Only administrators can call it.</p>
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.paths=" + DirectoryApiTestServlet.PATH,
        "sling.servlet.methods=GET"
})
@ServiceDescription("Temporary directory API connectivity check (YUKONAEMF-88)")
public class DirectoryApiTestServlet extends SlingSafeMethodsServlet {

    static final String PATH = "/bin/yukon-forms/directory-api-test";
    static final List<String> ENDPOINTS = Collections.unmodifiableList(Arrays.asList(
            DirectoryApiClient.DEPARTMENTS, DirectoryApiClient.DIVISIONS, DirectoryApiClient.EMPLOYEES));

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DirectoryApiTestServlet.class);
    private static final String ADMIN_GROUP = "administrators";
    private static final int MAX_ERROR_LENGTH = 300;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectWriter PRETTY = MAPPER.writerWithDefaultPrettyPrinter();

    @Reference
    private transient DirectoryApiClient directoryApiClient;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        if (!isAdministrator(request.getResourceResolver())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String endpoint = request.getParameter("endpoint");
        List<String> endpoints;
        if (StringUtils.isBlank(endpoint)) {
            endpoints = ENDPOINTS;
        } else if (ENDPOINTS.contains(endpoint)) {
            endpoints = Collections.singletonList(endpoint);
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "endpoint must be one of " + ENDPOINTS);
            return;
        }

        Map<String, Object> report = buildReport(endpoints);
        LOG.info("Directory API connectivity check: {}", MAPPER.writeValueAsString(report));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(PRETTY.writeValueAsString(report));
    }

    Map<String, Object> buildReport(List<String> endpoints) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("baseUrl", directoryApiClient.getBaseUrl());
        report.put("apiKeyConfigured", directoryApiClient.isConfigured());
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("proxy", proxySettings());
        List<Map<String, Object>> results = new ArrayList<>();
        if (directoryApiClient.isConfigured()) {
            for (String endpoint : endpoints) {
                results.add(check(endpoint));
            }
        }
        report.put("results", results);
        return report;
    }

    private Map<String, Object> check(String endpoint) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("endpoint", endpoint);
        long start = System.currentTimeMillis();
        try {
            DirectoryApiResponse apiResponse = directoryApiClient.get(endpoint);
            result.put("status", apiResponse.getStatusCode());
            result.put("bytes", apiResponse.getBody().length());
            result.put("elapsedMs", apiResponse.getElapsedMillis());
            if (apiResponse.isSuccess()) {
                result.put("count", readCount(apiResponse.getBody()));
            } else {
                // Gateway errors such as 401 and 429 carry a short message and no directory data.
                result.put("message", StringUtils.abbreviate(apiResponse.getBody(), MAX_ERROR_LENGTH));
            }
        } catch (IOException | RuntimeException e) {
            result.put("error", e.getClass().getName() + ": " + e.getMessage());
            result.put("elapsedMs", System.currentTimeMillis() - start);
            LOG.warn("Directory API connectivity check failed for {}", endpoint, e);
        }
        return result;
    }

    private static Object readCount(String body) {
        try {
            JsonNode count = MAPPER.readTree(body).get("count");
            return count == null || !count.canConvertToInt() ? null : count.asInt();
        } catch (IOException e) {
            return "unreadable: " + e.getMessage();
        }
    }

    private static Map<String, String> proxySettings() {
        Map<String, String> proxy = new LinkedHashMap<>();
        for (String scheme : new String[] { "http", "https" }) {
            String host = System.getProperty(scheme + ".proxyHost");
            if (host != null) {
                proxy.put(scheme, host + ":" + System.getProperty(scheme + ".proxyPort", ""));
            }
        }
        return proxy;
    }

    static boolean isAdministrator(ResourceResolver resolver) {
        UserManager userManager = resolver.adaptTo(UserManager.class);
        if (userManager == null || resolver.getUserID() == null) {
            return false;
        }
        try {
            Authorizable authorizable = userManager.getAuthorizable(resolver.getUserID());
            if (!(authorizable instanceof User)) {
                return false;
            }
            User user = (User) authorizable;
            if (user.isAdmin()) {
                return true;
            }
            Iterator<Group> groups = user.memberOf();
            while (groups.hasNext()) {
                if (ADMIN_GROUP.equals(groups.next().getID())) {
                    return true;
                }
            }
        } catch (RepositoryException e) {
            LOG.warn("Could not read group membership for {}", resolver.getUserID(), e);
        }
        return false;
    }
}
