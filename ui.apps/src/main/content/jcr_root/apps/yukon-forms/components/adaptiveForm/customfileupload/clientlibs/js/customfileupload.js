/**
 * customfileupload.js
 *
 * Overrides isValid and invalidMessage on individual AdobeFileAttachment
 * widget instances whose field model carries fileNamePattern / fileNamePatternMessage.
 *
 * AdobeFileAttachment is defined inside an IIFE in xfalibWidgets.js so its
 * prototype is not accessible globally. Instead we override the two methods
 * as own properties on each matching instance — own properties shadow prototype
 * methods in the normal JS prototype chain lookup.
 *
 * Widget instance is retrieved via:  $(input).data('adobeFileAttachment')
 */
(function (window, $) {
  "use strict";

  var LOG = "[CustomFileUpload] ";

  // ── Default validation (mirrors original AdobeFileAttachment.isValid) ──────
  var DEFAULT_RG1 = /^[^\\/:\*\;\$\%\?"<>\|]+$/;
  var DEFAULT_RG2 = /^\./;
  var DEFAULT_RG3 = /^(nul|prn|con|lpt[0-9]|com[0-9])(\.|$)/i;

  function defaultIsValid(fname) {
    return DEFAULT_RG1.test(fname) && !DEFAULT_RG2.test(fname) && !DEFAULT_RG3.test(fname);
  }

  function buildRegex(patternStr) {
    if (patternStr && patternStr.trim()) {
      try {
        return new RegExp(patternStr.trim());
      } catch (e) {
        console.warn(LOG + "Invalid regex '" + patternStr + "' — using default.", e);
      }
    }
    return null;
  }

  // ── Patch one widget instance ─────────────────────────────────────────────
  function patchInstance(widget, patternStr, messageStr, showErrorMessage, errorMessageContainer) {
    try {
      var customRegex   = buildRegex(patternStr);
      var customMessage = (messageStr && messageStr.trim()) ? messageStr.trim() : null;

      // Own-property assignment shadows AdobeFileAttachment.prototype.isValid
      widget.isValid = function (fname) {
        try {
          errorMessageContainer.html('');
          if (customRegex) {
            return !customRegex.test(fname) && !DEFAULT_RG2.test(fname);
          }
          return defaultIsValid(fname);
        } catch (e) {
          console.error(LOG + "Error in custom isValid — accepting file.", e);
          return true;
        }
      };

      // Only override invalidMessage when a custom message is configured.
      // SIZE and MIMETYPE branches fall through to original locale strings.
      if (customMessage || showErrorMessage) {
        widget.invalidMessage = function (refObj, fileName, invalidFeature) {
          var message = '';
          var strings = xfalib.locale.Strings;
          var loc = xfalib.ut.LocalizationUtil.prototype;
          try {
            if (invalidFeature === refObj.invalidFeature.NAME) {
              if (customMessage) {
                message = customMessage.replace("{fileName}", fileName);
              } else {
                message = loc.getLocalizedMessage("", strings["FileNameInvalid"], [fileName]);
              }
            } else {
              if (invalidFeature === refObj.invalidFeature.SIZE) {
                message = loc.getLocalizedMessage("", strings["FileSizeGreater"], [fileName, refObj.options.fileSizeLimit]);
              } else if (invalidFeature === refObj.invalidFeature.MIMETYPE) {
                message = loc.getLocalizedMessage("", strings["FileMimeTypeInvalid"], [fileName]);
              }
            }
          } catch (e) {
            message = LOG + "Error in custom invalidMessage.";
            console.error(LOG + "Error in custom invalidMessage.", e);
          }
          if (showErrorMessage) {
            errorMessageContainer.css("visibility", message ? "visible" : "hidden");
            errorMessageContainer.text(message);
          } else {
            alert(message);
          }
        };
      }

      console.log(LOG + "Instance patched — pattern: " + (patternStr || "(default)"));
    } catch (e) {
      console.error(LOG + "Failed to patch instance.", e);
    }
  }

  // ── Find and patch all relevant file inputs ───────────────────────────────
  function processInputs() {
    try {
      $("input[type='file']").each(function () {
        try {
          // Guard against double-patching when processInputs reruns for a new panel instance
          if ($(this).data("customFileUploadPatched")) return;

          var widget = $(this).data("adobeFileAttachment");
          if (!widget) return;

          var pattern = $(this).data("fileNamePattern");
          var message = $(this).data("fileNamePatternMessage");
          var showErrorMessageInline = $(this).data("showAlertMessage") !== "true";

          if (pattern || message || showErrorMessageInline) {
            patchInstance(widget, pattern, message, showErrorMessageInline, $(this).closest('.guideFileUpload').find('.guideFieldError'));
            $(this).data("customFileUploadPatched", true);
          }
        } catch (e) {
          console.warn(LOG + "Error processing input.", e);
        }
      });
    } catch (e) {
      console.error(LOG + "Error in processInputs.", e);
    }
  }

  // ── Initialisation ────────────────────────────────────────────────────────
  function onBridgeReady(guideBridge) {
    try {
      guideBridge.connect(function () {
        try {
          processInputs();
        } catch (e) {
          console.error(LOG + "Error after guideBridge.connect().", e);
        }
      });
    } catch (e) {
      console.error(LOG + "guideBridge.connect() failed.", e);
    }
  }

  function init() {
    try {
      if (window.guideBridge &&
          typeof window.guideBridge.isConnected === "function" &&
          window.guideBridge.isConnected()) {
        onBridgeReady(window.guideBridge);
      } else {
        window.addEventListener("bridgeInitializeStart", function (e) {
          try {
            var gb = (e.detail && e.detail.guideBridge)
                ? e.detail.guideBridge : window.guideBridge;
            if (gb) onBridgeReady(gb);
          } catch (e2) {
            console.error(LOG + "Error in bridgeInitializeStart handler.", e2);
          }
        });
      }
    } catch (e) {
      console.error(LOG + "Error in init().", e);
    }
  }

  try {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", function () {
        try { init(); } catch (e) { console.error(LOG + "DOMContentLoaded error.", e); }
      });
    } else {
      init();
    }
  } catch (e) {
    console.error(LOG + "Top-level init error — custom validation disabled.", e);
  }

  // Re-patch whenever new nodes land in the DOM (repeatable panels).
  // adobeFileAttachment may not be set yet on the first mutation — the guard
  // in processInputs skips those inputs, and Foundation's own subsequent DOM
  // writes will trigger another callback that picks them up.
  new MutationObserver(function (mutations) {
    var hasNewNodes = mutations.some(function (m) {
      return m.addedNodes.length > 0;
    });
    if (hasNewNodes) {
      try { processInputs(); } catch (e) { console.error(LOG + "MutationObserver error.", e); }
    }
  }).observe(document.body, { childList: true, subtree: true });

}(window, jQuery));
