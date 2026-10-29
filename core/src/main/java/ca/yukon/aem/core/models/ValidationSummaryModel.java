package ca.yukon.aem.core.models;

import com.adobe.cq.export.json.ExporterConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Model(
    adaptables = SlingHttpServletRequest.class,
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
@Exporter(name = ExporterConstants.SLING_MODEL_EXPORTER_NAME,
          extensions = ExporterConstants.SLING_MODEL_EXTENSION)
public class ValidationSummaryModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String DEFAULT_BADGE_TEXT = "New: error summary";
    static final String DEFAULT_TITLE_TEXT = "There\u2019s a problem";
    static final String DEFAULT_INTRO_TEXT = "Fix these {count} errors before you submit.";
    static final String DEFAULT_INTRO_TEXT_ONE = "Fix this error before you submit.";

    /** Field names (Somerset/API) to suppress from the validation summary. */
    @ValueMapValue
    private String[] excludedFields;

    /** Whether to show the badge above the summary. */
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
    }

    public String getExcludedFieldsJson() {
        return excludedFieldsJson;
    }

    public boolean isShowBadge() {
        return showBadge == null || showBadge;
    }

    public String getBadgeText() {
        return orDefault(badgeText, DEFAULT_BADGE_TEXT);
    }

    public String getTitleText() {
        return orDefault(titleText, DEFAULT_TITLE_TEXT);
    }

    public String getIntroText() {
        return orDefault(introText, DEFAULT_INTRO_TEXT);
    }

    public String getIntroTextOne() {
        return orDefault(introTextOne, DEFAULT_INTRO_TEXT_ONE);
    }

    private static String orDefault(String value, String defaultValue) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
    }
}
