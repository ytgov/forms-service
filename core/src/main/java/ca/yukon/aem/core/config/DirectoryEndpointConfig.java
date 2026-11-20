package ca.yukon.aem.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
        name = "Yukon - ygDirectory FDM endpoint",
        description = "The AEM URL the ygDirectory form data model calls for /bin/yukon-forms/ygdirectory"
)
public @interface DirectoryEndpointConfig {

    @AttributeDefinition(
            name = "Endpoint override",
            description = "Optional, e.g. https://forms.yukon.ca. Empty: this instance's own URL from the "
                    + "Externalizer (author URL on author, publish URL on publish). Set from the Cloud Manager "
                    + "variable YG_DIRECTORY_FDM_ENDPOINT."
    )
    String endpoint() default "";
}
