/**
 * To be used on guideRootPanel is initialized event
 * @name loadFormWithQueryParams Load Form with Query Params
 * @returns {string}
 */

function loadFormWithQueryParams() {
    window.guideBridge.connect(function () {
        // Get the query string
        var search = "";
        try {
            if (window.location.search) search = window.location.search;
        } catch (e) {
            // nothing
        }

        if (!search) {
            try {
                if (window.top && window.top !== window && window.top.location && window.top.location.search) {
                    search = window.top.location.search;
                }
            } catch (e) {
                // nothing
            }
        }

        if (!search && window.location.hash && window.location.hash.indexOf("?") > -1) {
            search = window.location.hash.slice(window.location.hash.indexOf("?"));
        }

        if (!search || search === "?") return;

        var qs = new URLSearchParams(search);
        var dataStructure = {}; // dotted keys → nested object

        // Build the nested JSON structure for dotted keys, userinfo.name=john > {userinfo:{name:"john"}})
        qs.forEach(function (val, key) {
            if (!key) return;
            var lk = key.toLowerCase();
            if (lk === "wcmmode" || lk === "cq_ck") return; // ignore AEM tech params
            if (key.indexOf(".") > -1) {
                var parts = key.split(".");
                var cursor = dataStructure;
                for (var i = 0; i < parts.length; i++) {
                    var last = (i === parts.length - 1);
                    var p = parts[i];
                    if (last) {
                        cursor[p] = val;
                    } else {
                        if (typeof cursor[p] !== "object" || cursor[p] === null || Array.isArray(cursor[p])) {
                            cursor[p] = {};
                        }
                        cursor = cursor[p];
                    }
                }
            }
        });

        // Push the whole dataStructure at once
        // If it fails, field-by-field below still runs
        try {
            if (Object.keys(dataStructure).length) {
                window.guideBridge.setData({data: dataStructure});
            }
        } catch (e) { /* safe to ignore */
        }

        // Set values field-by-field (covers flat keys and dotted-last-segment fallbacks)
        // flat keys: ?name=john&email=john@example.com
        // dotted keys: ?userinfo.name=john&userinfo.age=26
        qs.forEach(function (val, key) {
            if (!key) return;
            var lk = key.toLowerCase();

            // Skip AEM system params from JSON prefill data
            if (lk === "wcmmode" || lk === "cq_ck") return;

            var candidates = [key];
            if (key.indexOf(".") > -1) candidates.push(key.split(".").pop()); // userinfo.name → name

            for (var i = 0; i < candidates.length; i++) {
                try {
                    var node = window.guideBridge.resolveNode(candidates[i]);
                    if (node) {
                        // light coercion: booleans + numbers
                        var v = val;
                        var t = (node.type || node.jsonType || "").toLowerCase();
                        if (t === "boolean") {
                            var s = String(val).toLowerCase();
                            v = (s === "1" || s === "true" || s === "yes" || s === "on");
                        } else if (t === "number") {
                            var n = Number(val);
                            if (!Number.isNaN(n)) v = n;
                        }
                        node.value = v;
                        break; // stop after first match
                    }
                } catch (e) {
                    // nothing
                }
            }
        });
    });
}

