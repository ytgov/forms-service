package ca.yukon.aem.core.forms.services.impl;

import com.adobe.forms.common.service.ContentType;
import com.adobe.forms.common.service.DataOptions;
import com.adobe.forms.common.service.DataProvider;
import com.adobe.forms.common.service.FormsException;
import com.adobe.forms.common.service.PrefillData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Prefills the signed-in user's synced SAML profile ({@code fname}, {@code lname}, {@code email},
 * {@code verificationStatus}, {@code birthdate}, {@code fullName}, {@code nickname}). Returns JSON ({@code afData.afBoundData.data}) to JSON-based forms (JSON schema,
 * form data model) and XML ({@code <data>}) to XML-based ones (no schema, XSD) - handing XML to a JSON-based form
 * makes it fail to parse the data and hang.
 */
@Component(service = DataProvider.class)
public class PrefillLoggedinUserAdaptiveForm implements DataProvider {
    private static final Logger log = LoggerFactory.getLogger(PrefillLoggedinUserAdaptiveForm.class);

    /** Profile property -> prefill data field name. */
    private static final Map<String, String> PROFILE_FIELDS = new LinkedHashMap<>();

    static {
        PROFILE_FIELDS.put("profile/givenName", "fname");
        PROFILE_FIELDS.put("profile/familyName", "lname");
        PROFILE_FIELDS.put("profile/email", "email");
        PROFILE_FIELDS.put("profile/verificationStatus", "verificationStatus");
        PROFILE_FIELDS.put("profile/birthDate", "birthdate");
        PROFILE_FIELDS.put("profile/fullName", "fullName");
        PROFILE_FIELDS.put("profile/nickname", "nickname");
    }

    /** Leading yyyy-MM-dd of an ISO date or date-time, e.g. "1980-05-17" or "1980-05-17T00:00:00Z". */
    private static final Pattern ISO_DATE_PREFIX = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})(T.*)?$");

    @Override
    public String getServiceDescription() {
        return "Custom AEM Forms PreFill Service";
    }

    @Override
    public String getServiceName() {
        return "CustomAemFormsPrefillService";
    }

    @Override
    public PrefillData getPrefillData(DataOptions dataOptions) throws FormsException {
        ContentType contentType = resolveContentType(dataOptions);
        Map<String, String> fields;
        try {
            fields = readProfileFields(dataOptions);
        } catch (Exception e) {
            // Never fail the request: the form waits for prefill data and hangs on its loading screen without it.
            log.error("Couldn't read the user's profile, returning empty prefill data", e);
            fields = Collections.emptyMap();
        }
        try {
            byte[] bytes = contentType == ContentType.JSON ? toJson(fields) : toXml(fields);
            return new PrefillData(new ByteArrayInputStream(bytes), contentType);
        } catch (Exception e) {
            log.error("Couldn't build prefill data, returning empty prefill data", e);
            return new PrefillData(new ByteArrayInputStream(emptyData(contentType)), contentType);
        }
    }

    /**
     * The format the form expects, as reported by AEM Forms; falls back to the form container's schema type if
     * AEM Forms doesn't say.
     */
    static ContentType resolveContentType(DataOptions dataOptions) {
        if (dataOptions.getContentType() != null) {
            return dataOptions.getContentType();
        }
        Resource formResource = dataOptions.getFormResource();
        String schemaType = null;
        if (formResource != null) {
            schemaType = formResource.getValueMap().get("schemaType", String.class);
            Resource guideContainer = formResource.getChild("jcr:content/guideContainer");
            if (schemaType == null && guideContainer != null) {
                schemaType = guideContainer.getValueMap().get("schemaType", String.class);
            }
        }
        return "jsonschema".equals(schemaType) || "formdatamodel".equals(schemaType)
                ? ContentType.JSON
                : ContentType.XML;
    }

    /**
     * The user's profile values keyed by prefill field name - empty for anonymous requests and for sessions that
     * can't read their own user node (e.g. anonymous on publish).
     */
    private static Map<String, String> readProfileFields(DataOptions dataOptions) throws RepositoryException {
        Map<String, String> fields = new LinkedHashMap<>();
        Authorizable user = getLoggedinUser(dataOptions);
        if (user == null) {
            log.debug("No signed-in user whose profile can be read, returning empty prefill data");
            return fields;
        }
        for (Map.Entry<String, String> field : PROFILE_FIELDS.entrySet()) {
            if (user.hasProperty(field.getKey())) {
                Value[] values = user.getProperty(field.getKey());
                if (values != null && values.length > 0) {
                    String value = values[0].getString();
                    fields.put(field.getValue(), "birthdate".equals(field.getValue()) ? toDateFieldValue(value) : value);
                }
            }
        }
        return fields;
    }

    /**
     * Date fields take yyyy-MM-dd, so an ISO date-time is cut down to its date part. Anything else is passed
     * through unchanged (and won't fill a Date Picker field).
     */
    static String toDateFieldValue(String value) {
        Matcher matcher = ISO_DATE_PREFIX.matcher(value.trim());
        if (matcher.matches()) {
            return matcher.group(1);
        }
        log.debug("Birthdate '{}' isn't an ISO date, passing it through unchanged", value);
        return value;
    }

    private static Authorizable getLoggedinUser(DataOptions dataOptions) throws RepositoryException {
        ResourceResolver resolver = dataOptions.getFormResource().getResourceResolver();
        Session session = resolver.adaptTo(Session.class);
        if (!(session instanceof JackrabbitSession)) {
            return null;
        }
        String userId = session.getUserID();
        if (userId == null || "anonymous".equals(userId)) {
            return null;
        }
        UserManager um = ((JackrabbitSession) session).getUserManager();
        return um.getAuthorizable(userId);
    }

    /**
     * The same values go into both halves: {@code afUnboundData} fills unbound fields by their name, and
     * {@code afBoundData} fills fields bound to top-level model properties with these names (fields bound deeper
     * into a form data model, e.g. {@code /Entity/fname}, aren't matched).
     */
    private static byte[] toJson(Map<String, String> fields) {
        JsonObjectBuilder data = Json.createObjectBuilder();
        fields.forEach(data::add);
        JsonObjectBuilder unboundData = Json.createObjectBuilder();
        fields.forEach(unboundData::add);
        String payload = Json.createObjectBuilder()
                .add("afData", Json.createObjectBuilder()
                        .add("afUnboundData", Json.createObjectBuilder().add("data", unboundData))
                        .add("afBoundData", Json.createObjectBuilder().add("data", data)))
                .build()
                .toString();
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] toXml(Map<String, String> fields) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element rootElement = doc.createElement("data");
        doc.appendChild(rootElement);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            Element element = doc.createElement(field.getKey());
            element.setTextContent(field.getValue());
            rootElement.appendChild(element);
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(outputStream));
        return outputStream.toByteArray();
    }

    private static byte[] emptyData(ContentType contentType) {
        String empty = contentType == ContentType.JSON
                ? "{\"afData\":{\"afUnboundData\":{\"data\":{}},\"afBoundData\":{\"data\":{}}}}"
                : "<?xml version=\"1.0\" encoding=\"UTF-8\"?><data/>";
        return empty.getBytes(StandardCharsets.UTF_8);
    }
}
