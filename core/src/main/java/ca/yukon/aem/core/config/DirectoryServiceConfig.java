package ca.yukon.aem.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
        name = "Yukon - Directory Service",
        description = "In-memory copy of the YG directory used by the ygDirectory form data model"
)
public @interface DirectoryServiceConfig {

    @AttributeDefinition(
            name = "Refresh interval (minutes)",
            description = "How often each AEM instance downloads the directory again (5 to 1440). If a "
                    + "download fails, the last good copy stays in use. Set per environment with the Cloud "
                    + "Manager variable YG_DIRECTORY_REFRESH_MINUTES."
    )
    int refresh_interval_minutes() default 60;

    @AttributeDefinition(
            name = "Minimum keyword length",
            description = "searchEmployees returns nothing for shorter keywords."
    )
    int search_min_length() default 3;

    @AttributeDefinition(
            name = "Maximum search results",
            description = "Most positions returned by one searchEmployees call (1 to 100). The total number of "
                    + "matches is always returned. Set per environment with the Cloud Manager variable "
                    + "YG_DIRECTORY_SEARCH_MAX_RESULTS."
    )
    int search_max_results() default 10;
}
