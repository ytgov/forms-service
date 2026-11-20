package ca.yukon.aem.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
        name = "Yukon - ygDirectory REST endpoint",
        description = "REST endpoint behind the ygDirectory form data model"
)
public @interface DirectoryLookupServletConfig {

    @AttributeDefinition(
            name = "Access key",
            description = "Callers must send this value in the X-YG-Directory-Key header. The ygDirectory FDM cloud "
                    + "configuration sends it. Set it from the Cloud Manager secret YG_DIRECTORY_FDM_KEY; the "
                    + "endpoint refuses every request while it is empty."
    )
    String access_key() default "";
}
