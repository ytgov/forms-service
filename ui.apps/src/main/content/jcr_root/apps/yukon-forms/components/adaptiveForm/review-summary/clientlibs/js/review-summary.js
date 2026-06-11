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

  function getDisplayValue(field) {
    var raw     = field.value;
    var display = field.displayValue;
    if (raw === null || raw === undefined || raw === "") return null;
    if (field.className === "guideTextBox" && field.options && field.options.jsonModel && field.options.jsonModel.options) {
      var value = null;
      field.options.jsonModel.options.forEach(item => {
        var nameValues = item.split('=');
        if (nameValues.length === 2) {
          if (raw === nameValues[0]) {
            value = nameValues[1];
          }
        }
      });
      if (value) {
        return value;
      }
    }
    if (Array.isArray(raw) && raw.length === 0) return null;
    if (Array.isArray(display)) return display.join(", ") || null;
    if (display !== null && display !== undefined && display !== "") return String(display);
    if (Array.isArray(raw)) return raw.join(", ") || null;
    if (field.className === "guideTextBox" && field.options && field.options.jsonModel && field.options.jsonModel && field.options.jsonModel.options) {
      field.options.jsonModel.options.forEach(item => {
        var nameValues = item.split('=');
        if (nameValues.length === 2) {
          if (raw === nameValues[0]) {
            raw = nameValues[1];
          }
        }
      });
    }
    return String(raw);
  }

  function maybeMask(field, value) {
    if (field.jsonModel && field.jsonModel.sensitive === true) return "••••••••";
    return value;
  }

  // ─── jsTree conversion ────────────────────────────────────────────────────

  function escapeHtml(str) {
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function toJsTreeNodes(container) {
    return container.items.map(function (item) {
      if (item.type === "panel") {
        var title = item.node.panel.title || item.node.panel.name || "Section";
        return {
          text: escapeHtml(title),
          state: { opened: true },
          children: toJsTreeNodes(item.node),
          li_attr: {
            "class": "rs-panel-node",
            "data-rs-panel": item.node.panel.name,
            "data-rs-panel-som": item.node.panel.somExpression || item.node.panel.name
          }
        };
      }
      return {
        text: "<span class='rs-label'>" + escapeHtml(item.label) + "</span>"
            + "<span class='rs-value'>" + escapeHtml(item.value) + "</span>",
        icon: false,
        children: false,
        li_attr: { "class": "rs-field-node" }
      };
    });
  }

  // ─── Core renderer ────────────────────────────────────────────────────────

  function render(root, guideBridge) {
    var excludedFields = [];
    var showEditLinks  = root.dataset.showEditLinks !== "false";

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

      current.items.push({ type: "field", label: node.title || node.name, value: value });
    });

    // ── Initialise jsTree ─────────────────────────────────────────────────
    var $root = $(root);

    if ($root.data("jstree")) {
      $root.off(".rs");
      $root.jstree("destroy", true);
    }
    root.innerHTML = "";

    var jsData = toJsTreeNodes(treeRoot);

    if (!jsData.length) {
      var empty = document.createElement("p");
      empty.className = "review-summary__empty";
      empty.textContent = "No responses to display.";
      root.appendChild(empty);
      return;
    }

    $root.jstree({
      core: {
        data: jsData,
        themes: { icons: false, dots: false }
      }
    });

    if (showEditLinks) {
      // Delegated handler on $root catches clicks regardless of DOM re-renders
      $root.on("click.rs", ".rs-edit-btn", function (e) {
        e.preventDefault();
        e.stopPropagation();
        var $li = $(this).closest("li.rs-panel-node");
        var panelName = $li.attr("data-rs-panel");
        var panelSom  = $li.attr("data-rs-panel-som") || panelName;
        guideBridge.setFocus(panelSom);
        setTimeout(function () {
          var el = document.getElementById(panelName);
          if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
        }, 300);
      });

      // Append Edit links after jsTree finishes rendering
      $root.on("ready.jstree.rs", function () {
        $root.find("li.rs-panel-node").each(function () {
          $(this).children(".jstree-anchor").after(
            $('<a href="#" class="rs-edit-btn">Edit</a>')
          );
        });
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
