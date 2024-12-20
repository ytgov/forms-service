package ca.yukon.aem.core.forms.services.impl;

import com.adobe.forms.common.service.*;
import com.google.gson.Gson;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

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

            String givenName = "Given Name Undefined";
            String familyName = "Family Name Undefined";
            String email = "Email Undefined";

            if (loggedinUser.hasProperty("profile/givenName")) {
                givenName = loggedinUser.getProperty("profile/givenName")[0].getString();
            }
            if (loggedinUser.hasProperty("profile/familyName")) {
                familyName = loggedinUser.getProperty("profile/familyName")[0].getString();
            }
            if (loggedinUser.hasProperty("profile/email")) {
                email = loggedinUser.getProperty("profile/email")[0].getString();
            }

            Gson gson = new Gson();
            String jsonStr = "{\n  \"simple_submission\": {\n    \"Title\": \"" +familyName+" \",\n    \"Name\": \""+givenName+"\",\n    \"Email\": \""+email+ "\"\n  }\n}";
            HashMap myPojo = gson.fromJson(jsonStr, HashMap.class);
            String outputStr = gson.toJson(myPojo);

            InputStream inputStream = new ByteArrayInputStream(outputStr.getBytes(StandardCharsets.UTF_8));
            return inputStream;

        } catch (Exception e) {
            logger.error("Error while creating prefill data", e);
            throw new FormsException(e);
        }
    }
}

