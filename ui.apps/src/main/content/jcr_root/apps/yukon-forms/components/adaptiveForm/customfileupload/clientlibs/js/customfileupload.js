/**
 * customfileupload.js — File Attachment (Custom).
 *
 * Patches each AdobeFileAttachment widget of this component (marked data-yf-file-upload) to:
 *  - check each added file in order: file type (AEM's check) → file name characters → same name as
 *    an attached file → maximum file size → maximum total size (optional), stopping at the first
 *    failure, with one authorable
 *    message per rejected file;
 *  - show each file's size in its row and a total row under the list (display only);
 *  - show messages under the total row (box turns red), linked to the button through aria-describedby, cleared on the next
 *    add or remove.
 *
 * Sizes are compared on raw bytes; formatSize() (yukon-forms.utils) only formats them for display.
 * AEM's maximum file size is in binary MB (file.size / 1024 / 1024 > limit), so formatSize uses 1024.
 *
 * AdobeFileAttachment is defined inside an IIFE in xfalibWidgets.js, so its prototype isn't global.
 * Methods are overridden as own properties on each instance, which shadow the prototype's.
 * The widget instance is $(input).data('adobeFileAttachment').
 */
(function (window, $) {
  "use strict";

  var LOG = "[CustomFileUpload] ";
  var BYTES_PER_MB = 1024 * 1024;
  var messageCount = 0;

  // ── Default name validation (mirrors AdobeFileAttachment.isValid) ─────────
  var DEFAULT_RG1 = /^[^\\/:\*\;\$\%\?"<>\|]+$/;
  var DEFAULT_RG2 = /^\./;
  var DEFAULT_RG3 = /^(nul|prn|con|lpt[0-9]|com[0-9])(\.|$)/i;

  function defaultIsValid(fname) {
    return DEFAULT_RG1.test(fname) && !DEFAULT_RG2.test(fname) && !DEFAULT_RG3.test(fname);
  }

  function buildRegex(patternStr) {
    if (patternStr && String(patternStr).trim()) {
      try {
        return new RegExp(String(patternStr).trim());
      } catch (e) {
        console.warn(LOG + "Invalid regex '" + patternStr + "' — using default.", e);
      }
    }
    return null;
  }

  function sizeText(bytes, language) {
    return typeof window.formatSize === "function" ? window.formatSize(bytes, language) : "";
  }

  /** A limit for a message: "20 MB", not "20.0 MB" (French "20 Mo"). Other decimals are kept. */
  function limitText(bytes, language) {
    return sizeText(bytes, language).replace(/[.,]0(?=[\s\u00A0])/, "");
  }

  function fillMessage(template, values, language) {
    if (typeof window.fillMessagePlaceholders === "function") {
      return window.fillMessagePlaceholders(template, values, language);
    }
    return String(template || "");
  }

  function displayName(fileName) {
    try {
      return xfalib.ut.Utilities._getNameWithoutMarker(fileName);
    } catch (e) {
      return fileName;
    }
  }

  // ── Draft resume: a restored attachment has a URL but no File, so read its size from the server ──
  var sizeRequests = {};

  function attachmentUrl(widget, fileUrl) {
    var url = fileUrl,
        contextPath = widget.options._getUrl || "";
    if (contextPath && url.lastIndexOf(contextPath, 0) !== 0) {
      url = contextPath + url;
    }
    // Same encoding as AdobeFileAttachment.previewFile: only the file name after the last slash.
    var lastSlash = url.lastIndexOf("/");
    if (lastSlash >= 0 && url.indexOf("?") === -1) {
      url = url.substr(0, lastSlash) + "/" + encodeURIComponent(url.substr(lastSlash + 1));
    }
    return url;
  }

  /** Resolves to the size in bytes, or null. Asks for one byte so the file itself isn't downloaded. */
  function fetchAttachmentSize(widget, fileUrl) {
    if (sizeRequests[fileUrl]) {
      return sizeRequests[fileUrl];
    }
    if (typeof window.fetch !== "function") {
      return Promise.resolve(null);
    }
    var controller = typeof window.AbortController === "function" ? new window.AbortController() : null;
    sizeRequests[fileUrl] = window.fetch(attachmentUrl(widget, fileUrl), {
      method: "GET",
      credentials: "same-origin",
      headers: { "Range": "bytes=0-0" },
      signal: controller ? controller.signal : undefined
    }).then(function (response) {
      var size = null,
          range = response.headers.get("Content-Range"),
          length = response.headers.get("Content-Length");
      if (response.status === 206 && range && /\/(\d+)\s*$/.test(range)) {
        size = parseInt(/\/(\d+)\s*$/.exec(range)[1], 10);
      } else if (response.status === 200 && length !== null && /^\d+$/.test(length)) {
        size = parseInt(length, 10);
      }
      if (controller) {
        controller.abort(); // the server ignored the range: don't download the rest
      }
      return size;
    }).catch(function (e) {
      console.warn(LOG + "Couldn't read the size of a restored attachment.", e);
      delete sizeRequests[fileUrl];
      return null;
    });
    return sizeRequests[fileUrl];
  }

  // ── Patch one widget instance ─────────────────────────────────────────────
  function patchInstance(widget, $input) {
    var proto = Object.getPrototypeOf(widget),
        language = $input.data("yfLanguage") === "fr" ? "fr" : "en",
        customRegex = buildRegex($input.data("fileNamePattern")),
        alertMode = $input.data("showAlertMessage") === true || $input.data("showAlertMessage") === "true",
        maxTotalBytes = parseFloat($input.attr("data-yf-max-total-size")) * BYTES_PER_MB, // NaN: not set
        templates = {
          fileTooLarge: $input.attr("data-yf-msg-file-too-large") || "",
          totalTooLarge: $input.attr("data-yf-msg-total-too-large") || "",
          fileName: $input.attr("data-yf-msg-file-name") || "",
          duplicate: $input.attr("data-yf-msg-duplicate") || "",
          total: $input.attr("data-yf-msg-total") || ""
        },
        $button = widget.$elementFileUploadBtn,
        $list = widget.$elementFileList,
        originalDescribedBy = $button.attr("aria-describedby") || "",
        $messages = $("<div class='yf-fu-messages'></div>"),
        $total = $("<div class='yf-fu-total'></div>").attr("hidden", "hidden"),
        sizes = {};          // raw bytes by file name and by attachment URL
    var pendingSize = null;  // size of the file handleChange is adding right now

    // Box order (wireframe): button, file list, total row, then messages.
    $list.after($total);
    $total.after($messages);
    var $box = $list.closest(".afFileUpload");

    // ── Messages ──
    function clearMessages() {
      $messages.empty();
      $box.removeClass("yf-fu-has-error");
      if (originalDescribedBy) {
        $button.attr("aria-describedby", originalDescribedBy);
      } else {
        $button.removeAttr("aria-describedby");
      }
    }

    function showMessages(texts) {
      clearMessages();
      if (!texts.length) {
        return;
      }
      if (alertMode) {
        window.alert(texts.join("\n"));
        return;
      }
      var ids = [];
      $.each(texts, function (i, text) {
        var id = "yf-fu-message-" + (++messageCount);
        ids.push(id);
        $("<p class='yf-fu-message' role='alert'></p>").attr("id", id).text(text).appendTo($messages);
      });
      $box.addClass("yf-fu-has-error");
      $button.attr("aria-describedby", $.trim(originalDescribedBy + " " + ids.join(" ")));
    }

    function rejectionMessage(rejection, limitBytes) {
      if (rejection.reason === "type") {
        try {
          return $.trim(xfalib.ut.LocalizationUtil.prototype.getLocalizedMessage(
              "", xfalib.locale.Strings["FileMimeTypeInvalid"], [rejection.name]));
        } catch (e) {
          return rejection.name;
        }
      }
      var values = {
        fileName: rejection.name,
        size: sizeText(rejection.size, language),
        maxSize: isFinite(limitBytes) ? limitText(limitBytes, language) : "",
        maxTotal: maxTotalBytes > 0 ? limitText(maxTotalBytes, language) : ""
      };
      var template = rejection.reason === "size" ? templates.fileTooLarge
          : rejection.reason === "total" ? templates.totalTooLarge
          : rejection.reason === "duplicate" ? templates.duplicate
          : templates.fileName;
      return fillMessage(template, values, language);
    }

    /** Sum of the listed files' raw bytes; a restored file whose size isn't known yet counts as 0. */
    function listedBytes() {
      var total = 0;
      $list.children("li.guide-fu-fileItem").each(function () {
        var bytes = Number($(this).attr("data-yf-size"));
        if (bytes > 0) {
          total += bytes;
        }
      });
      return total;
    }

    /** Lower-cased names of the listed files, including attachments restored from a draft. */
    function listedNames() {
      var names = {};
      $list.children("li.guide-fu-fileItem").find(".guide-fu-fileName").each(function () {
        names[$.trim($(this).text()).toLowerCase()] = true;
      });
      return names;
    }

    // ── Total row: the sum of raw bytes, formatted once ──
    function updateTotal() {
      var $rows = $list.children("li.guide-fu-fileItem"),
          total = 0,
          known = true;
      $rows.each(function () {
        var bytes = $(this).attr("data-yf-size");
        if (bytes === undefined || bytes === "") {
          known = false;
        } else {
          total += Number(bytes);
        }
      });
      // Hidden with no files, and while a restored file's size is still unknown, so it's never wrong.
      if (!$rows.length || !known) {
        $total.attr("hidden", "hidden").text("");
        return;
      }
      $total.text(fillMessage(templates.total, { total: sizeText(total, language) }, language)).removeAttr("hidden");
      alignTotal();
    }

    /**
     * Lines the total up with the size column: its right edge on wide screens, where sizes are
     * right-aligned, and its left edge on narrow ones, where sizes sit under the file name. Measured,
     * because the form theme sets the row's padding and the icons' widths.
     */
    function alignTotal() {
      var row = $list.children("li.guide-fu-fileItem")[0],
          size = row && $(row).find(".yf-fu-fileSize")[0],
          name = row && $(row).find(".guide-fu-fileName")[0];
      if (!size || !name || $total.attr("hidden")) {
        return;
      }
      var rowBox = row.getBoundingClientRect(),
          sizeBox = size.getBoundingClientRect(),
          beside = sizeBox.top < name.getBoundingClientRect().bottom;
      $total.css(beside
          ? { textAlign: "right", paddingRight: Math.max(0, Math.round(rowBox.right - sizeBox.right)) + "px", paddingLeft: "" }
          : { textAlign: "left", paddingLeft: Math.max(0, Math.round(sizeBox.left - rowBox.left)) + "px", paddingRight: "" });
    }
    $(window).on("resize", alignTotal);

    function setRowSize($row, bytes) {
      $row.attr("data-yf-size", bytes);
      $row.find(".yf-fu-fileSize").text(sizeText(bytes, language));
      updateTotal();
    }

    // ── Name check (configurable pattern) ──
    widget.isValid = function (fname) {
      try {
        if (customRegex) {
          return !customRegex.test(fname) && !DEFAULT_RG2.test(fname);
        }
        return defaultIsValid(fname);
      } catch (e) {
        console.error(LOG + "Error in custom isValid — accepting file.", e);
        return true;
      }
    };

    // ── Row: name, size, then AEM's close button ──
    widget.fileItem = function (fileName, comment, fileUrl) {
      var $row = proto.fileItem.call(this, fileName, comment, fileUrl),
          name = displayName(fileName),
          bytes = pendingSize !== null ? pendingSize
              : (fileUrl && sizes.hasOwnProperty(fileUrl)) ? sizes[fileUrl]
              : sizes.hasOwnProperty(name) ? sizes[name] : null,
          $size = $("<span class='yf-fu-fileSize'></span>");
      $row.find(".guide-fu-fileName").after($size);
      if (bytes !== null) {
        $row.attr("data-yf-size", bytes);
        $size.text(sizeText(bytes, language));
      } else if (fileUrl && fileUrl.indexOf("/") !== -1) {
        fetchAttachmentSize(this, fileUrl).then(function (fetched) {
          if (fetched !== null) {
            sizes[fileUrl] = fetched;
            sizes[name] = fetched;
            if ($.contains(document, $row[0])) {
              setRowSize($row, fetched);
            }
          }
        });
      }
      return $row;
    };

    // ── Remove: clear messages, then AEM's handler ──
    // AEM finds the file to remove from the element just before the close button ($(target).prev()),
    // which is our size label. Take it out first so AEM reads the file name; the row is removed anyway.
    // Otherwise the file stays in the field's value and comes back on the next change.
    widget.handleClick = function (event) {
      clearMessages();
      if (event && event.target) {
        $(event.target).siblings(".yf-fu-fileSize").remove();
      }
      return proto.handleClick.call(this, event);
    };

    // ── Add: type → name → size per file, first failure only ──
    widget.handleChange = function (evnt) {
      if (this._isFileUpdate) {
        return;
      }
      var files = evnt && evnt.target ? evnt.target.files : undefined;
      if (files === undefined) {
        return proto.handleChange.call(this, evnt); // browsers without FileList
      }
      clearMessages();
      if (!this.options.multiSelect && this.$fileDomElements.length > 1) {
        this.$fileDomElements.splice(0, 1);
      }
      this.resetIfNotMultiSelect();

      var self = this,
          limitBytes = parseFloat(this.options.fileSizeLimit) * BYTES_PER_MB,
          totalBytes = listedBytes(), // grows as each accepted file is added
          names = listedNames(),      // likewise
          invalidIndexes = [],
          rejections = [];
      $.each(files, function (index, file) {
        var name = file.name.split("\\").pop(),
            reason = null;
        if (file.type && !self.regexMimeTypeList.some(function (rx) { return rx && rx.test(file.type); })) {
          reason = "type";
        } else if (!self.isValid(name)) {
          reason = "name";
        } else if (names[name.toLowerCase()]) { // same name as an attached file, ignoring case
          reason = "duplicate";
        } else if (file.size > limitBytes) { // raw bytes; false when no limit is set
          reason = "size";
        } else if (maxTotalBytes > 0 && totalBytes + file.size > maxTotalBytes) { // false when not set
          reason = "total";
        }
        if (reason) {
          invalidIndexes.push(index);
          rejections.push({ name: name, size: file.size, reason: reason });
          return;
        }
        sizes[name] = file.size;
        totalBytes += file.size;
        names[name.toLowerCase()] = true;
        pendingSize = file.size;
        try {
          self.showFileList(name);
        } finally {
          pendingSize = null;
        }
        self.values.push(name);
      });

      // Take rejected files out of the file input, as AEM does.
      if (invalidIndexes.length > 0 && this.$fileDomElements.length > 0) {
        var $current = this.$fileDomElements[this.$fileDomElements.length - 1];
        if ($current && $current.length > 0) {
          if ($current[0].files.length === invalidIndexes.length) {
            this.$fileDomElements.splice(-1, 1);
          } else {
            this._deleteFilesFromInputDom($current, invalidIndexes);
          }
        }
      }
      this.$element.trigger("change.fileupload");
      updateTotal();
      showMessages($.map(rejections, function (rejection) { return rejectionMessage(rejection, limitBytes); }));
    };

    // The widget bound the prototype's handleChange to the original input when it was built; inputs
    // cloned later (one per click of the button) bind the override themselves. jQuery's $.proxy keeps
    // the function's guid, so .off() with the prototype function removes that binding only.
    widget.$element.off("change", proto.handleChange).on("change", $.proxy(widget.handleChange, widget));

    // Server-side errors on submit (e.g. the total check): shown like the widget's own messages.
    $input.on("yf-fu-server-error", function (event, text) {
      showMessages([text]);
    });

    // Rows are rebuilt from the model (e.g. after a draft save or restore); keep the total in step.
    if (window.MutationObserver) {
      new MutationObserver(updateTotal).observe($list[0], { childList: true });
    }
    updateTotal();
  }

  // ── Server-side validation errors ─────────────────────────────────────────
  // On a failed submit, guideBridge.handleServerValidationError calls markAsInvalid(message) on the
  // field named in the error. AEM's file upload model is a composite field, not a Field, and has no
  // markAsInvalid: the call throws and the error is never shown. Give it one that passes the message
  // to this component's widget (its input's id is the field's id + "_widget").
  function installServerErrorHandler() {
    var model = window.guidelib && window.guidelib.model && window.guidelib.model.GuideFileUpload;
    if (!model || typeof model.prototype.markAsInvalid === "function") {
      return;
    }
    model.prototype.markAsInvalid = function (errorText) {
      var input = document.getElementById(this.id + "_widget");
      if (input) {
        $(input).trigger("yf-fu-server-error", [String(errorText || "")]);
      }
    };
  }

  // ── Find and patch this component's file inputs ───────────────────────────
  function processInputs() {
    installServerErrorHandler();
    $("input[type='file'][data-yf-file-upload]").each(function () {
      try {
        var $input = $(this);
        if ($input.data("customFileUploadPatched")) return; // repeatable panels rerun this
        var widget = $input.data("adobeFileAttachment");
        if (!widget) return; // not initialised yet; a later DOM change triggers another pass
        $input.data("customFileUploadPatched", true);
        patchInstance(widget, $input);
      } catch (e) {
        console.error(LOG + "Failed to patch a file upload.", e);
      }
    });
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
            var gb = (e.detail && e.detail.guideBridge) ? e.detail.guideBridge : window.guideBridge;
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
    // Patch file uploads added later (repeatable panels). adobeFileAttachment may not be set on the
    // first mutation; Foundation's own later DOM writes trigger another pass.
    new MutationObserver(function (mutations) {
      var hasNewNodes = mutations.some(function (m) {
        return m.addedNodes.length > 0;
      });
      if (hasNewNodes) {
        try { processInputs(); } catch (e) { console.error(LOG + "MutationObserver error.", e); }
      }
    }).observe(document.body, { childList: true, subtree: true });
  } catch (e) {
    if (typeof console !== "undefined") {
      console.error(LOG + "Top-level init error — custom file upload disabled.", e);
    }
  }
}(window, jQuery));
