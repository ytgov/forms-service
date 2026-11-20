package ca.yukon.aem.core.directory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A sorted list of unique organization names (departments, divisions, branches or units).
 */
@JsonPropertyOrder({ "names", "count" })
public final class NameList {

    private static final NameList EMPTY = new NameList(Collections.emptyList());

    private final List<String> names;

    public NameList(List<String> names) {
        this.names = Collections.unmodifiableList(new ArrayList<>(names));
    }

    public static NameList empty() {
        return EMPTY;
    }

    @JsonProperty("names")
    public List<String> getNames() {
        return names;
    }

    @JsonProperty("count")
    public int getCount() {
        return names.size();
    }
}
