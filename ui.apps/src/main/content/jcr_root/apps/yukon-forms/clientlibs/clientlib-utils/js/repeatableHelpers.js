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

// TODO: rendering repeatable panels from replicated data causes form to freeze
// // Renders an item using either a single template string or a {discriminator -> template} map.
// function _renderItem(item, templateOrMap, discriminatorField) {
//     console.debug('_renderItem running')
//     var template;
//     if (typeof templateOrMap === "string") {
//         template = templateOrMap;
//     } else {
//         var key = item[discriminatorField];
//         template = templateOrMap[key] || templateOrMap["_default"] || "";
//     }
//     return template.replace(/\{(\w+)\}/g, function (_, k) {
//         return item[k] == null ? "" : item[k];
//     }).trim();
// }

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
    console.debug("[getDataFromRepeatablePanel] called with:", { containerName: containerName, repeatableName: repeatableName, fieldNamesCsv: fieldNamesCsv });

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
    console.debug("[getDataFromRepeatablePanel] instance count:", instances.length);

    var results = instances.map(function (inst, idx) {
        var item = { _index: idx };
        fieldNames.forEach(function (fieldName) {
            // Search through descendants because fields may be in nested panels (e.g., address fields live inside personServedMailingAddress)
            var field = _findByName(inst, fieldName);
            item[fieldName] = field ? field.value : "";
        });
        console.debug("[getDataFromRepeatablePanel] instance " + idx + ":", item);
        return item;
    });

    console.debug("[getDataFromRepeatablePanel] returning:", results);
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
    console.debug("[filterData] called with:", { fieldName: fieldName, operator: operator, value: value });

    var data;
    try { data = JSON.parse(dataJson); }
    catch (e) { console.error("invalid dataJson", e); return "[]"; }

    var filtered = data.filter(function (item) {
        var actual = item[fieldName];
        return operator === "notEquals" ? actual !== value : actual === value;
    });

    console.debug("[filterData] filtered count:", filtered.length, "of", data.length);
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
    console.debug("[formatData] called with:", { itemTemplate: itemTemplate, separator: separator });

    var data;
    try { data = JSON.parse(dataJson); }
    catch (e) { console.error("invalid dataJson", e); return ""; }

    var lines = data.map(function (item) {
        return itemTemplate.replace(/\{(\w+)\}/g, function (_, key) {
            return item[key] == null ? "" : item[key];
        });
    }).filter(Boolean);

    var output = lines.join(separator !== "," ? separator : ", ");
    console.debug("[formatData] returning:", output);
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
    console.debug("[formatDataConditional] called with:", {
        discriminatorField: discriminatorField,
        templateMapJson: templateMapJson,
        separator: separator
    });

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
    console.debug("[formatDataConditional] returning:", output);
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
    console.debug("[getFilteredFormatted] called with:", {
        containerName: containerName,
        repeatableName: repeatableName,
        fieldNamesCsv: fieldNamesCsv,
        filterField: filterField,
        filterOperator: filterOperator,
        filterValue: filterValue,
        itemTemplate: itemTemplate,
        separator: separator
    });
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
        console.debug("[nav] populated:", result);
    }
};

// TODO: fix rendering portion of logic, causing form to freeze
// /** Adjusts a destination repeatable panel to match the number of filtered respondents,
// then populates each instance's target field with formatted text.
//  *
// @name populatePanelsFromData Populate a repeatable panel with data from another repeatable
// @param {string} sourceContainerName Name of the source wrapper panel
// @param {string} sourceRepeatableName Name of the source repeatable
// @param {string} fieldNamesCsv Fields to pull from source
// @param {string} filterField Field to filter by (empty string to skip)
// @param {string} filterOperator "equals" or "notEquals"
// @param {string} filterValue Value to compare against
// @param {string} targetContainerName Name of the destination wrapper panel
// @param {string} targetRepeatableName Name of the destination repeatable
// @param {string} targetFieldName Name of the field inside each destination instance to populate
// @param {string} itemTemplate Template to render for each item, e.g. "{personServedFirstName} {personServedLastName}"
// @param {string} discriminatorField Optional. Field name whose value selects which template from the map. Required if itemTemplate is a map
// @return {string}
//  */
// function populatePanelsFromData(sourceContainerName, sourceRepeatableName, fieldNamesCsv,
//                                        filterField, filterOperator, filterValue,
//                                        targetContainerName, targetRepeatableName, targetFieldName, itemTemplate, discriminatorField) {
//     console.debug("[populatePanelsFromData] called with:", {
//         sourceContainerName: sourceContainerName, 
//         sourceRepeatableName: sourceRepeatableName, 
//         fieldNamesCsv: fieldNamesCsv,
//         filterField: filterField, 
//         filterOperator: filterOperator, 
//         filterValue: filterValue,
//         targetContainerName: targetContainerName, 
//         targetRepeatableName: targetRepeatableName, 
//         targetFieldName: targetFieldName, 
//         itemTemplate: itemTemplate,
//         discriminatorField: discriminatorField
//     });

//     // 1. Get + filter source data
//     var dataJson = getDataFromRepeatablePanel(sourceContainerName, sourceRepeatableName, fieldNamesCsv);
//     if (filterField) dataJson = filterData(dataJson, filterField, filterOperator, filterValue);
//     var data = JSON.parse(dataJson);
//     console.debug("[populatePanelsFromData] filtered data:", data);

//     // 2. Find target repeatable
//     var root = guideBridge._guide && guideBridge._guide.rootPanel;
//     var targetContainer = _findByName(root, targetContainerName);
//     if (!targetContainer) { console.warn("target container not found"); return "Error"; }

//     var searchIn = targetContainer;
//     if (targetContainer.instanceManager && targetContainer.instanceManager.instances[0]) {
//         searchIn = targetContainer.instanceManager.instances[0];
//     }
//     var targetRepeatable = (searchIn.children || []).find(function (c) { return c && c.name === targetRepeatableName; });
//     if (!targetRepeatable || !targetRepeatable.instanceManager) { console.warn("target repeatable not found"); return "Error"; }

//     var im = targetRepeatable.instanceManager;
//     console.debug("[populatePanelsFromData] current target instances:", im.instances.length, "| needed:", data.length);

//     // 3. Adjust instance count
//     var minOccur = im.minOccur || 0;
//     while (im.instances.length > Math.max(minOccur, data.length)) {
//         im.removeInstance(im.instances.length - 1);
//     }
//     while (im.instances.length < data.length) {
//         im.addInstance();
//     }

//     // TODO: fix this part, causing form to freeze
//     // 4. Populate each instance's target field
//     // Small delay to let AEM render newly-added instances
//     setTimeout(function () {
//         data.forEach(function (item, idx) {
//             var instance = im.instances[idx];
//             if (!instance) { console.warn("no instance at index", idx); return; }

//             var field = _findByName(instance, targetFieldName);
//             if (!field) { console.warn("target field not found in instance", idx); return; }

//             var rendered = _renderItem(item, itemTemplate, discriminatorField);
//             field.value = rendered;
//             console.debug("[populatePanelsFromData] set instance " + idx + " field to:", rendered);
//         });
//     }, 100);

//     return "Populated " + data.length + " instances";
// };

// TODO: dynamically creating repeatable panels causes form to freeze
// pre-populate destination repeatable panel with filtered data from source repeatable
// function runPrePopulatePanels() {
//     populatePanelsFromData(
//         "personsServedContainer",
//         "personToBeServed",
//         "personServedFirstName,personServedLastName,personServedOrganizationName,personServedIsIndividualOrOrganization,personServedMailingProvince",
//         "personServedMailingProvince",
//         "notEquals",
//         "YT",
//         "outsideYukonRespondentsContainer",   // destination wrapper panel name
//         "outsideYukonRespondent",             // destination repeatable name
//         "respondentName",                     // text field inside each destination instance
//         {
//             "Individual": "{personServedFirstName} {personServedLastName}",
//             "Organization": "{personServedOrganizationName}"
//         },
//         "personServedIsIndividualOrOrganization"
//     );
// }

// listener for nav to destination panel for where to replicate data to
(function () {
    function setupNavigationListener() {
        if (typeof guideBridge === "undefined" || !guideBridge.on) {
            setTimeout(setupNavigationListener, 200);
            return;
        }

        guideBridge.on("elementNavigationChanged", function (event, payload) {
            var targetName = payload && payload.target && payload.target.name;
            console.debug("[nav] arrived at:", targetName);

            if (targetName === "serviceOutsideYukon") {
                console.debug("[nav] running pre-populate (text field)");
                runPrePopulate();
            }

            // TODO: uncomment when dynamic panel replication is fixed
            // if (targetName === "serviceOutsideYukon") {
            //     console.debug("[nav] running pre-populate (panels)");
            //     runPrePopulatePanels();
            // }
        });

        console.debug("[nav] listener registered");
    }

    setupNavigationListener();
})();