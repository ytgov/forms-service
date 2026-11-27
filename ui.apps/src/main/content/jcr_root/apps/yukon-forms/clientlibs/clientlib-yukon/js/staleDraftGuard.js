// Stops a submit that would fail because its draft is gone.
//
// A file restored from a Forms Portal draft is submitted as the draft storage URL, not the file. A
// successful submit deletes the draft and its files, so a second page still open on the same draft
// (another tab, the back button) would submit URLs to deleted files: AEM's Document of Record step
// then fails with HTTP 500. Before such a submit, ask the form's prefill endpoint (same origin) for
// the draft; an empty answer means it is gone, so the submit is stopped with a message instead.
//
// Inside an IIFE so the Rule Editor doesn't list these functions as custom functions.
(function (window, $) {
    "use strict";

    var LOG = "[StaleDraftGuard] ";
    var MESSAGES = {
        en: "This saved form was already submitted or deleted, so its attached files are no longer available. " +
            "Remove each attached file and attach it again, then submit.",
        fr: "Ce formulaire enregistré a déjà été soumis ou supprimé, ses pièces jointes ne sont donc plus disponibles. " +
            "Retirez chaque pièce jointe et joignez-la de nouveau, puis soumettez le formulaire."
    };

    /** True when a file field still holds a file restored from the draft (a URL, not a file the user picked). */
    function hasRestoredFile(guideBridge) {
        var found = false;
        guideBridge.visit(function (node) {
            if (node.className === "guideFileUpload" && $.isArray(node.fileList)) {
                $.each(node.fileList, function (i, item) {
                    if (typeof item === "string" && /^https?:\/\//.test(item)) {
                        found = true;
                    }
                });
            }
        });
        return found;
    }

    /** Calls back with false when the draft is gone, true when it exists or the check itself failed. */
    function draftExists(guideBridge, draftId, callback) {
        var url = guideBridge._getUrl(guideBridge.getGuidePath()) + ".af.prefilldata.json?dataRef=" +
            encodeURIComponent("service://FP/draft/" + draftId);
        $.ajax({ url: url, type: "POST", dataType: "json" }).done(function (response) {
            callback(!(response && response.data === ""));
        }).fail(function (xhr) {
            console.warn(LOG + "Couldn't check the draft (HTTP " + xhr.status + "); submitting anyway.");
            callback(true);
        });
    }

    function install(guideBridge) {
        if (guideBridge._yfStaleDraftGuard) {
            return;
        }
        guideBridge._yfStaleDraftGuard = true;
        var submit = guideBridge.submit;
        guideBridge.submit = function () {
            var self = this,
                args = arguments,
                draftId = guideBridge.customContextProperty("draftId");
            if (!draftId || !hasRestoredFile(guideBridge)) {
                return submit.apply(self, args);
            }
            draftExists(guideBridge, draftId, function (exists) {
                if (exists) {
                    submit.apply(self, args);
                } else {
                    window.alert(MESSAGES[(document.documentElement.lang || "en").slice(0, 2) === "fr" ? "fr" : "en"]);
                }
            });
        };
    }

    if (window.guideBridge && typeof window.guideBridge.isConnected === "function" && window.guideBridge.isConnected()) {
        install(window.guideBridge);
    } else {
        window.addEventListener("bridgeInitializeStart", function (e) {
            var gb = (e.detail && e.detail.guideBridge) ? e.detail.guideBridge : window.guideBridge;
            if (gb) {
                gb.connect(function () {
                    install(gb);
                });
            }
        });
    }
}(window, jQuery));
