package ca.yukon.aem.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
        name = "Yukon - Canada Post Api Configuration",
        description = "Canada Post Api Configuration details"
)
public @interface CanadaPostApiConfig {
    @AttributeDefinition(
            name = "Api Key",
            description = "Api key for Canada Post API."
    )
    String canada_post_key() default "";

    @AttributeDefinition(
            name = "Host Url",
            description = "Host url for Canada Post API."
    )
    String canada_post_host() default "";
}
