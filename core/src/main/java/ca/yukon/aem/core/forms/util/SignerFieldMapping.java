package ca.yukon.aem.core.forms.util;

/**
 * Maps {@link ca.yukon.aem.core.forms.model.Signer} attributes to the
 * actual child element names used inside a repeatable panel in submitted
 * form data XML.
 *
 * <p>Forms are unlikely to all use the same field names — one form might
 * call the email field {@code email}, another {@code petitionerEmail} or
 * {@code signerEmail}. This mapping lets each workflow declare what names
 * its form uses.</p>
 *
 * <p>Lookup is case-insensitive, so {@code email} matches {@code Email}
 * or {@code EMAIL}.</p>
 */
public final class SignerFieldMapping {

    /** Default mapping matching the conventional names. */
    public static final SignerFieldMapping DEFAULT = new SignerFieldMapping(
            "email", "name", "role",
            "signaturePage", "signatureX", "signatureY");

    private final String emailField;
    private final String nameField;
    private final String roleField;
    private final String signaturePageField;
    private final String signatureXField;
    private final String signatureYField;

    public SignerFieldMapping(String emailField, String nameField, String roleField,
                              String signaturePageField,
                              String signatureXField,
                              String signatureYField) {
        this.emailField = orDefault(emailField, "email");
        this.nameField = orDefault(nameField, "name");
        this.roleField = orDefault(roleField, "role");
        this.signaturePageField = orDefault(signaturePageField, "signaturePage");
        this.signatureXField = orDefault(signatureXField, "signatureX");
        this.signatureYField = orDefault(signatureYField, "signatureY");
    }

    public String getEmailField() { return emailField; }
    public String getNameField() { return nameField; }
    public String getRoleField() { return roleField; }
    public String getSignaturePageField() { return signaturePageField; }
    public String getSignatureXField() { return signatureXField; }
    public String getSignatureYField() { return signatureYField; }

    private static String orDefault(String v, String fallback) {
        return (v == null || v.trim().isEmpty()) ? fallback : v.trim();
    }

    @Override
    public String toString() {
        return "SignerFieldMapping{email=" + emailField +
                ", name=" + nameField +
                ", role=" + roleField +
                ", signaturePage=" + signaturePageField +
                ", signatureX=" + signatureXField +
                ", signatureY=" + signatureYField + "}";
    }
}
