package ca.yukon.aem.core.models;

import ca.yukon.aem.core.testcontext.AppAemContext;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class ValidationSummaryModelTest {

    private final AemContext context = AppAemContext.newAemContext();

    @Test
    void testTexts_notSet_returnDefaults() {
        ValidationSummaryModel model = model();
        assertTrue(model.isShowBadge());
        assertEquals("New: error summary", model.getBadgeText());
        assertEquals("There’s a problem", model.getTitleText());
        assertEquals("Fix these {count} errors before you submit.", model.getIntroText());
        assertEquals("Fix this error before you submit.", model.getIntroTextOne());
    }

    @Test
    void testTexts_blank_returnDefaults() {
        ValidationSummaryModel model = model("titleText", "   ", "introText", "");
        assertEquals("There’s a problem", model.getTitleText());
        assertEquals("Fix these {count} errors before you submit.", model.getIntroText());
    }

    @Test
    void testTexts_set_returnTrimmedText() {
        ValidationSummaryModel model = model(
            "titleText", " Il y a un problème ",
            "introText", "Corrigez ces {count} erreurs avant de soumettre.",
            "introTextOne", "Corrigez cette erreur avant de soumettre.",
            "badgeText", "Nouveau : résumé des erreurs");
        assertEquals("Il y a un problème", model.getTitleText());
        assertEquals("Corrigez ces {count} erreurs avant de soumettre.", model.getIntroText());
        assertEquals("Corrigez cette erreur avant de soumettre.", model.getIntroTextOne());
        assertEquals("Nouveau : résumé des erreurs", model.getBadgeText());
    }

    @Test
    void testShowBadge_false_hidesBadge() {
        assertFalse(model("showBadge", false).isShowBadge());
    }

    private ValidationSummaryModel model(Object... properties) {
        context.currentResource(context.create().resource("/content/validation-summary", properties));
        return context.request().adaptTo(ValidationSummaryModel.class);
    }
}
