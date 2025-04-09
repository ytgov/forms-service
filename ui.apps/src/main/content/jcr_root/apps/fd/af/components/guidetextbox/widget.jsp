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
 TextBox Component
--%>
<%@include file="/libs/fd/af/components/guidesglobal.jsp"%>
<c:if test="${isPreviewMode && guideField.allowRichText}">
    <ui:includeClientLib categories="form.richtexteditor"/>
</c:if>
<%-- todo: In case of repeatable panels, please change this logic at view layer --%>
<div class="<%= GuideConstants.GUIDE_FIELD_WIDGET%> textField ${guideField.multiLine ? " multiline" : ""} ${guideField.allowRichText ? " richText" : ""}" style="${guide:encodeForHtmlAttr(guideField.styles,xssAPI)}">

    <c:choose>
        <c:when test="${guideField.allowRichText}">
            <div id="${guide:encodeForHtmlAttr(guideid,xssAPI)}${'_widget'}" name="${guide:encodeForHtmlAttr(guideField.name,xssAPI)}" style="${guide:encodeForHtmlAttr(guideField.widgetInlineStyles,xssAPI)}" placeholder="${guide:encodeForHtmlAttr(guideField.placeholderText,xssAPI)}">
                <div id="${guide:encodeForHtmlAttr(guideid,xssAPI)}${'_widget'}${'_richText'}" class="richTextWidget" data-locale="${guide:getLocale(slingRequest,resource)}" aria-describedby="${guide:encodeForHtmlAttr(guideField.labelForId,xssAPI)}_desc">${guide:filterHtml(guideField.value,xssAPI)}</div>
            </div>
        </c:when>

        <c:when test="${guideField.multiLine}">
            <textarea id="${guide:encodeForHtmlAttr(guideid,xssAPI)}${'_widget'}"
                    <c:if test="${not empty guideField.html5MaxLength}">
                        maxlength="${guideField.html5MaxLength}"
                    </c:if>
                      <%-- data-name was added because other attributes were overwritten --%>
                      data-name="${guideField.name}"
                      autocomplete = "${guideField.autocomplete? guideField.autofillFieldKeyword :"off"}" name="${guide:encodeForHtmlAttr(guideField.name,xssAPI)}" style="${guide:encodeForHtmlAttr(guideField.widgetInlineStyles,xssAPI)}" placeholder="${guide:encodeForHtmlAttr(guideField.placeholderText,xssAPI)}" aria-describedby="${guide:encodeForHtmlAttr(guideField.labelForId,xssAPI)}_desc">${guide:encodeForHtml(guideField.value,xssAPI)}</textarea>
        </c:when>

        <c:otherwise>
            <input type="text"
                    <c:if test="${not empty guideField.html5MaxLength}">
                        maxlength="${guideField.html5MaxLength}"
                    </c:if>
                   <%-- data-name was added because other attributes were overwritten --%>
                   data-name="${guideField.name}"
                   autocomplete = "${guideField.autocomplete? guideField.autofillFieldKeyword :"off"}" id="${guide:encodeForHtmlAttr(guideid,xssAPI)}${'_widget'}" name="${guide:encodeForHtmlAttr(guideField.name,xssAPI)}" value="${guide:encodeForHtmlAttr(guideField.value,xssAPI)}" style="${guide:encodeForHtmlAttr(guideField.widgetInlineStyles,xssAPI)}" placeholder="${guide:encodeForHtmlAttr(guideField.placeholderText,xssAPI)}" aria-describedby="${guide:encodeForHtmlAttr(guideField.labelForId,xssAPI)}_desc"/>
        </c:otherwise>
    </c:choose>
    <%-- End of Widget Div --%>
</div>
