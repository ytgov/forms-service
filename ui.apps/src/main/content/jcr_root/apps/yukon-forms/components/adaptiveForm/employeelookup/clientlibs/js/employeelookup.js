/*
 * Employee Lookup (YUKONAEMF-88): type-ahead search for a YG employee.
 *
 * Calls the searchEmployees operation of the ygDirectory form data model through the form's
 * .af.dermis endpoint (the same request the rule editor's Invoke Service makes), with its own
 * debounce and timeout and without the full-page loading overlay. Each result is one position: a
 * person with several titles appears once per title. Writes to sibling fields in the same panel,
 * found by name (configurable in the dialog):
 *   - empSelectedEmail:      email of the selected employee, empty when nothing is selected
 *   - empSelectedTitle:      title of the selected position (pass it to getEmployee with the department)
 *   - empSelectedDepartment: department of the selected position
 *   - empSearchStatus:       ok | noResults | unavailable (also when the user is not signed in)
 *
 * Accessibility: WAI-ARIA 1.2 combobox on the input (role=combobox, aria-autocomplete=list,
 * aria-expanded, aria-controls, aria-activedescendant), listbox/option roles on the menu, and a
 * polite live region announcing the number of results. typeahead.js provides Up/Down, Enter and Esc.
 *
 * Several lookups can sit on one form: each instance only touches its own panel.
 */
(function ($) {
    "use strict";

    var INIT_KEY = "employeeLookupInitialized";
    var STATUS = { OK: "ok", NO_RESULTS: "noResults", UNAVAILABLE: "unavailable" };
    var WORD_BREAK = /[\s\-'’._@]/;

    function format(template, args) {
        return String(template).replace(/\{(\d+)\}/g, function (match, index) {
            return args[index] !== undefined ? args[index] : match;
        });
    }

    function escapeHtml(value) {
        return String(value == null ? "" : value).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[c];
        });
    }

    function foldChar(c) {
        return c.normalize ? c.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase() : c.toLowerCase();
    }

    function keywordTokens(query) {
        return Array.from(String(query || "")).map(foldChar).join("").split(/[\s,;]+/).filter(Boolean);
    }

    /**
     * Escapes text and wraps in <strong> every part that matches the start of a word for one of the
     * typed words, ignoring case and accents, the same way the server matches.
     */
    function highlight(text, query) {
        var chars = Array.from(String(text || ""));
        var folded = "";
        var owner = [];
        chars.forEach(function (c, i) {
            var f = foldChar(c);
            for (var k = 0; k < f.length; k++) {
                folded += f[k];
                owner.push(i);
            }
        });
        var marked = chars.map(function () { return false; });
        keywordTokens(query).forEach(function (token) {
            var from = 0;
            var at;
            while ((at = folded.indexOf(token, from)) !== -1) {
                var start = owner[at];
                if (start === 0 || WORD_BREAK.test(chars[start - 1])) {
                    for (var k = at; k < at + token.length; k++) {
                        marked[owner[k]] = true;
                    }
                }
                from = at + 1;
            }
        });
        var html = "";
        var open = false;
        chars.forEach(function (c, i) {
            if (marked[i] && !open) {
                html += "<strong>";
                open = true;
            } else if (!marked[i] && open) {
                html += "</strong>";
                open = false;
            }
            html += escapeHtml(c);
        });
        return open ? html + "</strong>" : html;
    }

    function displayName(employee) {
        var name = ((employee.first_name || "") + " " + (employee.last_name || "")).trim();
        return name || employee.full_name || employee.email || "";
    }

    /** "First Last, Title (email)": the title tells apart the positions of people who hold several. */
    function selectedLabel(employee) {
        return displayName(employee) + (employee.title ? ", " + employee.title : "") + " (" + employee.email + ")";
    }

    function meta(employee, query) {
        var parts = [highlight(employee.email, query)];
        if (employee.title) {
            parts.push(escapeHtml(employee.title));
        }
        if (employee.department) {
            parts.push(escapeHtml(employee.department));
        }
        return parts.join(" · ");
    }

    function EmployeeLookup(input) {
        this.$input = $(input);
        this.$root = this.$input.closest("[data-cmp-is='adaptiveFormEmployeeLookup']");
        this.$status = this.$root.find(".employee-lookup__status");
        var data = this.$root.data();
        this.fdmPath = data.fdmPath;
        this.operation = data.operation;
        this.emailFieldName = data.emailField || "empSelectedEmail";
        this.titleFieldName = data.titleField || "empSelectedTitle";
        this.departmentFieldName = data.departmentField || "empSelectedDepartment";
        this.statusFieldName = data.statusField || "empSearchStatus";
        this.minChars = parseInt(data.minChars, 10) || 3;
        this.debounceMs = parseInt(data.debounceMs, 10) || 300;
        this.timeoutMs = parseInt(data.timeoutMs, 10) || 10000;
        this.messages = {
            noResults: data.msgNoResults,
            moreResults: data.msgMoreResults,
            resultsAvailable: data.msgResultsAvailable
        };
        this.requestSeq = 0;
        this.timer = null;
        this.selectedText = null;
        this.lastTotal = 0;
        this.lastCount = 0;
        this.init();
    }

    EmployeeLookup.prototype = {

        init: function () {
            var self = this;
            var inputId = this.$input.attr("id");
            this.listboxId = inputId + "_listbox";

            this.$input.typeahead(
                { hint: false, highlight: false, minLength: this.minChars },
                {
                    name: "employees",
                    // Server returns at most 10; a high limit avoids typeahead.js 0.11.1 dropping async results.
                    limit: 100,
                    display: selectedLabel,
                    source: function (query, syncResults, asyncResults) {
                        // Focusing the field again after a selection asks for the selected label:
                        // that is not a search (it would match nobody and report noResults).
                        if (self.selectedText !== null && query === self.selectedText) {
                            return;
                        }
                        self.search(query, asyncResults);
                    },
                    templates: {
                        suggestion: function (employee) {
                            return "<div class='employee-lookup__option'>"
                                + "<span class='employee-lookup__name'>" + highlight(displayName(employee), self.query) + "</span>"
                                + "<span class='employee-lookup__meta'>" + meta(employee, self.query) + "</span>"
                                + "</div>";
                        },
                        footer: function () {
                            if (self.lastTotal <= self.lastCount) {
                                return "";
                            }
                            // Screen readers hear this through the live region instead.
                            return "<div class='employee-lookup__more' aria-hidden='true'>"
                                + escapeHtml(format(self.messages.moreResults, [self.lastCount, self.lastTotal])) + "</div>";
                        }
                    }
                }
            );

            this.$menu = this.$input.closest(".twitter-typeahead").find(".tt-menu");
            this.$menu.attr({ id: this.listboxId, role: "listbox" });
            var label = this.labelText();
            if (label) {
                this.$menu.attr("aria-label", label);
            }
            this.$input.attr({
                role: "combobox",
                "aria-autocomplete": "list",
                "aria-haspopup": "listbox",
                "aria-expanded": "false",
                "aria-controls": this.listboxId
            });
            this.$live = $("<span class='employee-lookup__sr-only' aria-live='polite' aria-atomic='true'></span>")
                .appendTo(this.$root);

            this.$input.on("typeahead:render", function () { self.onRender(); });
            this.$input.on("typeahead:cursorchange", function () { self.onCursorChange(); });
            this.$input.on("typeahead:close", function () { self.setExpanded(false); });
            this.$input.on("typeahead:select", function (e, employee) {
                self.select(employee);
            });
            // typeahead.js fills in the top result on Tab or Right-arrow; the board selects with Enter only.
            this.$input.on("typeahead:beforeautocomplete", function (e) {
                e.preventDefault();
            });
            // Esc closes the list area, including the "No employees match" message.
            this.$input.on("keydown", function (e) {
                if (e.key === "Escape" || e.key === "Esc" || e.keyCode === 27) {
                    self.showMessage("");
                }
            });
            this.$input.on("input", function () { self.onEdit(); });

            // A form opened with a selection already made (for example a saved draft): its text is
            // the selected label, not a search.
            var email = this.sibling(this.emailFieldName);
            var text = this.$input.val();
            if (text && email && email.value) {
                this.selectedText = text;
            }
        },

        labelText: function () {
            var $field = this.$root.closest(".guideFieldNode");
            return $.trim($field.find("label").first().text());
        },

        model: function () {
            if (!window.guideBridge || !guideBridge._resolveId) {
                return null;
            }
            return guideBridge._resolveId(this.$input.attr("id").replace(/_widget$/, ""));
        },

        sibling: function (name) {
            var model = this.model();
            var items = model && model.parent && model.parent.items;
            if (!items) {
                return null;
            }
            for (var i = 0; i < items.length; i++) {
                if (items[i].name === name) {
                    return items[i];
                }
            }
            return null;
        },

        setSibling: function (name, value) {
            var field = this.sibling(name);
            if (field && field.value !== value) {
                field.value = value;
            }
        },

        setSelection: function (employee) {
            // Email last: rules usually call getEmployee when it changes, and need the title and
            // department of the selected position by then.
            this.setSibling(this.titleFieldName, employee ? employee.title || "" : "");
            this.setSibling(this.departmentFieldName, employee ? employee.department || "" : "");
            this.setSibling(this.emailFieldName, employee ? employee.email : "");
        },

        setStatus: function (status) {
            this.setSibling(this.statusFieldName, status);
        },

        showMessage: function (text) {
            this.$status.text(text || "");
            this.$status.prop("hidden", !text);
        },

        announce: function (text) {
            var $live = this.$live;
            $live.text("");
            // A short delay makes screen readers announce repeated identical messages.
            setTimeout(function () { $live.text(text); }, 50);
        },

        setExpanded: function (expanded) {
            this.$input.attr("aria-expanded", expanded ? "true" : "false");
            if (!expanded) {
                this.$input.removeAttr("aria-activedescendant");
            }
        },

        onEdit: function () {
            // Any typing after a selection drops it.
            this.selectedText = null;
            this.setSelection(null);
            var text = $.trim(this.$input.val());
            if (keywordTokens(text).join("").length < this.minChars) {
                this.requestSeq++;
                clearTimeout(this.timer);
                this.showMessage("");
                // typeahead.js empties the list without a render event: keep ARIA in step.
                this.setExpanded(false);
            }
        },

        onRender: function () {
            var listboxId = this.listboxId;
            var $options = this.$menu.find(".tt-suggestion");
            $options.each(function (index) {
                $(this).attr({ id: listboxId + "_option" + index, role: "option", "aria-selected": "false" });
            });
            this.setExpanded($options.length > 0);
        },

        onCursorChange: function () {
            var $options = this.$menu.find(".tt-suggestion").attr("aria-selected", "false");
            var $active = $options.filter(".tt-cursor");
            if ($active.length) {
                $active.attr("aria-selected", "true");
                this.$input.attr("aria-activedescendant", $active.attr("id"));
            } else {
                this.$input.removeAttr("aria-activedescendant");
            }
        },

        select: function (employee) {
            if (!employee || !employee.email) {
                return;
            }
            var label = selectedLabel(employee);
            this.selectedText = label;
            this.setSelection(employee);
            this.setStatus(STATUS.OK);
            this.showMessage("");
            this.$input.typeahead("val", label);
            var model = this.model();
            if (model) {
                model.value = label;
            }
            this.setExpanded(false);
        },

        search: function (query, done) {
            var self = this;
            var seq = ++this.requestSeq;
            clearTimeout(this.timer);
            this.timer = setTimeout(function () {
                self.query = query;
                self.invoke(query).then(function (result) {
                    if (seq !== self.requestSeq) {
                        return;
                    }
                    var employees = (result && result.employees) || [];
                    self.lastCount = employees.length;
                    self.lastTotal = Math.max((result && result.total) || 0, employees.length);
                    if (employees.length) {
                        self.setStatus(STATUS.OK);
                        self.showMessage("");
                        var announcement = format(self.messages.resultsAvailable, [employees.length]);
                        if (self.lastTotal > self.lastCount) {
                            // The count line is hidden from screen readers in the list; they hear it here.
                            announcement = announcement.replace(/[.\s]*$/, "") + ". "
                                + format(self.messages.moreResults, [self.lastCount, self.lastTotal]);
                        }
                        self.announce(announcement);
                    } else {
                        self.setStatus(STATUS.NO_RESULTS);
                        self.showMessage(format(self.messages.noResults, [query]));
                    }
                    done(employees);
                }, function () {
                    if (seq !== self.requestSeq) {
                        return;
                    }
                    // No message here: the form shows its own fallback when the status is "unavailable".
                    self.lastCount = 0;
                    self.lastTotal = 0;
                    self.showMessage("");
                    self.setStatus(STATUS.UNAVAILABLE);
                    done([]);
                });
            }, this.debounceMs);
        },

        /** Runs the FDM operation the way guidelib.dataIntegrationUtils.executeOperationV2 does. */
        invoke: function (keyword) {
            var model = this.model();
            var guide = model && model._guide && model._guide();
            if (!guide || !this.fdmPath) {
                return $.Deferred().reject().promise();
            }
            var url = guide.jcrPath + ".af.dermis";
            url = guideBridge._getUrl ? guideBridge._getUrl(url) : Granite.HTTP.externalize(url);
            return $.ajax({
                type: "POST",
                url: url,
                timeout: this.timeoutMs,
                dataType: "text",
                data: {
                    functionToExecute: "invokeFDMOperation",
                    formDataModelId: this.fdmPath,
                    operationName: this.operation,
                    input: JSON.stringify({ keyword: keyword }),
                    apiVersion: "2",
                    guideNodePath: model.jcrPath
                }
            }).then(function (text) {
                var result = text ? JSON.parse(text) : {};
                // Version 2 responses may wrap the operation output in "body".
                return result && result.body && !result.employees ? result.body : result;
            });
        }
    };

    function initAll(root) {
        $(root || document).find("[data-cmp-is='adaptiveFormEmployeeLookup'] .employee-lookup__input").each(function () {
            var $input = $(this);
            if ($input.data(INIT_KEY)) {
                return;
            }
            $input.data(INIT_KEY, true);
            try {
                new EmployeeLookup(this);
            } catch (e) {
                if (window.console) {
                    console.error("Employee lookup failed to start", e);
                }
            }
        });
    }

    window.YukonEmployeeLookup = { highlight: highlight, initAll: initAll };

    // The form model must exist before the lookup can read or write fields.
    function connect(bridge) {
        bridge.connect(function () {
            initAll();
        });
    }
    if (window.guideBridge) {
        connect(window.guideBridge);
    } else {
        window.addEventListener("bridgeInitializeStart", function (event) {
            connect(event.detail.guideBridge);
        });
    }

    // Repeatable panels and lazily loaded fragments add lookups later.
    if (window.MutationObserver) {
        new MutationObserver(function (mutations) {
            mutations.forEach(function (mutation) {
                mutation.addedNodes.forEach(function (node) {
                    if (node.nodeType === 1 && window.guideBridge && guideBridge.isConnected && guideBridge.isConnected()) {
                        initAll(node);
                    }
                });
            });
        }).observe(document.documentElement, { childList: true, subtree: true });
    }
})(jQuery);
