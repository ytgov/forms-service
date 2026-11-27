/*
 * File size formatting and authorable-message placeholders (WEBPLAN: custom file upload).
 * Shared so other components (e.g. the maximum total size check) can reuse them.
 *
 * Sizes are formatted for display only: limits are always compared on raw bytes, never on
 * these strings.
 */

/** Bytes in a KB. AEM's per-file "Maximum file size" check is binary: file.size / 1024 / 1024 > limit. */
var FILE_SIZE_BINARY_BASE = 1024;

/** Whether a language code (en, fr, fr-CA, fr_CA) is French. */
function _isFrenchLocale(locale) {
	return typeof locale === "string" && /^fr([-_]|$)/i.test(locale.trim());
}

/** Formats a file size for display, always rounding up, so a file is never shown smaller than it is.
 *
 * Under 1 KB shows "1 KB"; under 1 MB shows whole KB; from 1 MB, MB with one decimal.
 * English: "5.7 MB", "340 KB". French: "5,7 Mo", "340 Ko", with a non-breaking space.
 *
@name formatSize
@param {number} bytes Size in bytes
@param {string} locale Language of the form: "en" or "fr"
@param {number} base Bytes in a KB; leave out for 1024, the base of AEM's maximum file size check
@return {string} The formatted size, or "" when bytes isn't a number
 */
function formatSize(bytes, locale, base) {
	var size = Number(bytes);
	if (bytes === null || bytes === undefined || bytes === "" || !isFinite(size) || size < 0) {
		return "";
	}
	var kb = Number(base) > 0 ? Number(base) : FILE_SIZE_BINARY_BASE;
	var mb = kb * kb;
	var french = _isFrenchLocale(locale);
	var space = french ? "\u00A0" : " ";
	if (size < mb) {
		// Under 1 KB (including 0 bytes) shows as 1 KB.
		return Math.max(1, Math.ceil(size / kb)) + space + (french ? "Ko" : "KB");
	}
	// Tenths of a MB, rounded up. Rounding the ratio first to 9 places keeps floating-point noise
	// (e.g. 5.700000000001) from rounding an exact value up by an extra tenth.
	var tenths = Math.ceil(Math.round((size * 10 / mb) * 1e9) / 1e9);
	var text = (tenths / 10).toFixed(1);
	return (french ? text.replace(".", ",") : text) + space + (french ? "Mo" : "MB");
}

/** Fills {placeholders} in an authorable message.
 *
 * A placeholder without a value becomes empty text, never the literal {token}; brackets, quotes and
 * spaces left empty by it are tidied so the sentence still reads. For French, the spaces inside « »
 * and before : ; ? ! become non-breaking.
 *
@name fillMessagePlaceholders
@param {string} template Message text, e.g. "{fileName}" ({size}) wasn't added.
@param {object} values Placeholder values, e.g. {fileName: "a.pdf", size: "5.7 MB"}
@param {string} locale Language of the form: "en" or "fr"
@return {string} The message
 */
function fillMessagePlaceholders(template, values, locale) {
	if (template === null || template === undefined) {
		return "";
	}
	var data = values || {};
	var message = String(template).replace(/\{\s*([A-Za-z0-9_]+)\s*\}/g, function (match, key) {
		var value = Object.prototype.hasOwnProperty.call(data, key) ? data[key] : null;
		return value === null || value === undefined ? "" : String(value);
	});
	message = message
		.replace(/\(\s*\)/g, "")                    // "()" left by an empty {size}
		.replace(/"\s*"|“\s*”|«[\s\u00A0]*»/g, "")  // quotes left by an empty {fileName}
		.replace(/[ \t]{2,}/g, " ")
		.replace(/ +([.,])/g, "$1")
		.trim();
	if (_isFrenchLocale(locale)) {
		message = message
			.replace(/«[ \t\u00A0]*/g, "«\u00A0")
			.replace(/[ \t\u00A0]*»/g, "\u00A0»")
			.replace(/[ \t\u00A0]+([:;?!])/g, "\u00A0$1");
	}
	return message;
}
