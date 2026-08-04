/** Extracts the display value of a date field
 *
@name getDateDisplayValue
@param {string} SOM expression of the date field to extract display value from
@return {string} The display value of the date field
 */
function getDateDisplayValue(dateFieldSOM) {
	var dateField = window.guideBridge.resolveNode(dateFieldSOM);
	if (!dateField) {
		console.debug("Date field not found. Returning empty string as display value.");
		return "";
	}
	return dateField.formattedValue;
}


/** Returns today's date, according to the user's timezone
 *
@name todaysDate
@return {Date} Todays date
*/
function todaysDate() {
	return new Date();
}

