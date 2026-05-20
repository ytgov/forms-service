package ca.yukon.aem.core.forms.util;

import ca.yukon.aem.core.forms.model.Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses Adaptive Form submission data (XML) and extracts a list of
 * {@link Signer}s from a repeatable panel.
 *
 * <p>The parser looks for repeating elements whose tag name matches
 * {@code panelName} (e.g. {@code personsRepeat}). Inside each repeated
 * element it reads child elements whose names are configured via the
 * supplied {@link SignerFieldMapping}. Entries missing the email field
 * are skipped.</p>
 *
 * <p>Designed to be tolerant: child names are case-insensitive, missing
 * non-email children are simply omitted from the resulting Signer.</p>
 */
public final class FormDataParser {

    private static final Logger LOG = LoggerFactory.getLogger(FormDataParser.class);

    private FormDataParser() {
    }

    /**
     * Parse signers using the default field mapping (email/name/role/...).
     */
    public static List<Signer> parseSigners(String xml, String panelName) {
        return parseSigners(xml, panelName, SignerFieldMapping.DEFAULT);
    }

    /**
     * Parse signers using a custom {@link SignerFieldMapping}.
     *
     * @param xml       submitted form data XML content
     * @param panelName the repeatable panel element name (e.g. "personsRepeat")
     * @param mapping   names of child elements within each panel instance
     * @return list of valid signers (those with non-empty email); empty on error
     */
    public static List<Signer> parseSigners(String xml, String panelName,
                                            SignerFieldMapping mapping) {
        if (xml == null || xml.isEmpty() || panelName == null || panelName.isEmpty()) {
            return Collections.emptyList();
        }
        if (mapping == null) mapping = SignerFieldMapping.DEFAULT;
        try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return parseSigners(is, panelName, mapping);
        } catch (Exception e) {
            LOG.warn("Failed to parse form data XML for panel '{}': {}", panelName, e.getMessage());
            return Collections.emptyList();
        }
    }

    public static List<Signer> parseSigners(InputStream xmlStream, String panelName,
                                            SignerFieldMapping mapping) {
        if (mapping == null) mapping = SignerFieldMapping.DEFAULT;
        List<Signer> signers = new ArrayList<>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            // XXE protection.
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(xmlStream));

            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList panels = (NodeList) xpath.evaluate(
                    "//*[local-name()='" + panelName + "']",
                    doc, XPathConstants.NODESET);

            for (int i = 0; i < panels.getLength(); i++) {
                Node panel = panels.item(i);
                if (panel.getNodeType() == Node.ELEMENT_NODE) {
                    Signer s = extractSigner((Element) panel, mapping);
                    if (s != null && s.isValid()) {
                        signers.add(s);
                    } else if (s != null) {
                        LOG.debug("Skipping signer with no '{}' value", mapping.getEmailField());
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Error parsing form data for panel '{}': {}", panelName, e.toString());
        }
        return signers;
    }

    private static Signer extractSigner(Element panel, SignerFieldMapping mapping) {
        String name = textOf(panel, mapping.getNameField());
        String email = textOf(panel, mapping.getEmailField());
        String role = textOf(panel, mapping.getRoleField());
        Integer page = intOf(panel, mapping.getSignaturePageField());
        Integer x = intOf(panel, mapping.getSignatureXField());
        Integer y = intOf(panel, mapping.getSignatureYField());
        return new Signer(name, email, role, page, x, y);
    }

    private static String textOf(Element parent, String childLocalName) {
        NodeList all = parent.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE &&
                    childLocalName.equalsIgnoreCase(n.getLocalName() == null
                            ? n.getNodeName() : n.getLocalName())) {
                String text = n.getTextContent();
                if (text != null) {
                    String trimmed = text.trim();
                    if (!trimmed.isEmpty()) return trimmed;
                }
            }
        }
        return null;
    }

    private static Integer intOf(Element parent, String childLocalName) {
        String s = textOf(parent, childLocalName);
        if (s == null) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            LOG.debug("Non-numeric value for {}: '{}'", childLocalName, s);
            return null;
        }
    }
}
