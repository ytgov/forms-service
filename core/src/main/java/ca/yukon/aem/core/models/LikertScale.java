package ca.yukon.aem.core.models;

import com.adobe.aemds.guide.common.GuideRadioButton;

public class LikertScale extends GuideRadioButton {

    public LikertScale() {
    }

    public String getLowEndLabel() {
        return this.resourceProps.get("lowEndLabel", String.class);
    }

    public String getHighEndLabel() {
        return this.resourceProps.get("highEndLabel", String.class);
    }

}
