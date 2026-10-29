(function (window, document, $) {
  "use strict";

  // ─── Constants ────────────────────────────────────────────────────────────

  var BRIDGE_READY_EVENT = "bridgeInitializeStart";
  var SUBMIT_BUTTON_SELECTOR = "button.submit";
  var ROOT_SELECTOR = "[id='validation-summary-root']";
  var FOCUSABLE_SELECTOR = "input:not([type='hidden']):not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled])";

  var INPUT_CLASSES = [
    "guideTextBox",
    "guideTermsAndConditions",
    "guideTelephone",
    "guideSwitch",
    "guideRadioButton",
    "guidePasswordBox",
    "guideNumericBox",
    "guideCheckBox",
    "guideFileUpload",
    "guideDropDownList",
    "guideDatePicker"
  ];

  // ─── Model helpers ────────────────────────────────────────────────────────

  function allParentsVisible(node) {
    var parent = node.parent;
    while (parent) {
      if (parent.visible === false) return false;
      parent = parent.parent;
    }
    return true;
  }

  // Error messages can be rich text; show them as plain text only.
  function plainText(html) {
    var doc = new DOMParser().parseFromString(String(html || ""), "text/html");
    return (doc.body.textContent || "").replace(/\s+/g, " ").trim();
  }

  // ─── Core renderer ────────────────────────────────────────────────────────

  /**
   * Validates every visible field without moving focus and returns
   * { som: errorText } for the fields that failed.
   */
  function collectErrors(guideBridge) {
    var errorList = [];
    var errorsBySom = {};
    guideBridge.validate(errorList, null, false);
    errorList.forEach(function (error) {
      errorsBySom[error.som] = error.errorText || "";
    });
    return errorsBySom;
  }

  /** Failing fields in form order, as { som, id, message }. */
  function listErrors(root, guideBridge, errorsBySom) {
    var excludedFields = [];
    try {
      excludedFields = JSON.parse(root.dataset.excludedFields || "[]");
    } catch (e) { /* ignore */ }

    var errors = [];
    guideBridge.visit(function (node) {
      if (INPUT_CLASSES.indexOf(node.className) < 0) return;
      if (!node.visible) return;
      if (!allParentsVisible(node)) return;
      if (excludedFields.indexOf(node.name) >= 0) return;
      if (!errorsBySom.hasOwnProperty(node.somExpression)) return;

      errors.push({
        som:     node.somExpression,
        id:      node.id,
        message: plainText(errorsBySom[node.somExpression]) || node.title || node.name
      });
    });
    return errors;
  }

  // Opens the field's panel, then focuses the field (first option of a group).
  function goToField(guideBridge, error) {
    guideBridge.setFocus(error.som);
    setTimeout(function () {
      var el = document.getElementById(error.id);
      if (!el) return;
      var input = el.querySelector(FOCUSABLE_SELECTOR);
      el.scrollIntoView({ block: "center" });
      if (input) input.focus({ preventScroll: true });
    }, 300);
  }

  /** Renders the list; returns whether there was anything to list. */
  function render(root, guideBridge, errorsBySom) {
    var errors = listErrors(root, guideBridge, errorsBySom);
    var intro = root.querySelector(".validation-summary__intro");
    var list = root.querySelector(".validation-summary__list");
    list.innerHTML = "";

    if (!errors.length) {
      root.hidden = true;
      return false;
    }

    intro.textContent = errors.length === 1
      ? root.dataset.introTextOne
      : root.dataset.introText.replace("{count}", errors.length);

    errors.forEach(function (error) {
      var link = document.createElement("a");
      link.href = "#" + error.id;
      link.textContent = error.message;
      link.addEventListener("click", function (e) {
        e.preventDefault();
        goToField(guideBridge, error);
      });

      var item = document.createElement("li");
      item.appendChild(link);
      list.appendChild(item);
    });

    root.hidden = false;
    return true;
  }

  // ─── Submit interception ──────────────────────────────────────────────────

  // The summary root is hidden until a failed submit, so check its container.
  function isOnCurrentPage(root) {
    return !!root.parentElement && root.parentElement.offsetParent !== null;
  }

  // Move focus to the summary so screen readers announce it.
  function showSummary(root) {
    var panel = root.querySelector(".validation-summary__panel");
    root.scrollIntoView({ block: "start" });
    if (panel) panel.focus({ preventScroll: true });
  }

  /**
   * Runs in the capture phase so it fires before the submit button's own
   * handler (guideBridge.submit), which would otherwise jump to the first
   * invalid field on another page.
   */
  function onSubmitClick(roots, e) {
    var guideBridge = window.guideBridge;
    if (!guideBridge || !e.target.closest(SUBMIT_BUTTON_SELECTOR)) return;

    var root = roots.filter(isOnCurrentPage)[0];
    if (!root) return;

    // Only cancel the submit if the summary lists something; otherwise
    // (e.g. only excluded fields fail) let guideBridge.submit handle it.
    var errorsBySom = collectErrors(guideBridge);
    if (!Object.keys(errorsBySom).length || !render(root, guideBridge, errorsBySom)) {
      root.hidden = true;
      return;
    }

    e.preventDefault();
    e.stopImmediatePropagation();

    root.dataset.vsActive = "true";
    showSummary(root);
  }

  // ─── Initialisation ───────────────────────────────────────────────────────

  function init() {
    var roots = Array.prototype.slice.call(document.querySelectorAll(ROOT_SELECTOR));
    if (!roots.length) return;

    document.addEventListener("click", function (e) { onSubmitClick(roots, e); }, true);

    function attachBridge(guideBridge) {
      guideBridge.connect(function () {
        roots.forEach(function (root) {
          // After a failed submit, keep the list current as errors are fixed.
          // Refresh once navigation has finished and the page is shown.
          function refresh() {
            setTimeout(function () {
              if (root.dataset.vsActive && isOnCurrentPage(root)) render(root, guideBridge, collectErrors(guideBridge));
            }, 0);
          }
          // Wizard navigation fires focus changes (before the page is shown).
          guideBridge.on("elementFocusChanged", refresh);
          // Switching tabs on a tabbed layout fires no GuideBridge event.
          ["click", "keyup"].forEach(function (type) {
            document.addEventListener(type, function (e) {
              if (e.target.closest && e.target.closest("[role='tab']")) refresh();
            });
          });
        });
      });
    }

    if (window.guideBridge && window.guideBridge.isConnected()) {
      attachBridge(window.guideBridge);
    } else {
      window.addEventListener(BRIDGE_READY_EVENT, function (e) {
        attachBridge(e.detail.guideBridge || window.guideBridge);
      });
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }

}(window, document, window.jQuery));
