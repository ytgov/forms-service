/**
 * Downloads the submission PDF (Document of Record) for the data currently entered, without submitting the form
 * @name downloadDorPreview Download submission PDF before submitting
 * @param {string} fileName Name of the downloaded file, e.g. "my-application.pdf" (optional)
 * @return {string}
 */

// Posts the current form data to AEM's built-in DoR endpoint (/adobe/forms/af/dor/{id}, the one Core Components'
// downloadDoR() uses) and saves the returned PDF. It renders the same PDF as the one emailed on submit (YUKONAEMF-80).
function downloadDorPreview(fileName) {
    // The endpoint identifies the form by its base64url-encoded guideContainer path.
    var formId = btoa(guideBridge.getGuidePath()).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    var url = "/adobe/forms/af/dor/" + formId + "?locale=" + encodeURIComponent(document.documentElement.lang || "en");
    if (window.Granite && Granite.HTTP && Granite.HTTP.externalize) {
        url = Granite.HTTP.externalize(url);
    }

    guideBridge.getDataXML({
        success: function (result) {
            downloadDorPreviewSend(url, result.data, fileName || "submission-preview.pdf");
        },
        error: function () {
            console.error("downloadDorPreview: could not read form data");
        }
    });
    return "";
}

function downloadDorPreviewSend(url, data, fileName) {
    var formData = new FormData();
    formData.append("data", data);

    var xhr = new XMLHttpRequest();
    xhr.open("POST", url, true);
    xhr.responseType = "blob";
    xhr.onload = function () {
        if (xhr.status !== 200) {
            console.error("downloadDorPreview: PDF generation failed with status " + xhr.status);
            return;
        }
        var blobUrl = URL.createObjectURL(xhr.response);
        var link = document.createElement("a");
        link.href = blobUrl;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(blobUrl);
    };
    xhr.send(formData);
}
