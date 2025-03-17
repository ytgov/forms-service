package ca.yukon.aem.core.forms.services.impl;

import ca.yukon.aem.core.config.CanadaPostApiConfig;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.Designate;

@Component(service = CanadaPostApiService.class, immediate = true)
@Designate(ocd = CanadaPostApiConfig.class)
public class CanadaPostApiService {

    private String apiKey;
    private String host;

    @Activate
    public void activate(CanadaPostApiConfig config) {
        this.apiKey = config.canada_post_key();
        this.host = config.canada_post_host();
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public String getHost() {
        return this.host;
    }
}
