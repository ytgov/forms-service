package ca.yukon.aem.core.forms.services.impl;

import com.adobe.forms.common.service.DataOptions;
import com.adobe.forms.common.service.DataProvider;
import com.adobe.forms.common.service.FormsException;
import com.adobe.forms.common.service.ContentType;
import com.adobe.forms.common.service.PrefillData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.osgi.service.component.annotations.Component;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = DataProvider.class,
        property = {
                "service.description=Query String Prefill Service",
                "service.vendor=YukonForms",
                "service.ranking:Integer=1000"
        }
)

public class QueryStringPrefillService implements DataProvider {
    private static final Logger log = LoggerFactory.getLogger(QueryStringPrefillService.class);
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    // Helper Functions
    // Build the nested JSON structure, "UserDetails.name" > { "UserDetails": { "name": "John" } }
    private static void buildDataStructure(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\."); // split by .
        Map<String, Object> cursor = root;
        for (int i = 0; i < parts.length; i++) {
            String key = parts[i];
            boolean last = (i == parts.length - 1);
            if (last) {
                cursor.put(key, value);
            } else {
                // go one level deeper or create the level if it does not exist yet
                Object next = cursor.get(key);
                if (!(next instanceof Map)) {
                    next = new LinkedHashMap<String, Object>();
                    cursor.put(key, next);
                }
                cursor = (Map<String, Object>) next;
            }
        }
    }

    // Skip AEM system params from JSON prefill data
    private static boolean isIgnorableKey(String k) {
        return "wcmmode".equalsIgnoreCase(k) || "cq_ck".equalsIgnoreCase(k);
    }

    // Unicode and non-ASCII Character decoder
    private static String urlDecodeUtf8(String s) {
        if (s == null || s.isBlank()) return s;
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // If malformed % sequence, then keep original to avoid data loss
            return s;
        }
    }

    @Override
    public PrefillData getPrefillData(DataOptions dataOptions) throws FormsException {
        log.info("QueryStringPrefillService --------- V3 ------- Prefill START extras={}", dataOptions.getExtras());

        try {
            // Read query params (via extras)
            // AEM passes request‑context parameters via DataOptions “extras” & copy all of them into the AF prefill structure.
            log.info("QueryStringPrefillService --------- V3 -------  in function");

            Map<String, Object> extras = dataOptions.getExtras(); // contains request-scope data like query params
            Map<String, Object> data = new LinkedHashMap<>();

            if (extras != null) {
                // Resolve url_referrer, if declared in params get that value, otherwise get Header Referer, exposed in extras as "url_referrer", then decode
                String resolvedReferrer = null;
                Object p = extras.get("url_referrer");
                if (p instanceof String && !((String) p).isBlank()) {
                    resolvedReferrer = (String) p;
                } else if (p instanceof String[] && ((String[]) p).length > 0 && !((String[]) p)[0].isBlank()) {
                    resolvedReferrer = ((String[]) p)[0];
                } else if (p instanceof java.util.Collection && !((java.util.Collection<?>) p).isEmpty()) {
                    Object first = ((java.util.Collection<?>) p).iterator().next();
                    if (first != null && !String.valueOf(first).isBlank()) {
                        resolvedReferrer = String.valueOf(first);
                    }
                } else {
                    // Fallback to HTTP Referer Header value exposed in extras as "referer"
                    Object h = extras.get("referer");
                    if (h instanceof String && !((String) h).isBlank()) {
                        resolvedReferrer = (String) h;
                    } else if (h instanceof String[] && ((String[]) h).length > 0 && !((String[]) h)[0].isBlank()) {
                        resolvedReferrer = ((String[]) h)[0];
                    } else if (h instanceof java.util.Collection && !((java.util.Collection<?>) h).isEmpty()) {
                        Object firstH = ((java.util.Collection<?>) h).iterator().next();
                        if (firstH != null && !String.valueOf(firstH).isBlank()) {
                            resolvedReferrer = String.valueOf(firstH);
                        }
                    }
                }

                if (resolvedReferrer != null) {
                    resolvedReferrer = urlDecodeUtf8(resolvedReferrer);
                }

                for (Map.Entry<String, Object> entry : extras.entrySet()) {
                    // Skip null, blank and AEM system params from JSON prefill data
                    String key = entry.getKey();
                    if (key == null || key.isBlank() || isIgnorableKey(key)) continue;

                    // Explicitly skip url_referrer and referer to avoid duplicates
                    // Locale.ROOT = Make sure param values behave consistently regardless of locale
                    String keyLower = key.toLowerCase(Locale.ROOT);
                    if (keyLower.equals("url_referrer") || keyLower.equals("referer")) continue;

                    // Support both dotted and flat keys. If the key contains dots, treat as path.
                    // flat keys: ?name=john&email=john@example.com
                    // dotted keys: ?userinfo.name=john&userinfo.age=26
                    // If it doesn't, then put it at root (still works for fields bound by last-segment).
                    Object dataValue = entry.getValue();
                    if (dataValue instanceof String) {
                        String v = urlDecodeUtf8((String) dataValue);
                        if (key.contains(".")) buildDataStructure(data, key, v);
                        else data.put(key, v);
                    } else if (dataValue instanceof String[]) {
                        List<String> decoded = new ArrayList<>();
                        for (String s : (String[]) dataValue) decoded.add(urlDecodeUtf8(s));
                        if (key.contains(".")) buildDataStructure(data, key, decoded);
                        else data.put(key, decoded);
                    } else if (dataValue instanceof java.util.Collection) {
                        List<String> decoded = new ArrayList<>();
                        for (Object o : (java.util.Collection<?>) dataValue) {
                            if (o != null) decoded.add(urlDecodeUtf8(String.valueOf(o)));
                        }
                        if (key.contains(".")) buildDataStructure(data, key, decoded);
                        else data.put(key, decoded);
                    }
                    // Skip other types silently, ex: skip non-string-ish values
                }

                // Add the resolved referrer
                if (resolvedReferrer != null && !resolvedReferrer.isBlank()) {
                    // Save under "url_referrer"
                    data.put("url_referrer", resolvedReferrer);
                }
            }

            log.info("QueryStringPrefillService --------- V3 -------  Prefill called with extras: {}", extras);

            // Build Foundation AF prefill JSON: afData.afBoundData.data
            Map<String, Object> payload = Map.of(
                    "afData", Map.of(
                            "afBoundData", Map.of(
                                    "data", data
                            )
                    )
            );

            byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
            log.info("QueryStringPrefillService --------- V3 ------- Prefill OK bytes={}", bytes.length);
            return new PrefillData(new ByteArrayInputStream(bytes), ContentType.JSON);

        } catch (Exception ex) {
            log.error("QueryStringPrefillService --------- V3 ------- Prefill FAIL", ex);

            // Always return a valid (empty) payload to avoid UI hang
            byte[] empty = "{\"afData\":{\"afBoundData\":{\"data\":{}}}}".getBytes(StandardCharsets.UTF_8);
            return new PrefillData(new ByteArrayInputStream(empty), ContentType.JSON);
        }

    }

    @Override
    public String getServiceName() {
        return "query-string-prefill-service";
    }

    @Override
    public String getServiceDescription() {
        return "Query String Prefill Service";
    }
}
