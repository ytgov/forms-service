/**
 * Downloads the submission PDF (Document of Record) for the data currently entered, without submitting the form
 * @name downloadDorPreview Download submission PDF before submitting
 * @param {string} [fileName] File name for the PDF, for example application.pdf. The date and time are added to it. If empty, the form name is used
 * @param {boolean} [allowInvalid] Set to true to allow the PDF even when the form has errors
 * @return {string}
 */

// Keep hyphens out of the JSDoc above: the Rule Editor cuts a parameter description at the first hyphen.
// Posts the current form data to AEM's built-in DoR endpoint (/adobe/forms/af/dor/{id}, the one Core Components'
// downloadDoR() uses) and saves the returned PDF. It renders the same PDF as the one emailed on submit (YUKONAEMF-80).
// Unless allowInvalid is true, an invalid form gets no PDF. It's compared as text because a Rule Editor input can pass
// "true" or "false" as a string, and !"false" would wrongly skip the validation.
function downloadDorPreview(fileName, allowInvalid) {
    if (String(allowInvalid) !== "true" && !downloadDorPreviewHelpers.isValid()) {
        return "";
    }
    downloadDorPreviewHelpers.fetch(function (pdf) {
        downloadDorPreviewHelpers.save(pdf, fileName);
    });
    return "";
}

/**
 * Opens the submission PDF (Document of Record) for the data currently entered in a new browser tab, without submitting the form; downloads the PDF instead if the browser blocks the tab
 * @name previewDorInNewTab Preview submission PDF in a new tab before submitting
 * @param {boolean} [allowInvalid] Set to true to allow the PDF even when the form has errors
 * @return {string}
 */

// Same PDF as downloadDorPreview. Browsers only allow a new tab during the click itself, but the PDF arrives
// later, so open an empty tab now and load the PDF into it when it's ready. Validate first, so an invalid form
// doesn't leave an empty tab; validate() is synchronous, so the tab still opens during the click.
function previewDorInNewTab(allowInvalid) {
    if (String(allowInvalid) !== "true" && !downloadDorPreviewHelpers.isValid()) {
        return "";
    }
    var tab = window.open("", "_blank");
    downloadDorPreviewHelpers.fetch(function (pdf) {
        if (!tab) {
            // The pop-up was blocked, and a second window.open from here would be too; a download needs no pop-up permission.
            downloadDorPreviewHelpers.save(pdf);
            return;
        }
        var blobUrl = URL.createObjectURL(pdf);
        tab.location.href = blobUrl;
        // The tab is still loading the blob, so keep it alive for a while before releasing it.
        setTimeout(function () {
            URL.revokeObjectURL(blobUrl);
        }, 60000);
    }, function () {
        if (tab) {
            tab.close();
        }
    });
    return "";
}

// Helpers shared by the two functions above. They're object properties, not top-level function declarations,
// because the Foundation Rule Editor lists every top-level function, even without JSDoc, and authors shouldn't pick these.
var downloadDorPreviewHelpers = {
    // Validates the whole form like Submit does: shows the inline errors and focuses the first invalid field,
    // even on another panel. Returns true if the form is valid.
    isValid: function () {
        return guideBridge.validate([], null, true);
    },

    // Saves the PDF blob as a file download.
    save: function (pdf, fileName) {
        var blobUrl = URL.createObjectURL(pdf);
        var link = document.createElement("a");
        link.href = blobUrl;
        link.download = downloadDorPreviewHelpers.fileName(fileName);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        // Some browsers start the download after click() returns, so keep the blob alive for a while before releasing it.
        setTimeout(function () {
            URL.revokeObjectURL(blobUrl);
        }, 60000);
    },

    // Builds "<name>-YYYYMMDD-HHmmss.pdf" in the user's local time, so each download gets its own file name. <name> is
    // fileName without ".pdf", or the form's page name (e.g. update-yhcip-information_e) when fileName is empty.
    // fileName may come from a form field, so it can be a number or have stray spaces.
    fileName: function (fileName) {
        var name = String(fileName || "").trim().replace(/\.pdf$/i, "").trim();
        if (!name) {
            // getGuidePath() is ".../<form page>/jcr:content/guideContainer".
            var formPath = guideBridge.getGuidePath().split("/jcr:content")[0];
            name = formPath.substring(formPath.lastIndexOf("/") + 1);
        }
        var now = new Date();
        var pad = function (n) {
            return (n < 10 ? "0" : "") + n;
        };
        return name + "-" + now.getFullYear() + pad(now.getMonth() + 1) + pad(now.getDate()) +
            "-" + pad(now.getHours()) + pad(now.getMinutes()) + pad(now.getSeconds()) + ".pdf";
    },

    // Reads the form data, posts it to the DoR endpoint and passes the PDF blob to onPdf. Calls onError (optional) on failure.
    fetch: function (onPdf, onError) {
        // The endpoint identifies the form by its base64url-encoded guideContainer path.
        var formId = btoa(guideBridge.getGuidePath()).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
        var url = "/adobe/forms/af/dor/" + formId + "?locale=" + encodeURIComponent(document.documentElement.lang || "en");
        if (window.Granite && Granite.HTTP && Granite.HTTP.externalize) {
            url = Granite.HTTP.externalize(url);
        }
        var fail = onError || function () {};

        guideBridge.getDataXML({
            success: function (result) {
                var formData = new FormData();
                formData.append("data", result.data);

                var xhr = new XMLHttpRequest();
                xhr.open("POST", url, true);
                xhr.responseType = "blob";
                xhr.onload = function () {
                    if (xhr.status !== 200) {
                        console.error("downloadDorPreview: PDF generation failed with status " + xhr.status);
                        fail();
                        return;
                    }
                    onPdf(xhr.response);
                };
                xhr.onerror = function () {
                    console.error("downloadDorPreview: PDF request failed");
                    fail();
                };
                xhr.send(formData);
            },
            error: function () {
                console.error("downloadDorPreview: could not read form data");
                fail();
            }
        });
    }
};
