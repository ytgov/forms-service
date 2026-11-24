package ca.yukon.aem.core.models;

import com.adobe.aemds.guide.common.GuideFileUpload;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;

import java.util.Locale;

/**
 * Bean for the File Attachment (Custom) component. Besides the file name pattern, it gives the
 * widget its authorable messages, falling back to a default in the form's language when a message
 * is left blank.
 *
 * <p>Message placeholders: {@code {fileName}}, {@code {size}}, {@code {maxSize}}, {@code {maxTotal}}
 * and, in the total row, {@code {total}}. A placeholder without a value is left out.</p>
 */
public class CustomGuideFileUpload extends GuideFileUpload {

    static final String LANGUAGE_PARAMETER = "afAcceptLang";
    static final String ENGLISH = "en";
    static final String FRENCH = "fr";

    static final String DEFAULT_TOTAL = "Total: {total}";
    static final String DEFAULT_TOTAL_FR = "Total : {total}";

    static final String DEFAULT_FILE_TOO_LARGE =
            "\"{fileName}\" ({size}) wasn't added. Choose a file that is {maxSize} or smaller.";
    static final String DEFAULT_FILE_TOO_LARGE_FR =
            "«\u00A0{fileName}\u00A0» ({size}) n'a pas été ajouté. Choisissez un fichier de {maxSize} ou moins.";

    static final String DEFAULT_FILE_NAME_NOT_ALLOWED =
            "\"{fileName}\" ({size}) wasn't added. Rename the file using only allowed characters, then upload it again.";
    static final String DEFAULT_FILE_NAME_NOT_ALLOWED_FR =
            "«\u00A0{fileName}\u00A0» ({size}) n'a pas été ajouté. Renommez le fichier en utilisant seulement "
                    + "les caractères permis, puis téléversez-le de nouveau.";

    private Boolean french;

    public CustomGuideFileUpload() {
    }

    public String getFileNamePattern() {
        return this.resourceProps.get("fileNamePattern", String.class);
    }

    /** The "File name not allowed" message as authored; blank when the default applies. */
    public String getFileNamePatternMessage() {
        return this.resourceProps.get("fileNamePatternMessage", String.class);
    }

    public String getShowAlertMessage() {
        Boolean value = this.resourceProps.get("showAlertMessage", Boolean.class);
        return value != null && value ? "true" : "false";
    }

    /** "en" or "fr": the language the widget formats sizes and messages in. */
    public String getLanguage() {
        return isFrench() ? FRENCH : ENGLISH;
    }

    public String getMsgFileTooLarge() {
        return message("msgFileTooLarge", DEFAULT_FILE_TOO_LARGE, DEFAULT_FILE_TOO_LARGE_FR);
    }

    public String getMsgFileNameNotAllowed() {
        return message("fileNamePatternMessage", DEFAULT_FILE_NAME_NOT_ALLOWED, DEFAULT_FILE_NAME_NOT_ALLOWED_FR);
    }

    public String getMsgTotal() {
        return message("msgTotal", DEFAULT_TOTAL, DEFAULT_TOTAL_FR);
    }

    private String message(String property, String english, String french) {
        String value = this.resourceProps.get(property, String.class);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        return isFrench() ? french : english;
    }

    /**
     * Whether the form is shown in French. Same rules as the validation summary: the
     * {@code afAcceptLang} parameter, then a selector (e.g. {@code form.fr.html}), then
     * {@code jcr:language} on the form page. Regional variants such as {@code fr-CA} count.
     */
    private boolean isFrench() {
        if (french == null) {
            french = detectFrench(this.slingRequest, this.resource);
        }
        return french;
    }

    static boolean detectFrench(SlingHttpServletRequest request, Resource resource) {
        if (request != null) {
            String parameter = request.getParameter(LANGUAGE_PARAMETER);
            if (parameter != null && !parameter.isEmpty()) {
                return isFrench(parameter);
            }
            for (String selector : request.getRequestPathInfo().getSelectors()) {
                if (isFrench(selector)) {
                    return true;
                }
            }
        }
        Resource formContent = formContent(resource != null ? resource : request != null ? request.getResource() : null);
        return formContent != null && isFrench(formContent.getValueMap().get("jcr:language", String.class));
    }

    /** The jcr:content of the form page containing {@code resource}, or {@code null}. */
    private static Resource formContent(Resource resource) {
        Resource current = resource;
        while (current != null && !"jcr:content".equals(current.getName())) {
            current = current.getParent();
        }
        return current;
    }

    private static boolean isFrench(String language) {
        return language != null && FRENCH.equals(language.split("[-_]")[0].toLowerCase(Locale.ROOT));
    }
}
