/**
 * To be used on guideRootPanel is initialized event
 * @name loadFormWithQueryParams Load Form with Query Params
 * @returns {void}
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

        if (!search || search === "?") search = "";

        // Get URL Parameters
        var qs = new URLSearchParams(search);
        var dataStructure = Object.create(null); // dotted keys -> nested object
        var lastValues = Object.create(null); // dedupe keys (URLSearchParams iterates per value), store the last occurrence of a key-value pair

        // Resolve url_referrer: param first, then document.referrer (browser analogue of HTTP Referer)
        var resolvedReferrer = "";
        var allUrlRef = qs.getAll("url_referrer");
        var tmpUrlRef = allUrlRef.length ? allUrlRef[allUrlRef.length - 1] : "";
        tmpUrlRef = (tmpUrlRef || "").trim();
        if (tmpUrlRef) {
            // decode '+' as space
            resolvedReferrer = tmpUrlRef.replace(/\+/g, " ");
        } else {
            try {
                var docRef = (document.referrer || "").trim();
                if (docRef) {
                    try {
                        resolvedReferrer = decodeURI(docRef);
                    } catch (e) {
                        resolvedReferrer = docRef;
                    }
                }
            } catch (e) {
                // nothing
            }
        }

        // Collect the last value per key (overwrites earlier ones)
        qs.forEach(function (value, key) {
            if (!key) return;
            var lk = key.toLowerCase();
            // Ignore AEM tech params and avoid duplicates for referrer keys
            if (lk === "wcmmode" || lk === "cq_ck" || lk === "url_referrer" || lk === "referer") return; // ignore tech/dup keys

            // URLSearchParams decodes %xx; normalize '+' -> space and trim
            var v = (value == null) ? "" : String(value);
            v = v.replace(/\+/g, " ").trim();
            if (v !== "") {
                lastValues[key] = v; // later duplicates overwrite earlier ones
            }
        });

        // Build the nested JSON structure for dotted keys, userinfo.name=john > {userinfo:{name:"john"}})
        Object.keys(lastValues).forEach(function (key) {
            var value = lastValues[key];

            if (key.indexOf(".") > -1) {
                // inline buildDataStructure(root=data, path=key, value=value)
                var parts = key.split(".");
                var cursor = dataStructure;
                for (var i = 0; i < parts.length; i++) {
                    var last = (i === parts.length - 1);
                    var p = parts[i];

                    // Guard against prototype pollution & empty segments
                    if (!p || p === "__proto__" || p === "constructor" || p === "prototype") continue;

                    if (last) {
                        cursor[p] = value;
                    } else {
                        if (typeof cursor[p] !== "object" || cursor[p] === null || Array.isArray(cursor[p])) {
                            cursor[p] = Object.create(null);
                        }
                        cursor = cursor[p];
                    }
                }
            } else {
                // Guard against prototype pollution at root
                if (key === "__proto__" || key === "constructor" || key === "prototype") return;
                dataStructure[key] = value;
            }
        });

        if (resolvedReferrer) dataStructure["url_referrer"] = resolvedReferrer;

        // Push the whole dataStructure at once
        // If it fails, field-by-field below still runs
        try {
            if (Object.keys(dataStructure).length) {
                window.guideBridge.setData({data: dataStructure});
            }
        } catch (e) {
            // nothing
        }

        // Set values field-by-field (covers flat keys and dotted-last-segment fallbacks)
        // flat keys: ?name=john&email=john@example.com
        // dotted keys: ?userinfo.name=john&userinfo.age=26
        Object.keys(lastValues).forEach(function (key) {
            var lk = String(key).toLowerCase();

            // Skip AEM system params from JSON prefill data
            if (lk === "wcmmode" || lk === "cq_ck" || lk === "url_referrer" || lk === "referer") return;

            var val = lastValues[key];
            var candidates = [key];
            if (key.indexOf(".") > -1) candidates.push(key.split(".").pop()); // userinfo.name -> name

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

        // Ensure the explicit field bound to `url_referrer` gets the final value
        if (resolvedReferrer) {
            try {
                var refNode = window.guideBridge.resolveNode("url_referrer");
                if (refNode) refNode.value = resolvedReferrer;
            } catch (e) {
                // nothing
            }
        }
    });
}
