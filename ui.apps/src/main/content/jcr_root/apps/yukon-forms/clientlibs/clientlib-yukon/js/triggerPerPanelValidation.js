window.formState = {
	wasOnStartPage: true
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
						window.guideBridge.setFocus(null, 'nextItemDeep');
						return;
					}
					var validation = window.guideBridge.validate([], activePanelSOM);
					if (validation) {
						console.debug(
							'Panel successfully validated. Proceeding to next panel.');
						window.guideBridge.setFocus(null, 'nextItemDeep');
					} else {
						console.debug('Validation errors encountered.');
					}
				}
			}
		} catch (e) {
			console.error("Error while resolving active panel: ".concat(e.message));
		}
	});
});
