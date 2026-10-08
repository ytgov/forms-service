package ca.yukon.aem.core.filters;

import ca.yukon.aem.core.config.VerificationStatusGateConfig;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class VerificationStatusGateFilterTest {

    private static final String REDIRECT_PATTERN = "/content/yukon-forms/ca/{lang}/verification-required.html";
    /** Redirect for the user at level 1 trying a form in a level-5 folder, as used throughout these tests. */
    private static final String REDIRECT_QUERY = "?u_loa=1&f_low=5&form_name=some-department%2Fsensitive-form";
    private static final String REDIRECT_PAGE =
            "/content/yukon-forms/ca/en/verification-required.html" + REDIRECT_QUERY;
    private static final String REDIRECT_PAGE_FR =
            "/content/yukon-forms/ca/fr/verification-required.html" + REDIRECT_QUERY;
    private static final String FOLDER_PATH =
            "/content/dam/formsanddocuments/yukon-forms/some-department";
    private static final String FORM_PATH = FOLDER_PATH + "/sensitive-form";
    private static final String REQUEST_PATH = "/content/forms/af/yukon-forms/some-department/sensitive-form";

    private VerificationStatusGateFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setup() {
        filter = new VerificationStatusGateFilter();
        filter.activate(config());
        filterChain = mock(FilterChain.class);
    }

    @Test
    void testFindRequiredVerificationStatus_onOwnJcrContent(AemContext context) {
        withProperty(context, FORM_PATH, 3d);

        Double result = filter.findRequiredVerificationStatus(context.resourceResolver(), FORM_PATH);

        assertEquals(3d, result);
    }

    @Test
    void testFindRequiredVerificationStatus_inheritedFromAncestorFolder(AemContext context) {
        withProperty(context, FOLDER_PATH, 2d);

        Double result = filter.findRequiredVerificationStatus(context.resourceResolver(), FORM_PATH);

        assertEquals(2d, result);
    }

    @Test
    void testFindRequiredVerificationStatus_noneDeclared_returnsNull(AemContext context) {
        context.create().resource(FORM_PATH);

        Double result = filter.findRequiredVerificationStatus(context.resourceResolver(), FORM_PATH);

        assertNull(result);
    }

    @Test
    void testDoFilter_nonProtectedPath_skipsFilter(AemContext context) throws IOException, ServletException {
        context.requestPathInfo().setResourcePath("/content/forms/af/other-forms/public-form");
        MockSlingHttpServletRequest request = context.request();
        request.setMethod("GET");

        filter.doFilter(request, context.response(), filterChain);

        verify(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    void testDoFilter_siblingOfYukonFormsRoot_skipsFilter(AemContext context) throws IOException, ServletException {
        withProperty(context, "/content/dam/formsanddocuments/yukon-forms-archive/form", 5d);
        context.requestPathInfo().setResourcePath("/content/forms/af/yukon-forms-archive/form");
        MockSlingHttpServletRequest request = context.request();
        request.setMethod("GET");

        filter.doFilter(request, context.response(), filterChain);

        verify(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    void testDoFilter_folderOutsideProtectedForms_gated(AemContext context) throws IOException, ServletException {
        withProperty(context, "/content/dam/formsanddocuments/yukon-forms/hss", 2d);
        context.create().resource("/content/dam/formsanddocuments/yukon-forms/hss/some-form");
        context.requestPathInfo().setResourcePath("/content/forms/af/yukon-forms/hss/some-form");
        MockSlingHttpServletRequest request = context.request();
        request.setMethod("GET");

        filter.doFilter(request, context.response(), filterChain);

        assertTrue(context.response().getHeader("Location").startsWith("/system/sling/login?"));
        verifyNoInteractions(filterChain);
    }

    @Test
    void testDoFilter_folderWithoutRequiredStatus_allowsThrough(AemContext context)
            throws IOException, ServletException {
        context.create().resource(FORM_PATH);
        context.requestPathInfo().setResourcePath(REQUEST_PATH);
        MockSlingHttpServletRequest request = context.request();
        request.setMethod("GET");

        filter.doFilter(request, context.response(), filterChain);

        verify(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    void testDoFilter_anonymousUser_redirectsToSamlLogin(AemContext context)
            throws IOException, ServletException {
        withProperty(context, FORM_PATH, 5d);

        context.requestPathInfo().setResourcePath(REQUEST_PATH);
        MockSlingHttpServletRequest request = context.request();
        request.setMethod("GET");

        filter.doFilter(request, context.response(), filterChain);

        String location = context.response().getHeader("Location");
        assertTrue(location.startsWith("/system/sling/login?"));
        assertTrue(location.contains("resource=%2Fcontent%2Fyukon-forms"));
        assertTrue(location.contains("saml_request_path="));
        verifyNoInteractions(filterChain);
    }

    @Test
    void testDoFilter_directDamPathRequest_gated(AemContext context) throws IOException, ServletException {
        withProperty(context, FORM_PATH, 5d);

        context.requestPathInfo().setResourcePath(FORM_PATH + "/jcr:content");
        MockSlingHttpServletRequest request = context.request();
        request.setMethod("GET");

        filter.doFilter(request, context.response(), filterChain);

        String location = context.response().getHeader("Location");
        assertTrue(location.startsWith("/system/sling/login?"));
        verifyNoInteractions(filterChain);
    }

    @Test
    void testDoFilter_userBelowRequiredStatus_redirects(AemContext context)
            throws IOException, ServletException {
        withProperty(context, FORM_PATH, 5d);
        VerificationStatusGateFilter gate = new VerificationStatusGateFilter() {
            @Override
            boolean isLoggedIn(ResourceResolver resolver) {
                return true;
            }

            @Override
            double getUserVerificationStatus(ResourceResolver resolver) {
                return 1d;
            }
        };
        gate.activate(config());

        context.requestPathInfo().setResourcePath(REQUEST_PATH);
        MockSlingHttpServletRequest request = context.request();
        request.setMethod("GET");

        gate.doFilter(request, context.response(), filterChain);

        assertEquals(REDIRECT_PAGE, context.response().getHeader("Location"));
        verifyNoInteractions(filterChain);
    }

    @Test
    void testDoFilter_userBelowRequiredStatus_frenchParameter_redirectsToFrenchPage(AemContext context)
            throws IOException, ServletException {
        withProperty(context, FORM_PATH, 5d);
        context.requestPathInfo().setResourcePath(REQUEST_PATH);
        context.request().setParameterMap(Map.of("afAcceptLang", "fr-CA"));

        assertEquals(REDIRECT_PAGE_FR, redirectBelowStatus(context));
    }

    @Test
    void testDoFilter_userBelowRequiredStatus_frenchSelector_redirectsToFrenchPage(AemContext context)
            throws IOException, ServletException {
        withProperty(context, FORM_PATH, 5d);
        context.requestPathInfo().setResourcePath(REQUEST_PATH);
        context.requestPathInfo().setSelectorString("fr");

        assertEquals(REDIRECT_PAGE_FR, redirectBelowStatus(context));
    }

    @Test
    void testDoFilter_userBelowRequiredStatus_frenchFormPage_redirectsToFrenchPage(AemContext context)
            throws IOException, ServletException {
        withProperty(context, FORM_PATH, 5d);
        context.create().resource(REQUEST_PATH + "/jcr:content", Map.of("jcr:language", "fr"));
        context.requestPathInfo().setResourcePath(REQUEST_PATH);

        assertEquals(REDIRECT_PAGE_FR, redirectBelowStatus(context));
    }

    @Test
    void testDoFilter_userBelowRequiredStatus_unsupportedLanguage_redirectsToDefaultPage(AemContext context)
            throws IOException, ServletException {
        withProperty(context, FORM_PATH, 5d);
        context.requestPathInfo().setResourcePath(REQUEST_PATH);
        context.request().setParameterMap(Map.of("afAcceptLang", "de"));

        assertEquals(REDIRECT_PAGE, redirectBelowStatus(context));
    }

    @Test
    void testDoFilter_userMeetsRequiredStatus_allowsThrough(AemContext context)
            throws IOException, ServletException {
        withProperty(context, FORM_PATH, 5d);
        VerificationStatusGateFilter gate = new VerificationStatusGateFilter() {
            @Override
            boolean isLoggedIn(ResourceResolver resolver) {
                return true;
            }

            @Override
            double getUserVerificationStatus(ResourceResolver resolver) {
                return 5d;
            }
        };
        gate.activate(config());

        context.requestPathInfo().setResourcePath(REQUEST_PATH);
        MockSlingHttpServletRequest request = context.request();
        request.setMethod("GET");

        gate.doFilter(request, context.response(), filterChain);

        verify(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    void testFormatLevel() {
        assertEquals("2", VerificationStatusGateFilter.formatLevel(2d));
        assertEquals("0", VerificationStatusGateFilter.formatLevel(0d));
        assertEquals("1.5", VerificationStatusGateFilter.formatLevel(1.5d));
    }

    @Test
    void testToFormName() {
        assertEquals("eco/youth-fund/application-f",
                VerificationStatusGateFilter.toFormName("/content/dam/formsanddocuments/yukon-forms/eco/youth-fund/application-f"));
    }

    private static VerificationStatusGateConfig config() {
        VerificationStatusGateConfig config = mock(VerificationStatusGateConfig.class);
        when(config.redirect_page()).thenReturn(REDIRECT_PATTERN);
        when(config.supported_languages()).thenReturn(new String[] {"en", "fr"});
        return config;
    }

    /** Runs the request through a gate whose signed-in user is below the required status; returns the redirect. */
    private String redirectBelowStatus(AemContext context) throws IOException, ServletException {
        VerificationStatusGateFilter gate = new VerificationStatusGateFilter() {
            @Override
            boolean isLoggedIn(ResourceResolver resolver) {
                return true;
            }

            @Override
            double getUserVerificationStatus(ResourceResolver resolver) {
                return 1d;
            }
        };
        gate.activate(config());
        context.request().setMethod("GET");

        gate.doFilter(context.request(), context.response(), filterChain);

        verifyNoInteractions(filterChain);
        return context.response().getHeader("Location");
    }

    private static void withProperty(AemContext context, String path, double requiredStatus) {
        Map<String, Object> props = new HashMap<>();
        props.put("required_verification_status", requiredStatus);
        context.create().resource(path);
        context.create().resource(path + "/jcr:content", props);
    }
}
