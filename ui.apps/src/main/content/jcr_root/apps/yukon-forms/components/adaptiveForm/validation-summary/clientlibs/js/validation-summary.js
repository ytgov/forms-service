(function (window, document, $) {
  "use strict";

  // ─── Constants ────────────────────────────────────────────────────────────

  var BRIDGE_READY_EVENT = "bridgeInitializeStart";

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
        var title     = panel.title || panelName;

        var header = null;
        if (panel.title) {
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
          var rootItems  = panel.parent && panel.parent.items;
          var pageNumber = rootItems ? rootItems.indexOf(panel) : null;

          var goBackBtn = document.createElement("button");
          goBackBtn.type = "button";
          goBackBtn.className = "vs-page-goto-btn";
          goBackBtn.textContent = "Go back to page " + pageNumber + " to fix errors in " + title;
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

  function render(root, guideBridge) {
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
      if (node.valid !== false) return;

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
        errorMessage: node.errorMessage || ""
      });
    });

    // ── Render ────────────────────────────────────────────────────────────
    var $root = $(root);
    $root.off(".vs");
    root.innerHTML = "";

    if (!treeRoot.items.length) {
      var empty = document.createElement("p");
      empty.className = "validation-summary__empty";
      empty.textContent = "No validation errors.";
      root.appendChild(empty);
      return;
    }

    root.appendChild(buildSection(treeRoot, excludedFields, true));

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

  // ─── Initialisation ───────────────────────────────────────────────────────

  function init() {
    var roots = document.querySelectorAll(".validation-summary[id='validation-summary-root']");
    if (!roots.length) return;

    function attachBridge(guideBridge) {
      guideBridge.connect(function () {
        roots.forEach(function (root) {
          guideBridge.on("elementFocusChanged", function () {
            if (root.offsetParent !== null) render(root, guideBridge);
          });
          if (root.offsetParent !== null) render(root, guideBridge);
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
