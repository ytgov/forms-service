package ca.yukon.aem.core.workflows;

import com.adobe.aemfd.docmanager.Document;
import com.adobe.granite.workflow.WorkflowException;
import com.adobe.granite.workflow.WorkflowSession;
import com.adobe.granite.workflow.exec.WorkItem;
import com.adobe.granite.workflow.exec.WorkflowProcess;
import com.adobe.granite.workflow.metadata.MetaDataMap;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component(
        service = WorkflowProcess.class,
        property = {
                "process.label=Rename List Of Form Attachments",
                "service.description=Reads attachments and returns renamed Document[]",
                "service.vendor=Ensemble"
        }
)
public class RenameListOfAttachments implements WorkflowProcess {

        private static final Logger log = LoggerFactory.getLogger(PopulateListOfDocuments.class);

        @Reference
        private ResourceResolverFactory resourceResolverFactory;

        private void collectAttachments(Node node, List<Document> documents, List<String> filenames) throws Exception {
                NodeIterator children = node.getNodes();
                while (children.hasNext()) {
                        Node child = children.nextNode();
                        if (child.isNodeType("nt:file") && child.hasNode("jcr:content")) {
                                Node content = child.getNode("jcr:content");
                                InputStream is = content.getProperty("jcr:data").getBinary().getStream();
                                documents.add(new Document(is));
                                filenames.add(child.getName());
                                log.debug("Collected attachment: {}", child.getPath());
                        } else if (child.hasNodes()) {
                                collectAttachments(child, documents, filenames); // Recursive call
                        }
                }
        }

        @Override
        public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap processArgs) throws WorkflowException {
                String payloadPath = workItem.getWorkflowData().getPayload().toString();
                String attachmentsFolder = processArgs.get("PROCESS_ARGS", "attachments");
                String fullPath = payloadPath + "/" + attachmentsFolder;
                String[] attachmentRenamesList = processArgs.get("AttachmentRenames", "").split(",");

                log.debug("Looking for attachments under: {}", fullPath);

                try {
                        Session session = workflowSession.adaptTo(Session.class);
                        if (session == null) throw new WorkflowException("Session adaptation failed");

                        if (!session.nodeExists(fullPath)) {
                                log.warn("Attachment folder not found: {}", fullPath);
                                workItem.getWorkflow().getWorkflowData().getMetaDataMap().put("no_of_attachments", 0);
                                return;
                        }

                        Node attachmentRoot = session.getNode(fullPath);
                        List<Document> docs = new ArrayList<>();
                        List<String> names = new ArrayList<>();

                        collectAttachments(attachmentRoot, docs, names);  // fills both lists

                        // Rename files systematically
                        Document[] listOfDocuments = new Document[docs.size()];
                        String[] attachmentNames = new String[docs.size()];

                        for (int i = 0; i < docs.size(); i++) {
                                listOfDocuments[i] = docs.get(i);
                                String originalName = names.get(i);
                                String extension = "";

                                if (originalName != null && originalName.contains(".")) {
                                        extension = originalName.substring(originalName.lastIndexOf("."));
                                }

                                String rename = (i < attachmentRenamesList.length) ? attachmentRenamesList[i].trim() : "File";
                                attachmentNames[i] = "attachment-" + rename + "-" + (i + 1) + extension;

                                log.debug("Renamed: {} -> {}", originalName, attachmentNames[i]);
                        }

                        MetaDataMap metaDataMap = workItem.getWorkflow().getWorkflowData().getMetaDataMap();
                        metaDataMap.put("no_of_attachments", docs.size());
                        metaDataMap.put("listOfDocuments", listOfDocuments);
                        metaDataMap.put("attachmentNames", attachmentNames);

                        log.info("Total attachments found: {}", docs.size());

                } catch (Exception e) {
                        log.error("Error processing attachments at: " + fullPath, e);
                        throw new WorkflowException("Attachment collection failed", e);
                }
        }
}