package ca.yukon.aem.core.servlets;

import com.adobe.aemds.guide.utils.GuideUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Forwards prefill requests for a form embedded in a Sites page to the form's guideContainer.
 *
 * Inside the "Adaptive Form - Embed" component, Adobe's form runtime (guideRuntime.js) POSTs
 * {@code <embed>.af.prefilldata.json?<page query>} instead of
 * {@code <guideContainer>.af.prefilldata.json}. Nothing handles that path, so the default Sling POST
 * servlet answered: no data came back, and on author the query parameters were written onto the embed
 * node. This lets a Sites page resume a Forms Portal draft with
 * {@code ?dataRef=service://FP/draft/<id>} (YUKONAEMF-81). AEM's draft prefill service only returns
 * a draft to its owner.
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
        resourceTypes = "yukon-forms/components/aemformscontainer",
        methods = HttpConstants.METHOD_POST,
        selectors = "af.prefilldata",
        extensions = "json")
@ServiceDescription("Forwards embedded form prefill requests to the form's guideContainer")
public class EmbeddedFormPrefillServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    static final String FORM_REF = "formRef";
    static final String FORMS_ASSET_ROOT = "/content/dam/formsanddocuments/";
    static final String PREFILL_SELECTORS_AND_EXTENSION = ".af.prefilldata.json";

    @Override
    protected void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException {
        final String formRef = request.getResource().getValueMap().get(FORM_REF, String.class);
        if (formRef == null || !formRef.startsWith(FORMS_ASSET_ROOT)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        final String guideContainerPath = GuideUtils.convertFMAssetPathToContainerPath(formRef);
        if (request.getResourceResolver().getResource(guideContainerPath) == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // A forward keeps the POST and the original query string (dataRef, wcmmode).
        final RequestDispatcher dispatcher =
                request.getRequestDispatcher(guideContainerPath + PREFILL_SELECTORS_AND_EXTENSION);
        if (dispatcher == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        dispatcher.forward(request, response);
    }
}
