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

    /** Field names (Somerset/API) to suppress from the validation summary. */
    @ValueMapValue
    private String[] excludedFields;

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
}
