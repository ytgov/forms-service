package ca.yukon.aem.core.models;

import com.adobe.cq.export.json.ExporterConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.Self;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Model(
    adaptables = SlingHttpServletRequest.class,
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
@Exporter(name = ExporterConstants.SLING_MODEL_EXPORTER_NAME,
          extensions = ExporterConstants.SLING_MODEL_EXTENSION)
public class ValidationSummaryModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String LANGUAGE_PARAMETER = "afAcceptLang";
    static final String FRENCH = "fr";

    static final String DEFAULT_BADGE_TEXT = "New: error summary";
    static final String DEFAULT_TITLE_TEXT = "There’s a problem";
    static final String DEFAULT_INTRO_TEXT = "Fix these {count} errors before you submit.";
    static final String DEFAULT_INTRO_TEXT_ONE = "Fix this error before you submit.";

    static final String DEFAULT_BADGE_TEXT_FR = "Nouveau : résumé des erreurs";
    static final String DEFAULT_TITLE_TEXT_FR = "Il y a un problème";
    static final String DEFAULT_INTRO_TEXT_FR = "Corrigez ces {count} erreurs avant de soumettre le formulaire.";
    static final String DEFAULT_INTRO_TEXT_ONE_FR = "Corrigez cette erreur avant de soumettre le formulaire.";

    @Self
    private SlingHttpServletRequest request;

    /** Field names (Somerset/API) to suppress from the validation summary. */
    @ValueMapValue
    private String[] excludedFields;

    /** Whether to show the badge above the summary. Off unless an author turns it on. */
    @ValueMapValue
    private Boolean showBadge;

    /** Badge text above the summary. */
    @ValueMapValue
    private String badgeText;

    /** Summary heading. */
    @ValueMapValue
    private String titleText;

    /** Line under the heading; "{count}" is replaced with the number of errors. */
    @ValueMapValue
    private String introText;

    /** Line under the heading when there is only one error. */
    @ValueMapValue
    private String introTextOne;

    private String excludedFieldsJson;

    private boolean french;

    @PostConstruct
    protected void init() {
        List<String> fields = (excludedFields != null)
            ? Arrays.asList(excludedFields)
            : Collections.emptyList();

        try {
            excludedFieldsJson = MAPPER.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            excludedFieldsJson = "[]";
        }

        french = isFrench();
    }

    public String getExcludedFieldsJson() {
        return excludedFieldsJson;
    }

    public boolean isShowBadge() {
        return showBadge != null && showBadge;
    }

    public String getBadgeText() {
        return orDefault(badgeText, french ? DEFAULT_BADGE_TEXT_FR : DEFAULT_BADGE_TEXT);
    }

    public String getTitleText() {
        return orDefault(titleText, french ? DEFAULT_TITLE_TEXT_FR : DEFAULT_TITLE_TEXT);
    }

    public String getIntroText() {
        return orDefault(introText, french ? DEFAULT_INTRO_TEXT_FR : DEFAULT_INTRO_TEXT);
    }

    public String getIntroTextOne() {
        return orDefault(introTextOne, french ? DEFAULT_INTRO_TEXT_ONE_FR : DEFAULT_INTRO_TEXT_ONE);
    }

    /**
     * Whether the form is shown in French, going by the {@code afAcceptLang} parameter
     * (e.g. {@code ?afAcceptLang=fr}), a selector (e.g. {@code form.fr.html}), then
     * {@code jcr:language} on the form page. Regional variants such as {@code fr-CA} count.
     */
    private boolean isFrench() {
        if (request == null) {
            return false;
        }
        String parameter = request.getParameter(LANGUAGE_PARAMETER);
        if (parameter != null && !parameter.isEmpty()) {
            return isFrench(parameter);
        }
        for (String selector : request.getRequestPathInfo().getSelectors()) {
            if (isFrench(selector)) {
                return true;
            }
        }
        Resource formContent = formContent(request.getResource());
        return formContent != null && isFrench(formContent.getValueMap().get("jcr:language", String.class));
    }

    /** The jcr:content of the form page containing {@code resource}, or {@code null}. */
    private static Resource formContent(Resource resource) {
        Resource current = resource;
        while (current != null && !"jcr:content".equals(current.getName())) {
            current = current.getParent();
        }
        return current;
    }

    private static boolean isFrench(String language) {
        return language != null
            && FRENCH.equals(language.split("[-_]")[0].toLowerCase(Locale.ROOT));
    }

    private static String orDefault(String value, String defaultValue) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }
}
