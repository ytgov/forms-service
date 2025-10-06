package ca.yukon.aem.core.filters;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(AemContextExtension.class)
class RawQueryInjectorFilterTest {

    private RawQueryInjectorFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setup() {
        filter = new RawQueryInjectorFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void testSameOriginRequest_Allowed(AemContext context) throws IOException, ServletException {
        MockSlingHttpServletRequest request = context.request();
        request.setQueryString("name=John");
        request.setPathInfo("/content/forms/af/yukon-forms/test-form");
        request.setHeader("Referer", "http://localhost:4502/content/forms/af/yukon-forms/other-form");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(4502);

        filter.doFilter(request, context.response(), filterChain);

        verify(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    void testDirectNavigation_Allowed(AemContext context) throws IOException, ServletException {
        MockSlingHttpServletRequest request = context.request();
        request.setQueryString("name=John");
        request.setPathInfo("/content/forms/af/yukon-forms/test-form");
        // No Referer header (direct navigation)

        filter.doFilter(request, context.response(), filterChain);

        verify(filterChain).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    void testCrossOriginRequest_Blocked(AemContext context) throws IOException, ServletException {
        MockSlingHttpServletRequest request = context.request();
        request.setQueryString("name=John");
        request.setPathInfo("/content/forms/af/yukon-forms/test-form");
        request.setHeader("Referer", "https://malicious-site.com/phishing");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(4502);

        filter.doFilter(request, context.response(), filterChain);

        // Request should be blocked - original request passed without wrapping
        verify(filterChain).doFilter(request, context.response());
    }

    @Test
    void testNonAdaptiveFormsRequest_Skipped(AemContext context)
            throws IOException, ServletException {
        MockSlingHttpServletRequest request = context.request();
        request.setQueryString("name=John");
        request.setPathInfo("/content/other-path/page"); // Not an AF path

        filter.doFilter(request, context.response(), filterChain);

        // Should bypass filter entirely
        verify(filterChain).doFilter(request, context.response());
    }

    @Test
    void testEmptyQueryString_Skipped(AemContext context) throws IOException, ServletException {
        MockSlingHttpServletRequest request = context.request();
        request.setPathInfo("/content/forms/af/yukon-forms/test-form");
        // No query string

        filter.doFilter(request, context.response(), filterChain);

        verify(filterChain).doFilter(request, context.response());
    }
}
