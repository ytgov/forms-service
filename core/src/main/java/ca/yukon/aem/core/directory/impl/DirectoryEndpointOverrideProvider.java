package ca.yukon.aem.core.directory.impl;

import ca.yukon.aem.core.config.DirectoryEndpointConfig;
import com.day.cq.commons.Externalizer;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.caconfig.spi.ConfigurationOverrideProvider;
import org.apache.sling.settings.SlingSettingsService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;

/**
 * Sets the service endpoint of the ygDirectory FDM data source
 * ({@code /conf/yukon-forms/settings/cloudconfigs/fdm/ygdirectory}) to the AEM instance that serves
 * {@code /bin/yukon-forms/ygdirectory} (YUKONAEMF-88).
 *
 * <p>The REST data source only holds a fixed, absolute URL, so this provider overrides it per
 * environment: with the configured endpoint (Cloud Manager variable {@code YG_DIRECTORY_FDM_ENDPOINT})
 * when there is one, otherwise with this instance's own URL from the Externalizer: the author URL on
 * author, the publish URL on publish. The FDM key is overridden separately, by
 * {@code OsgiConfigurationOverrideProvider~ygdirectory}.</p>
 */
@Component(service = ConfigurationOverrideProvider.class, immediate = true)
@Designate(ocd = DirectoryEndpointConfig.class)
public class DirectoryEndpointOverrideProvider implements ConfigurationOverrideProvider {

    static final String PROPERTY = "cloudconfigs/fdm/ygdirectory/serviceEndPoint";

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryEndpointOverrideProvider.class);
    private static final String AUTHOR_RUN_MODE = "author";

    @Reference
    private Externalizer externalizer;

    @Reference
    private SlingSettingsService settings;

    private volatile Collection<String> overrides = Collections.emptyList();
    private volatile String endpoint = "";

    // No @Modified: Sling reads override strings when a provider registers, so a config change must
    // re-register the component (deactivate and activate) to take effect.
    @Activate
    protected void activate(DirectoryEndpointConfig config) {
        String configured = trimSlash(config.endpoint());
        String url = configured.isEmpty() ? ownUrl() : configured;
        String source = configured.isEmpty() ? "Externalizer" : "YG_DIRECTORY_FDM_ENDPOINT";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            LOG.warn("ygDirectory FDM endpoint not set: no valid URL from {}; the cloud configuration's own "
                    + "serviceEndPoint applies", source);
            endpoint = "";
            overrides = Collections.emptyList();
            return;
        }
        endpoint = url;
        overrides = Collections.singletonList(PROPERTY + "=" + jsonString(url));
        LOG.info("ygDirectory FDM endpoint: {} (from {})", url, source);
    }

    @Override
    public Collection<String> getOverrideStrings() {
        return overrides;
    }

    String endpoint() {
        return endpoint;
    }

    private String ownUrl() {
        try {
            boolean author = settings.getRunModes().contains(AUTHOR_RUN_MODE);
            // No resolver: only the configured domain is needed, not resource mapping.
            String link = author ? externalizer.authorLink(null, "/") : externalizer.publishLink(null, "/");
            return trimSlash(link);
        } catch (RuntimeException e) {
            LOG.warn("ygDirectory FDM endpoint: Externalizer failed: {}", e.getClass().getName());
            return "";
        }
    }

    private static String trimSlash(String value) {
        return StringUtils.stripEnd(StringUtils.trimToEmpty(value), "/");
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
