package ca.yukon.aem.core.models;

import ca.yukon.aem.core.forms.services.impl.CanadaPostApiService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

@Model(adaptables = Resource.class)
public class AddressModel {

    @Inject
    protected CanadaPostApiService canadaPostApiService;
    private String key = "";
    private String host = "";

    @PostConstruct
    protected void init() {
        if (canadaPostApiService != null) {
            this.key = canadaPostApiService.getApiKey();
            this.host = canadaPostApiService.getHost();
        }
    }

    public String getHost() {
        return this.host;
    }

    public String getKey() {
        return this.key;
    }
}
