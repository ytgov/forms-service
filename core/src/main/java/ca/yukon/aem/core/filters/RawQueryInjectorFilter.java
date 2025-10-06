package ca.yukon.aem.core.filters;

import ca.yukon.aem.core.utils.SecurityValidationUtils;
import org.apache.sling.engine.EngineConstants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Servlet filter that injects the raw query string as a request parameter for Adaptive Forms.
 * Applies security validation to block cross-origin requests while allowing same-origin and direct
 * navigation.
 */
@Component(
        service = Filter.class,
        property = {
                EngineConstants.SLING_FILTER_SCOPE + "=" + EngineConstants.FILTER_SCOPE_REQUEST,
                "service.ranking:Integer=5000"
        })
public class RawQueryInjectorFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RawQueryInjectorFilter.class);

    // Limit to AF pages/requests (adjust as needed)
    private static boolean isAdaptiveFormsRequest(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri != null && uri.contains("/content/forms/af/yukon-forms/");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest req = (HttpServletRequest) request;

        if (!isAdaptiveFormsRequest(req)) {
            chain.doFilter(request, response);
            return;
        }

        // If we already have our param, don't wrap again
        if (req.getParameter("requestQueryString") != null) {
            chain.doFilter(request, response);
            return;
        }

        final String qs = req.getQueryString(); // raw, ordered, percent-encoded, '+'-for-space
        if (qs == null || qs.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        // Security validation: block cross-origin, allow same-origin + direct navigation
        if (!SecurityValidationUtils.isValidRequest(req, log)) {
            // Reject the request - don't inject query string
            chain.doFilter(request, response);
            return;
        }

        // Wrap request to merge in the raw query string as a normal parameter
        HttpServletRequest wrapped =
                new HttpServletRequestWrapper(req) {
                    private Map<String, String[]> merged;

                    private Map<String, String[]> mergedParams() {
                        if (merged == null) {
                            Map<String, String[]> m = new LinkedHashMap<>(super.getParameterMap());
                            // Inject raw query as a normal parameter so AEM Forms copies it into
                            // DataOptions.extras
                            m.putIfAbsent("requestQueryString", new String[]{qs});
                            merged = Collections.unmodifiableMap(m);
                        }
                        return merged;
                    }

                    @Override
                    public String getParameter(String name) {
                        String[] v = mergedParams().get(name);
                        return (v != null && v.length > 0) ? v[0] : null;
                    }

                    @Override
                    public Map<String, String[]> getParameterMap() {
                        return mergedParams();
                    }

                    @Override
                    public Enumeration<String> getParameterNames() {
                        return Collections.enumeration(mergedParams().keySet());
                    }

                    @Override
                    public String[] getParameterValues(String name) {
                        return mergedParams().get(name);
                    }
        };

        chain.doFilter(wrapped, response);
    }
}
