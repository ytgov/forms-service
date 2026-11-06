package ca.yukon.aem.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
        name = "Yukon Forms - Verification Status Gate Filter Configuration",
        description = "Gates access to yukon-forms content based on the requesting user's synced "
                + "verification_status SAML attribute against the required_verification_status property "
                + "set on the form's folder."
)
public @interface VerificationStatusGateConfig {

    @AttributeDefinition(
            name = "Redirect Page",
            description = "Page users are redirected to when their verification status is below the "
                    + "level required by the form's folder. {lang} is replaced with the language from the form's language tag; "
                    + "an untagged form embedded in a Sites page uses the page's language, and any other uses the first "
                    + "supported language, "
                    + "e.g. /content/yukon-forms/ca/{lang}/verification-required.html."
    )
    String redirect_page() default "/content/yukon-forms/ca/{lang}/verification-required.html";

    @AttributeDefinition(
            name = "Supported Languages",
            description = "Languages that have a redirect page. A form tagged language:<code> with one of these "
                    + "goes to that language's page."
    )
    String[] supported_languages() default {"en", "fr"};
}
