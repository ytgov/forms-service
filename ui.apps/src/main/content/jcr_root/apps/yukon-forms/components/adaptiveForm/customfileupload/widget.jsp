<%------------------------------------------------------------------------
 ~
 ~ ADOBE CONFIDENTIAL
 ~ __________________
 ~
 ~  Copyright 2014 Adobe Systems Incorporated
 ~  All Rights Reserved.
 ~
 ~ NOTICE:  All information contained herein is, and remains
 ~ the property of Adobe Systems Incorporated and its suppliers,
 ~ if any.  The intellectual and technical concepts contained
 ~ herein are proprietary to Adobe Systems Incorporated and its
 ~ suppliers and may be covered by U.S. and Foreign Patents,
 ~ patents in process, and are protected by trade secret or copyright law.
 ~ Dissemination of this information or reproduction of this material
 ~ is strictly forbidden unless prior written permission is obtained
 ~ from Adobe Systems Incorporated.
 --------------------------------------------------------------------------%>
<%--
 File Upload Component
--%>
<%@include file="/libs/fd/af/components/guidesglobal.jsp"%>
<%@ page import="com.adobe.granite.toggle.api.ToggleRouter,
                 com.adobe.aemds.guide.fdfl.utils.FeatureToggleConstants" %>
<%
    ToggleRouter toggleRouter = sling.getService(ToggleRouter.class);
    boolean isDoubleExtFTEnabled = toggleRouter != null && toggleRouter.isEnabled(FeatureToggleConstants.FT_DISABLE_DOUBLE_EXTENSION_FILES);
    pageContext.setAttribute("isDoubleExtFTEnabled", isDoubleExtFTEnabled);
%>
<div class="<%= GuideConstants.GUIDE_FIELD_WIDGET%> afFileUpload"
     <c:if test="${isDoubleExtFTEnabled}">data-disable-double-extension-files="${guideField.disableDoubleExtensionFiles}"</c:if>
     style="${guide:encodeForHtmlAttr(guideField.styles,xssAPI)}">
    <input id="${guide:encodeForHtmlAttr(guideid,xssAPI)}${'_widget'}"  name="${guide:encodeForHtmlAttr(guideField.name,xssAPI)}" type="file" 
           accept="${guide:encodeForHtmlAttr(guideField.mimeType,xssAPI)}" tabindex="-1" style="" aria-describedby="${guide:encodeForHtmlAttr(guideField.labelForId,xssAPI)}_desc"
           data-file-name-pattern="${guideField.fileNamePattern}"
           data-file-name-pattern-message="${guideField.fileNamePatternMessage}"
           data-show-alert-message="${guideField.showAlertMessage}"
           <c:if test="${guideField.multiSelection}"> multiple</c:if>/>
    <button class="button-default button-medium guide-fu-attach-button" type="button" style="${guide:encodeForHtmlAttr(guideField.widgetInlineStyles,xssAPI)}" 
            aria-labelledby="${guide:encodeForHtmlAttr(guideField.labelForId,xssAPI)}_desc">${guide:encodeForHtmlAttr(guideField.buttonText,xssAPI)}</button>
    <ul class="guide-fu-fileItemList"></ul>
</div>