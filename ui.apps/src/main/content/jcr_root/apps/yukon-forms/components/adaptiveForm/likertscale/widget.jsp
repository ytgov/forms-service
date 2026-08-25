<%--
  Likert Scale Component
--%>
<%@include file="/libs/fd/af/components/guidesglobal.jsp"%>
<cq:includeClientLib categories="yukon-forms.components.likert-scale"/>
<%@ page import="com.adobe.granite.toggle.api.ToggleRouter,
                 com.adobe.aemds.guide.fdfl.utils.FeatureToggleConstants" %>

<%
    ToggleRouter toggleRouter = sling.getService(ToggleRouter.class);
    boolean isDoubleExtFTEnabled = toggleRouter != null && toggleRouter.isEnabled(FeatureToggleConstants.FT_DISABLE_DOUBLE_EXTENSION_FILES);
    pageContext.setAttribute("isDoubleExtFTEnabled", isDoubleExtFTEnabled);
%>

<cq:includeClientLib categories="yukon-forms.components.likert-scale"/>
<div class="afLikertScale" style="${guide:encodeForHtmlAttr(guideField.styles,xssAPI)};${guide:encodeForHtmlAttr(guideField.widgetInlineStyles,xssAPI)}">
    <span class="likert-high-end-label">
        ${guide:encodeForHtml(guideField.highEndLabel, xssAPI)}
    </span>
    <c:forEach items="${guideField.options}" var="option" varStatus="loopCounter">
        <div class="likertScaleContainer ${guide:encodeForHtmlAttr(guideField.alignment,xssAPI)} ${guide:encodeForHtmlAttr(guideField.name,xssAPI)} ${guide:encodeForHtmlAttr(guideField.cssClassName,xssAPI)}">
            <div class="<%= GuideConstants.GUIDE_FIELD_WIDGET%> left" data-id="${loopCounter.count}">
                <input type="radio" id="${guideField.id}${'-'}${loopCounter.count}${"_widget"}"
                       name="${guide:encodeForHtmlAttr(guideField.name,xssAPI)}" value="${guide:encodeForHtmlAttr(option.key,xssAPI)}" ${option.key == guideField.value ? "checked" : ""}
                       aria-describedby="${guide:encodeForHtmlAttr(guideField.labelForId,xssAPI)}_input_label_${loopCounter.count} ${guide:encodeForHtmlAttr(guideField.labelForId,xssAPI)}_desc" />
            </div>
            <div class="likertWidgetLabel right" >
                <label id="${guide:encodeForHtmlAttr(guideField.labelForId,xssAPI)}_input_label_${loopCounter.count}"><c:if test="${guideField.areOptionsRichText eq false}">${guide:encodeForHtml(option.value,xssAPI)}</c:if><c:if test="${guideField.areOptionsRichText}">${guide:filterHtml(option.value,xssAPI)}</c:if></label>
            </div>
        </div>
    </c:forEach>
    <span class="likert-low-end-label">
        ${guide:encodeForHtml(guideField.lowEndLabel, xssAPI)}
    </span>
</div>

