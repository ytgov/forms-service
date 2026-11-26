package ca.yukon.aem.core.directory.impl;

import com.day.cq.commons.Externalizer;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.apache.sling.settings.SlingSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.HashSet;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(AemContextExtension.class)
class DirectoryEndpointOverrideProviderTest {

    private final Externalizer externalizer = mock(Externalizer.class);
    private final SlingSettingsService settings = mock(SlingSettingsService.class);

    @Test
    void usesTheAuthorUrlOnAuthor(AemContext context) {
        runModes("author", "dev");
        DirectoryEndpointOverrideProvider provider = activate(context, "");

        assertEquals("https://author-p1-e2.adobeaemcloud.com", provider.endpoint());
        assertEquals(Collections.singletonList(
                        "cloudconfigs/fdm/ygdirectory/serviceEndPoint=\"https://author-p1-e2.adobeaemcloud.com\""),
                provider.getOverrideStrings());
    }

    @Test
    void usesThePublishUrlOnPublish(AemContext context) {
        runModes("publish", "prod");
        assertEquals("https://publish-p1-e2.adobeaemcloud.com", activate(context, "").endpoint());
    }

    @Test
    void theVariableOverridesTheExternalizer(AemContext context) {
        runModes("publish");
        assertEquals("https://forms.yukon.ca", activate(context, " https://forms.yukon.ca/ ").endpoint());
    }

    @Test
    void noValidUrlMeansNoOverride(AemContext context) {
        runModes("publish");
        when(externalizer.publishLink(any(), eq("/"))).thenReturn("/");
        assertTrue(activate(context, "").getOverrideStrings().isEmpty());
        assertTrue(activate(context, "forms.yukon.ca").getOverrideStrings().isEmpty());
    }

    private void runModes(String... modes) {
        when(settings.getRunModes()).thenReturn(new HashSet<>(Arrays.asList(modes)));
        when(externalizer.authorLink(any(), eq("/"))).thenReturn("https://author-p1-e2.adobeaemcloud.com/");
        when(externalizer.publishLink(any(), eq("/"))).thenReturn("https://publish-p1-e2.adobeaemcloud.com/");
    }

    private DirectoryEndpointOverrideProvider activate(AemContext context, String endpoint) {
        context.registerService(Externalizer.class, externalizer, "service.ranking", Integer.MAX_VALUE);
        context.registerService(SlingSettingsService.class, settings, "service.ranking", Integer.MAX_VALUE);
        return context.registerInjectActivateService(new DirectoryEndpointOverrideProvider(), "endpoint", endpoint);
    }
}
