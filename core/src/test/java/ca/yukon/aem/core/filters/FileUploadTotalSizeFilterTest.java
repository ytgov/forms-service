package ca.yukon.aem.core.filters;

import ca.yukon.aem.core.models.CustomGuideFileUpload;
import com.adobe.aemds.guide.common.GuideError;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.servlet.FilterChain;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class FileUploadTotalSizeFilterTest {

    private static final String FORM = "/content/forms/af/yukon-forms/test-form";
    private static final String CONTAINER = FORM + "/jcr:content/guideContainer";
    private static final String SOM = "guide[0].guide1[0].guideRootPanel[0].docs[0].upload[0]";
    private static final long MB = 1024L * 1024L;

    private final AemContext context = new AemContext();
    private final FileUploadTotalSizeFilter filter = new FileUploadTotalSizeFilter();
    private final Map<String, RequestParameter[]> parameters = new HashMap<>();

    @BeforeEach
    void setUp() {
        context.create().resource(FORM + "/jcr:content", "jcr:language", "en");
        context.create().resource(CONTAINER, "sling:resourceType", "fd/af/components/guideContainer", "name", "guide1");
        context.create().resource(CONTAINER + "/rootPanel", "name", "guideRootPanel");
        context.create().resource(CONTAINER + "/rootPanel/items/panel1", "name", "docs");
        context.create().resource(CONTAINER + "/rootPanel/items/panel1/items/upload1",
                "sling:resourceType", CustomGuideFileUpload.RESOURCE_TYPE, "name", "upload",
                CustomGuideFileUpload.MAX_TOTAL_SIZE, "2");
        context.create().resource(CONTAINER + "/rootPanel/items/panel1/items/other",
                "sling:resourceType", "fd/af/components/guidefileupload", "name", "other");
    }

    @Test
    void fileAttachmentMap_parsedPerField() {
        Map<String, List<String>> map = FileUploadTotalSizeFilter.parseFileAttachmentMap(
                "{\"a[0]\":\"upload/one.pdf\\nupload/two.pdf\\n\",\"b[0]\":\"\",\"c[0]\":5}");
        assertEquals(Arrays.asList("upload/one.pdf", "upload/two.pdf"), map.get("a[0]"));
        assertEquals(1, map.size());
        assertTrue(FileUploadTotalSizeFilter.parseFileAttachmentMap("not json").isEmpty());
        assertTrue(FileUploadTotalSizeFilter.parseFileAttachmentMap(null).isEmpty());
    }

    @Test
    void som_resolvedThroughItemsAndFragments() {
        Resource container = context.resourceResolver().getResource(CONTAINER);
        assertEquals(CONTAINER + "/rootPanel/items/panel1/items/upload1",
                FileUploadTotalSizeFilter.resolveSom(container, SOM).getPath());
        assertNull(FileUploadTotalSizeFilter.resolveSom(container, "guide[0].guide1[0].guideRootPanel[0].missing[0]"));

        context.create().resource(CONTAINER + "/rootPanel/items/frag", "name", "fragPanel",
                "fragRef", "/content/dam/formsanddocuments/yukon-forms/frags/docs");
        context.create().resource("/content/forms/af/yukon-forms/frags/docs/jcr:content/guideContainer/rootPanel/items/up",
                "name", "fragUpload");
        assertEquals("/content/forms/af/yukon-forms/frags/docs/jcr:content/guideContainer/rootPanel/items/up",
                FileUploadTotalSizeFilter.resolveSom(container,
                        "guide[0].guide1[0].guideRootPanel[0].fragPanel[0].fragUpload[0]").getPath());
    }

    @Test
    void totalCountsPartsByEntryOrByFieldNameAndFileName() {
        filePart("upload/one.pdf", "one.pdf", MB);
        filePart("upload", "two.pdf", MB);
        filePart("upload", "notListed.pdf", 5 * MB); // another field with the same name
        formField("upload/three.pdf");               // a reference, not a file part
        SlingHttpServletRequest request = request("{}");

        assertEquals(2 * MB, FileUploadTotalSizeFilter.totalBytes(request, "upload",
                Arrays.asList("upload/one.pdf", "upload/two.pdf", "upload/three.pdf")));
    }

    @Test
    void overTheLimit_rejectedWithAFieldError() throws Exception {
        filePart("upload/one.pdf", "one.pdf", MB);
        filePart("upload/two.pdf", "two.pdf", MB + 1);
        SlingHttpServletRequest request = request("{\"" + SOM + "\":\"upload/one.pdf\\nupload/two.pdf\"}");
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response).setStatus(400);
        assertTrue(body.toString().contains("SERVER_SIDE_VALIDATION"), body.toString());
        assertTrue(body.toString().contains(SOM), body.toString());
        assertTrue(body.toString().contains("total 2.1 MB, which is more than the 2 MB allowed"), body.toString());
    }

    @Test
    void withinTheLimitOrNoLimit_passesThrough() throws Exception {
        filePart("upload/one.pdf", "one.pdf", 2 * MB); // exactly the limit
        filePart("other/big.pdf", "big.pdf", 50 * MB);  // plain file upload: no total check
        SlingHttpServletRequest request = request("{\"" + SOM + "\":\"upload/one.pdf\","
                + "\"guide[0].guide1[0].guideRootPanel[0].docs[0].other[0]\":\"other/big.pdf\"}");
        FilterChain chain = mock(FilterChain.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(400);
    }

    @Test
    void otherRequests_notChecked() throws Exception {
        filePart("upload/one.pdf", "one.pdf", 10 * MB);
        SlingHttpServletRequest request = request("{\"" + SOM + "\":\"upload/one.pdf\"}");
        when(request.getRequestPathInfo().getSelectorString()).thenReturn("af.dermis");
        FilterChain chain = mock(FilterChain.class);
        SlingHttpServletResponse response = mock(SlingHttpServletResponse.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void messagesAndSizes() {
        assertEquals("2 MB", FileUploadTotalSizeFilter.formatSize(2 * MB, false));
        assertEquals("2.1 MB", FileUploadTotalSizeFilter.formatSize(2 * MB + 1, false));
        assertEquals("12,5 Mo", FileUploadTotalSizeFilter.formatSize(12 * MB + MB / 2, true));
        assertTrue(FileUploadTotalSizeFilter.message(true, 3 * MB, 2 * MB).startsWith(
                "Les fichiers joints ici totalisent 3 Mo, ce qui dépasse la limite de 2 Mo."));
    }

    @Test
    void frenchForm_frenchMessage() {
        context.resourceResolver().getResource(FORM + "/jcr:content").adaptTo(org.apache.sling.api.resource.ModifiableValueMap.class)
                .put("jcr:language", "fr");
        filePart("upload/one.pdf", "one.pdf", 3 * MB);
        List<GuideError> errors = FileUploadTotalSizeFilter.check(request("{\"" + SOM + "\":\"upload/one.pdf\"}"));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getErrorMessage().startsWith("Les fichiers joints ici totalisent"));
    }

    private void filePart(String key, String fileName, long size) {
        RequestParameter part = mock(RequestParameter.class);
        when(part.isFormField()).thenReturn(false);
        when(part.getFileName()).thenReturn(fileName);
        when(part.getSize()).thenReturn(size);
        add(key, part);
    }

    private void formField(String key) {
        RequestParameter part = mock(RequestParameter.class);
        when(part.isFormField()).thenReturn(true);
        add(key, part);
    }

    private void add(String key, RequestParameter part) {
        RequestParameter[] existing = parameters.get(key);
        RequestParameter[] parts = existing == null ? new RequestParameter[] {part}
                : Arrays.copyOf(existing, existing.length + 1);
        parts[parts.length - 1] = part;
        parameters.put(key, parts);
    }

    private SlingHttpServletRequest request(String fileAttachmentMap) {
        SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        RequestPathInfo pathInfo = mock(RequestPathInfo.class);
        when(pathInfo.getSelectorString()).thenReturn("af.submit");
        when(pathInfo.getSelectors()).thenReturn(new String[] {"af", "submit"});
        when(request.getRequestPathInfo()).thenReturn(pathInfo);
        when(request.getMethod()).thenReturn("POST");
        when(request.getResource()).thenReturn(context.resourceResolver().getResource(CONTAINER));
        when(request.getParameter(FileUploadTotalSizeFilter.FILE_ATTACHMENT_MAP)).thenReturn(fileAttachmentMap);
        RequestParameterMap map = mock(RequestParameterMap.class);
        when(map.get(any())).thenAnswer(invocation -> parameters.get((String) invocation.getArgument(0)));
        when(request.getRequestParameterMap()).thenReturn(map);
        return request;
    }
}
