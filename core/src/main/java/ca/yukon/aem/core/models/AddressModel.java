package ca.yukon.aem.core.models;

import ca.yukon.aem.core.forms.services.impl.CanadaPostApiService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.Optional;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

@Model(adaptables = Resource.class)
public class AddressModel {

    @Inject
    protected CanadaPostApiService canadaPostApiService;
    private String key = "";
    private String host = "";
    @ValueMapValue
    @Optional
    private int limit = 7;

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

    public int getLimit() {
        return this.limit;
    }
}
