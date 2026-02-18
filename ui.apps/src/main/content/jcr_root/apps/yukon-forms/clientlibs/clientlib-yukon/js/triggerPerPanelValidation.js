window.formState = {
	wasOnStartPage: true,
};

/**
 * Traverses the entire tree of elements starting from a parent node and returns a flattened array containing all the 'leaves'.
 */
const flattenItems = (parentNode) => {
	const children = parentNode?.items;
	if (children === null || children === undefined || !Array.isArray(children) || children.length === 0) {
		return
	}
	return children.concat(children.flatMap((c) => flattenItems(c)));
};

/**
 * Navigates to the previous panel when the 'Next' toolbar button is clicked. A workaround, as preventing event propagation does not work.
 */

window.addEventListener('bridgeInitializeStart', (e) => {
	const guide = e.detail.guideBridge;
	guide.on('elementButtonClicked', (_e, payload) => {
		const componentType = payload.target?.jsonModel?.type;

		if (componentType === 'moveNext') {
			console.debug('Clicked on "Next" toolbar button');
			const hasNavigated = guide.setFocus(null, 'prevItemDeep');
			if (window.formState) {
				window.formState.wasOnStartPage = !hasNavigated;
			}
		}
	});
});

/**
 * Triggers validation of the active panel when the 'Next' toolbar button is clicked. If validation is successful, navigates to the next panel.
 */

document.addEventListener('DOMContentLoaded', () => {
	const nextButton = document.querySelector('button.moveNext');
	if (nextButton === null || nextButton === undefined) {
		return;
	}
	nextButton.addEventListener('click', (e) => {
		try {
			const activePanelSOM = window.guideBridge.getFocus({ 'focusOption': 'navigablePanel' });
			const activePanel = window.guideBridge.resolveNode(activePanelSOM);
			if (window.formState) {
				if (!window.formState.wasOnStartPage) {
					// Checks if the user has completed any fields (with the exception of those that are defined by custom rules), or if any of the fields has already been marked as invalid
					const activePanelFieldsCompleted = flattenItems(activePanel).filter((item) => {
						return item?.isValid === false
							|| (item?.visible
								&& item?.enabled
								&& item?.jsonModel?.defaultToCurrentDate !== 'true'
								&& !item?.jsonModel?.calcExp
								&& !(item?.jsonModel?.['{default}'] && item?.value === item?.jsonModel?.['{default}'])
								&& item?.value !== null
								&& item?.value !== undefined
								&& item?.value !== ''
							);
					});
					// If the user has not entered any data, skip validation and proceed to the next panel
					if (activePanelFieldsCompleted.length === 0) {
						console.debug('No data entered in the panel. Skipping validation.');
						window.guideBridge.setFocus(null, 'nextItemDeep');
						return;
					}
					const validation = window.guideBridge.validate([], activePanelSOM);
					if (validation) {
						console.debug('Panel successfully validated. Proceeding to next panel.');
						window.guideBridge.setFocus(null, 'nextItemDeep');
					} else {
						console.debug('Validation errors encountered.');
					}
				}
			}
		} catch (e) {
			console.error(`Error while resolving active panel: ${e.message}`);
		}
	});
});

