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
 * filter to the form embedded on the page, page title as item title, French operation labels,
 * "No drafts saved" row, resume drafts in this page (openDraftInPage), full dates instead of
 * relative ones, keyboard access to the item link and actions menu, and a confirm before Discard.
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

    // Replaces Adobe's "openDraft" operation, which opens the standalone form page in a new tab
    var RESUME_OPERATION = "openDraftInPage";
    var DRAFT_DATA_REF_PREFIX = "service://FP/draft/";

    var cleanup = function (id) {
            var componentConfig = componentStore[id];
            ItemAPI.clear(componentConfig.itemTemplate, componentConfig.resultsNode);
            componentConfig.loadmoreNode.style.display = "inline-block";
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
                title: navigator.language !== "fr" && navigator.userLanguage !== "fr"
		    ? op.title
		    : op.title === "Open"
		    	? "Ouvrir"
		    	: op.title === "Discard"
		    		? "Supprimer"
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
	// Hide "No drafts saved" span and show header
        updateLayoutWithDrafts = function () {
            var thead = document.querySelector(".cmp-portallister__results-list-head");
	    if (!thead) {
		console.error("No header found for drafts table");
		return;
	    }
	    thead.style.display = "";
	    var noDraftsRow = document.querySelector(".no-drafts-row");
	    if (!noDraftsRow) {
		console.error("No 'No drafts saved' row found");
		return;
	    }
            noDraftsRow.style.display = "none";
	},
	// Adobe's injector renders a relative date ("2 weeks ago") and a link without href.
	// Show the full date in the page language and make the link reachable by keyboard.
	decorateItem = function (row, item) {
	    var link = row.querySelector(".item-link");
	    if (link) {
		link.setAttribute("href", link.getAttribute("link"));
	    }
	    var time = row.querySelector(".cmp-portallister__item-list-time");
	    if (time && item.lastModified) {
		time.textContent = new Date(Number(item.lastModified))
		    .toLocaleDateString(document.documentElement.lang || undefined, { dateStyle: "long" });
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
	    var isFrench = navigator.language === "fr" || navigator.userLanguage === "fr";
	    var message = isFrench
		? "Supprimer ce brouillon? Cette action est irréversible."
		: "Discard this draft? This can't be undone.";
	    if (!window.confirm(message)) {
		event.preventDefault();
		event.stopPropagation();
	    }
	},
	handleMenuKeys = function (event) {
	    var menuItem = event.target.closest(".cmp-portallister__menu-item");
	    if (!menuItem) {
		return;
	    }
	    if (event.key === "Enter" || event.key === " ") {
		event.preventDefault();
		menuItem.click();
	    } else if (event.key === "Escape") {
		var button = menuButton(menuItem.closest(".cmp-portallister__item-list-operations"));
		button.click();
		button.focus();
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
			    // Translate operations if the page is in French
			    operations: item.operations.map(translateOperation).map(toResumeOperation)
			}
		   });
            }
	    if (queryResults.data.length > 0) {
		updateLayoutWithDrafts();
	    }
            var itemContainer = componentConfig.resultsNode.querySelector("[data-cmp-hook-item-template=\"container\"]");
            queryResults.data.forEach(function(item) {
                ItemAPI.createAndInject(componentConfig, item);
                decorateItem(itemContainer.lastElementChild, item);
            });
            if (componentConfig.nextOffset < 0) {
                hideLoadMore(id);
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
            var queryPath = componentConfig.queryPath + "?" + parameters.toString();
            fetch(queryPath)
                .then(response => response.json())
                .then(data => updateSearchResults(data, id));
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

            ItemAPI.init(componentConfig);

            componentStore[config.id] = componentConfig;
            cleanup(config.id);
            queryFPRecords(config.id);
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
        $(window).trigger("core-forms-register-operation", {
            name: RESUME_OPERATION,
            handler: function (event, operationData) {
                window.location.assign(draftResumeUrl(operationData.operation_model_id));
            }
        });
    });

}(jQuery));

