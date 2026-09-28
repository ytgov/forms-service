package ca.yukon.aem.core.servlets;

import ca.yukon.aem.core.utils.UserInfoUtils;
import java.io.IOException;
import java.util.List;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.servlet.Servlet;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Returns the current session user's identity, verification attributes and group memberships as
 * JSON. Useful for verifying that SAML profile sync ({@code profile/verificationStatus},
 * {@code profile/verificationProcess}, group memberships) is working as expected. Only ever
 * reflects the requesting user's own session - anonymous requests get anonymous's (empty) info.
 */
@Component(service = {Servlet.class})
@SlingServletPaths(value = "/bin/yukon-forms/userinfo.json")
@ServiceDescription("Outputs the current user's profile info, verification attributes and groups as JSON")
public class UserInfoServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(UserInfoServlet.class);

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JsonObjectBuilder json = Json.createObjectBuilder();

        try {
            Authorizable user = UserInfoUtils.getUser(request.getResourceResolver());

            json.add("username", user.getID());
            addStringOrNull(json, "firstName", getProfileProperty(user, "profile/givenName"));
            addStringOrNull(json, "lastName", getProfileProperty(user, "profile/familyName"));
            addStringOrNull(json, "email", getProfileProperty(user, "profile/email"));
            addStringOrNull(json, "verificationStatus", getProfileProperty(user, "profile/verificationStatus"));
            addStringOrNull(json, "verificationProcess", getProfileProperty(user, "profile/verificationProcess"));

            List<String> groups = UserInfoUtils.listGroups(request.getResourceResolver());
            JsonArrayBuilder groupsArray = Json.createArrayBuilder();
            for (String group : groups) {
                groupsArray.add(group);
            }
            json.add("groups", groupsArray);
        } catch (Exception e) {
            LOGGER.error("Failed to read current user info", e);
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            json = Json.createObjectBuilder().add("error", "Couldn't read current user info");
        }

        response.getWriter().write(json.build().toString());
    }

    private static void addStringOrNull(JsonObjectBuilder json, String key, String value) {
        if (value != null) {
            json.add(key, value);
        } else {
            json.addNull(key);
        }
    }

    private static String getProfileProperty(Authorizable user, String relPath) throws RepositoryException {
        if (!user.hasProperty(relPath)) {
            return null;
        }
        Value[] values = user.getProperty(relPath);
        return values.length > 0 ? values[0].getString() : null;
    }
}
