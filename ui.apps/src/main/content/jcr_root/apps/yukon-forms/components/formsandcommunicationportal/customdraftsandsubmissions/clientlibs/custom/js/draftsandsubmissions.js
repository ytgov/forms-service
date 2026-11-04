/*******************************************************************************
 * Copyright 2021 Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

/*
 * Fork of Adobe's Core Forms Components file
 * core/fd/components/formsportal/draftsandsubmissions/v1/draftsandsubmissions/clientlibs/site/js/draftsandsubmissions.js
 * (identical in 3.0.172; upstream unchanged since a1c569ce, 2021-09-06).
 * Adobe's script has no hook to change items before they're rendered, so it is copied. Our changes:
 * filter to the form embedded on the page, page title as item title, translated operation labels,
 * "No drafts saved" row, resume drafts in this page (openDraftInPage), newest first (across Load more), full date and
 * time instead of relative dates, keyboard access to the item link and actions menu, a confirm before Discard,
 * a table refresh when the embedded form saves a draft, and alignment with the form's content column.
 * On a Core Forms Components or Forms add-on upgrade: diff against upstream and retest list, open, resume.
 */

(function($) {
    var NS = 'cmp',
        IS = 'draftsandsubmissions',
        ATTR_PREFIX = "data-" + NS + "-hook-" + IS;

    var keyCodes = {
        ENTER: 13
    }

    var selectors = {
        results: "[" + ATTR_PREFIX + "=\"results\"]",
        itemTemplate: "[" + ATTR_PREFIX + "=\"itemTemplate\"]",
        menuTemplate: "[" + ATTR_PREFIX + "=\"menuTemplate\"]",
        loadMore: "[" + ATTR_PREFIX + "=\"more\"]",
        self: "[data-" + NS + '-is="' + IS + '"]'
    };

    // All instances are stored in the store object with key being their model's id
    // and ItemAPI is used for item creation and injection
    var componentStore = {}, ItemAPI;

    var formURL = null;

    // Translated by the page's i18n dictionary; read from the item template (list.html) at init
    var labels = {};

    // Replaces Adobe's "openDraft" operation, which opens the standalone form page in a new tab
    var RESUME_OPERATION = "openDraftInPage";
    var DRAFT_DATA_REF_PREFIX = "service://FP/draft/";

    var cleanup = function (id) {
            var componentConfig = componentStore[id];
            ItemAPI.clear(componentConfig.itemTemplate, componentConfig.resultsNode);
            componentConfig.loadmoreNode.style.display = "inline-block";
            // Responses to queries sent before this reset are ignored (see queryFPRecords)
            componentConfig.generation = (componentConfig.generation || 0) + 1;
        },
        hideLoadMore = function (id) {
            var componentConfig = componentStore[id];
            if (componentConfig.loadmoreNode) {
                componentConfig.loadmoreNode.style.display = "none";
            }
        },
	translateOperation = function (op) {
	    return {
                ...op,
                title: op.title === "Open"
		    ? labels.labelOpen
		    : op.title === "Discard"
		    	? labels.labelDiscard
		    	: op.title,
            };
	},
	// Resume a draft in this page, same tab. The inline-embedded form reads dataRef from the page URL;
	// EmbeddedFormPrefillServlet forwards its prefill request to the form's guideContainer.
	draftResumeUrl = function (draftId) {
	    var url = new URL(window.location.href);
	    url.searchParams.set("dataRef", DRAFT_DATA_REF_PREFIX + draftId);
	    return url.toString();
	},
	toResumeOperation = function (op) {
	    return op.name === "openDraft" ? { ...op, name: RESUME_OPERATION } : op;
	},
	// Show the header when there are rows, and the "No drafts saved" row when there are none.
	// Runs on every change to the results (see init), because Adobe's Discard removes a row without telling us.
        syncEmptyState = function (resultsNode) {
            var hasItems = !!resultsNode.querySelector("[data-cmp-hook-item-template=\"item\"]");
            var thead = resultsNode.querySelector(".cmp-portallister__results-list-head");
	    if (!thead) {
		console.error("No header found for drafts table");
		return;
	    }
	    thead.style.display = hasItems ? "" : "none";
	    var noDraftsRow = resultsNode.querySelector(".no-drafts-row");
	    if (!noDraftsRow) {
		console.error("No 'No drafts saved' row found");
		return;
	    }
            noDraftsRow.style.display = hasItems ? "none" : "";
	},
	// Adobe's injector renders a relative date ("2 weeks ago") and a link without href.
	// Show the full date in the page language and make the link reachable by keyboard.
	decorateItem = function (row, item) {
	    var link = row.querySelector(".item-link");
	    // Only same-origin links become real hrefs; anything else (another site, javascript:) stays
	    // click-only through Adobe's handler, as before
	    var url = link && new URL(link.getAttribute("link"), window.location.href);
	    if (url && url.origin === window.location.origin) {
		link.setAttribute("href", url.href);
	    }
	    var time = row.querySelector(".cmp-portallister__item-list-time");
	    if (time && item.lastModified) {
		// Include the time so drafts saved on the same day can be told apart
		time.textContent = new Date(Number(item.lastModified))
		    .toLocaleString(document.documentElement.lang || undefined, { dateStyle: "medium", timeStyle: "short" });
	    }
	},
	// Newest first across the whole table. The API returns drafts in draft-id order and may page them
	// (Load more), so each new row moves in front of the first older row already shown.
	placeNewestFirst = function (container, row, item) {
	    var lastModified = Number(item.lastModified) || 0;
	    row.setAttribute("data-last-modified", lastModified);
	    var older = Array.prototype.find.call(container.querySelectorAll("tr.cmp-portallister__item"), function (other) {
		return other !== row && Number(other.getAttribute("data-last-modified")) < lastModified;
	    });
	    if (older) {
		container.insertBefore(row, older);
	    }
	},
	menuButton = function (cell) {
	    return cell && cell.querySelector(".cmp-portallister__item-list-operations-button");
	},
	// Adobe's menu opens and closes on click only; keep aria-expanded in sync and move focus into it
	syncMenuState = function (componentNode) {
	    componentNode.querySelectorAll(".cmp-portallister__item-list-operations").forEach(function (cell) {
		var menuItem = cell.querySelector(".cmp-portallister__menu-item"),
		    button = menuButton(cell);
		if (button) {
		    button.setAttribute("aria-expanded", menuItem ? "true" : "false");
		}
		if (menuItem && !cell.contains(document.activeElement.closest(".cmp-portallister__menu"))) {
		    menuItem.focus();
		}
	    });
	},
	// Close any open menu that doesn't contain the given node. Adobe's menu has no close-on-outside-click,
	// so this clicks its own toggle (the operations cell) to keep Adobe's menu state consistent.
	closeMenusOutside = function (componentNode, node) {
	    componentNode.querySelectorAll(".cmp-portallister__item-list-operations").forEach(function (cell) {
		if (cell.querySelector(".cmp-portallister__menu") && !cell.contains(node)) {
		    menuButton(cell).click();
		}
	    });
	},
	// Adobe's Discard deletes the draft at once. Ask first; cancelling stops the click before
	// it reaches Adobe's handler on the menu item, so the draft and the open menu stay.
	confirmDiscard = function (event) {
	    var menuItem = event.target.closest(".cmp-portallister__menu-item");
	    var operation = menuItem && JSON.parse(menuItem.getAttribute("operation_data"));
	    if (!operation || operation.name !== "discardDraft") {
		return;
	    }
	    if (!window.confirm(labels.labelDiscardConfirm)) {
		event.preventDefault();
		event.stopPropagation();
		return;
	    }
	    // Discarding the draft the embedded form is saving to: unlink the form, or its next save would
	    // send the deleted draft's id and fail (500, AEM-FMP-001-004 "Unable to get the draft").
	    var cell = menuItem.closest(".cmp-portallister__item-list-operations");
	    if (window.guideBridge && cell && guideBridge.customContextProperty("draftId") === cell.getAttribute("operation_model_id")) {
		guideBridge.customContextProperty("draftId", "");
	    }
	},
	handleMenuKeys = function (event) {
	    var menuItem = event.target.closest(".cmp-portallister__menu-item");
	    if (menuItem && (event.key === "Enter" || event.key === " ")) {
		event.preventDefault();
		menuItem.click();
	    } else if (event.key === "Escape") {
		// From a menu item or from the ⋮ button itself: close an open menu and focus its button
		var cell = event.target.closest(".cmp-portallister__item-list-operations"),
		    button = menuButton(cell);
		if (button && cell.querySelector(".cmp-portallister__menu")) {
		    button.click();
		    button.focus();
		}
	    }
	},
        updateSearchResults = function (response, id) {
            var componentConfig = componentStore[id],
                queryResults = response.elements;
            componentConfig.nextOffset = queryResults.pagination.nextOffset;
	    if (formURL) {
		queryResults.data = queryResults.data
	            .filter(function(item) {
			// Make sure that only drafts and submissions related to the current form are displayed
		        return item.formLink.includes(formURL);
		    })
		    .map(function(item) {
			var isDraft = item.operations.some(function(op) {
			    return op.name === "openDraft";
			});
			return {
			    ...item,
			    // Replace form title with page title
			    title: document.title,
			    // Resume drafts in this page instead of the standalone form page
			    formLink: isDraft ? draftResumeUrl(item.id) : item.formLink,
			    // Translate operations to the page language
			    operations: item.operations.map(translateOperation).map(toResumeOperation)
			}
		   });
            }
            var itemContainer = componentConfig.resultsNode.querySelector("[data-cmp-hook-item-template=\"container\"]");
            queryResults.data.forEach(function(item) {
                ItemAPI.createAndInject(componentConfig, item);
                var row = itemContainer.lastElementChild;
                decorateItem(row, item);
                placeNewestFirst(itemContainer, row, item);
            });
            if (componentConfig.nextOffset < 0) {
                hideLoadMore(id);
            }
            if (componentConfig.restoreState) {
                restoreState(componentConfig.componentNode, componentConfig.restoreState);
                componentConfig.restoreState = null;
            }
        },
        queryFPRecords = function (id, offset) {
            var parameters = new URLSearchParams(),
                componentConfig = componentStore[id];
            if (componentConfig.limit) {
                parameters.append("limit", componentConfig.limit);
            }
            if (offset) {
                parameters.append("offset", offset);
            }
            var queryPath = componentConfig.queryPath + "?" + parameters.toString(),
                generation = componentConfig.generation;
            fetch(queryPath)
                .then(response => response.json())
                .then(function (data) {
                    // A newer reset (e.g. another draft save) happened meanwhile; its own query will render
                    if (generation === componentConfig.generation) {
                        updateSearchResults(data, id);
                    }
                });
        },
        handleKeyPresses = function (event, id) {
            switch (event.keyCode) {
                case keyCodes.ENTER:
                    event.preventDefault();
                    cleanup(id);
                    queryFPRecords(id);
                    break;
                default:
                    return;
            }
        },
        paginateNext = function (event, id) {
            var componentConfig = componentStore[id];
            if (componentConfig.nextOffset) {
                queryFPRecords(id, componentConfig.nextOffset);
            } else {
                hideLoadMore(id);
            }
        },
        initializeDraftsAndSubmissionsInstance = function (config) {
            if (componentStore[config.id]) {
                // to prevent multiple initializations of same component
                return;
            }
            var componentConfig = config;

            componentConfig.componentNode = document.getElementById(config.id);
            componentConfig.resultsNode = componentConfig.componentNode.querySelector(selectors.results);
            componentConfig.loadmoreNode = componentConfig.componentNode.querySelector(selectors.loadMore);
            componentConfig.itemTemplate = componentConfig.componentNode.querySelector(selectors.itemTemplate).innerHTML;
            componentConfig.menuTemplate = componentConfig.componentNode.querySelector(selectors.menuTemplate).innerHTML;
            var template = document.createElement("template");
            template.innerHTML = componentConfig.itemTemplate;
            labels = template.content.querySelector("[data-label-open]").dataset;

            // input node, sort button and filter button are rendered conditionally
            if (componentConfig.inputNode) {
                componentConfig.inputNode.addEventListener("keydown", (event) => {
                    handleKeyPresses(event, config.id);
                });
            }
            componentConfig.loadmoreNode.addEventListener("click", (event) => {
                paginateNext(event, config.id)
            });
            // Draft title links resume in this page. Capture phase, so Adobe's link handler
            // (window.open(link, "_blank")) never runs for them.
            componentConfig.componentNode.addEventListener("click", (event) => {
                var link = event.target.closest(".item-link");
                var dataRef = link && new URL(link.getAttribute("link"), window.location.href).searchParams.get("dataRef");
                if (link) {
                    // The href is only there for keyboard access; Adobe's handler or ours navigates
                    event.preventDefault();
                }
                if (dataRef && dataRef.startsWith(DRAFT_DATA_REF_PREFIX)) {
                    event.stopPropagation();
                    window.location.assign(link.getAttribute("link"));
                }
            }, true);
            componentConfig.componentNode.addEventListener("click", function () {
                syncMenuState(componentConfig.componentNode);
            });
            componentConfig.componentNode.addEventListener("click", confirmDiscard, true);
            componentConfig.componentNode.addEventListener("keydown", handleMenuKeys);
            document.addEventListener("click", function (event) {
                closeMenusOutside(componentConfig.componentNode, event.target);
            });
            // Tabbing out of the menu closes it too
            componentConfig.componentNode.addEventListener("focusout", function (event) {
                if (event.relatedTarget) {
                    closeMenusOutside(componentConfig.componentNode, event.relatedTarget);
                }
            });

            new MutationObserver(function () {
                syncEmptyState(componentConfig.resultsNode);
            }).observe(componentConfig.resultsNode, { childList: true, subtree: true });

            alignWithForm(componentConfig.componentNode);
            ItemAPI.init(componentConfig);

            componentStore[config.id] = componentConfig;
            cleanup(config.id);
            queryFPRecords(config.id);
        },
        // Line the component up with the embedded form's content column (right of its page menu)
        // by setting --ds-inset-start/end, used as margins in the CSS. Measured rather than copied
        // from the theme so it follows the form's layout at every width. No form: no inset.
        alignWithForm = function (componentNode) {
            if (!window.ResizeObserver) {
                return;
            }
            var wrapper = componentNode.parentElement,
                formContent = null,
                observer = null;
            var update = function () {
                var area = wrapper.getBoundingClientRect(),
                    column = formContent.getBoundingClientRect(),
                    visible = column.width > 0;
                componentNode.style.setProperty("--ds-inset-start", (visible ? Math.max(0, column.left - area.left) : 0) + "px");
                componentNode.style.setProperty("--ds-inset-end", (visible ? Math.max(0, area.right - column.right) : 0) + "px");
            };
            // Watch the form's column, or the new one if the form re-rendered it, and measure now
            var attach = function () {
                var found = document.querySelector("#guideContainerForm .afTabLeftPanel .tab-content");
                if (found && found !== formContent) {
                    if (observer) {
                        observer.disconnect();
                    }
                    formContent = found;
                    observer = new ResizeObserver(update);
                    observer.observe(formContent);
                    observer.observe(wrapper);
                }
                if (formContent) {
                    update();
                }
            };
            attach();
            // The form may render after this component initializes: try again once it's ready.
            // connect() runs at once if the form is already ready, or when it finishes otherwise.
            onGuideBridge(function (bridge) {
                bridge.connect(attach);
            });
        },
        // Reload every drafts table, e.g. after the embedded form saves a draft
        refreshAllInstances = function () {
            Object.keys(componentStore).forEach(function (id) {
                var componentConfig = componentStore[id];
                // Keep a pending state if refreshes overlap: the table is already empty by then
                componentConfig.restoreState = componentConfig.restoreState || captureState(componentConfig.componentNode);
                cleanup(id);
                queryFPRecords(id);
            });
        },
        // The refresh rebuilds every row. Remember which draft's link, ⋮ button or menu item had
        // keyboard focus, and which draft's menu was open, so they can be put back afterwards.
        captureState = function (componentNode) {
            var draftId = function (node) {
                    var row = node && node.closest("tr.cmp-portallister__item"),
                        cell = row && row.querySelector(".cmp-portallister__item-list-operations");
                    return cell && cell.getAttribute("operation_model_id");
                },
                active = componentNode.contains(document.activeElement) ? document.activeElement : null,
                openMenu = componentNode.querySelector(".cmp-portallister__menu"),
                menuItem = active && active.closest(".cmp-portallister__menu-item");
            if (!openMenu && !(active && active.closest("tr.cmp-portallister__item"))) {
                return null;
            }
            return {
                openMenuId: openMenu ? draftId(openMenu) : null,
                focusId: active && active.closest("tr.cmp-portallister__item") ? draftId(active) : null,
                focus: menuItem ? "menuItem"
                    : active && active.closest(".item-link") ? "link"
                    : active && active.closest(".cmp-portallister__item-list-operations-button") ? "button"
                    : null,
                menuItemIndex: menuItem ? Array.prototype.indexOf.call(menuItem.parentElement.children, menuItem) : -1
            };
        },
        restoreState = function (componentNode, state) {
            var cellFor = function (draftId) {
                    return draftId && componentNode.querySelector(".cmp-portallister__item-list-operations[operation_model_id=\"" + draftId + "\"]");
                },
                menuCell = cellFor(state.openMenuId),
                focusCell = cellFor(state.focusId),
                previousFocus = document.activeElement;
            if (menuCell) {
                // Adobe's toggle; our click listener then syncs aria-expanded (and moves focus into the menu)
                menuButton(menuCell).click();
            }
            if (!focusCell) {
                // Focus wasn't in the table: don't let reopening the menu take it from elsewhere
                if (previousFocus && previousFocus !== document.activeElement) {
                    previousFocus.focus();
                }
                return;
            }
            var target = state.focus === "menuItem"
                    ? focusCell.querySelectorAll(".cmp-portallister__menu-item")[state.menuItemIndex]
                : state.focus === "link"
                    ? focusCell.closest("tr.cmp-portallister__item").querySelector(".item-link")
                : state.focus === "button" ? menuButton(focusCell) : null;
            if (target) {
                target.focus();
            }
        },
        // guideBridge fires saveCompleted after Save draft (and autosave) once the server returns the draft id.
        // The form may initialize before or after this script, so handle both.
        listenForDraftSaves = function () {
            onGuideBridge(function (bridge) {
                bridge.on("saveCompleted", refreshAllInstances);
            });
        },
        // Call back with the embedded form's guideBridge, whether it loaded before or after this script
        onGuideBridge = function (callback) {
            if (window.guideBridge) {
                callback(window.guideBridge);
            } else {
                window.addEventListener("bridgeInitializeStart", function (event) {
                    callback(event.detail.guideBridge);
                });
            }
        },
        queryDomForAllInstances = function () {
            // This function should execute after DOM is safe to manipulate
            // and we've received ItemAPI, i.e some equivalent of $.ready
            var elements = document.querySelectorAll(selectors.self);
            for (var i = 0; i < elements.length; i++) {
                var element = elements[i];
                initializeDraftsAndSubmissionsInstance({
                    "id": element.getAttribute("id"),
                    "queryPath": element.getAttribute("data-queryPath")
                })
            }
        },
        initializeItemAPI = function (api) {
	    // Identify the form featured on the page
            var formContainerWrapper = document.querySelector("#guideContainerForm .guideContainerWrapperNode");
	    if (!formContainerWrapper) {
                console.debug("Form not found. Unable to filter drafts.");
            } else {
		formURL = formContainerWrapper.getAttribute("data-path").replace(/\/jcr:content\/guideContainer$/, "");
	    }
            if (!ItemAPI) {
                ItemAPI = api;
                queryDomForAllInstances();
            }
        },
        tmpEvent = {
            detail: {
                portalLister: {
                    initializeItemAPI: initializeItemAPI
                }
            }
        };

    // Using jQuery event api trigger on $.ready
    // wait for window load to prevent any race condition
    $(function() {
        $(window).trigger("core-forms-itemapi-onload", tmpEvent);
        listenForDraftSaves();
        $(window).trigger("core-forms-register-operation", {
            name: RESUME_OPERATION,
            handler: function (event, operationData) {
                window.location.assign(draftResumeUrl(operationData.operation_model_id));
            }
        });
    });

}(jQuery));

