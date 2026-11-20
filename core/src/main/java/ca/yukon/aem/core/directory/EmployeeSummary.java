package ca.yukon.aem.core.directory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One row of an employee search: one position. A person with several titles appears once per title.
 * Missing values are empty strings, never null.
 */
@JsonPropertyOrder({ "full_name", "first_name", "last_name", "email", "title", "department" })
public final class EmployeeSummary {

    private final String fullName;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String title;
    private final String department;

    public EmployeeSummary(String fullName, String firstName, String lastName, String email, String title,
                           String department) {
        this.fullName = Strings.orEmpty(fullName);
        this.firstName = Strings.orEmpty(firstName);
        this.lastName = Strings.orEmpty(lastName);
        this.email = Strings.orEmpty(email);
        this.title = Strings.orEmpty(title);
        this.department = Strings.orEmpty(department);
    }

    /** @return "First Last", for display */
    @JsonProperty("full_name")
    public String getFullName() {
        return fullName;
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

    /** @return the job title of this position */
    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    @JsonProperty("department")
    public String getDepartment() {
        return department;
    }
}
