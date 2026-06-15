package ca.yukon.aem.core.models;

import com.adobe.aemds.guide.common.GuideFileUpload;

public class CustomGuideFileUpload extends GuideFileUpload {

    public CustomGuideFileUpload() {
    }

    public String getFileNamePattern() {
        return this.resourceProps.get("fileNamePattern", String.class);
    }

    public String getFileNamePatternMessage() {
        return this.resourceProps.get("fileNamePatternMessage", String.class);
    }

    public String getShowAlertMessage() {
        Boolean value = this.resourceProps.get("showAlertMessage", Boolean.class);
        return value != null && value ? "true" : "false";
    }
}
