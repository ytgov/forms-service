package ca.yukon.aem.core.filters;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.Test;

import javax.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectorySignInFilterTest {

    private static final String OTHER_FDM = "/content/dam/formsanddocuments-fdm/yukon-forms/other";

    private final DirectorySignInFilter filter = new DirectorySignInFilter();

    @Test
    void refusesAnonymousDirectoryCalls() throws Exception {
        for (String user : new String[] { "anonymous", null, "" }) {
            FilterChain chain = mock(FilterChain.class);
            MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

            filter.doFilter(request(user, DirectorySignInFilter.FDM_PATH), response, chain);

            assertEquals(403, response.getStatus(), String.valueOf(user));
            assertTrue(response.getOutputAsString().contains("signInRequired"));
            verify(chain, never()).doFilter(any(), any());
        }
    }

    @Test
    void refusesAnonymousCallsWithPathsThatResolveToTheDirectory() throws Exception {
        String[] paths = {
                "/content/dam/formsanddocuments-fdm//yukon-forms/ygdirectory",
                "/content/dam/formsanddocuments-fdm/yukon-forms/./ygdirectory",
                "/content/dam/formsanddocuments-fdm/yukon-forms/other/../ygdirectory",
                "//content/dam/formsanddocuments-fdm/yukon-forms/ygdirectory//",
                "/content/dam/formsanddocuments-fdm/yukon-forms/ygdirectory/./jcr:content",
                "/../content/dam/formsanddocuments-fdm/yukon-forms/ygdirectory",
        };
        for (String path : paths) {
            FilterChain chain = mock(FilterChain.class);
            MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

            filter.doFilter(request("anonymous", path), response, chain);

            assertEquals(403, response.getStatus(), path);
            verify(chain, never()).doFilter(any(), any());
        }
    }

    @Test
    void letsSignedInUsersThrough() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        SlingHttpServletRequest request = request("someone@yukon.ca", DirectorySignInFilter.FDM_PATH + "/");
        MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void leavesOtherFormDataModelsAlone() throws Exception {
        for (String fdm : new String[] { OTHER_FDM, null, DirectorySignInFilter.FDM_PATH + "-copy" }) {
            FilterChain chain = mock(FilterChain.class);
            SlingHttpServletRequest request = request("anonymous", fdm);
            MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    private static SlingHttpServletRequest request(String userId, String fdm) {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        when(resolver.getUserID()).thenReturn(userId);
        when(request.getResourceResolver()).thenReturn(resolver);
        when(request.getParameter(DirectorySignInFilter.FDM_PARAM)).thenReturn(fdm);
        return request;
    }
}
