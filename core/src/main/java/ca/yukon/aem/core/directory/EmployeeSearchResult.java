package ca.yukon.aem.core.directory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of {@link DirectoryService#searchEmployees(String)}.
 */
@JsonPropertyOrder({ "employees", "count", "total" })
public final class EmployeeSearchResult {

    private static final EmployeeSearchResult EMPTY = new EmployeeSearchResult(Collections.emptyList(), 0);

    private final List<EmployeeSummary> employees;
    private final int total;

    public EmployeeSearchResult(List<EmployeeSummary> employees, int total) {
        this.employees = Collections.unmodifiableList(new ArrayList<>(employees));
        this.total = total;
    }

    public static EmployeeSearchResult empty() {
        return EMPTY;
    }

    /** @return the employees returned, at most the configured maximum */
    @JsonProperty("employees")
    public List<EmployeeSummary> getEmployees() {
        return employees;
    }

    /** @return the number of employees returned */
    @JsonProperty("count")
    public int getCount() {
        return employees.size();
    }

    /** @return the number of employees that matched, which can exceed {@link #getCount()} */
    @JsonProperty("total")
    public int getTotal() {
        return total;
    }
}
