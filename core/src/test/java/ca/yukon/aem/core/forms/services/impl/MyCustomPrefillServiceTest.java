package ca.yukon.aem.core.forms.services.impl;

import com.adobe.forms.common.service.DataOptions;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonObject;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyCustomPrefillServiceTest {

    private final MyCustomPrefillService service = new MyCustomPrefillService();

    private DataOptions options;
    private Authorizable user;

    @BeforeEach
    void setup() throws RepositoryException {
        options = mock(DataOptions.class);
        Resource formResource = mock(Resource.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        JackrabbitSession session = mock(JackrabbitSession.class);
        UserManager userManager = mock(UserManager.class);
        user = mock(Authorizable.class);
        when(options.getFormResource()).thenReturn(formResource);
        when(formResource.getResourceResolver()).thenReturn(resolver);
        when(resolver.adaptTo(Session.class)).thenReturn(session);
        when(session.getUserManager()).thenReturn(userManager);
        when(session.getUserID()).thenReturn("loa2@example.com");
        when(userManager.getAuthorizable("loa2@example.com")).thenReturn(user);
    }

    @Test
    void profileValues_writtenUnderSimpleSubmission() throws Exception {
        Map<String, String> profile = new HashMap<>();
        profile.put("profile/givenName", "Jane \"JJ\"");
        profile.put("profile/familyName", "Ndī");
        profile.put("profile/email", "loa2@example.com");
        profile.put("profile/verificationStatus", "2");
        profile.put("profile/birthDate", "1980-05-17");
        profile.put("profile/fullName", "Loa Two Test");
        profile.put("profile/nickname", "Loa2");
        withProfile(profile);

        JsonObject data = read().getJsonObject("simple_submission");

        assertEquals("Ndī", data.getString("MyYukon_LastName"));
        assertEquals("Jane \"JJ\"", data.getString("MyYukon_FirstName"));
        assertEquals("loa2@example.com", data.getString("MyYukon_Email"));
        assertEquals("2", data.getString("MyYukon_VerificationStatus"));
        assertEquals("1980-05-17", data.getString("MyYukon_Birthdate"));
        assertEquals("Loa Two Test", data.getString("MyYukon_FullName"));
        assertEquals("Loa2", data.getString("MyYukon_Nickname"));
    }

    @Test
    void missingProfileValues_useDefaults() throws Exception {
        withProfile(new HashMap<>());

        JsonObject data = read().getJsonObject("simple_submission");

        assertEquals("Family Name Undefined", data.getString("MyYukon_LastName"));
        assertEquals("Given Name Undefined", data.getString("MyYukon_FirstName"));
        assertEquals("Email Undefined", data.getString("MyYukon_Email"));
        assertEquals("0", data.getString("MyYukon_VerificationStatus"));
        assertEquals("", data.getString("MyYukon_Birthdate"));
        assertEquals("", data.getString("MyYukon_FullName"));
        assertEquals("", data.getString("MyYukon_Nickname"));
    }

    private void withProfile(Map<String, String> profile) throws RepositoryException {
        for (Map.Entry<String, String> entry : profile.entrySet()) {
            Value value = mock(Value.class);
            when(value.getString()).thenReturn(entry.getValue());
            when(user.hasProperty(entry.getKey())).thenReturn(true);
            when(user.getProperty(entry.getKey())).thenReturn(new Value[] {value});
        }
    }

    private JsonObject read() throws Exception {
        String json = new String(service.getPrefillData(options).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return Json.createReader(new StringReader(json)).readObject();
    }
}
