package ca.yukon.aem.core.models;

import ca.yukon.aem.core.forms.services.impl.CanadaPostApiService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
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

    @ValueMapValue @Default(values = "addressLine1")
    private String fieldLine1;

    @ValueMapValue @Default(values = "addressLine2")
    private String fieldLine2;

    @ValueMapValue @Default(values = "city")
    private String fieldCity;

    @ValueMapValue @Default(values = "province")
    private String fieldProvince;

    @ValueMapValue @Default(values = "postalCode")
    private String fieldPostalCode;

    public String getHost() {
        return this.host;
    }

    public String getKey() {
        return this.key;
    }

    public int getLimit() {
        return this.limit;
    }
    public String getFieldLine1()  { return fieldLine1; }
    public String getFieldLine2()  { return fieldLine2; }
    public String getFieldCity()   { return fieldCity; }
    public String getFieldProvince()   { return fieldProvince; }
    public String getFieldPostalCode() { return fieldPostalCode; }
}
