window.formState = {
	wasOnStartPage: true,
};

/**
 * Navigates to the previous panel when the 'Next' toolbar button is clicked. A workaround, as preventing event propagation does not work.
 */

window.addEventListener('bridgeInitializeStart', (e) => {
	const guide = e.detail.guideBridge;
	guide.on('elementButtonClicked', (_e, payload) => {
		const componentSOM = payload.target.somExpression;
		const nextButtonSOM = 'guide[0].guide1[0].guideRootPanel[0].toolbar[0].nextitemnav[0]';

		if (componentSOM === nextButtonSOM) {
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
		const activePanelSOM = window.guideBridge.getFocus({ 'focusOption': 'navigablePanel' });
		const activePanel = window.guideBridge.resolveNode(activePanelSOM);
		if (window.formState) {
			if (!window.formState.wasOnStartPage) {
				const validation = window.guideBridge.validate([], activePanelSOM);
				if (validation) {
					window.guideBridge.setFocus(null, 'nextItemDeep');
				}
			}
		}
	});
});

