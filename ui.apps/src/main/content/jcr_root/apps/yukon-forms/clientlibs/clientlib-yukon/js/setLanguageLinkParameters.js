/**
 * Adds the form's guide state to local storage so that it can be restored later.
 */
function addStateToSessionStorage() {
	if (!window.guideBridge) {
		console.error("Guide bridge object not found.");
		return;
	}
	window.guideBridge.getGuideState({
		success: function (guideResultObj) {
			var jsonData = guideResultObj.data;
			var jsonString = JSON.stringify(jsonData.guideState);
			localStorage.setItem("previousLanguageState", jsonString);
		},
		error: function (guideResultObj) {
			console.error("Unable to fetch guide state. Proceeding without restoring state.");
		}
	});
}

/**
 * Attempts to load the form's guide state from local storage. If found, the object is then removed (as it will become out of date).
 */
function loadStateFromSessionStorage() {
	if (!window.guideBridge) {
		console.error("Guide bridge object not found.");
		return;
	}
	var previousLanguageState = localStorage.getItem("previousLanguageState");	
	if (previousLanguageState) {
		window.guideBridge.restoreGuideState({
			guideState: JSON.parse(previousLanguageState),
			error: function (guideResultObj) {
				console.error("Unable to restore guide state.");
			}
		});
		localStorage.removeItem("previousLanguageState");
		console.debug("Loaded and removed previous guide state from storage.");
	} else {
		console.debug("No previous language state found.");
	}
}
	
/**
 * Changes 'click' event of switch language links such that form state is migrated across different versions of it.
 */
document.addEventListener("DOMContentLoaded", function () {
    loadStateFromSessionStorage();
    var links = document.querySelectorAll("footer + .link a");
    for (var i=0; i<links.length; i++) {
	    links[i].addEventListener("click", function (e) {
		    addStateToSessionStorage();
	    });
    }
});

