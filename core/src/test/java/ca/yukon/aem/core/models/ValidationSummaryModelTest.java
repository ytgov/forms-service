package ca.yukon.aem.core.models;

import ca.yukon.aem.core.testcontext.AppAemContext;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(AemContextExtension.class)
class ValidationSummaryModelTest {

    private final AemContext context = AppAemContext.newAemContext();

    @Test
    void testTexts_notSet_returnDefaults() {
        ValidationSummaryModel model = model();
        assertFalse(model.isShowBadge());
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
    void testShowBadge_true_showsBadge() {
        assertTrue(model("showBadge", true).isShowBadge());
    }

    @Test
    void testShowBadge_false_hidesBadge() {
        assertFalse(model("showBadge", false).isShowBadge());
    }

    @Test
    void testTexts_frenchParameter_returnFrenchDefaults() {
        context.request().setParameterMap(Collections.singletonMap("afAcceptLang", "fr-CA"));
        assertFrenchDefaults(model());
    }

    @Test
    void testTexts_frenchSelector_returnFrenchDefaults() {
        context.requestPathInfo().setSelectorString("fr");
        assertFrenchDefaults(model());
    }

    @Test
    void testTexts_frenchFormLanguage_returnFrenchDefaults() {
        context.create().resource(FORM_CONTENT, "jcr:language", "fr");
        assertFrenchDefaults(model());
    }

    @Test
    void testTexts_englishParameterOnFrenchForm_returnEnglishDefaults() {
        context.create().resource(FORM_CONTENT, "jcr:language", "fr");
        context.request().setParameterMap(Collections.singletonMap("afAcceptLang", "en"));
        assertEquals("There\u2019s a problem", model().getTitleText());
    }

    @Test
    void testTexts_frenchFormWithAuthoredText_returnAuthoredText() {
        context.create().resource(FORM_CONTENT, "jcr:language", "fr");
        assertEquals("Erreurs", model("titleText", "Erreurs").getTitleText());
    }

    private static final String FORM_CONTENT = "/content/forms/af/test-form/jcr:content";

    private void assertFrenchDefaults(ValidationSummaryModel model) {
        assertEquals("Il y a un probl\u00e8me", model.getTitleText());
        assertEquals("Corrigez ces {count}\u00a0erreurs avant de soumettre le formulaire.", model.getIntroText());
        assertEquals("Corrigez cette erreur avant de soumettre le formulaire.", model.getIntroTextOne());
        assertEquals("Nouveau\u00a0: r\u00e9sum\u00e9 des erreurs", model.getBadgeText());
    }

    private ValidationSummaryModel model(Object... properties) {
        if (context.resourceResolver().getResource(FORM_CONTENT) == null) {
            context.create().resource(FORM_CONTENT);
        }
        context.currentResource(context.create().resource(
            FORM_CONTENT + "/guideContainer/validationsummary", properties));
        return context.request().adaptTo(ValidationSummaryModel.class);
    }
}
