package ca.yukon.aem.core.filters;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.engine.EngineConstants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Makes ygDirectory lookups available only to signed-in users (YUKONAEMF-88 privacy approval:
 * "Search should be available only on forms where the user is signed in with MyYukon").
 *
 * <p>Forms call form data model operations through {@code <form>.af.dermis}. This filter refuses
 * those calls with 403 when they target the ygDirectory FDM and the user is anonymous. On publish,
 * MyYukon (SAML) is the only way to sign in, so a signed-in user is a MyYukon user. Calls to other
 * form data models are not affected. The type-ahead treats the 403 as "unavailable".</p>
 */
@Component(service = Filter.class, property = {
        EngineConstants.SLING_FILTER_SCOPE + "=" + EngineConstants.FILTER_SCOPE_REQUEST,
        "sling.filter.extensions=dermis",
        "service.ranking:Integer=4000"
})
public class DirectorySignInFilter implements Filter {

    static final String FDM_PATH = "/content/dam/formsanddocuments-fdm/yukon-forms/ygdirectory";
    static final String FDM_PARAM = "formDataModelId";
    static final String ANONYMOUS = "anonymous";

    private static final Logger LOG = LoggerFactory.getLogger(DirectorySignInFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        // nothing to initialise
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof SlingHttpServletRequest
                && response instanceof HttpServletResponse
                && isDirectoryCall((SlingHttpServletRequest) request)
                && !isSignedIn((SlingHttpServletRequest) request)) {
            LOG.debug("ygDirectory call refused: user not signed in");
            HttpServletResponse http = (HttpServletResponse) response;
            http.setStatus(HttpServletResponse.SC_FORBIDDEN);
            http.setHeader("Cache-Control", "no-store, private");
            http.setContentType("application/json");
            http.setCharacterEncoding(StandardCharsets.UTF_8.name());
            http.getWriter().write("{\"error\":\"signInRequired\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isDirectoryCall(SlingHttpServletRequest request) {
        String fdm = StringUtils.removeEnd(StringUtils.trimToEmpty(request.getParameter(FDM_PARAM)), "/");
        return fdm.equals(FDM_PATH) || fdm.startsWith(FDM_PATH + "/");
    }

    private static boolean isSignedIn(SlingHttpServletRequest request) {
        String userId = request.getResourceResolver().getUserID();
        return StringUtils.isNotBlank(userId) && !ANONYMOUS.equals(userId);
    }

    @Override
    public void destroy() {
        // nothing to release
    }
}
