package ca.yukon.aem.core.servlets;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;

import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.servlet.MockRequestDispatcherFactory;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(AemContextExtension.class)
class EmbeddedFormPrefillServletTest {

    private static final String EMBED = "/content/site/page/jcr:content/root/form";
    private static final String FORM_REF = "/content/dam/formsanddocuments/yukon-forms/edu/wra";
    private static final String GUIDE_CONTAINER = "/content/forms/af/yukon-forms/edu/wra/jcr:content/guideContainer";

    private final EmbeddedFormPrefillServlet fixture = new EmbeddedFormPrefillServlet();
    private final RequestDispatcher dispatcher = mock(RequestDispatcher.class);
    private String dispatchedPath;

    @BeforeEach
    void setUp(AemContext context) {
        context.request().setRequestDispatcherFactory(new MockRequestDispatcherFactory() {
            @Override
            public RequestDispatcher getRequestDispatcher(String path, RequestDispatcherOptions options) {
                dispatchedPath = path;
                return dispatcher;
            }

            @Override
            public RequestDispatcher getRequestDispatcher(Resource resource, RequestDispatcherOptions options) {
                throw new UnsupportedOperationException();
            }
        });
    }

    @Test
    void forwardsToTheEmbeddedFormsGuideContainer(AemContext context) throws ServletException, IOException {
        context.build().resource(GUIDE_CONTAINER).resource(EMBED, "formRef", FORM_REF).commit();
        context.currentResource(EMBED);
        MockSlingHttpServletRequest request = context.request();
        MockSlingHttpServletResponse response = context.response();

        fixture.doPost(request, response);

        assertEquals(GUIDE_CONTAINER + ".af.prefilldata.json", dispatchedPath);
        verify(dispatcher).forward(request, response);
    }

    @Test
    void notFoundWithoutFormRef(AemContext context) throws ServletException, IOException {
        context.build().resource(EMBED).commit();
        context.currentResource(EMBED);

        fixture.doPost(context.request(), context.response());

        assertEquals(404, context.response().getStatus());
        assertNull(dispatchedPath);
        verify(dispatcher, never()).forward(any(), any());
    }

    @Test
    void notFoundForFormRefOutsideFormsAndDocuments(AemContext context) throws ServletException, IOException {
        context.build().resource("/content/forms/af/other/jcr:content/guideContainer")
                .resource(EMBED, "formRef", "/content/forms/af/other").commit();
        context.currentResource(EMBED);

        fixture.doPost(context.request(), context.response());

        assertEquals(404, context.response().getStatus());
        assertNull(dispatchedPath);
    }

    @Test
    void notFoundWhenTheFormDoesNotExist(AemContext context) throws ServletException, IOException {
        context.build().resource(EMBED, "formRef", FORM_REF).commit();
        context.currentResource(EMBED);

        fixture.doPost(context.request(), context.response());

        assertEquals(404, context.response().getStatus());
        assertNull(dispatchedPath);
    }
}
