(function (window, document, $) {
  "use strict";

  // ─── Constants ────────────────────────────────────────────────────────────

  var BRIDGE_READY_EVENT = "bridgeInitializeStart";
  var SUBMIT_BUTTON_SELECTOR = "button.submit";

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

  // Repeatable panel instances share a title, so number them (1-based).
  function panelTitle(panel) {
    var title = panel.title || panel.name;
    var instanceManager = panel.instanceManager;
    if (!instanceManager || instanceManager.maxOccur === 1) return title;
    if (typeof panel.instanceIndex !== "number") return title;
    return title + " " + (panel.instanceIndex + 1);
  }

  // The page's position among the wizard pages the user can see (1-based),
  // so pages hidden by rules don't throw the numbering off.
  function pageNumber(panel) {
    var pages = (panel.parent && panel.parent.items) || [];
    var number = 0;
    for (var i = 0; i < pages.length; i++) {
      if (pages[i].visible !== false) number++;
      if (pages[i] === panel) return number;
    }
    return null;
  }

  // ─── Flat list rendering ──────────────────────────────────────────────────

  function buildSection(container, excludedFields, isTopLevelContainer) {
    var section = document.createElement("div");
    section.className = "vs-section";

    container.items.forEach(function (item) {
      if (item.type === "panel") {
        var panel = item.node.panel;
        if (excludedFields.indexOf(panel.name) >= 0) return;

        var panelName = panel.name;
        var panelSom  = panel.somExpression || panelName;
        var title     = panelTitle(panel);
        var number    = isTopLevelContainer ? pageNumber(panel) : null;

        if (number) {
          title = panel.title ? "Page " + number + ": " + title : "Page " + number;
        }

        var header = null;
        if (panel.title || number) {
          header = document.createElement("div");
          header.className = "vs-section-header" + (isTopLevelContainer ? " vs-page-header" : "");

          var heading = document.createElement("h3");
          heading.className = "vs-section-title";
          heading.textContent = title;
          header.appendChild(heading);
        }

        var childSection = buildSection(item.node, excludedFields, false);

        var panelBlock = document.createElement("div");
        panelBlock.className = "vs-panel-block";
        if (header) panelBlock.appendChild(header);
        panelBlock.appendChild(childSection);

        if (isTopLevelContainer) {
          var goBackBtn = document.createElement("button");
          goBackBtn.type = "button";
          goBackBtn.className = "vs-page-goto-btn";
          goBackBtn.textContent = number ? "Go back to page " + number : "Go back to " + title;
          goBackBtn.dataset.vsPanel = panelName;
          goBackBtn.dataset.vsPanelSom = panelSom;
          panelBlock.appendChild(goBackBtn);
        }

        section.appendChild(panelBlock);
      } else {
        var row = document.createElement("div");
        row.className = "vs-field";

        var label = document.createElement("div");
        label.className = "vs-label";
        label.textContent = item.label;

        var error = document.createElement("div");
        error.className = "vs-error";
        error.textContent = item.errorMessage;

        row.appendChild(label);
        row.appendChild(error);
        section.appendChild(row);
      }
    });

    return section;
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

  function render(root, guideBridge, errorsBySom) {
    var body = root.querySelector(".validation-summary__body");
    var excludedFields = [];

    try {
      excludedFields = JSON.parse(root.dataset.excludedFields || "[]");
    } catch (e) { /* ignore */ }

    // ── Collect invalid fields into tree structure ─────────────────────────
    // children[] uses panel object identity so repeatable panel instances
    // (same name, different object) each get their own tree node.
    var treeRoot = { items: [], children: [] };

    function findOrCreateChild(container, panel) {
      for (var i = 0; i < container.children.length; i++) {
        if (container.children[i].panel === panel) return container.children[i].node;
      }
      var treeNode = { panel: panel, items: [], children: [] };
      container.children.push({ panel: panel, node: treeNode });
      container.items.push({ type: "panel", node: treeNode });
      return treeNode;
    }

    guideBridge.visit(function (node) {
      if (INPUT_CLASSES.indexOf(node.className) < 0) return;
      if (!node.visible) return;
      if (!allParentsVisible(node)) return;
      if (!node.parent) return;
      if (excludedFields.indexOf(node.name) >= 0) return;
      if (!errorsBySom.hasOwnProperty(node.somExpression)) return;

      // Panel chain from top-level panel down to node.parent,
      // stopping before the guideContainer root (no parent of its own).
      var chain = [];
      var p = node.parent;
      while (p) {
        if (!p.parent) break;        // p is guideContainer — stop
        if (!p.parent.parent) break; // p is root panel — stop
        chain.unshift(p);
        p = p.parent;
      }

      var current = treeRoot;
      chain.forEach(function (panel) {
        current = findOrCreateChild(current, panel);
      });

      current.items.push({
        type:         "field",
        label:        node.title || node.name,
        errorMessage: errorsBySom[node.somExpression]
      });
    });

    // ── Render ────────────────────────────────────────────────────────────
    var $root = $(root);
    $root.off(".vs");
    body.innerHTML = "";

    if (!treeRoot.items.length) {
      root.hidden = true;
      return;
    }

    body.appendChild(buildSection(treeRoot, excludedFields, true));
    root.hidden = false;

    $root.on("click.vs", ".vs-page-goto-btn", function (e) {
      e.preventDefault();
      e.stopPropagation();
      var panelName = this.dataset.vsPanel;
      var panelSom  = this.dataset.vsPanelSom || panelName;
      guideBridge.setFocus(panelSom);
      setTimeout(function () {
        var el = document.getElementById(panelName);
        if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
      }, 300);
    });
  }

  // ─── Submit interception ──────────────────────────────────────────────────

  // The summary root is hidden until a failed submit, so check its container.
  function isOnCurrentPage(root) {
    return !!root.parentElement && root.parentElement.offsetParent !== null;
  }

  function showSummary(root) {
    window.scrollTo(0, 0);
    var title = root.querySelector(".validation-summary__title");
    if (title) title.focus({ preventScroll: true });
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

    var errorsBySom = collectErrors(guideBridge);
    if (!Object.keys(errorsBySom).length) {
      root.hidden = true;
      return;
    }

    e.preventDefault();
    e.stopImmediatePropagation();

    root.dataset.vsActive = "true";
    render(root, guideBridge, errorsBySom);
    showSummary(root);
  }

  // ─── Initialisation ───────────────────────────────────────────────────────

  function init() {
    var roots = Array.prototype.slice.call(
      document.querySelectorAll(".validation-summary[id='validation-summary-root']"));
    if (!roots.length) return;

    document.addEventListener("click", function (e) { onSubmitClick(roots, e); }, true);

    function attachBridge(guideBridge) {
      guideBridge.connect(function () {
        roots.forEach(function (root) {
          // After a failed submit, keep the list current as errors are fixed.
          guideBridge.on("elementFocusChanged", function () {
            if (root.dataset.vsActive && isOnCurrentPage(root)) render(root, guideBridge, collectErrors(guideBridge));
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
