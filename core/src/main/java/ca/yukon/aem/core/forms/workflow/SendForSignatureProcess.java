package ca.yukon.aem.core.forms.workflow;

import ca.yukon.aem.core.forms.model.Signer;
import ca.yukon.aem.core.forms.services.AdobeSignService;
import ca.yukon.aem.core.forms.util.FormDataParser;
import ca.yukon.aem.core.forms.util.SignerFieldMapping;

import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowData;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * AEM Workflow Process Step: reads submitted form data from the workflow
 * payload, extracts signers from a repeatable panel, and creates a single
 * Adobe Sign agreement with one dynamic signer per repeated panel instance.
 *
 * <p>All signers receive the signing email in parallel; each is positioned
 * as a separate {@code participantSet} with the same order value.</p>
 *
 * <h2>Inputs</h2>
 * <ul>
 *   <li>Workflow payload — a JCR path pointing to a form submission folder
 *       (or a node) that contains the submitted data XML and (optionally) a
 *       Document of Record PDF.</li>
 *   <li>Process arguments (configured in the workflow step) — comma-separated
 *       {@code key=value} pairs. Supported keys:
 *       <ul>
 *         <li>{@code cloudConfigPath} — JCR path to the AEM Adobe Sign Cloud
 *             Service config to use for this workflow, e.g.
 *             {@code /conf/yukon-forms/settings/cloudconfigs/adobesign/<name>/jcr:content}.
 *             Different workflow models can target different configs.</li>
 *         <li>{@code panelName} — repeatable panel element name. Default:
 *             {@code personsRepeat}.</li>
 *         <li>{@code dataResource} — relative path under the payload to the
 *             submitted XML. Default: {@code data.xml}.</li>
 *         <li>{@code pdfResource} — relative path under the payload to the
 *             DoR PDF. Default: {@code dor.pdf}.</li>
 *         <li>{@code message} — optional message included in signing emails.</li>
 *         <li>{@code emailField} — child element holding signer email.
 *             Default: {@code email}.</li>
 *         <li>{@code nameField} — child element holding signer display name.
 *             Default: {@code name}.</li>
 *         <li>{@code roleField} — child element holding signer role
 *             (SIGNER / APPROVER / etc). Default: {@code role}.</li>
 *         <li>{@code signaturePageField} — child element with 1-based page
 *             number for the signature field. Default: {@code signaturePage}.</li>
 *         <li>{@code signatureXField} — child element with X coordinate
 *             (PDF points). Default: {@code signatureX}.</li>
 *         <li>{@code signatureYField} — child element with Y coordinate
 *             (PDF points). Default: {@code signatureY}.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>Outputs</h2>
 * <ul>
 *   <li>Sets {@code adobeSignAgreementId} in workflow metadata.</li>
 *   <li>Sets {@code adobeSignSignerCount} in workflow metadata.</li>
 * </ul>
 */
@Component(
        service = WorkflowProcess.class,
        property = {
                "process.label=Yukon - Send Form for Dynamic Signatures"
        }
)
public class SendForSignatureProcess implements WorkflowProcess {

    private static final Logger LOG = LoggerFactory.getLogger(SendForSignatureProcess.class);

    private static final String DEFAULT_PANEL_NAME = "personsRepeat";
    private static final String DEFAULT_DATA_RESOURCE = "data.xml";
    private static final String DEFAULT_PDF_RESOURCE = "dor.pdf";

    static final String META_AGREEMENT_ID = "adobeSignAgreementId";
    static final String META_SIGNER_COUNT = "adobeSignSignerCount";

    @Reference
    private AdobeSignService adobeSignService;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession,
                        MetaDataMap args) throws WorkflowException {

        StepArgs stepArgs = StepArgs.parse(getProcessArg(args));
        WorkflowData wfData = workItem.getWorkflowData();
        String payloadPath = String.valueOf(wfData.getPayload());

        LOG.info("SendForSignatureProcess starting. payload={} args={}", payloadPath, stepArgs);

        ResourceResolver resolver = workflowSession.adaptTo(ResourceResolver.class);
        if (resolver == null) {
            throw new WorkflowException("Cannot adapt WorkflowSession to ResourceResolver");
        }

        Resource payload = resolver.getResource(payloadPath);
        if (payload == null) {
            throw new WorkflowException("Workflow payload not found at " + payloadPath);
        }

        try {
            // 1. Read and parse submitted data
            String xml = readResourceAsString(payload, stepArgs.dataResource);
            if (xml == null) {
                throw new WorkflowException(
                        "Form data (" + stepArgs.dataResource + ") not found under " + payloadPath);
            }
            SignerFieldMapping mapping = stepArgs.toFieldMapping();
            List<Signer> signers = FormDataParser.parseSigners(xml, stepArgs.panelName, mapping);
            if (signers.isEmpty()) {
                LOG.warn("No signers found in panel '{}' at {}. Skipping Adobe Sign step.",
                        stepArgs.panelName, payloadPath);
                wfData.getMetaDataMap().put(META_SIGNER_COUNT, 0);
                return;
            }
            LOG.info("Found {} signer(s) in panel '{}'", signers.size(), stepArgs.panelName);

            // 2. Upload PDF as transient document
            byte[] pdfBytes = readResourceAsBytes(payload, stepArgs.pdfResource);
            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new WorkflowException(
                        "DoR PDF (" + stepArgs.pdfResource + ") not found under " + payloadPath);
            }
            String fileName = payload.getName() + ".pdf";
            String transientId;
            try (InputStream pdfStream = new ByteArrayInputStream(pdfBytes)) {
                transientId = adobeSignService.uploadTransientDocument(
                        fileName, "application/pdf", pdfStream,
                        resolver, stepArgs.cloudConfigPath);
            }
            LOG.debug("Uploaded transient document: {}", transientId);

            // 3. Create the agreement
            String agreementName = buildAgreementName(payload);
            String agreementId = adobeSignService.createAgreement(
                    agreementName, transientId, signers, stepArgs.message,
                    resolver, stepArgs.cloudConfigPath);
            LOG.info("Created Adobe Sign agreement {} with {} signer(s)", agreementId, signers.size());

            // 4. Store result in workflow metadata for downstream steps
            wfData.getMetaDataMap().put(META_AGREEMENT_ID, agreementId);
            wfData.getMetaDataMap().put(META_SIGNER_COUNT, signers.size());

        } catch (IOException e) {
            throw new WorkflowException("Adobe Sign call failed", e);
        }
    }

    private static String getProcessArg(MetaDataMap args) {
        return args == null ?  null  : args.get("PROCESS_ARGS", "string");
    }

    private String buildAgreementName(Resource payload) {
        // Caller can override by setting a custom name in the OSGi config
        // prefix; here we suffix the agreement name with the payload's
        // last path segment for uniqueness/traceability.
        return "Yukon Form - " + payload.getName();
    }

    private static String readResourceAsString(Resource parent, String relPath) {
        byte[] bytes = readResourceAsBytes(parent, relPath);
        return bytes == null ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] readResourceAsBytes(Resource parent, String relPath) {
        Resource child = parent.getChild(relPath);
        if (child == null) {
            // Some submission storage places the file as a nt:file with
            // jcr:content/jcr:data. Try the renditions-style path too.
            child = parent.getChild(relPath + "/jcr:content");
        }
        if (child == null) {
            return null;
        }
        InputStream is = child.adaptTo(InputStream.class);
        if (is == null) {
            // Try child's resource as nt:file
            Resource jcrContent = child.getChild("jcr:content");
            if (jcrContent != null) {
                is = jcrContent.adaptTo(InputStream.class);
            }
        }
        if (is == null) {
            return null;
        }
        try (InputStream stream = is;
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = stream.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            LOG.warn("Failed to read {}/{}: {}", parent.getPath(), relPath, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Process step arguments parsing
    // ------------------------------------------------------------------

    static class StepArgs {
        String cloudConfigPath = null;
        String panelName = DEFAULT_PANEL_NAME;
        String dataResource = DEFAULT_DATA_RESOURCE;
        String pdfResource = DEFAULT_PDF_RESOURCE;
        String message = null;

        // Per-form signer field name overrides; null = use the default
        // ("email", "name", ...) from SignerFieldMapping.DEFAULT.
        String emailField = null;
        String nameField = null;
        String roleField = null;
        String signaturePageField = null;
        String signatureXField = null;
        String signatureYField = null;

        static StepArgs parse(String raw) {
            StepArgs out = new StepArgs();
            if (raw == null || raw.trim().isEmpty()) return out;
            for (String pair : raw.split("[,\\n]")) {
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                String key = pair.substring(0, eq).trim();
                String val = pair.substring(eq + 1).trim();
                switch (key) {
                    case "cloudConfigPath":    out.cloudConfigPath = val; break;
                    case "panelName":          out.panelName = val; break;
                    case "dataResource":       out.dataResource = val; break;
                    case "pdfResource":        out.pdfResource = val; break;
                    case "message":            out.message = val; break;
                    case "emailField":         out.emailField = val; break;
                    case "nameField":          out.nameField = val; break;
                    case "roleField":          out.roleField = val; break;
                    case "signaturePageField": out.signaturePageField = val; break;
                    case "signatureXField":    out.signatureXField = val; break;
                    case "signatureYField":    out.signatureYField = val; break;
                    default:                   /* ignore unknown */    break;
                }
            }
            return out;
        }

        SignerFieldMapping toFieldMapping() {
            // Each argument is optional. The mapping constructor falls back
            // to the conventional default when a value is null/empty.
            return new SignerFieldMapping(
                    emailField, nameField, roleField,
                    signaturePageField, signatureXField, signatureYField);
        }

        @Override
        public String toString() {
            return "StepArgs{cloudConfigPath=" + (cloudConfigPath == null ? "<none>" : cloudConfigPath) +
                    ", panelName=" + panelName +
                    ", dataResource=" + dataResource +
                    ", pdfResource=" + pdfResource +
                    ", message=" + (message == null ? "<none>" : "set") +
                    ", fieldMapping=" + toFieldMapping() + "}";
        }
    }
}
