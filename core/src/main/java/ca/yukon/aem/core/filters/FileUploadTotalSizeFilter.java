package ca.yukon.aem.core.filters;

import ca.yukon.aem.core.models.CustomGuideFileUpload;
import com.adobe.aemds.guide.common.GuideError;
import com.adobe.aemds.guide.common.GuideValidationResult;
import com.adobe.aemds.guide.utils.GuideSubmitErrorCause;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.engine.EngineConstants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side check of the File Attachment (Custom) component's Maximum total size on submit.
 *
 * <p>The widget already refuses files that would push a field's total over the limit, but that check runs in
 * the browser and can be bypassed. This filter runs before AEM's submit servlet ({@code .af.submit},
 * {@code .af.signSubmit}, {@code .af.agreement} on a guide container). For each field of this component with a
 * Maximum total size, it adds up the sizes of the field's files in the request and, when a total is over the
 * limit, answers {@code 400} with a server-side validation error on that field, in the format AEM's submit
 * servlet uses. The submit action then doesn't run.
 *
 * <p>The request's {@code fileAttachmentMap} parameter maps each file field's SOM expression to its files, one
 * {@code fieldName/fileName} per line. The files themselves are multipart file parts of the request; a field's
 * files are the parts named {@code fieldName/fileName}, or named {@code fieldName} with that file name. Files
 * that aren't in the request (e.g. not re-sent) aren't counted.
 */
@Component(
        service = Filter.class,
        property = {
                EngineConstants.SLING_FILTER_SCOPE + "=" + EngineConstants.FILTER_SCOPE_REQUEST,
                "sling.filter.methods=POST",
                "service.ranking:Integer=4000"
        })
public class FileUploadTotalSizeFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(FileUploadTotalSizeFilter.class);

    static final String GUIDE_CONTAINER_TYPE = "fd/af/components/guideContainer";
    static final String FILE_ATTACHMENT_MAP = "fileAttachmentMap";
    static final Set<String> SUBMIT_SELECTORS = Set.of("af.submit", "af.signSubmit", "af.agreement");
    static final long BYTES_PER_MB = 1024L * 1024L;

    static final String MESSAGE =
            "The files attached here total {total}, which is more than the {maxTotal} allowed. "
                    + "Remove a file or replace it with a smaller one, then submit the form again.";
    static final String MESSAGE_FR =
            "Les fichiers joints ici totalisent {total}, ce qui dépasse la limite de {maxTotal}. "
                    + "Retirez un fichier ou remplacez-le par un fichier plus petit, puis soumettez de nouveau le formulaire.";

    @Override
    public void init(FilterConfig filterConfig) {
        // nothing to initialise
    }

    @Override
    public void destroy() {
        // nothing to clean up
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof SlingHttpServletRequest) || !(response instanceof SlingHttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }
        SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) request;
        if (!isSubmit(slingRequest)) {
            chain.doFilter(request, response);
            return;
        }
        List<GuideError> errors;
        try {
            errors = check(slingRequest);
        } catch (RuntimeException e) {
            // A bug here must not block every submission; the browser check still applies.
            log.error("Couldn't check the file upload totals, letting the submission through", e);
            errors = Collections.emptyList();
        }
        if (errors.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        reject((SlingHttpServletResponse) response, errors);
    }

    static boolean isSubmit(SlingHttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return false;
        }
        String selectors = request.getRequestPathInfo().getSelectorString();
        Resource resource = request.getResource();
        return selectors != null && SUBMIT_SELECTORS.contains(selectors)
                && resource != null && resource.isResourceType(GUIDE_CONTAINER_TYPE);
    }

    /** One error per field over its Maximum total size; empty when all fields are within their limits. */
    static List<GuideError> check(SlingHttpServletRequest request) {
        Map<String, List<String>> attachments = parseFileAttachmentMap(request.getParameter(FILE_ATTACHMENT_MAP));
        if (attachments.isEmpty()) {
            return Collections.emptyList();
        }
        Resource container = request.getResource();
        boolean french = CustomGuideFileUpload.detectFrench(request, container);
        List<GuideError> errors = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : attachments.entrySet()) {
            String som = entry.getKey();
            Resource field = resolveSom(container, som);
            if (field == null) {
                log.debug("No field found for {}, not checking its total", som);
                continue;
            }
            if (!field.isResourceType(CustomGuideFileUpload.RESOURCE_TYPE)) {
                continue;
            }
            BigDecimal maxMb = CustomGuideFileUpload.maxTotalSizeMb(field.getValueMap().get(CustomGuideFileUpload.MAX_TOTAL_SIZE));
            if (maxMb == null) {
                continue;
            }
            long maxBytes = maxMb.multiply(BigDecimal.valueOf(BYTES_PER_MB)).setScale(0, RoundingMode.FLOOR).longValueExact();
            String fieldName = field.getValueMap().get("name", String.class);
            long total = totalBytes(request, fieldName, entry.getValue());
            if (total > maxBytes) {
                log.info("Rejected a submission: {} has {} bytes of files, over its maximum of {} bytes", som, total, maxBytes);
                errors.add(new GuideError(som, message(french, total, maxBytes)));
            }
        }
        return errors;
    }

    /**
     * SOM expression -> the field's file entries ({@code fieldName/fileName}). Lines are trimmed; empty ones are
     * dropped. Malformed JSON gives an empty map: AEM's own submit handling deals with it.
     */
    static Map<String, List<String>> parseFileAttachmentMap(String json) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return result;
        }
        JsonObject object;
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            object = reader.readObject();
        } catch (RuntimeException e) {
            log.debug("fileAttachmentMap isn't a JSON object, not checking totals");
            return result;
        }
        for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
            if (!(entry.getValue() instanceof JsonString)) {
                continue;
            }
            List<String> files = new ArrayList<>();
            for (String line : ((JsonString) entry.getValue()).getString().split("\\r?\\n")) {
                if (!line.trim().isEmpty()) {
                    files.add(line.trim());
                }
            }
            if (!files.isEmpty()) {
                result.put(entry.getKey(), files);
            }
        }
        return result;
    }

    /**
     * Sum of the sizes of the field's files in the request: parts named {@code fieldName/fileName}, or named
     * {@code fieldName} whose file name is one of the field's files. Each part is counted once.
     */
    static long totalBytes(SlingHttpServletRequest request, String fieldName, List<String> entries) {
        Set<String> fileNames = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (String entry : entries) {
            keys.add(entry);
            int slash = entry.lastIndexOf('/');
            fileNames.add(slash >= 0 ? entry.substring(slash + 1) : entry);
        }
        if (fieldName != null) {
            keys.add(fieldName);
        }
        long total = 0;
        Map<String, RequestParameter[]> parameters = request.getRequestParameterMap();
        for (String key : keys) {
            RequestParameter[] parts = parameters.get(key);
            if (parts == null) {
                continue;
            }
            for (RequestParameter part : parts) {
                if (part.isFormField()) {
                    continue;
                }
                boolean namedEntry = !key.equals(fieldName);
                if (namedEntry || (part.getFileName() != null && fileNames.contains(part.getFileName()))) {
                    total += Math.max(0, part.getSize());
                }
            }
        }
        return total;
    }

    /**
     * The field for a SOM expression such as {@code guide[0].guide1[0].guideRootPanel[0].panel[0].upload[0]}:
     * after {@code guide[0]} and the container's own name, each segment is the {@code name} of a child item.
     * Items are found through unnamed nodes (e.g. {@code items}); a fragment's items are read from the fragment.
     */
    static Resource resolveSom(Resource container, String som) {
        String[] segments = som.split("\\.");
        if (segments.length < 3) {
            return null;
        }
        Resource current = container;
        for (int i = 2; i < segments.length && current != null; i++) {
            String name = segments[i].replaceAll("\\[\\d+]$", "");
            current = findChildItem(current, name, new HashSet<>());
        }
        return current;
    }

    private static Resource findChildItem(Resource parent, String name, Set<String> visited) {
        if (!visited.add(parent.getPath())) {
            return null;
        }
        for (Resource child : children(parent)) {
            if (name.equals(child.getValueMap().get("name", String.class))) {
                return child;
            }
        }
        for (Resource child : children(parent)) {
            if (child.getValueMap().get("name", String.class) == null) {
                Resource found = findChildItem(child, name, visited);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** A node's children, or for a fragment panel the items of the fragment's root panel. */
    private static Iterable<Resource> children(Resource resource) {
        String fragRef = resource.getValueMap().get("fragRef", String.class);
        if (fragRef != null && fragRef.startsWith("/content/dam/formsanddocuments/")) {
            String fragmentRootPanel = "/content/forms/af/" + fragRef.substring("/content/dam/formsanddocuments/".length())
                    + "/jcr:content/guideContainer/rootPanel";
            Resource fragment = resource.getResourceResolver().getResource(fragmentRootPanel);
            if (fragment != null) {
                return fragment.getChildren();
            }
        }
        return resource.getChildren();
    }

    static String message(boolean french, long totalBytes, long maxBytes) {
        String template = french ? MESSAGE_FR : MESSAGE;
        return template.replace("{total}", formatSize(totalBytes, french))
                .replace("{maxTotal}", formatSize(maxBytes, french));
    }

    /**
     * Binary MB with one decimal, rounded up, without a trailing ".0" ("20 MB", "20.5 MB"); French uses a
     * comma and "Mo" with a non-breaking space. Same units as the widget.
     */
    static String formatSize(long bytes, boolean french) {
        BigDecimal mb = BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(BYTES_PER_MB), 1, RoundingMode.CEILING)
                .stripTrailingZeros();
        String number = mb.scale() < 0 ? mb.setScale(0).toPlainString() : mb.toPlainString();
        return french ? number.replace('.', ',') + " Mo" : number + " MB";
    }

    private static void reject(SlingHttpServletResponse response, List<GuideError> errors) throws IOException {
        GuideValidationResult result = new GuideValidationResult(errors, GuideSubmitErrorCause.SERVER_SIDE_VALIDATION);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(result.getValidationPayload().toString());
    }
}
