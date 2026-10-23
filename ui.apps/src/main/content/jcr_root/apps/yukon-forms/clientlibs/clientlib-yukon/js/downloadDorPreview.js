/**
 * Downloads the submission PDF (Document of Record) for the data currently entered, without submitting the form
 * @name downloadDorPreview Download submission PDF before submitting
 * @param {string} fileName Name of the downloaded file, e.g. "my-application.pdf" (optional)
 * @return {string}
 */

// Posts the current form data to AEM's built-in DoR endpoint (/adobe/forms/af/dor/{id}, the one Core Components'
// downloadDoR() uses) and saves the returned PDF. It renders the same PDF as the one emailed on submit (YUKONAEMF-80).
function downloadDorPreview(fileName) {
    downloadDorPreviewFetch(function (pdf) {
        downloadDorPreviewSave(pdf, fileName);
    });
    return "";
}

/**
 * Opens the submission PDF (Document of Record) for the data currently entered in a new browser tab, without submitting the form
 * @name previewDorInNewTab Preview submission PDF in a new tab before submitting
 * @return {string}
 */

// Same PDF as downloadDorPreview. Browsers only allow a new tab during the click itself, but the PDF arrives
// later, so open an empty tab now and load the PDF into it when it's ready.
function previewDorInNewTab() {
    var tab = window.open("", "_blank");
    downloadDorPreviewFetch(function (pdf) {
        if (!tab) {
            // The pop-up was blocked, and a second window.open from here would be too; a download needs no pop-up permission.
            downloadDorPreviewSave(pdf);
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

// Saves the PDF blob as a file download.
function downloadDorPreviewSave(pdf, fileName) {
    var blobUrl = URL.createObjectURL(pdf);
    var link = document.createElement("a");
    link.href = blobUrl;
    link.download = fileName || "submission-preview.pdf";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(blobUrl);
}

// Reads the form data, posts it to the DoR endpoint and passes the PDF blob to onPdf. Calls onError (optional) on failure.
function downloadDorPreviewFetch(onPdf, onError) {
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
