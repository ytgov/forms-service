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
                    + "level required by the form's folder. {lang} is replaced with the form's language, "
                    + "e.g. /content/yukon-forms/ca/{lang}/verification-required.html."
    )
    String redirect_page() default "/content/yukon-forms/ca/{lang}/verification-required.html";

    @AttributeDefinition(
            name = "Supported Languages",
            description = "Languages that have a redirect page. A form in any other language uses the first "
                    + "one."
    )
    String[] supported_languages() default {"en", "fr"};
}
