window.formState = {
	wasOnStartPage: true,
	wasOnFinishPage: false
};

/**
 * Traverses the entire tree of elements starting from a parent node and returns a flattened array containing all the 'leaves'.
 */
var flattenItems = function flattenItems(parentNode) {
	if (parentNode === null || parentNode === undefined) {
		return [];
	}
	var children = parentNode.items;
	if (children === null || children === undefined || !Array.isArray(children) || children.length ===
		0) {
		return [];
	}
	return children.concat(children.flatMap(function(c) {
		return flattenItems(c);
	}));
};

/**
 * Finds the first fillable field of a panel
 */
var getFirstFillableField = function getFirstFillableField(parentPanel) {
	if (parentPanel === null || parentPanel === undefined) {
		console.debug('No parent panel found.');
		return null;
	}
	var firstFillableField = null;
	parentPanel.visit(function (cmp) {
		if (cmp.className !== 'guidePanel'
			&& cmp.className !== 'guideInstanceManager'
			&& cmp.className !== 'guideTextDraw'
			&& cmp.className !== 'guideButton'
			&& cmp.enabled === true
			&& cmp.visible
		) {
			if (firstFillableField === null) {
				console.debug('Found first fillable field.');
				firstFillableField = cmp.somExpression;
				return;
			}
			console.debug('First fillable field already found.');
			return;
		}
	});
	return firstFillableField;
};

/**
 * Scrolls to the top of the page and looks for a fillable field. 
 */
var setFocusToFirstFillableField = function setFocusToFirstFillableField(guide, panel) {
	window.scrollTo(0, 0);
	var firstFillableField = getFirstFillableField(panel);
	if (firstFillableField) {
		guide.setFocus(firstFillableField);
		return true;
	}
	return false;
};

/**
 * Wraps toolbar navigation to ensure that the first fillable field is on focus.
 */
var navigatePanels = function navigatePanels(guide, isForward) {
	if (guide === null || guide === undefined) {
		console.debug('No guide bridge found.');
	}
	var activePanelSOM = guide.getFocus({
		'focusOption': 'navigablePanel'
	});
	var activePanel = guide.resolveNode(activePanelSOM);
	if (!activePanel) {
		console.debug('Failed to find active panel');
		return;
	}
	var rootPanel = activePanel.parent;
	if (!rootPanel || !rootPanel.navigationContext) {
		console.debug('Failed to find navigation context');
	}
	var destination = isForward ? rootPanel.navigationContext.nextItem : rootPanel.navigationContext.prevItem;
	if (!(setFocusToFirstFillableField(guide, destination)) {
		guide.setFocus(destination)
	}
};

/**
 * Navigates to the previous panel when the 'Next' toolbar button is clicked. A workaround, as preventing event propagation does not work.
 */
window.addEventListener('bridgeInitializeStart', function(e) {
	var guide = e.detail.guideBridge;
	guide.on('elementButtonClicked', function(_e, payload) {
		if (payload.target === null || payload.target === undefined) {
			return;
		}
		if (payload.target.jsonModel === null || payload.target.jsonModel === undefined) {
			return;
		}
		var componentType = payload.target.jsonModel.type;
		if (componentType === 'moveNext') {
			console.debug('Clicked on "Next" toolbar button');
			var hasNavigated = guide.setFocus(null, 'prevItemDeep');
			if (window.formState) {
				window.formState.wasOnStartPage = !hasNavigated;
			}
		}
		if (componentType === 'movePrev') {
			console.debug('Clicked on "Previous" toolbar button');
			var hasNavigated = guide.setFocus(null, 'nextItemDeep');
			if (window.formState) {
				window.formState.wasOnFinishPage = !hasNavigated;
			}
		}
	});
});

/**
 * Triggers validation of the active panel when the 'Next' toolbar button is clicked. If validation is successful, navigates to the next panel.
 */
document.addEventListener('DOMContentLoaded', function() {
	var nextButton = document.querySelector('button.moveNext');
	if (nextButton === null || nextButton === undefined) {
		return;
	}
	nextButton.addEventListener('click', function(e) {
		try {
			var activePanelSOM = window.guideBridge.getFocus({
				'focusOption': 'navigablePanel'
			});
			var activePanel = window.guideBridge.resolveNode(activePanelSOM);
			if (window.formState) {
				if (!window.formState.wasOnStartPage) {
					// Checks if the user has completed any fields (with the exception of those that are defined by custom rules), or if any of the fields has already been marked as invalid
					var activePanelFieldsCompleted = flattenItems(activePanel).filter(
						function(item) {
							if (item === null || item === undefined) {
								return false;
							}
							return item.isValid === false
								|| item.visible
								&& item.enabled
								&& item.jsonModel
								&& item.jsonModel.defaultToCurrentDate !== 'true'
								&& !(item.jsonModel && item.jsonModel.calcExp)
								&& !(item.jsonModel && item.jsonModel['{default}'] && item.value === item.jsonModel['{default}'])
								&& item.value !== null
								&& item.value !== undefined
								&& item.value !== '';
						});
					// If the user has not entered any data, skip validation and proceed to the next panel
					if (activePanelFieldsCompleted.length === 0) {
						console.debug('No data entered in the panel. Skipping validation.');
						navigatePanels(window.guideBridge, true);
						return;
					}
					var validation = window.guideBridge.validate([], activePanelSOM);
					if (validation) {
						console.debug(
							'Panel successfully validated. Proceeding to next panel.');
						navigatePanels(window.guideBridge, true);
					} else {
						console.debug('Validation errors encountered.');
					}
				} else {	
					setFocusToFirstFillableField(window.guideBridge, activePanel);
				}
			}
		} catch (e) {
			console.error("Error while resolving active panel: ".concat(e.message));
		}
	});
});

/**
 * Ensures that focus is set to the first fillable field (if it exists) when the 'Previous' toolbar button is clicked.
 */
document.addEventListener('DOMContentLoaded', function() {
	var prevButton = document.querySelector('button.movePrev');
	if (prevButton === null || prevButton === undefined) {
		return;
	}
	prevButton.addEventListener('click', function(e) {
		if (!window.formState.wasOnFinishPage) {
			navigatePanels(window.guideBridge, false);
		} else {
			setFocusToFirstFillableField(window.guideBridge, activePanel);
		}
	});
});

