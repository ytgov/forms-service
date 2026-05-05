// Shared walker used by all helpers
function _findByName(node, name, visited) {
    visited = visited || new Set();
    if (!node || typeof node !== "object" || visited.has(node)) return null;
    visited.add(node);
    if (node.name === name) return node;

    var kids = node.children || [];
    for (var i = 0; i < kids.length; i++) {
        var found = _findByName(kids[i], name, visited);
        if (found) return found;
    }
    if (node.instanceManager && node.instanceManager.instances) {
        var insts = node.instanceManager.instances;
        for (var j = 0; j < insts.length; j++) {
            var found2 = _findByName(insts[j], name, visited);
            if (found2) return found2;
        }
    }
    return null;
};

/** Generic getter for a repeatable panel. Returns an array of objects,
one per instance, with the fields you requested.
 *
@name getDataFromRepeatablePanel Get specified fields from all instances of a repeatable panel
@param {string} containerName Name of the wrapper panel containing the repeatable
@param {string} repeatableName Name of the repeatable child panel
@param {string} fieldNamesCsv Comma-separated list of field names to pull from each instance. Example: "personServedFirstName,personServedLastName"
@return {string} JSON string of the extracted data (array of objects)
 */
function getDataFromRepeatablePanel(containerName, repeatableName, fieldNamesCsv) {

    var fieldNames = fieldNamesCsv.split(",").map(function (s) { return s.trim(); });
    var root = guideBridge._guide && guideBridge._guide.rootPanel;
    if (!root) { console.warn("no rootPanel"); return "[]"; }

    var container = _findByName(root, containerName);
    if (!container) { console.warn("container not found:", containerName); return "[]"; }

    var searchIn = container;
    if (container.instanceManager && container.instanceManager.instances[0]) {
        searchIn = container.instanceManager.instances[0];
    }

    var repeatable = (searchIn.children || []).find(function (c) { return c && c.name === repeatableName; });
    if (!repeatable || !repeatable.instanceManager) { console.warn("no instanceManager on:", repeatableName); return "[]"; }

    var instances = repeatable.instanceManager.instances;

    var results = instances.map(function (inst, idx) {
        var item = { _index: idx };
        fieldNames.forEach(function (fieldName) {
            // Search through descendants because fields may be in nested panels (e.g., address fields live inside personServedMailingAddress)
            var field = _findByName(inst, fieldName);
            item[fieldName] = field ? field.value : "";
        });
        return item;
    });

    return JSON.stringify(results);
};

/**
Filters an array of objects by whether a given field matches (or doesn't match) a value.
 *
@name filterData Filter data from getDataFromRepeatablePanel
@param {string} dataJson JSON string from getDataFromRepeatablePanel
@param {string} fieldName Field to filter by
@param {string} operator "equals" or "notEquals"
@param {string} value Value to compare against
@return {string} JSON string of filtered data
 */
function filterData(dataJson, fieldName, operator, value) {

    var data;
    try { data = JSON.parse(dataJson); }
    catch (e) { console.error("invalid dataJson", e); return "[]"; }

    var filtered = data.filter(function (item) {
        var actual = item[fieldName];
        return operator === "notEquals" ? actual !== value : actual === value;
    });

    return JSON.stringify(filtered);
};

/** Formats an array of objects into a string using a template.
 *
@name formatData Format data as a string using a template
@param {string} dataJson JSON string from getDataFromRepeatablePanel or filterData
@param {string} itemTemplate Template per item with {fieldName} placeholders. Example: "{personServedFirstName} {personServedLastName}"
@param {string} separator String to join items with. Example: ", "
@return {string}
 */
function formatData(dataJson, itemTemplate, separator) {

    var data;
    try { data = JSON.parse(dataJson); }
    catch (e) { console.error("invalid dataJson", e); return ""; }

    var lines = data.map(function (item) {
        return itemTemplate.replace(/\{(\w+)\}/g, function (_, key) {
            return item[key] == null ? "" : item[key];
        });
    }).filter(Boolean);

    var output = lines.join(separator !== "," ? separator : ", ");
    return output;
};

/**
Formats data with conditional templates based on a discriminator field.
 *
@name formatDataConditional Format data using a different template per item type
@param {string} dataJson JSON from afGetFromRepeatable or afFilterData
@param {string} discriminatorField Field whose value picks which template to use
@param {string} templateMapJson JSON map of {discriminatorValue: template}. Use "_default" key for fallback. Example: {"Individual":"{personServedFirstName} {personServedLastName}","Organization":"{personServedOrganizationName}"}
@param {string} separator String to join items with
@return {string}
 */
function formatDataConditional(dataJson, discriminatorField, templateMapJson, separator) {

    var data, templateMap;
    try {
        data = typeof dataJson === "string" ? JSON.parse(dataJson) : dataJson;
        templateMap = typeof templateMapJson === "string" ? JSON.parse(templateMapJson) : templateMapJson;
    } catch (e) {
        console.error("[formatDataConditional] invalid JSON:", e);
        return "";
    }

    var lines = data.map(function (item) {
        var discriminatorValue = item[discriminatorField];
        var template = templateMap[discriminatorValue] || templateMap["_default"] || "";
        return template.replace(/\{(\w+)\}/g, function (_, key) {
            return item[key] == null ? "" : item[key];
        }).trim();
    }).filter(Boolean);

    var output = lines.join(separator || ", ");
    return output;
}

/**
Convenience function that gets, filters, and formats repeatable panel data in one call.
 *
@name getFilteredFormattedData Get filtered and formatted data from a repeatable panel
@param {string} containerName Name of the wrapper panel, e.g. personsServedContainer
@param {string} repeatableName Name of the repeatable child, e.g. personToBeServed
@param {string} fieldNamesCsv Fields to pull (comma-separated), e.g. personServedFirstName,personServedLastName,personServedMailingProvince
@param {string} filterField Field to filter by (empty string to skip filtering), e.g.personServedMailingProvince
@param {string} filterOperator "equals" or "notEquals", e.g. notEquals
@param {string} filterValue Value to compare against, e.g. YT
@param {string} itemTemplate Template per item, e.g. {personServedFirstName} {personServedLastName}
@param {string} separator Separator for joining, e.g. ,
@return {string}
 */
function getFilteredFormattedData(containerName, repeatableName, fieldNamesCsv, filterField, filterOperator, filterValue, itemTemplate, separator) {

    var data = getDataFromRepeatablePanel(containerName, repeatableName, fieldNamesCsv);
    if (filterField) {
        data = filterData(data, filterField, filterOperator, filterValue);
    }
    return formatData(data, itemTemplate, separator);
};

// TODO: look into making the destination more general, i.e. a param
// pre-populate destination field with data from a repeatable panel
function runPrePopulate() {
    var data = getDataFromRepeatablePanel(
        "personsServedContainer",
        "personToBeServed",
        "personServedFirstName,personServedLastName,personServedOrganizationName,personServedIsIndividualOrOrganization,personServedMailingProvince"
    );

    data = filterData(data, "personServedMailingProvince", "notEquals", "YT");

    var result = formatDataConditional(
        data,
        "personServedIsIndividualOrOrganization",
        JSON.stringify({
            "Individual":   "{personServedFirstName} {personServedLastName}",
            "Organization": "{personServedOrganizationName}"
        }),
        ", "
    );

    var root = guideBridge._guide && guideBridge._guide.rootPanel;
    var destField = _findByName(root, "outsideYukonIndividualNames"); // destination component name, TODO: change to 'endorsementDoR'
    if (destField) {
        destField.value = result;
    }
};

/** Adds instances to a destination repeatable panel matching the count of source instances.
Destination panels are expected to contain only blank fields — no population is done.
 *
@name addPanelsFromData Add instances to a repeatable panel matching the count from another repeatable panel
@param {string} sourceContainerName Name of the source wrapper panel
@param {string} sourceRepeatableName Name of the source repeatable panel
@param {string} targetContainerName Name of the destination wrapper panel
@param {string} targetRepeatableName Name of the destination repeatable panel
@return {void} 
 */
function addPanelsFromData(sourceContainerName, sourceRepeatableName,
                           targetContainerName, targetRepeatableName) {

    var root = guideBridge._guide && guideBridge._guide.rootPanel;
    if (!root) { console.warn("[addPanelsFromData] no rootPanel"); return "Error"; }

    // Helper: locate a repeatable inside a container, handling the case where
    // the container itself is wrapped in an instanceManager.
    function findRepeatable(containerName, repeatableName) {
        var container = _findByName(root, containerName);
        if (!container) { console.warn("[addPanelsFromData] container not found:", containerName); return null; }

        var searchIn = container;
        if (container.instanceManager && container.instanceManager.instances[0]) {
            searchIn = container.instanceManager.instances[0];
        }
        var repeatable = (searchIn.children || []).find(function (c) { return c && c.name === repeatableName; });
        if (!repeatable || !repeatable.instanceManager) {
            console.warn("[addPanelsFromData] repeatable not found:", repeatableName);
            return null;
        }
        return repeatable;
    }

    // 1. Get source count
    var sourceRepeatable = findRepeatable(sourceContainerName, sourceRepeatableName);
    if (!sourceRepeatable) return "Error";
    var sourceCount = sourceRepeatable.instanceManager.instances.length;

    // 2. Find target repeatable
    var targetRepeatable = findRepeatable(targetContainerName, targetRepeatableName);
    if (!targetRepeatable) return "Error";
    var im = targetRepeatable.instanceManager;

    // 3. Adjust instance count
    var minOccur = im.minOccur || 0;
    var targetLength = Math.max(minOccur, sourceCount);

    var toRemove = im.instances.length - targetLength;
    for (var j = 0; j < toRemove; j++) {
        im.removeInstance(im.instances.length - 1);
    }
    
    var toAdd = targetLength - im.instances.length;
    for (var i = 0; i < toAdd; i++) {
        im.addInstance();
    }

    // 4. Find all Sign Block wrappers and hide any that aren't already hidden
    // setTimeout to let the panels finish rendering
    setTimeout(function () {
        document.querySelectorAll('[id$="-adobesignblock___guide-item"]').forEach(function (el) {
            el.classList.add('hidden');
        });
    }, 200);

    return;
};

// Add same # of instances to destination repeatable panel as source repeatable panel
function runAddSignBlocks() {
    addPanelsFromData(
        "petitionersRepeatableContainer",       // source wrapper
        "petitioner",                           // source repeatable
        "petitionSignatures",                   // destination wrapper
        "petitionSignatureFields"               // destination repeatable
    );
};

// listener for nav to destination panel for where to replicate data to
(function () {
    var MAX_RETRIES = 50;
    var RETRY_INTERVAL = 200;
    var retryCount = 0;

    function setupNavigationListener() {
        if (typeof guideBridge === "undefined" || !guideBridge.on) {
            retryCount++;
            if (retryCount >= MAX_RETRIES) {
                console.warn("[setupNavigationListener] guideBridge not ready after " + 
                    (MAX_RETRIES * RETRY_INTERVAL / 1000) + 
                    " seconds. Navigation listener will not be registered.");
                return;
            }
            setTimeout(setupNavigationListener, RETRY_INTERVAL);
            return;
        }

        guideBridge.on("elementNavigationChanged", function (event, payload) {
            var targetName = payload && payload.target && payload.target.name;

            // on serviceOutsideYukon page, pull outside yukon respondents names & pre-populate
            if (targetName === "serviceOutsideYukon") {
                runPrePopulate();
            }

            // on signaturePanel, add instances to repeatable signature panel matching # of petitioners
            if (targetName === "signaturePanel") {
                runAddSignBlocks();
            }
        });

    }

    setupNavigationListener();
})();