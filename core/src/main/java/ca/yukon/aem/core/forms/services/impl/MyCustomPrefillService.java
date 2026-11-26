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
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Prefills the signed-in user's synced profile into the {@code MyYukon_*} fields.
 *
 * <p>For JSON-based forms (JSON schema, form data model) each value is returned twice: by field name in
 * {@code afUnboundData}, for unbound fields, and at the field's bind reference in {@code afBoundData}, for
 * bound fields. A bound field (e.g. {@code MyYukon_FirstName} bound to {@code /MyYukonAdobeSignDemo/firstName})
 * is filled from its bind reference only and ignores {@code afUnboundData}, so without the second copy it
 * would stay empty. The bind references are read from the form's {@code MyYukon_*} fields, including those in
 * fragments.
 *
 * <p>Other forms get the {@code simple_submission} data as before.
 */
@Component
public class MyCustomPrefillService implements DataProvider {

    private static final String GUIDE_CONTAINER = "jcr:content/guideContainer";
    private static final String FRAGMENT_DAM_ROOT = "/content/dam/formsanddocuments/";
    private static final String FRAGMENT_FORMS_ROOT = "/content/forms/af/";

    private Logger logger = LoggerFactory.getLogger(MyCustomPrefillService.class);

    public String getServiceName() {
        return "My Custom Prefill Service Name";
    }

    public String getServiceDescription() {
        return "My Custom Prefill Service";
    }

    public PrefillData getPrefillData(final DataOptions dataOptions) throws FormsException {
        Map<String, String> fields = readFields(dataOptions);
        ContentType contentType = PrefillLoggedinUserAdaptiveForm.resolveContentType(dataOptions);
        String payload;
        if (contentType == ContentType.JSON) {
            payload = toAfData(fields, findBindRefs(dataOptions, fields.keySet()));
        } else {
            JsonObjectBuilder data = Json.createObjectBuilder();
            fields.forEach(data::add);
            payload = Json.createObjectBuilder().add("simple_submission", data).build().toString();
            // Unchanged from before: this has always been labelled XML.
            contentType = ContentType.XML;
        }
        return new PrefillData(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)), contentType);
    }

    /** Prefill field name -> profile value, or its default when the profile has none. */
    private Map<String, String> readFields(DataOptions dataOptions) throws FormsException {
        try {
            Resource aemFormContainer = dataOptions.getFormResource();
            ResourceResolver resolver = aemFormContainer.getResourceResolver();
            Session session = resolver.adaptTo(Session.class);
            UserManager um = ((JackrabbitSession) session).getUserManager();
            Authorizable loggedinUser = um.getAuthorizable(session.getUserID());

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("MyYukon_FirstName", profileValue(loggedinUser, "profile/givenName", "Given Name Undefined"));
            fields.put("MyYukon_LastName", profileValue(loggedinUser, "profile/familyName", "Family Name Undefined"));
            fields.put("MyYukon_Email", profileValue(loggedinUser, "profile/email", "Email Undefined"));
            // Same default as VerificationStatusGateFilter: no synced status means unverified.
            fields.put("MyYukon_VerificationStatus", profileValue(loggedinUser, "profile/yukon/verificationStatus", "0"));
            fields.put("MyYukon_Birthdate", profileValue(loggedinUser, "profile/yukon/birthDate", ""));
            fields.put("MyYukon_FullName", profileValue(loggedinUser, "profile/yukon/fullName", ""));
            fields.put("MyYukon_Nickname", profileValue(loggedinUser, "profile/yukon/nickname", ""));
            return fields;
        } catch (Exception e) {
            logger.error("Error while creating prefill data", e);
            throw new FormsException(e);
        }
    }

    /**
     * Field name -> bind reference of the form's {@code MyYukon_*} fields that are bound. Reading the form must
     * never stop the prefill: on an error, unbound prefill still works.
     */
    private Map<String, String> findBindRefs(DataOptions dataOptions, Set<String> fieldNames) {
        Map<String, String> bindRefs = new LinkedHashMap<>();
        try {
            Resource container = formContainer(dataOptions.getFormResource());
            if (container != null) {
                collectBindRefs(container, fieldNames, bindRefs, new HashSet<>());
            }
        } catch (Exception e) {
            logger.warn("Couldn't read the form's bind references, prefilling unbound fields only", e);
        }
        return bindRefs;
    }

    private static Resource formContainer(Resource formResource) {
        if (formResource == null) {
            return null;
        }
        Resource container = formResource.getChild(GUIDE_CONTAINER);
        return container != null ? container : formResource;
    }

    static void collectBindRefs(Resource resource, Set<String> fieldNames, Map<String, String> bindRefs,
                                Set<String> visited) {
        if (!visited.add(resource.getPath())) {
            return;
        }
        String name = resource.getValueMap().get("name", String.class);
        String bindRef = resource.getValueMap().get("bindRef", String.class);
        if (name != null && fieldNames.contains(name) && bindRef != null && !bindRef.trim().isEmpty()) {
            bindRefs.putIfAbsent(name, bindRef.trim());
        }
        // A fragment's fields live in the fragment's own form.
        String fragRef = resource.getValueMap().get("fragRef", String.class);
        if (fragRef != null && fragRef.startsWith(FRAGMENT_DAM_ROOT)) {
            Resource fragment = resource.getResourceResolver().getResource(
                    FRAGMENT_FORMS_ROOT + fragRef.substring(FRAGMENT_DAM_ROOT.length()) + "/" + GUIDE_CONTAINER);
            if (fragment != null) {
                collectBindRefs(fragment, fieldNames, bindRefs, visited);
            }
        }
        for (Resource child : resource.getChildren()) {
            collectBindRefs(child, fieldNames, bindRefs, visited);
        }
    }

    /**
     * {@code afData} with every value by name in {@code afUnboundData} and, for bound fields, at the bind
     * reference in {@code afBoundData}: {@code /MyYukonAdobeSignDemo/firstName} becomes
     * {@code {"MyYukonAdobeSignDemo": {"firstName": value}}}.
     */
    static String toAfData(Map<String, String> fields, Map<String, String> bindRefs) {
        JsonObjectBuilder unbound = Json.createObjectBuilder();
        fields.forEach(unbound::add);

        Map<String, Object> bound = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : bindRefs.entrySet()) {
            String value = fields.get(entry.getKey());
            if (value != null) {
                putAtPath(bound, entry.getValue(), value);
            }
        }
        return Json.createObjectBuilder()
                .add("afData", Json.createObjectBuilder()
                        .add("afUnboundData", Json.createObjectBuilder().add("data", unbound))
                        .add("afBoundData", Json.createObjectBuilder().add("data", toJson(bound))))
                .build()
                .toString();
    }

    @SuppressWarnings("unchecked")
    private static void putAtPath(Map<String, Object> root, String bindRef, String value) {
        String[] parts = bindRef.replaceAll("^/+", "").split("/");
        Map<String, Object> cursor = root;
        for (int i = 0; i < parts.length; i++) {
            String key = parts[i];
            if (key.isEmpty()) {
                return;
            }
            if (i == parts.length - 1) {
                cursor.put(key, value);
            } else {
                Object next = cursor.get(key);
                if (!(next instanceof Map)) {
                    next = new LinkedHashMap<String, Object>();
                    cursor.put(key, next);
                }
                cursor = (Map<String, Object>) next;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static JsonObject toJson(Map<String, Object> map) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            JsonValue json = value instanceof Map
                    ? toJson((Map<String, Object>) value)
                    : Json.createValue((String) value);
            builder.add(entry.getKey(), json);
        }
        return builder.build();
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
