package ca.yukon.aem.core.forms.services.impl;

import com.adobe.forms.common.service.ContentType;
import com.adobe.forms.common.service.DataOptions;
import com.adobe.forms.common.service.PrefillData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrefillLoggedinUserAdaptiveFormTest {

    private static final String EMPTY_DATA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><data/>";

    private final PrefillLoggedinUserAdaptiveForm service = new PrefillLoggedinUserAdaptiveForm();

    private static final String EMPTY_JSON = "{\"afData\":{\"afUnboundData\":{\"data\":{}},\"afBoundData\":{\"data\":{}}}}";

    private DataOptions options;
    private Resource formResource;
    private ResourceResolver resolver;
    private JackrabbitSession session;
    private UserManager userManager;

    @BeforeEach
    void setup() throws RepositoryException {
        options = mock(DataOptions.class);
        formResource = mock(Resource.class);
        resolver = mock(ResourceResolver.class);
        session = mock(JackrabbitSession.class);
        userManager = mock(UserManager.class);
        when(options.getFormResource()).thenReturn(formResource);
        when(formResource.getResourceResolver()).thenReturn(resolver);
        when(resolver.adaptTo(Session.class)).thenReturn(session);
        when(session.getUserManager()).thenReturn(userManager);
        when(options.getContentType()).thenReturn(ContentType.XML);
    }

    @Test
    void signedInUser_profileFieldsPrefilled() throws Exception {
        Map<String, String> profile = new HashMap<>();
        profile.put("profile/givenName", "Jane");
        profile.put("profile/familyName", "Doe & Co");
        profile.put("profile/email", "jane@example.com");
        profile.put("profile/verificationStatus", "2");
        withUser("jane@example.com", profile);

        String xml = read(service.getPrefillData(options));

        assertTrue(xml.contains("<MyYukon_FirstName>Jane</MyYukon_FirstName>"), xml);
        assertTrue(xml.contains("<MyYukon_LastName>Doe &amp; Co</MyYukon_LastName>"), xml);
        assertTrue(xml.contains("<MyYukon_Email>jane@example.com</MyYukon_Email>"), xml);
        assertTrue(xml.contains("<MyYukon_VerificationStatus>2</MyYukon_VerificationStatus>"), xml);
    }

    @Test
    void birthdate_prefilledAsDateFieldValue() throws Exception {
        Map<String, String> profile = new HashMap<>();
        profile.put("profile/birthDate", "1980-05-17T00:00:00Z");
        withUser("jane@example.com", profile);

        String xml = read(service.getPrefillData(options));

        assertTrue(xml.contains("<MyYukon_Birthdate>1980-05-17</MyYukon_Birthdate>"), xml);
    }

    @Test
    void fullNameAndNickname_prefilled() throws Exception {
        Map<String, String> profile = new HashMap<>();
        profile.put("profile/fullName", "Shèhtsō Ndī");
        profile.put("profile/nickname", "Loa2");
        withUser("loa2@example.com", profile);

        String xml = read(service.getPrefillData(options));

        assertTrue(xml.contains("<MyYukon_FullName>Shèhtsō Ndī</MyYukon_FullName>"), xml);
        assertTrue(xml.contains("<MyYukon_Nickname>Loa2</MyYukon_Nickname>"), xml);
    }

    @Test
    void toDateFieldValue_keepsIsoDatesAndPassesOthersThrough() {
        assertEquals("1980-05-17", PrefillLoggedinUserAdaptiveForm.toDateFieldValue("1980-05-17"));
        assertEquals("1980-05-17", PrefillLoggedinUserAdaptiveForm.toDateFieldValue("1980-05-17T08:30:00-07:00"));
        assertEquals("1980-05-17", PrefillLoggedinUserAdaptiveForm.toDateFieldValue(" 1980-05-17 "));
        assertEquals("17/05/1980", PrefillLoggedinUserAdaptiveForm.toDateFieldValue("17/05/1980"));
    }

    @Test
    void missingProfileProperties_leftOut() throws Exception {
        Map<String, String> profile = new HashMap<>();
        profile.put("profile/givenName", "Jane");
        withUser("jane@example.com", profile);

        String xml = read(service.getPrefillData(options));

        assertTrue(xml.contains("<MyYukon_FirstName>Jane</MyYukon_FirstName>"), xml);
        assertTrue(!xml.contains("<MyYukon_VerificationStatus>"), xml);
    }

    @Test
    void anonymous_returnsEmptyData() throws Exception {
        when(session.getUserID()).thenReturn("anonymous");

        assertEquals(EMPTY_DATA, read(service.getPrefillData(options)));
        verify(userManager, never()).getAuthorizable(anyString());
    }

    @Test
    void unreadableUserNode_returnsEmptyDataInsteadOfNull() throws Exception {
        // What used to hang the form: getAuthorizable() returns null when the session can't read
        // its own user node, and the old code threw an NPE and returned null.
        when(session.getUserID()).thenReturn("someone");
        when(userManager.getAuthorizable("someone")).thenReturn(null);

        assertEquals(EMPTY_DATA, read(service.getPrefillData(options)));
    }

    @Test
    void repositoryError_returnsEmptyDataInsteadOfNull() throws Exception {
        when(session.getUserID()).thenReturn("someone");
        when(userManager.getAuthorizable("someone")).thenThrow(new RepositoryException("boom"));

        assertEquals(EMPTY_DATA, read(service.getPrefillData(options)));
    }

    @Test
    void jsonForm_profileFieldsPrefilledAsBoundJson() throws Exception {
        when(options.getContentType()).thenReturn(ContentType.JSON);
        Map<String, String> profile = new HashMap<>();
        profile.put("profile/givenName", "Jane \"JJ\"");
        profile.put("profile/verificationStatus", "2");
        withUser("jane@example.com", profile);

        PrefillData data = service.getPrefillData(options);

        assertEquals(ContentType.JSON, data.getContentType());
        String fields = "{\"MyYukon_FirstName\":\"Jane \\\"JJ\\\"\",\"MyYukon_VerificationStatus\":\"2\"}";
        assertEquals("{\"afData\":{\"afUnboundData\":{\"data\":" + fields + "},\"afBoundData\":{\"data\":" + fields + "}}}",
                read(data));
    }

    @Test
    void jsonForm_anonymous_returnsEmptyJson() throws Exception {
        when(options.getContentType()).thenReturn(ContentType.JSON);
        when(session.getUserID()).thenReturn("anonymous");

        assertEquals(EMPTY_JSON, read(service.getPrefillData(options)));
    }

    @Test
    void jsonForm_repositoryError_returnsEmptyJson() throws Exception {
        when(options.getContentType()).thenReturn(ContentType.JSON);
        when(session.getUserID()).thenReturn("someone");
        when(userManager.getAuthorizable("someone")).thenThrow(new RepositoryException("boom"));

        assertEquals(EMPTY_JSON, read(service.getPrefillData(options)));
    }

    @Test
    void resolveContentType_fallsBackToSchemaType() {
        when(options.getContentType()).thenReturn(null);
        Resource guideContainer = mock(Resource.class);
        when(formResource.getValueMap()).thenReturn(ValueMap.EMPTY);
        when(formResource.getChild("jcr:content/guideContainer")).thenReturn(guideContainer);

        when(guideContainer.getValueMap()).thenReturn(schemaType("formdatamodel"));
        assertEquals(ContentType.JSON, PrefillLoggedinUserAdaptiveForm.resolveContentType(options));

        when(guideContainer.getValueMap()).thenReturn(schemaType("jsonschema"));
        assertEquals(ContentType.JSON, PrefillLoggedinUserAdaptiveForm.resolveContentType(options));

        when(guideContainer.getValueMap()).thenReturn(schemaType("xmlschema"));
        assertEquals(ContentType.XML, PrefillLoggedinUserAdaptiveForm.resolveContentType(options));

        when(guideContainer.getValueMap()).thenReturn(ValueMap.EMPTY);
        assertEquals(ContentType.XML, PrefillLoggedinUserAdaptiveForm.resolveContentType(options));
    }

    private static ValueMap schemaType(String schemaType) {
        Map<String, Object> props = new HashMap<>();
        props.put("schemaType", schemaType);
        return new ValueMapDecorator(props);
    }

    private void withUser(String userId, Map<String, String> profile) throws RepositoryException {
        Authorizable user = mock(Authorizable.class);
        when(session.getUserID()).thenReturn(userId);
        when(userManager.getAuthorizable(userId)).thenReturn(user);
        when(user.getPath()).thenReturn("/home/users/yukon/sso/" + userId);
        for (Map.Entry<String, String> entry : profile.entrySet()) {
            Value value = mock(Value.class);
            when(value.getString()).thenReturn(entry.getValue());
            when(user.hasProperty(entry.getKey())).thenReturn(true);
            when(user.getProperty(entry.getKey())).thenReturn(new Value[] {value});
        }
    }

    private static String read(PrefillData data) throws IOException {
        assertNotNull(data);
        InputStream stream = data.getInputStream();
        assertNotNull(stream);
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
