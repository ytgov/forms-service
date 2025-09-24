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
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        data.put(entry.getKey(), value);
                    } else if (value instanceof String[]) {
                        data.put(entry.getKey(), Arrays.asList((String[]) value));
                    } else if (value instanceof Collection) {
                        List<String> list = new ArrayList<>();
                        for (Object o : (Collection<?>) value) {
                            if (o != null) list.add(String.valueOf(o));
                        }
                        data.put(entry.getKey(), list);
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
