/*
 * Loads and saves the required_verification_status property from the Forms & Documents folder "Edit"
 * dialog. The libs editfolder.js builds its own POST to <folder>/jcr:content containing only jcr:title
 * (and jcr:primaryType, so a missing jcr:content is created as nt:unstructured), so the verification
 * level is appended to that same request instead of being sent separately. The field is only shown,
 * and only saved, for FOLDER_ROOT and the folders under it.
 */
(function (document, $) {
    "use strict";

    var EDIT_FOLDER_ACTIVATOR = ".cq-formsmanager-admin-actions-editfolderproperties-activator";
    var STATUS_FIELD = "#yf-id-folder-required-verification-status";
    var PROPERTY = "required_verification_status";
    var FOLDER_ROOT = "/content/dam/formsanddocuments/yukon-forms";

    var folderContentUrl = null;

    function statusField() {
        return document.querySelector(STATUS_FIELD);
    }

    function isUnderFolderRoot(path) {
        return !!path && (path === FOLDER_ROOT || path.indexOf(FOLDER_ROOT + "/") === 0);
    }

    function setStatusFieldVisible(visible) {
        var field = statusField();
        if (field) {
            $(field).closest(".coral-Form-fieldwrapper").toggle(visible);
        }
    }

    function setStatusFieldValue(value) {
        var field = statusField();
        if (field) {
            Coral.commons.ready(field, function () {
                field.value = value;
            });
        }
    }

    $(document).on("click", EDIT_FOLDER_ACTIVATOR, function () {
        var dataSets = FMBase.Util.getDataSetOfAllSelectedItems();
        var folderPath = dataSets && dataSets.length ? dataSets[0].path : null;
        var applicable = isUnderFolderRoot(folderPath);
        folderContentUrl = applicable ? Granite.HTTP.externalize(folderPath + "/jcr:content") : null;
        setStatusFieldVisible(applicable);
        setStatusFieldValue("");
        if (!folderContentUrl) {
            return;
        }
        $.getJSON(folderContentUrl + ".json").done(function (props) {
            setStatusFieldValue(props[PROPERTY] !== undefined ? String(props[PROPERTY]) : "");
        });
    });

    $.ajaxPrefilter(function (options) {
        var field = statusField();
        if (!field || !folderContentUrl || options.type.toUpperCase() !== "POST"
                || options.url !== folderContentUrl || typeof options.data !== "string"
                || options.data.indexOf("jcr%3Atitle=") === -1) {
            return;
        }
        var params = {};
        if (field.value === "") {
            params[PROPERTY + "@Delete"] = "";
        } else {
            params[PROPERTY] = field.value;
            params[PROPERTY + "@TypeHint"] = "Long";
        }
        options.data += "&" + $.param(params);
    });

})(document, Granite.$);
