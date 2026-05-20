package ca.yukon.aem.core.forms.model;

import java.util.Objects;

/**
 * Represents a single signer extracted from a repeatable panel in an
 * AEM Adaptive Form submission.
 *
 * <p>Each signer maps to one participant in the resulting Adobe Sign
 * agreement and (optionally) one signature field on the Document of
 * Record PDF.</p>
 */
public class Signer {

    private final String name;
    private final String email;
    private final String role;
    private final Integer signaturePage;
    private final Integer signatureX;
    private final Integer signatureY;

    public Signer(String name, String email, String role,
                  Integer signaturePage, Integer signatureX, Integer signatureY) {
        this.name = name;
        this.email = email;
        this.role = role;
        this.signaturePage = signaturePage;
        this.signatureX = signatureX;
        this.signatureY = signatureY;
    }

    public Signer(String name, String email) {
        this(name, email, "SIGNER", null, null, null);
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    /**
     * Adobe Sign participant role. Common values: SIGNER, APPROVER,
     * FORM_FILLER, DELEGATE_TO_SIGNER. Defaults to SIGNER.
     */
    public String getRole() {
        return role == null ? "SIGNER" : role;
    }

    /**
     * 1-based page number where this signer's signature field should be
     * placed on the DoR PDF. Null means use the configured default.
     */
    public Integer getSignaturePage() {
        return signaturePage;
    }

    /**
     * X coordinate (in PDF points) for the signature field. Null means
     * the field will be placed at the field's anchor or skipped.
     */
    public Integer getSignatureX() {
        return signatureX;
    }

    /**
     * Y coordinate (in PDF points) for the signature field.
     */
    public Integer getSignatureY() {
        return signatureY;
    }

    public boolean isValid() {
        return email != null && !email.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Signer)) return false;
        Signer s = (Signer) o;
        return Objects.equals(email, s.email) && Objects.equals(name, s.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email);
    }

    @Override
    public String toString() {
        return "Signer{name='" + name + "', email='" + email + "', role='" + getRole() + "'}";
    }
}
