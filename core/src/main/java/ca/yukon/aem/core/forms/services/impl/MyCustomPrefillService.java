package ca.yukon.aem.core.forms.services.impl;

import com.adobe.forms.common.service.*;
import com.google.gson.Gson;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

@Component
public class MyCustomPrefillService implements DataProvider {

    private Logger logger = LoggerFactory.getLogger(MyCustomPrefillService.class);

    public String getServiceName() {
        return "My Custom Prefill Service Name";
    }

    public String getServiceDescription() {
        return "My Custom Prefill Service";
    }

    public PrefillData getPrefillData(final DataOptions dataOptions) throws FormsException {
        return new PrefillData() {
            public InputStream getInputStream() {
                return getData(dataOptions);
            }

            public ContentType getContentType() {
                return ContentType.XML;
            }
        };

    }

    private InputStream getData(DataOptions dataOptions) throws FormsException {
        try {
            Gson gson = new Gson();
            String jsonStr = "{\n  \"nelson_test\": {\n    \"Title\": \"aaaaaa\",\n    \"Name\": \"bbbbb\",\n    \"yhcip_no\": \"ccccc\"\n  }\n}";
            HashMap myPojo = gson.fromJson(jsonStr, HashMap.class);
            String outputStr = gson.toJson(myPojo);

            InputStream inputStream = new ByteArrayInputStream(outputStr.getBytes(StandardCharsets.UTF_8));
            return inputStream;

        } catch (Exception e) {
            logger.error("Error while creating prefill data", e);
            throw new FormsException(e);
        }
    }
}

