package ca.yukon.aem.core.models;

import ca.yukon.aem.core.testcontext.AppAemContext;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(AemContextExtension.class)
class ReviewSummaryModelTest {

    private final AemContext context = AppAemContext.newAemContext();

    @Test
    void testGetEditLinkText_notSet_returnsDefault() {
        assertEquals("Go back to page {page} to edit", model().getEditLinkText());
    }

    @Test
    void testGetEditLinkText_blank_returnsDefault() {
        assertEquals("Go back to page {page} to edit", model("editLinkText", "   ").getEditLinkText());
    }

    @Test
    void testGetEditLinkText_set_returnsTrimmedText() {
        assertEquals("Retourner à la page {page} pour modifier", model("editLinkText", " Retourner à la page {page} pour modifier ").getEditLinkText());
    }

    private ReviewSummaryModel model(Object... properties) {
        context.currentResource(context.create().resource("/content/review-summary", properties));
        return context.request().adaptTo(ReviewSummaryModel.class);
    }
}
