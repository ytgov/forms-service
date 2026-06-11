package ca.yukon.aem.core.models;

import com.adobe.cq.export.json.ExporterConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Exporter;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Sling Model for the Review Summary component.
 *
 * Reads authoring dialog properties and exposes them to the HTL template
 * as data attributes consumed by review-summary.js at runtime.
 *
 * Resource type: yourproject/components/form/review-summary
 */
@Model(
    adaptables = SlingHttpServletRequest.class,
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
@Exporter(name = ExporterConstants.SLING_MODEL_EXPORTER_NAME,
          extensions = ExporterConstants.SLING_MODEL_EXTENSION)
public class ReviewSummaryModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Comma-separated field names (or Somerset names) to suppress from the review. */
    @ValueMapValue
    private String[] excludedFields;

    /** Whether to render an "Edit" link next to each section heading. Default: true. */
    @ValueMapValue
    private boolean showEditLinks = true;

    /** HTML heading level for section titles: h2, h3, or h4. Default: h3. */
    @ValueMapValue
    private String sectionHeadingTag;

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

        if (StringUtils.isBlank(sectionHeadingTag)) {
            sectionHeadingTag = "h3";
        }
    }

    public String getExcludedFieldsJson() {
        return excludedFieldsJson;
    }

    public boolean isShowEditLinks() {
        return showEditLinks;
    }

    public String getSectionHeadingTag() {
        return sectionHeadingTag;
    }
}
