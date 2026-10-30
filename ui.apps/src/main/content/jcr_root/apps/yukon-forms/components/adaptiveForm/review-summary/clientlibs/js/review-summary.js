(function (window, document, $) {
  "use strict";

  // ─── Constants ────────────────────────────────────────────────────────────

  var BRIDGE_READY_EVENT = "bridgeInitializeStart";
  // Layouts that show one panel at a time, so a panel can be navigated back to.
  var PAGED_LAYOUTS = [
    "fd/af/layouts/panel/wizard",
    "fd/af/layouts/panel/tabbedPanelLayout",
    "fd/af/layouts/panel/verticalTabbedPanelLayout"
  ];

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

  function getDisplayValue(field) {
    var raw     = field.value;
    var display = field.displayValue;
    if (raw === null || raw === undefined || raw === "") return null;
    if (field.className === "guideCheckBox" && field.jsonModel && field.jsonModel.options) {
      // Several selected values arrive as one comma-separated string.
      var selected = Array.isArray(raw) ? raw : String(raw).split(",");
      var labels = selected.map(function (value) {
        var label = value;
        field.jsonModel.options.forEach(function (item) {
          var nameValues = item.split("=");
          if (nameValues.length === 2 && value === nameValues[0]) label = nameValues[1];
        });
        return label;
      });
      return labels.join(", ") || null;
    }
    if (Array.isArray(raw) && raw.length === 0) return null;
    if (Array.isArray(display)) return display.join(", ") || null;
    if (display !== null && display !== undefined && display !== "") return String(display);
    if (Array.isArray(raw)) return raw.join(", ") || null;
    return String(raw);
  }

  function maybeMask(field, value) {
    if (field.className === "guidePasswordBox") return "••••••••";
    if (field.jsonModel && field.jsonModel.sensitive === true) return "••••••••";
    return value;
  }

  // Go-back buttons only make sense when pages (wizard steps or tabs) are
  // shown one at a time.
  function isPaged(rootPanel) {
    var layout = rootPanel && rootPanel.jsonModel && rootPanel.jsonModel.layout;
    return !!layout && PAGED_LAYOUTS.indexOf(layout["sling:resourceType"]) >= 0;
  }

  // Panels with no title, or with "hide title" set, get no heading.
  function hasVisibleTitle(panel) {
    if (!panel.title) return false;
    return !(panel.jsonModel && String(panel.jsonModel.hideTitle) === "true");
  }

  function isRepeatable(panel) {
    var instanceManager = panel.instanceManager;
    return !!instanceManager && instanceManager.maxOccur !== 1 && typeof panel.instanceIndex === "number";
  }

  // Repeatable panel instances share a title, so number them (1-based).
  function panelTitle(panel) {
    var title = panel.title || panel.name;
    return isRepeatable(panel) ? title + " " + (panel.instanceIndex + 1) : title;
  }

  // Panels wrapping repeatable panels get no heading; only each numbered
  // instance is labelled.
  function containsRepeatable(treeNode) {
    return treeNode.children.some(function (child) {
      return isRepeatable(child.panel) || containsRepeatable(child.node);
    });
  }

  // The page's position among the pages the user can see (1-based),
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

  function buildSection(container, excludedFields, showEditLinks, editLinkText, isTopLevelContainer) {
    var section = document.createElement("div");
    section.className = "rs-section";

    container.items.forEach(function (item) {
      if (item.type === "panel") {
        var panel = item.node.panel;
        if (excludedFields.indexOf(panel.name) >= 0) return;

        var panelName = panel.name;
        var panelSom  = panel.somExpression || panelName;
        var title     = panelTitle(panel);

        var header = null;
        if (hasVisibleTitle(panel) && (isTopLevelContainer || !containsRepeatable(item.node))) {
          header = document.createElement("div");
          header.className = "rs-section-header" + (isTopLevelContainer ? " rs-page-header" : "");

          var heading = document.createElement("h3");
          heading.className = "rs-section-title";
          heading.innerHTML = title;
          header.appendChild(heading);
        }

        var childSection = buildSection(item.node, excludedFields, showEditLinks, editLinkText, false);

        var panelBlock = document.createElement("div");
        panelBlock.className = "rs-panel-block";
        if (header) panelBlock.appendChild(header);
        panelBlock.appendChild(childSection);

        if (isTopLevelContainer && showEditLinks && isPaged(panel.parent)) {
          var goBackBtn = document.createElement("button");
          goBackBtn.type = "button";
          goBackBtn.className = "rs-page-goto-btn";
          goBackBtn.textContent = editLinkText.replace("{page}", pageNumber(panel));
          goBackBtn.dataset.rsPanel = panelName;
          goBackBtn.dataset.rsPanelSom = panelSom;
          panelBlock.appendChild(goBackBtn);
        }

        section.appendChild(panelBlock);
      } else {
        var row = document.createElement("div");
        row.className = "rs-field";

        if (item.label) {
          var label = document.createElement("div");
          label.className = "rs-label";
          label.innerHTML = item.label;
          row.appendChild(label);
        }
        var value = document.createElement("div");
        value.className = "rs-value";
        value.innerHTML = item.value;
        row.appendChild(value);

        section.appendChild(row);
      }
    });

    return section;
  }

  // ─── Core renderer ────────────────────────────────────────────────────────

  function render(root, guideBridge) {
    var excludedFields = [];
    var showEditLinks  = root.dataset.showEditLinks !== "false";
    var editLinkText   = root.dataset.editLinkText || "Go back to page {page} to edit";

    try {
      excludedFields = JSON.parse(root.dataset.excludedFields || "[]");
    } catch (e) { /* ignore */ }

    // ── Build tree data structure ─────────────────────────────────────────
    // children[] uses panel object identity as the key so repeatable panel
    // instances (same name, different object) each get their own tree node.
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

      var value = getDisplayValue(node);
      if (value === null) return;
      value = maybeMask(node, value);

      // Panel chain from top-level panel down to node.parent,
      // stopping before the guideContainer root (no parent of its own).
      var chain = [];
      var p = node.parent;
      while (p) {
        if (!p.parent) break;         // p is guideContainer — stop
        if (!p.parent.parent) break;  // p is root panel — stop
        chain.unshift(p);
        p = p.parent;
      }

      var current = treeRoot;
      chain.forEach(function (panel) {
        current = findOrCreateChild(current, panel);
      });

      current.items.push({
        type: "field",
        label: node.jsonModel && node.jsonModel.hideTitle === 'true' ? '' : (node.title || node.name),
        value: value
      });
    });

    // ── Render flat sections ──────────────────────────────────────────────
    var $root = $(root);
    $root.off(".rs");
    root.innerHTML = "";

    if (!treeRoot.items.length) {
      var empty = document.createElement("p");
      empty.className = "review-summary__empty";
      empty.textContent = "No responses to display.";
      root.appendChild(empty);
      return;
    }

    root.appendChild(buildSection(treeRoot, excludedFields, showEditLinks, editLinkText, true));

    if (showEditLinks) {
      // Delegated handler on $root catches clicks regardless of DOM re-renders
      $root.on("click.rs", ".rs-page-goto-btn", function (e) {
        e.preventDefault();
        e.stopPropagation();
        var panelName = this.dataset.rsPanel;
        var panelSom  = this.dataset.rsPanelSom || panelName;
        guideBridge.setFocus(panelSom);
        setTimeout(function () {
          var el = document.getElementById(panelName);
          if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
        }, 300);
      });
    }
  }

  // ─── Initialisation ───────────────────────────────────────────────────────

  function init() {
    var roots = document.querySelectorAll(".review-summary[id='review-summary-root']");
    if (!roots.length) return;

    function attachBridge(guideBridge) {
      guideBridge.connect(function () {
        roots.forEach(function (root) {
          // Render once navigation has finished and the root is visible.
          function refresh() {
            setTimeout(function () {
              if (root.offsetParent !== null) render(root, guideBridge);
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
