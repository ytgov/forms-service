package ca.yukon.aem.core.forms.services;

import ca.yukon.aem.core.forms.model.Signer;
import org.apache.sling.api.resource.ResourceResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Service that creates Adobe Sign agreements with a dynamic number of
 * signers.
 *
 * <p>This is the public abstraction used by workflow steps. The
 * implementation handles transient document upload, agreement creation,
 * dynamic participant placement, and dynamic signature field placement
 * via the Adobe Sign v6 REST API.</p>
 *
 * <h2>Credentials resolution</h2>
 * <p>Each method takes a {@code cloudConfigPath} pointing to an AEM Adobe
 * Sign Cloud Service config (e.g.
 * {@code /conf/yukon-forms/settings/cloudconfigs/adobesign/<configName>/jcr:content}).
 * When provided (non-empty), the implementation reads the access token,
 * API endpoint and sender email from that JCR node. Different workflows
 * can target different cloud configs by passing a different path.</p>
 *
 * <p>When {@code cloudConfigPath} is null or empty, the implementation
 * falls back to explicit credentials in the OSGi configuration. This is
 * useful for local development.</p>
 */
public interface AdobeSignService {

    /**
     * Upload a document to Adobe Sign as a transient document.
     *
     * @param fileName        display name of the document (e.g. "petition.pdf")
     * @param mimeType        MIME type (e.g. "application/pdf")
     * @param fileContent     file contents
     * @param resolver        resolver used to read the cloud config node
     *                        when {@code cloudConfigPath} is set; may be
     *                        null when OSGi fallback supplies credentials
     * @param cloudConfigPath JCR path to the AEM Adobe Sign Cloud Service
     *                        config; may be null/empty to use OSGi fallback
     * @return transient document id (valid for 7 days)
     * @throws IOException on network or API error
     */
    String uploadTransientDocument(String fileName, String mimeType,
                                   InputStream fileContent,
                                   ResourceResolver resolver,
                                   String cloudConfigPath) throws IOException;

    /**
     * Create an Adobe Sign agreement with one participant per signer.
     *
     * <p>Each signer is a separate participantSet with the same order
     * (parallel signing). A signature field is placed for each signer.</p>
     *
     * @param agreementName   visible name of the agreement
     * @param transientDocId  returned from {@link #uploadTransientDocument}
     * @param signers         non-empty list of signers
     * @param message         optional message included in signing emails
     * @param resolver        see param of {@link #uploadTransientDocument}
     * @param cloudConfigPath see param of {@link #uploadTransientDocument}
     * @return Adobe Sign agreement id
     * @throws IOException              on network or API error
     * @throws IllegalArgumentException if no valid signers are supplied
     */
    String createAgreement(String agreementName, String transientDocId,
                           List<Signer> signers, String message,
                           ResourceResolver resolver,
                           String cloudConfigPath) throws IOException;

    /**
     * Retrieve the current status of an agreement.
     *
     * @param agreementId     Adobe Sign agreement id
     * @param resolver        see param of {@link #uploadTransientDocument}
     * @param cloudConfigPath see param of {@link #uploadTransientDocument}
     * @return status string returned by the API (e.g. OUT_FOR_SIGNATURE)
     * @throws IOException on network or API error
     */
    String getAgreementStatus(String agreementId,
                              ResourceResolver resolver,
                              String cloudConfigPath) throws IOException;
}
