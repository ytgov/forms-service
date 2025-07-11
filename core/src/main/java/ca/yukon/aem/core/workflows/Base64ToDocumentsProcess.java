package ca.yukon.aem.core.workflows;


import com.adobe.aemfd.docmanager.Document;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.Base64;

@Component(
        service = WorkflowProcess.class,
        property = {
                "process.label=DecodeBase64Attachments",
                "service.description=Decodes base64attachments into Document[]",
                "service.vendor=Adobe Systems"
        }
)

public class Base64ToDocumentsProcess implements WorkflowProcess {

    private static final Logger log = LoggerFactory.getLogger(Base64ToDocumentsProcess.class);

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) throws WorkflowException {
        try {
            Object base64Obj = workItem.getWorkflow().getWorkflowData().getMetaDataMap().get("base64attachments");

            if (base64Obj == null || !(base64Obj instanceof String[])) {
                log.warn("No base64attachments found or incorrect type");
                return;
            }

            String[] base64Array = (String[]) base64Obj;
            Document[] decodedDocuments = new Document[base64Array.length];

            for (int i = 0; i < base64Array.length; i++) {
                byte[] decodedBytes = Base64.getDecoder().decode(base64Array[i]);
                decodedDocuments[i] = new Document(new ByteArrayInputStream(decodedBytes));

                log.debug("Decoded base64 string to Document[{}]", i);
            }

            // Store in workflow metadata as Document[]
            workItem.getWorkflow().getWorkflowData().getMetaDataMap().put("decodedDocuments", decodedDocuments);
            log.info("Successfully decoded {} base64 attachments into Document[]", decodedDocuments.length);

        } catch (Exception e) {
            log.error("Error decoding base64 attachments", e);
            throw new WorkflowException("Failed to decode base64 attachments", e);
        }
    }
}

