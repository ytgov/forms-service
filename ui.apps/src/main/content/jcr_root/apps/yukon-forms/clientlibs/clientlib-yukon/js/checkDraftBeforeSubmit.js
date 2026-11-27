/*
 * Before submitting a resumed draft that still has files restored from it, checks that the draft exists.
 *
 * A restored file is only a link to the draft's stored copy, and a successful submit deletes the draft and its
 * files. If the same draft is open in a second tab, that tab would submit links to deleted files, and AEM fails
 * with HTTP 500 ("buf" is null in DoRServiceImpl.addAttachmentsToPdf). Instead, the user is asked to attach the
 * files again and the form isn't submitted.
 *
 * The check asks for the draft's data the same way the form runtime does when it loads a draft:
 * POST <form container>.af.prefilldata.json?dataRef=service://FP/draft/<id>. A missing draft gives {"data":""}.
 * The browser doesn't fetch the file links themselves (they're on Azure storage, which would need CORS). If the
 * check itself fails, the form is submitted as normal.
 *
 * Wrapped in an IIFE so the Rule Editor doesn't list anything from this file.
 */
(function (window, $) {
  "use strict";

  var LOG = "[DraftCheck] ";
  var MESSAGES = {
    en: "\"{label}\": the saved draft this file came from was already submitted or deleted, so the file is no "
        + "longer available. Remove it, attach it again, then submit.",
    fr: "« {label} » : le brouillon d'où vient ce fichier a déjà été soumis ou supprimé, et le fichier "
        + "n'est plus disponible. Retirez-le, joignez-le de nouveau, puis soumettez le formulaire."
  };

  function isFrench() {
    return (document.documentElement.lang || "").toLowerCase().indexOf("fr") === 0;
  }

  function plainText(html) {
    return $("<div>").html(html || "").text().trim();
  }

  /** A file restored from a draft is a link (string); a newly attached one isn't. */
  function isRestoredFile(file) {
    return typeof file === "string" && file.indexOf("/") !== -1;
  }

  /** Visible file attachment fields that still hold a file restored from the draft. */
  function fieldsWithRestoredFiles(guideBridge) {
    var fields = [];
    guideBridge.visit(function (node) {
      if (node.className === "guideFileUpload" && node.visible !== false
          && $.isArray(node.fileList) && node.fileList.some(isRestoredFile)) {
        fields.push(node);
      }
    });
    return fields;
  }

  /** Resolves to true (draft found), false (draft gone) or null (couldn't tell). */
  function draftExists(guideBridge, draftId) {
    var deferred = $.Deferred(),
        container = guideBridge.userConfig && guideBridge.userConfig.submitConfig
            && guideBridge.userConfig.submitConfig.aemFormComponentPath,
        url = guideBridge._getGuidePathUrl(".af.prefilldata.json", container || undefined)
            + "?dataRef=" + encodeURIComponent("service://FP/draft/" + draftId);
    $.ajax({ url: url, type: "POST", dataType: "json", timeout: 15000 })
      .done(function (data) {
        var gone = !data || ((data.data === "" || data.data === null || data.data === undefined) && !data.attachments);
        deferred.resolve(!gone);
      })
      .fail(function (xhr, status) {
        console.warn(LOG + "Couldn't check the draft (" + status + "), submitting as normal.");
        deferred.resolve(null);
      });
    return deferred.promise();
  }

  function errorContainer(field) {
    var input = document.getElementById(field.id + "_widget");
    return input ? $(input).closest(".guideFileUpload").find(".guideFieldError").first() : $();
  }

  function showMissingFiles(guideBridge, fields) {
    var template = isFrench() ? MESSAGES.fr : MESSAGES.en;
    guideBridge.setFocus(fields[0].somExpression);
    // After the navigation, so the message is shown on the field's panel.
    setTimeout(function () {
      fields.forEach(function (field) {
        var message = template.replace("{label}", plainText(field.title) || field.name),
            $error = errorContainer(field);
        if ($error.length) {
          $error.text(message).css("visibility", "visible").attr("data-yf-draft-check", "true");
        } else {
          window.alert(message);
        }
      });
    }, 300);
  }

  function clearMessage(field) {
    var $error = errorContainer(field);
    if ($error.attr("data-yf-draft-check")) {
      $error.text("").removeAttr("data-yf-draft-check");
    }
  }

  function install(guideBridge) {
    if (!guideBridge || typeof guideBridge.submit !== "function" || guideBridge.submit.yfDraftCheck) {
      return;
    }
    var submit = guideBridge.submit,
        checking = false,
        flagged = [];

    guideBridge.submit = function () {
      var self = this,
          args = arguments,
          draftId = guideBridge.customContextProperty("draftId"),
          fields = draftId ? fieldsWithRestoredFiles(guideBridge) : [];
      if (!fields.length) {
        return submit.apply(self, args);
      }
      if (checking) {
        return undefined; // a second click while the check runs
      }
      checking = true;
      draftExists(guideBridge, draftId).then(function (exists) {
        checking = false;
        if (exists === false) {
          console.warn(LOG + "Draft " + draftId + " no longer exists; asking for the files again.");
          flagged = fields;
          showMissingFiles(guideBridge, fields);
        } else {
          submit.apply(self, args);
        }
      });
      return undefined;
    };
    guideBridge.submit.yfDraftCheck = true;

    // Clear the message once the user changes the field (e.g. removes the file or attaches a new one).
    // The changed field is the payload's target; the event's own target is guideBridge.
    guideBridge.on("elementValueChanged", function (event, payload) {
      var target = payload && payload.target;
      flagged = flagged.filter(function (field) {
        if (target === field || (target && target.parent === field)) {
          clearMessage(field);
          return false;
        }
        return true;
      });
    });
  }

  try {
    if (window.guideBridge) {
      install(window.guideBridge);
    }
    window.addEventListener("bridgeInitializeStart", function (event) {
      install((event.detail && event.detail.guideBridge) || window.guideBridge);
    });
  } catch (e) {
    console.error(LOG + "Couldn't install the draft check.", e);
  }
}(window, jQuery));
