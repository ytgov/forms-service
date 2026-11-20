package ca.yukon.aem.core.servlets;

import ca.yukon.aem.core.config.DirectoryLookupServletConfig;
import ca.yukon.aem.core.directory.DirectoryService;
import ca.yukon.aem.core.directory.DirectoryUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Map;

/**
 * REST endpoint behind the {@code ygDirectory} form data model (YUKONAEMF-88). One GET path per
 * FDM operation, under {@value #BASE_PATH}:
 *
 * <ul>
 *   <li>{@code searchEmployees?keyword=}</li>
 *   <li>{@code getEmployee?email=&title=&department=} (title and department optional)</li>
 *   <li>{@code getDepartments}</li>
 *   <li>{@code getDivisions?department=}</li>
 *   <li>{@code getBranches?department=&division=}</li>
 *   <li>{@code getUnits?department=&division=&branch=}</li>
 * </ul>
 *
 * <p>The FDM calls it server to server, so the path is open to anonymous requests on author too
 * ({@code sling.auth.requirements}); every request must instead carry the shared access key in
 * {@value #KEY_HEADER}. No match returns 200 with an empty result; a directory that never loaded
 * returns 503, which the type-ahead reports as "unavailable". Logs name the operation only.</p>
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.paths=" + DirectoryLookupServlet.BASE_PATH + "/" + DirectoryLookupServlet.SEARCH_EMPLOYEES,
        "sling.servlet.paths=" + DirectoryLookupServlet.BASE_PATH + "/" + DirectoryLookupServlet.GET_EMPLOYEE,
        "sling.servlet.paths=" + DirectoryLookupServlet.BASE_PATH + "/" + DirectoryLookupServlet.GET_DEPARTMENTS,
        "sling.servlet.paths=" + DirectoryLookupServlet.BASE_PATH + "/" + DirectoryLookupServlet.GET_DIVISIONS,
        "sling.servlet.paths=" + DirectoryLookupServlet.BASE_PATH + "/" + DirectoryLookupServlet.GET_BRANCHES,
        "sling.servlet.paths=" + DirectoryLookupServlet.BASE_PATH + "/" + DirectoryLookupServlet.GET_UNITS,
        "sling.servlet.methods=GET",
        "sling.auth.requirements=-" + DirectoryLookupServlet.BASE_PATH
})
@Designate(ocd = DirectoryLookupServletConfig.class)
public class DirectoryLookupServlet extends SlingSafeMethodsServlet {

    static final String BASE_PATH = "/bin/yukon-forms/ygdirectory";
    static final String KEY_HEADER = "X-YG-Directory-Key";

    static final String SEARCH_EMPLOYEES = "searchEmployees";
    static final String GET_EMPLOYEE = "getEmployee";
    static final String GET_DEPARTMENTS = "getDepartments";
    static final String GET_DIVISIONS = "getDivisions";
    static final String GET_BRANCHES = "getBranches";
    static final String GET_UNITS = "getUnits";

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(DirectoryLookupServlet.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Reference
    private transient DirectoryService directoryService;

    private transient volatile byte[] accessKey = new byte[0];

    @Activate
    @Modified
    protected void activate(DirectoryLookupServletConfig config) {
        accessKey = StringUtils.trimToEmpty(config.access_key()).getBytes(StandardCharsets.UTF_8);
        if (accessKey.length == 0) {
            LOG.warn("ygDirectory endpoint has no access key configured (YG_DIRECTORY_FDM_KEY); refusing all requests");
        }
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store, private");
        String operation = StringUtils.substringAfterLast(request.getRequestPathInfo().getResourcePath(), "/");

        byte[] key = accessKey;
        if (key.length == 0) {
            LOG.warn("ygDirectory {} refused: no access key configured", operation);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "notConfigured");
            return;
        }
        String supplied = request.getHeader(KEY_HEADER);
        if (supplied == null || !MessageDigest.isEqual(key, supplied.getBytes(StandardCharsets.UTF_8))) {
            LOG.warn("ygDirectory {} refused: missing or wrong access key", operation);
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
            return;
        }

        Object result;
        try {
            result = execute(operation, request);
        } catch (DirectoryUnavailableException e) {
            LOG.warn("ygDirectory {} failed: directory unavailable", operation);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "unavailable");
            return;
        } catch (RuntimeException e) {
            LOG.error("ygDirectory {} failed: {}", operation, e.getClass().getName());
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error");
            return;
        }
        if (result == null) {
            writeError(response, HttpServletResponse.SC_NOT_FOUND, "unknownOperation");
            return;
        }
        writeJson(response, HttpServletResponse.SC_OK, result);
    }

    private Object execute(String operation, SlingHttpServletRequest request) throws DirectoryUnavailableException {
        switch (operation) {
            case SEARCH_EMPLOYEES:
                return directoryService.searchEmployees(param(request, "keyword"));
            case GET_EMPLOYEE:
                return directoryService.getEmployee(param(request, "email"), param(request, "title"),
                        param(request, "department"));
            case GET_DEPARTMENTS:
                return directoryService.getDepartments();
            case GET_DIVISIONS:
                return directoryService.getDivisions(param(request, "department"));
            case GET_BRANCHES:
                return directoryService.getBranches(param(request, "department"), param(request, "division"));
            case GET_UNITS:
                return directoryService.getUnits(param(request, "department"), param(request, "division"),
                        param(request, "branch"));
            default:
                return null;
        }
    }

    private static String param(SlingHttpServletRequest request, String name) {
        return StringUtils.defaultString(request.getParameter(name));
    }

    private static void writeError(SlingHttpServletResponse response, int status, String error) throws IOException {
        Map<String, String> body = Collections.singletonMap("error", error);
        writeJson(response, status, body);
    }

    private static void writeJson(SlingHttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MAPPER.writeValue(response.getWriter(), body);
    }
}
