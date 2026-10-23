package ca.yukon.aem.core.filters;

import ca.yukon.aem.core.config.VerificationStatusGateConfig;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.engine.EngineConstants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gates requests for forms under {@code /content/forms/af/yukon-forms} (and their backing content
 * under {@code /content/dam/formsanddocuments/yukon-forms}) based on the
 * {@code required_verification_status} property set on the form's folder (or one of its ancestors,
 * up to and including the yukon-forms root):
 *
 * <ul>
 *   <li>No property declared, or declared as {@code 0}: not gated by this filter.
 *   <li>Property {@code > 0} and the requester is anonymous: redirected to SAML login
 *       ({@code /system/sling/login}), so they get a chance to authenticate.
 *   <li>Property {@code > 0} and the requester's synced {@code profile/verificationStatus} is lower
 *       than required: redirected to the configurable {@code redirect.page}, in the form's language
 *       (see {@link #resolveLanguage}), with the user's level ({@code u_loa}), the folder's required
 *       level ({@code f_low}) and the form's path under yukon-forms ({@code form_name}) as query parameters.
 * </ul>
 *
 * <p>This filter is the sole enforcement point for yukon-forms access - there is no repository
 * ACL backing it up, so it must cover both the public-facing {@code /content/forms/af/...} path and
 * the underlying DAM path (which is directly GET-able, e.g. for {@code jcr:content.json} model
 * fetches) to avoid a bypass.
 *
 * <p>The component requires its configuration, which only ships in {@code config.publish}, so it is
 * inactive on author - otherwise it would also gate authors, e.g. the Forms &amp; Documents folder
 * dialog reading a folder's {@code jcr:content.json}.
 */
@Component(
        service = Filter.class,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {
                EngineConstants.SLING_FILTER_SCOPE + "=" + EngineConstants.FILTER_SCOPE_REQUEST,
        })
@Designate(ocd = VerificationStatusGateConfig.class)
public class VerificationStatusGateFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(VerificationStatusGateFilter.class);

    static final String PROTECTED_PATH_PREFIX = "/content/forms/af/yukon-forms";
    static final String CONTENT_SOURCE_PREFIX = "/content/dam/formsanddocuments/yukon-forms";
    static final String REQUIRED_STATUS_PROPERTY = "required_verification_status";
    static final String USER_STATUS_PROPERTY = "profile/verificationStatus";
    static final String SAML_LOGIN_PATH = "/system/sling/login";
    /**
     * Must be a path the SAML handler's {@code path} config actually covers, so
     * {@code /system/sling/login} can pick the right handler - the gated forms paths are
     * intentionally NOT in that list (the filter alone decides login-vs-public), so this
     * points at the one root the handler is still registered for.
     */
    static final String SAML_AUTH_RESOURCE_PATH = "/content/yukon-forms";
    /** AEM Forms' own parameter for showing an adaptive form in another language. */
    static final String LANGUAGE_PARAMETER = "afAcceptLang";
    static final String LANGUAGE_PLACEHOLDER = "{lang}";
    static final String USER_LEVEL_PARAMETER = "u_loa";
    static final String FOLDER_LEVEL_PARAMETER = "f_low";
    static final String FORM_NAME_PARAMETER = "form_name";

    private String redirectPage;
    private List<String> supportedLanguages;

    @Activate
    protected void activate(VerificationStatusGateConfig config) {
        this.redirectPage = config.redirect_page();
        String[] languages = config.supported_languages();
        this.supportedLanguages = languages != null && languages.length > 0
                ? Arrays.asList(languages)
                : Collections.singletonList("en");
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {

        if (!(request instanceof SlingHttpServletRequest) || !(response instanceof HttpServletResponse)) {
            filterChain.doFilter(request, response);
            return;
        }

        SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!isGateableRequest(slingRequest)) {
            filterChain.doFilter(request, response);
            return;
        }

        ResourceResolver resolver = slingRequest.getResourceResolver();
        String requestPath = slingRequest.getRequestPathInfo().getResourcePath();
        String contentPath = resolveContentPath(requestPath);

        Double requiredStatus = findRequiredVerificationStatus(resolver, contentPath);
        if (requiredStatus == null || requiredStatus <= 0) {
            // Folder does not declare a required status, or is marked public - nothing to gate here.
            filterChain.doFilter(request, response);
            return;
        }

        if (!isLoggedIn(resolver)) {
            log.debug("Redirecting anonymous request to SAML login for {}", requestPath);
            redirectToSamlLogin(slingRequest, httpResponse);
            return;
        }

        double userStatus = getUserVerificationStatus(resolver);
        if (userStatus < requiredStatus) {
            String target = withQueryParameters(
                    redirectPage.replace(LANGUAGE_PLACEHOLDER, resolveLanguage(slingRequest, contentPath)),
                    USER_LEVEL_PARAMETER, formatLevel(userStatus),
                    FOLDER_LEVEL_PARAMETER, formatLevel(requiredStatus),
                    FORM_NAME_PARAMETER, toFormName(contentPath));
            log.debug(
                    "Redirecting user {} to {} - verification status {} is below required {} for {}",
                    resolver.getUserID(), target, userStatus, requiredStatus, requestPath);
            httpResponse.sendRedirect(target);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isGateableRequest(SlingHttpServletRequest request) {
        HttpServletRequest httpRequest = request;
        if (!"GET".equalsIgnoreCase(httpRequest.getMethod())) {
            return false;
        }
        String resourcePath = request.getRequestPathInfo().getResourcePath();
        return resourcePath != null
                && (isAtOrUnder(resourcePath, PROTECTED_PATH_PREFIX) || isAtOrUnder(resourcePath, CONTENT_SOURCE_PREFIX));
    }

    /**
     * Whether {@code path} is {@code root} itself or a descendant of it - a plain prefix match would
     * also accept siblings such as {@code /content/forms/af/yukon-forms-archive}.
     */
    private static boolean isAtOrUnder(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    /**
     * Maps a gateable request's resource path to the DAM node path
     * {@link #findRequiredVerificationStatus} should walk up from - either the request path itself
     * (already under the DAM tree, e.g. a direct {@code jcr:content.json} model fetch, with any
     * trailing {@code /jcr:content} segment stripped back to the asset/folder node) or the
     * public-facing {@code /content/forms/af/...} path translated to its DAM equivalent.
     */
    private static String resolveContentPath(String requestPath) {
        if (isAtOrUnder(requestPath, CONTENT_SOURCE_PREFIX)) {
            if (requestPath.endsWith("/jcr:content")) {
                return requestPath.substring(0, requestPath.length() - "/jcr:content".length());
            }
            return requestPath;
        }
        return CONTENT_SOURCE_PREFIX + requestPath.substring(PROTECTED_PATH_PREFIX.length());
    }

    /**
     * Walks up from {@code contentPath} to {@code CONTENT_SOURCE_PREFIX} (inclusive), returning the
     * {@code required_verification_status} property of the first {@code jcr:content} node found that
     * declares it, or {@code null} if none of them do.
     */
    Double findRequiredVerificationStatus(ResourceResolver resolver, String contentPath) {
        String path = contentPath;
        while (path != null && isAtOrUnder(path, CONTENT_SOURCE_PREFIX)) {
            Resource contentResource = resolver.getResource(path + "/jcr:content");
            if (contentResource != null) {
                ValueMap valueMap = contentResource.getValueMap();
                Double requiredStatus = valueMap.get(REQUIRED_STATUS_PROPERTY, Double.class);
                if (requiredStatus != null) {
                    return requiredStatus;
                }
            }
            if (path.equals(CONTENT_SOURCE_PREFIX)) {
                break;
            }
            int lastSlash = path.lastIndexOf('/');
            path = lastSlash > 0 ? path.substring(0, lastSlash) : null;
        }
        return null;
    }

    /**
     * The form's language, as the first supported language found in: the {@code afAcceptLang} parameter
     * (e.g. {@code ?afAcceptLang=fr}), a selector (e.g. {@code form.fr.html}), then {@code jcr:language}
     * on the form page. Falls back to the first supported language. Regional variants such as
     * {@code fr-CA} or {@code fr_CA} count as their base language.
     */
    String resolveLanguage(SlingHttpServletRequest request, String contentPath) {
        String language = supportedLanguage(request.getParameter(LANGUAGE_PARAMETER));
        if (language == null) {
            for (String selector : request.getRequestPathInfo().getSelectors()) {
                language = supportedLanguage(selector);
                if (language != null) {
                    break;
                }
            }
        }
        if (language == null) {
            Resource formContent = request.getResourceResolver()
                    .getResource(toFormPagePath(contentPath) + "/jcr:content");
            if (formContent != null) {
                language = supportedLanguage(formContent.getValueMap().get("jcr:language", String.class));
            }
        }
        return language != null ? language : supportedLanguages.get(0);
    }

    private String supportedLanguage(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String base = value.split("[-_]")[0].toLowerCase(Locale.ROOT);
        return supportedLanguages.contains(base) ? base : null;
    }

    /** Appends name/value pairs to {@code url}, URL-encoding the values. */
    private static String withQueryParameters(String url, String... namesAndValues) throws IOException {
        StringBuilder result = new StringBuilder(url);
        char separator = url.contains("?") ? '&' : '?';
        for (int i = 0; i < namesAndValues.length; i += 2) {
            result.append(separator).append(namesAndValues[i]).append('=').append(urlEncode(namesAndValues[i + 1]));
            separator = '&';
        }
        return result.toString();
    }

    /** Levels are stored as numbers; whole ones are written without a decimal part ("2", not "2.0"). */
    static String formatLevel(double level) {
        return level == Math.rint(level) ? String.valueOf((long) level) : String.valueOf(level);
    }

    /** The form's path relative to the yukon-forms folder, e.g. "eco/some-folder/some-form". */
    static String toFormName(String contentPath) {
        String relative = contentPath.substring(CONTENT_SOURCE_PREFIX.length());
        return relative.startsWith("/") ? relative.substring(1) : relative;
    }

    /** The adaptive form page behind a DAM content path, e.g. .../formsanddocuments/yukon-forms/x -> .../af/yukon-forms/x. */
    private static String toFormPagePath(String contentPath) {
        return PROTECTED_PATH_PREFIX + contentPath.substring(CONTENT_SOURCE_PREFIX.length());
    }

    boolean isLoggedIn(ResourceResolver resolver) {
        String userId = resolver.getUserID();
        return userId != null && !"anonymous".equals(userId);
    }

    private static void redirectToSamlLogin(SlingHttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        if (queryString != null) {
            requestUri = requestUri + "?" + queryString;
        }
        String loginUrl = SAML_LOGIN_PATH
                + "?resource=" + urlEncode(SAML_AUTH_RESOURCE_PATH)
                + "&saml_request_path=" + urlEncode(requestUri);
        response.sendRedirect(loginUrl);
    }

    private static String urlEncode(String value) throws IOException {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IOException(e);
        }
    }

    double getUserVerificationStatus(ResourceResolver resolver) {
        String userId = resolver.getUserID();
        if (userId == null || "anonymous".equals(userId)) {
            return 0d;
        }

        UserManager userManager = resolver.adaptTo(UserManager.class);
        if (userManager == null) {
            return 0d;
        }

        try {
            Authorizable user = userManager.getAuthorizable(userId);
            if (user == null) {
                return 0d;
            }
            if (!user.hasProperty(USER_STATUS_PROPERTY)) {
                return 1d;
            }
            Value[] values = user.getProperty(USER_STATUS_PROPERTY);
            return values.length > 0 ? values[0].getDouble() : 1d;
        } catch (RepositoryException e) {
            log.warn("Couldn't read {} for user {}", USER_STATUS_PROPERTY, userId, e);
            return 0d;
        }
    }

    @Override
    public void destroy() {
    }
}
