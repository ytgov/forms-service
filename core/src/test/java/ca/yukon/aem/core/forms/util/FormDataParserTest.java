package ca.yukon.aem.core.forms.util;

import ca.yukon.aem.core.forms.model.Signer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FormDataParserTest {

    @BeforeAll
    static void forceJdkXmlParser() {
        // The AEM SDK test classpath includes an old Xerces that does not implement
        // setFeature(). Force JAXP to use the JDK's built-in parser instead.
        System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
                "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
        System.setProperty("javax.xml.xpath.XPathFactory:http://java.sun.com/jaxp/xpath/dom",
                "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String wrap(String innerXml) {
        return "<data>" + innerXml + "</data>";
    }

    private static String panel(String name, String... fields) {
        StringBuilder sb = new StringBuilder("<").append(name).append(">");
        for (String f : fields) sb.append(f);
        return sb.append("</").append(name).append(">").toString();
    }

    private static String field(String name, String value) {
        return "<" + name + ">" + value + "</" + name + ">";
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    void singleSigner_allFields_parsed() {
        String xml = wrap(panel("signers",
                field("email", "alice@example.com"),
                field("name", "Alice"),
                field("role", "SIGNER"),
                field("signaturePage", "2"),
                field("signatureX", "100"),
                field("signatureY", "200")));

        List<Signer> result = FormDataParser.parseSigners(xml, "signers");

        assertEquals(1, result.size());
        Signer s = result.get(0);
        assertEquals("alice@example.com", s.getEmail());
        assertEquals("Alice", s.getName());
        assertEquals("SIGNER", s.getRole());
        assertEquals(2, s.getSignaturePage());
        assertEquals(100, s.getSignatureX());
        assertEquals(200, s.getSignatureY());
    }

    @Test
    void multipleSigners_allReturned() {
        String xml = wrap(
                panel("signers", field("email", "a@example.com"), field("name", "A")) +
                panel("signers", field("email", "b@example.com"), field("name", "B")) +
                panel("signers", field("email", "c@example.com"), field("name", "C")));

        List<Signer> result = FormDataParser.parseSigners(xml, "signers");

        assertEquals(3, result.size());
        assertEquals("a@example.com", result.get(0).getEmail());
        assertEquals("b@example.com", result.get(1).getEmail());
        assertEquals("c@example.com", result.get(2).getEmail());
    }

    @Test
    void optionalFieldsMissing_signerStillValid() {
        String xml = wrap(panel("signers", field("email", "minimal@example.com")));

        List<Signer> result = FormDataParser.parseSigners(xml, "signers");

        assertEquals(1, result.size());
        Signer s = result.get(0);
        assertEquals("minimal@example.com", s.getEmail());
        assertNull(s.getName());
        assertEquals("SIGNER", s.getRole()); // Signer.getRole() defaults to SIGNER
        assertNull(s.getSignaturePage());
        assertNull(s.getSignatureX());
        assertNull(s.getSignatureY());
    }

    // ------------------------------------------------------------------
    // Filtering
    // ------------------------------------------------------------------

    @Test
    void missingEmailField_entrySkipped() {
        String xml = wrap(panel("signers", field("name", "NoEmail")));

        List<Signer> result = FormDataParser.parseSigners(xml, "signers");

        assertTrue(result.isEmpty());
    }

    @Test
    void emptyEmailField_entrySkipped() {
        String xml = wrap(panel("signers",
                field("email", "   "),
                field("name", "BlankEmail")));

        List<Signer> result = FormDataParser.parseSigners(xml, "signers");

        assertTrue(result.isEmpty());
    }

    @Test
    void mixedValidAndInvalidEntries_onlyValidReturned() {
        String xml = wrap(
                panel("signers", field("email", "good@example.com")) +
                panel("signers", field("name", "NoEmail")) +
                panel("signers", field("email", "  ")));

        List<Signer> result = FormDataParser.parseSigners(xml, "signers");

        assertEquals(1, result.size());
        assertEquals("good@example.com", result.get(0).getEmail());
    }

    // ------------------------------------------------------------------
    // Null / empty guards
    // ------------------------------------------------------------------

    @Test
    void nullXml_returnsEmpty() {
        assertTrue(FormDataParser.parseSigners(null, "signers").isEmpty());
    }

    @Test
    void emptyXml_returnsEmpty() {
        assertTrue(FormDataParser.parseSigners("", "signers").isEmpty());
    }

    @Test
    void nullPanelName_returnsEmpty() {
        assertTrue(FormDataParser.parseSigners("<data/>", null).isEmpty());
    }

    @Test
    void emptyPanelName_returnsEmpty() {
        assertTrue(FormDataParser.parseSigners("<data/>", "").isEmpty());
    }

    @Test
    void panelNameNotFound_returnsEmpty() {
        String xml = wrap(panel("signers", field("email", "a@example.com")));

        assertTrue(FormDataParser.parseSigners(xml, "unknownPanel").isEmpty());
    }

    // ------------------------------------------------------------------
    // Error tolerance
    // ------------------------------------------------------------------

    @Test
    void malformedXml_returnsEmpty() {
        assertTrue(FormDataParser.parseSigners("<unclosed>", "signers").isEmpty());
    }

    @Test
    void xxeDoctype_returnsEmpty() {
        String xml = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>" +
                     "<data><signers><email>&xxe;</email></signers></data>";

        List<Signer> result = FormDataParser.parseSigners(xml, "signers");

        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // Case-insensitive field names
    // ------------------------------------------------------------------

    @Test
    void fieldNamesCaseInsensitive_parsed() {
        String xml = wrap(panel("signers",
                field("EMAIL", "upper@example.com"),
                field("Name", "Mixed")));

        List<Signer> result = FormDataParser.parseSigners(xml, "signers");

        assertEquals(1, result.size());
        assertEquals("upper@example.com", result.get(0).getEmail());
        assertEquals("Mixed", result.get(0).getName());
    }

    // ------------------------------------------------------------------
    // Whitespace trimming
    // ------------------------------------------------------------------

    @Test
    void fieldValuesWithWhitespace_trimmed() {
        String xml = wrap(panel("signers",
                field("email", "  spaces@example.com  "),
                field("name", "\t Tabbed \n")));

        List<Signer> result = FormDataParser.parseSigners(xml, "signers");

        assertEquals(1, result.size());
        assertEquals("spaces@example.com", result.get(0).getEmail());
        assertEquals("Tabbed", result.get(0).getName());
    }

    // ------------------------------------------------------------------
    // Signature coordinates
    // ------------------------------------------------------------------

    @Test
    void invalidIntegerCoordinates_treatedAsNull() {
        String xml = wrap(panel("signers",
                field("email", "coord@example.com"),
                field("signaturePage", "abc"),
                field("signatureX", "1.5"),
                field("signatureY", "")));

        List<Signer> result = FormDataParser.parseSigners(xml, "signers");

        assertEquals(1, result.size());
        Signer s = result.get(0);
        assertNull(s.getSignaturePage());
        assertNull(s.getSignatureX());
        assertNull(s.getSignatureY());
    }

    // ------------------------------------------------------------------
    // Custom SignerFieldMapping
    // ------------------------------------------------------------------

    @Test
    void customMapping_usesCustomFieldNames() {
        SignerFieldMapping mapping = new SignerFieldMapping(
                "petitionerEmail", "petitionerName", "petitionerRole",
                "page", "xPos", "yPos");
        String xml = wrap(panel("person",
                field("petitionerEmail", "custom@example.com"),
                field("petitionerName", "Custom"),
                field("petitionerRole", "APPROVER"),
                field("page", "3"),
                field("xPos", "50"),
                field("yPos", "150")));

        List<Signer> result = FormDataParser.parseSigners(xml, "person", mapping);

        assertEquals(1, result.size());
        Signer s = result.get(0);
        assertEquals("custom@example.com", s.getEmail());
        assertEquals("Custom", s.getName());
        assertEquals("APPROVER", s.getRole());
        assertEquals(3, s.getSignaturePage());
        assertEquals(50, s.getSignatureX());
        assertEquals(150, s.getSignatureY());
    }

    @Test
    void nullMapping_fallsBackToDefault() {
        String xml = wrap(panel("signers", field("email", "default@example.com")));

        List<Signer> result = FormDataParser.parseSigners(xml, "signers", null);

        assertEquals(1, result.size());
        assertEquals("default@example.com", result.get(0).getEmail());
    }

    // ------------------------------------------------------------------
    // InputStream overload
    // ------------------------------------------------------------------

    @Test
    void inputStreamOverload_parsed() {
        String xml = wrap(panel("signers", field("email", "stream@example.com")));
        ByteArrayInputStream stream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        List<Signer> result = FormDataParser.parseSigners(stream, "signers", SignerFieldMapping.DEFAULT);

        assertEquals(1, result.size());
        assertEquals("stream@example.com", result.get(0).getEmail());
    }

    // ------------------------------------------------------------------
    // Nested structure
    // ------------------------------------------------------------------

    @Test
    void panelNestedInsideOtherElement_stillFound() {
        String xml = wrap("<section><group>" +
                panel("signers", field("email", "nested@example.com")) +
                "</group></section>");

        List<Signer> result = FormDataParser.parseSigners(xml, "signers");

        assertEquals(1, result.size());
        assertEquals("nested@example.com", result.get(0).getEmail());
    }
}
