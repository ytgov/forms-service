package ca.yukon.aem.core.directory;

/** Null handling for the result types. */
final class Strings {

    private Strings() {
    }

    static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
