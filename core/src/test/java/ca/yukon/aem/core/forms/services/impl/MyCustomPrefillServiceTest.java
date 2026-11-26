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
import org.apache.sling.api.wrappers.ValueMapDecorator;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
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
        // No schema type: an XML-based form, which keeps the simple_submission data.
        when(formResource.getValueMap()).thenReturn(new ValueMapDecorator(new HashMap<>()));
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
        profile.put("profile/yukon/verificationStatus", "2");
        profile.put("profile/yukon/birthDate", "1980-05-17");
        profile.put("profile/yukon/fullName", "Loa Two Test");
        profile.put("profile/yukon/nickname", "Loa2");
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

    @Test
    void jsonForm_valuesByNameAndAtBindRefs() throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("MyYukon_FirstName", "Jane");
        fields.put("MyYukon_LastName", "Ndī");
        fields.put("MyYukon_Email", "loa2@example.com");
        Map<String, String> bindRefs = new LinkedHashMap<>();
        bindRefs.put("MyYukon_FirstName", "/MyYukonAdobeSignDemo/firstName");
        bindRefs.put("MyYukon_LastName", "/MyYukonAdobeSignDemo/name/last");

        JsonObject afData = Json.createReader(new StringReader(MyCustomPrefillService.toAfData(fields, bindRefs)))
                .readObject().getJsonObject("afData");

        JsonObject unbound = afData.getJsonObject("afUnboundData").getJsonObject("data");
        assertEquals("Jane", unbound.getString("MyYukon_FirstName"));
        assertEquals("loa2@example.com", unbound.getString("MyYukon_Email"));
        JsonObject demo = afData.getJsonObject("afBoundData").getJsonObject("data").getJsonObject("MyYukonAdobeSignDemo");
        assertEquals("Jane", demo.getString("firstName"));
        assertEquals("Ndī", demo.getJsonObject("name").getString("last"));
        // Unbound fields don't appear in the bound data.
        assertEquals(2, demo.size());
    }

    @Test
    void bindRefs_foundInFormAndFragments(AemContext context) {
        context.create().resource("/content/forms/af/demo/jcr:content/guideContainer/rootPanel/items/first",
                "name", "MyYukon_FirstName", "bindRef", "/MyYukonAdobeSignDemo/firstName");
        context.create().resource("/content/forms/af/demo/jcr:content/guideContainer/rootPanel/items/email",
                "name", "MyYukon_Email");
        context.create().resource("/content/forms/af/demo/jcr:content/guideContainer/rootPanel/items/other",
                "name", "somethingElse", "bindRef", "/MyYukonAdobeSignDemo/other");
        context.create().resource("/content/forms/af/demo/jcr:content/guideContainer/rootPanel/items/frag",
                "fragRef", "/content/dam/formsanddocuments/frags/profile");
        context.create().resource("/content/forms/af/frags/profile/jcr:content/guideContainer/rootPanel/items/last",
                "name", "MyYukon_LastName", "bindRef", "/MyYukonAdobeSignDemo/lastName");

        Set<String> names = new TreeSet<>(Set.of("MyYukon_FirstName", "MyYukon_LastName", "MyYukon_Email"));
        Map<String, String> bindRefs = new LinkedHashMap<>();
        MyCustomPrefillService.collectBindRefs(
                context.resourceResolver().getResource("/content/forms/af/demo/jcr:content/guideContainer"),
                names, bindRefs, new java.util.HashSet<>());

        assertEquals("/MyYukonAdobeSignDemo/firstName", bindRefs.get("MyYukon_FirstName"));
        assertEquals("/MyYukonAdobeSignDemo/lastName", bindRefs.get("MyYukon_LastName"));
        assertFalse(bindRefs.containsKey("MyYukon_Email"));
        assertEquals(2, bindRefs.size());
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
