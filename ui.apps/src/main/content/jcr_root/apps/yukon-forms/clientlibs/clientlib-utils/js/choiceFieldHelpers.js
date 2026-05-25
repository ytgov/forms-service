/** Returns an array with one option for choice fields (radio buttons or checkboxes). Used to set the options of such fields in AEM's rules.
 *
@name createChoiceFieldOption Creates an array from a value and a display value
@param {string} value Value of the option (on Author, it's the value before the '=' sign)
@param {string} displayValue Display value of the option (on Author, it's the value after the '=' sign)
@return {string[]} An array with the new option
 */
function createChoiceFieldOption(value, displayValue) {
	return [value + "=" + displayValue];
}

