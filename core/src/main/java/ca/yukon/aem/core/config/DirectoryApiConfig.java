package ca.yukon.aem.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
        name = "Yukon - Directory API Configuration",
        description = "Connection details for the Government of Yukon directory API (departments, divisions, employees)"
)
public @interface DirectoryApiConfig {

    @AttributeDefinition(
            name = "Base URL",
            description = "Directory API base URL, without a trailing slash. Example: https://api.gov.yk.ca/directory"
    )
    String base_url() default "https://api.gov.yk.ca/directory";

    @AttributeDefinition(
            name = "API key",
            description = "Azure API Management subscription key, sent in the Ocp-Apim-Subscription-Key header. "
                    + "Set it from the Cloud Manager secret DIRECTORY_API_KEY; never commit the value."
    )
    String api_key() default "";

    @AttributeDefinition(
            name = "Connect timeout (ms)",
            description = "Time allowed to open a connection to the API."
    )
    int connect_timeout_ms() default 5000;

    @AttributeDefinition(
            name = "Read timeout (ms)",
            description = "Time allowed between packets while reading a response. The employees list is about 4.4 MB."
    )
    int read_timeout_ms() default 30000;
}
