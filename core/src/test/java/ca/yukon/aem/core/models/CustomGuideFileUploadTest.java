package ca.yukon.aem.core.models;

import ca.yukon.aem.core.testcontext.AppAemContext;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(AemContextExtension.class)
class CustomGuideFileUploadTest {

    private static final String FORM = "/content/forms/af/yukon-forms/test-form";
    private static final String FIELD = FORM + "/jcr:content/guideContainer/rootPanel/items/upload";

    private final AemContext context = AppAemContext.newAemContext();

    @BeforeEach
    void setUp() {
        context.create().resource(FORM + "/jcr:content", "jcr:title", "Test form");
        context.create().resource(FIELD);
    }

    @Test
    void blankMessagesUseEnglishDefaults() {
        CustomGuideFileUpload field = field(Collections.singletonMap("msgTotal", "  "));
        assertEquals("en", field.getLanguage());
        assertEquals("Total: {total}", field.getMsgTotal());
        assertEquals("\"{fileName}\" ({size}) wasn't added. Choose a file that is {maxSize} or smaller.",
                field.getMsgFileTooLarge());
        assertEquals("\"{fileName}\" ({size}) wasn't added. Rename the file using only allowed characters, "
                + "then upload it again.", field.getMsgFileNameNotAllowed());
    }

    @Test
    void frenchFormUsesFrenchDefaults() {
        context.create().resource("/content/forms/af/yukon-forms/fr-form/jcr:content", "jcr:language", "fr_CA");
        Resource frField = context.create().resource("/content/forms/af/yukon-forms/fr-form/jcr:content/guideContainer/upload");
        CustomGuideFileUpload field = field(frField, Collections.emptyMap());
        assertEquals("fr", field.getLanguage());
        assertEquals("Total : {total}", field.getMsgTotal());
        assertEquals("«\u00A0{fileName}\u00A0» ({size}) n'a pas été ajouté. Choisissez un fichier de {maxSize} ou moins.",
                field.getMsgFileTooLarge());
    }

    @Test
    void afAcceptLangParameterWins() {
        context.request().setQueryString("afAcceptLang=fr");
        context.request().setParameterMap(Collections.singletonMap("afAcceptLang", "fr"));
        assertEquals("fr", field(Collections.emptyMap()).getLanguage());
    }

    @Test
    void authoredMessagesAreTrimmedAndKept() {
        Map<String, Object> props = new HashMap<>();
        props.put("msgFileTooLarge", " Too big: {fileName} ");
        props.put("fileNamePatternMessage", "Bad name: {fileName}");
        props.put("msgTotal", "Sum {total}");
        CustomGuideFileUpload field = field(props);
        assertEquals("Too big: {fileName}", field.getMsgFileTooLarge());
        assertEquals("Bad name: {fileName}", field.getMsgFileNameNotAllowed());
        assertEquals("Bad name: {fileName}", field.getFileNamePatternMessage());
        assertEquals("Sum {total}", field.getMsgTotal());
    }

    @Test
    void totalTooLargeDefaultsInBothLanguages() {
        assertEquals("\"{fileName}\" ({size}) wasn't added. Remove a file or choose a smaller one so the total is "
                + "{maxTotal} or less.", field(Collections.emptyMap()).getMsgTotalTooLarge());
        context.create().resource("/content/forms/af/yukon-forms/fr-form/jcr:content", "jcr:language", "fr");
        Resource frField = context.create().resource("/content/forms/af/yukon-forms/fr-form/jcr:content/guideContainer/upload");
        assertEquals("«\u00A0{fileName}\u00A0» ({size}) n'a pas été ajouté. Retirez un fichier ou choisissez-en un plus "
                + "petit pour que le total soit de {maxTotal} ou moins.", field(frField, Collections.emptyMap()).getMsgTotalTooLarge());
        assertEquals("Too much: {maxTotal}",
                field(Collections.singletonMap("msgTotalTooLarge", "Too much: {maxTotal}")).getMsgTotalTooLarge());
    }

    @Test
    void maxTotalSizeIsAPositiveNumberOrBlank() {
        assertEquals("", field(Collections.emptyMap()).getMaxTotalSize());
        assertEquals("20", field(Collections.singletonMap("maxTotalSize", "20")).getMaxTotalSize());
        assertEquals("20", field(Collections.singletonMap("maxTotalSize", " 20.0 ")).getMaxTotalSize());
        assertEquals("12.5", field(Collections.singletonMap("maxTotalSize", 12.5d)).getMaxTotalSize());
        assertEquals("20", field(Collections.singletonMap("maxTotalSize", 20L)).getMaxTotalSize());
        assertEquals("", field(Collections.singletonMap("maxTotalSize", "")).getMaxTotalSize());
        assertEquals("", field(Collections.singletonMap("maxTotalSize", "0")).getMaxTotalSize());
        assertEquals("", field(Collections.singletonMap("maxTotalSize", "-5")).getMaxTotalSize());
        assertEquals("", field(Collections.singletonMap("maxTotalSize", "abc")).getMaxTotalSize());
    }

    private CustomGuideFileUpload field(Map<String, Object> props) {
        return field(context.resourceResolver().getResource(FIELD), props);
    }

    private CustomGuideFileUpload field(Resource fieldResource, Map<String, Object> props) {
        context.currentResource(fieldResource);
        return new CustomGuideFileUpload() {
            {
                resource = fieldResource;
                slingRequest = context.request();
                resourceProps = new ValueMapDecorator(new HashMap<>(props));
            }
        };
    }
}
