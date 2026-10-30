package ca.yukon.aem.core.forms.services.impl;

import com.adobe.forms.common.service.*;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class MyCustomPrefillService implements DataProvider {

    private Logger logger = LoggerFactory.getLogger(MyCustomPrefillService.class);

    public String getServiceName() {
        return "My Custom Prefill Service Name";
    }

    public String getServiceDescription() {
        return "My Custom Prefill Service";
    }

    public PrefillData getPrefillData(final DataOptions dataOptions) throws FormsException {
        return new PrefillData() {
            public InputStream getInputStream() {
                return getData(dataOptions);
            }

            public ContentType getContentType() {
                return ContentType.XML;
            }
        };
    }

    private InputStream getData(DataOptions dataOptions) throws FormsException {
        try {
            Resource aemFormContainer = dataOptions.getFormResource();
            ResourceResolver resolver = aemFormContainer.getResourceResolver();
            Session session = resolver.adaptTo(Session.class);
            UserManager um = ((JackrabbitSession) session).getUserManager();
            Authorizable loggedinUser = um.getAuthorizable(session.getUserID());

            JsonObjectBuilder fields = Json.createObjectBuilder()
                    .add("MyYukon_FirstName", profileValue(loggedinUser, "profile/givenName", "Given Name Undefined"))
                    .add("MyYukon_LastName", profileValue(loggedinUser, "profile/familyName", "Family Name Undefined"))
                    .add("MyYukon_Email", profileValue(loggedinUser, "profile/email", "Email Undefined"))
                    // Same default as VerificationStatusGateFilter: no synced status means unverified.
                    .add("MyYukon_VerificationStatus", profileValue(loggedinUser, "profile/verificationStatus", "0"))
                    .add("MyYukon_Birthdate", profileValue(loggedinUser, "profile/birthDate", ""))
                    .add("MyYukon_FullName", profileValue(loggedinUser, "profile/fullName", ""))
                    .add("MyYukon_Nickname", profileValue(loggedinUser, "profile/nickname", ""));
            String json = Json.createObjectBuilder().add("simple_submission", fields).build().toString();

            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            logger.error("Error while creating prefill data", e);
            throw new FormsException(e);
        }
    }

    private static String profileValue(Authorizable user, String property, String defaultValue)
            throws RepositoryException {
        if (user.hasProperty(property)) {
            Value[] values = user.getProperty(property);
            if (values != null && values.length > 0) {
                return values[0].getString();
            }
        }
        return defaultValue;
    }
}

