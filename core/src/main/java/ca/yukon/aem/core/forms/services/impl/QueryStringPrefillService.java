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

    @Override
    public PrefillData getPrefillData(DataOptions dataOptions) throws FormsException {
        log.info("QueryStringPrefillService --------- V2 ------- Prefill START extras={}", dataOptions.getExtras());

        try {
            // Read query params (via extras)
            // AEM passes request‑context parameters via DataOptions “extras” & copy all of them into the AF prefill structure.
            log.info("QueryStringPrefillService --------- V2 -------  in function");

            Map<String, Object> extras = dataOptions.getExtras(); // contains request-scope data like query params
            Map<String, Object> data = new LinkedHashMap<>();

            if (extras != null) {
                for (Map.Entry<String, Object> entry : extras.entrySet()) {
                    String key = entry.getKey();
                    if (key == null || key.isBlank() || isIgnorableKey(key)) continue;

                    // Support both dotted and flat keys. If the key contains dots, treat as path.
                    // flat keys: ?name=john&email=john@example.com
                    // dotted keys: ?userinfo.name=john&userinfo.age=26
                    // If it doesn't, then put it at root (still works for fields bound by last-segment).
                    if (entry.getValue() instanceof String) {
                        String v = (String) entry.getValue();
                        if (key.contains(".")) buildDataStructure(data, key, v);
                        else data.put(key, v);
                    } else if (entry.getValue() instanceof String[]) {
                        List<String> list = java.util.Arrays.asList((String[]) entry.getValue());
                        if (key.contains(".")) buildDataStructure(data, key, list);
                        else data.put(key, list);
                    } else if (entry.getValue() instanceof java.util.Collection) {
                        java.util.List<String> list = new java.util.ArrayList<>();
                        for (Object o : (java.util.Collection<?>) entry.getValue()) if (o != null) list.add(String.valueOf(o));
                        if (key.contains(".")) buildDataStructure(data, key, list);
                        else data.put(key, list);
                    }
                    // Skip other types silently, ex: skip non-string-ish values
                }
            }

            log.info("QueryStringPrefillService --------- V2 -------  Prefill called with extras: {}", extras);

            // Build Foundation AF prefill JSON: afData.afBoundData.data
            Map<String, Object> payload = Map.of(
                    "afData", Map.of(
                            "afBoundData", Map.of(
                                    "data", data
                            )
                    )
            );

            byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
            log.info("QueryStringPrefillService --------- V2 ------- Prefill OK bytes={}", bytes.length);
            return new PrefillData(new ByteArrayInputStream(bytes), ContentType.JSON);

        } catch (Exception ex) {
            log.error("QueryStringPrefillService --------- V2 ------- Prefill FAIL", ex);

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
