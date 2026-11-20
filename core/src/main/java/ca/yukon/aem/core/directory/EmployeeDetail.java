package ca.yukon.aem.core.directory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Result of {@link DirectoryService#getEmployee(String, String, String)}: a flat record so the rule editor can map
 * each value to a field. Missing values are empty strings, never null.
 */
@JsonPropertyOrder({ "first_name", "last_name", "email", "title", "department", "division", "branch", "unit",
        "manager_first_name", "manager_last_name", "manager_email" })
public final class EmployeeDetail {

    private static final EmployeeDetail EMPTY = builder().build();

    private final String firstName;
    private final String lastName;
    private final String email;
    private final String title;
    private final String department;
    private final String division;
    private final String branch;
    private final String unit;
    private final String managerFirstName;
    private final String managerLastName;
    private final String managerEmail;

    private EmployeeDetail(Builder b) {
        firstName = Strings.orEmpty(b.firstName);
        lastName = Strings.orEmpty(b.lastName);
        email = Strings.orEmpty(b.email);
        title = Strings.orEmpty(b.title);
        department = Strings.orEmpty(b.department);
        division = Strings.orEmpty(b.division);
        branch = Strings.orEmpty(b.branch);
        unit = Strings.orEmpty(b.unit);
        managerFirstName = Strings.orEmpty(b.managerFirstName);
        managerLastName = Strings.orEmpty(b.managerLastName);
        managerEmail = Strings.orEmpty(b.managerEmail);
    }

    public static EmployeeDetail empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("first_name")
    public String getFirstName() {
        return firstName;
    }

    @JsonProperty("last_name")
    public String getLastName() {
        return lastName;
    }

    @JsonProperty("email")
    public String getEmail() {
        return email;
    }

    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    @JsonProperty("department")
    public String getDepartment() {
        return department;
    }

    @JsonProperty("division")
    public String getDivision() {
        return division;
    }

    @JsonProperty("branch")
    public String getBranch() {
        return branch;
    }

    @JsonProperty("unit")
    public String getUnit() {
        return unit;
    }

    @JsonProperty("manager_first_name")
    public String getManagerFirstName() {
        return managerFirstName;
    }

    @JsonProperty("manager_last_name")
    public String getManagerLastName() {
        return managerLastName;
    }

    @JsonProperty("manager_email")
    public String getManagerEmail() {
        return managerEmail;
    }

    public static final class Builder {
        private String firstName;
        private String lastName;
        private String email;
        private String title;
        private String department;
        private String division;
        private String branch;
        private String unit;
        private String managerFirstName;
        private String managerLastName;
        private String managerEmail;

        private Builder() {
        }

        public Builder firstName(String value) {
            firstName = value;
            return this;
        }

        public Builder lastName(String value) {
            lastName = value;
            return this;
        }

        public Builder email(String value) {
            email = value;
            return this;
        }

        public Builder title(String value) {
            title = value;
            return this;
        }

        public Builder department(String value) {
            department = value;
            return this;
        }

        public Builder division(String value) {
            division = value;
            return this;
        }

        public Builder branch(String value) {
            branch = value;
            return this;
        }

        public Builder unit(String value) {
            unit = value;
            return this;
        }

        public Builder managerFirstName(String value) {
            managerFirstName = value;
            return this;
        }

        public Builder managerLastName(String value) {
            managerLastName = value;
            return this;
        }

        public Builder managerEmail(String value) {
            managerEmail = value;
            return this;
        }

        public EmployeeDetail build() {
            return new EmployeeDetail(this);
        }
    }
}
