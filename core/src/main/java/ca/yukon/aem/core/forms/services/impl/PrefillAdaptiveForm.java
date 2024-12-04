package ca.yukon.aem.core.forms.services.impl;

import com.adobe.forms.common.service.DataXMLOptions;
import com.adobe.forms.common.service.DataXMLProvider;
import com.adobe.forms.common.service.FormsException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import javax.jcr.Session;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
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

@Component
public class PrefillAdaptiveForm implements DataXMLProvider {
    private static final Logger log = LoggerFactory.getLogger(PrefillAdaptiveForm.class);

    @Override
    public String getServiceDescription() {

        return "Custom AEM Forms PreFill Service";
    }

    @Override
    public String getServiceName() {

        return "CustomAemFormsPrefillService";
    }

    @Override
    public InputStream getDataXMLForDataRef(DataXMLOptions dataXmlOptions) throws FormsException {
        InputStream xmlDataStream = null;
        Resource aemFormContainer = dataXmlOptions.getFormResource();
        ResourceResolver resolver = aemFormContainer.getResourceResolver();
        Session session = (Session) resolver.adaptTo(Session.class);
        try {
            UserManager um = ((JackrabbitSession) session).getUserManager();
            Authorizable loggedinUser = um.getAuthorizable(session.getUserID());
            log.debug("The path of the user is" + loggedinUser.getPath());
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement("data");
            doc.appendChild(rootElement);

            if (loggedinUser.hasProperty("profile/givenName")) {
                Element firstNameElement = doc.createElement("fname");
                firstNameElement.setTextContent(loggedinUser.getProperty("profile/givenName")[0].getString());
                rootElement.appendChild(firstNameElement);
                log.debug("Created firstName Element");
            }

            if (loggedinUser.hasProperty("profile/familyName")) {
                Element lastNameElement = doc.createElement("lname");
                lastNameElement.setTextContent(loggedinUser.getProperty("profile/familyName")[0].getString());
                rootElement.appendChild(lastNameElement);
                log.debug("Created lastName Element");

            }
            if (loggedinUser.hasProperty("profile/email")) {
                Element emailElement = doc.createElement("email");
                emailElement.setTextContent(loggedinUser.getProperty("profile/email")[0].getString());
                rootElement.appendChild(emailElement);
                log.debug("Created email Element");

            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult outputTarget = new StreamResult(outputStream);
            TransformerFactory.newInstance().newTransformer().transform(source, outputTarget);
            if (log.isDebugEnabled()) {
                FileOutputStream output = new FileOutputStream("afdata.xml");
                StreamResult result = new StreamResult(output);
                transformer.transform(source, result);
            }

            xmlDataStream = new ByteArrayInputStream(outputStream.toByteArray());
            return xmlDataStream;
        } catch (Exception e) {
            log.error("The error message is " + e.getMessage());
        }
        return null;

    }

}
