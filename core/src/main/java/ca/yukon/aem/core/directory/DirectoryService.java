package ca.yukon.aem.core.directory;

/**
 * Searches a cached copy of the Government of Yukon directory. These are the operations behind
 * the {@code ygDirectory} form data model.
 *
 * <p>No match is not an error: every method then returns an empty result (empty strings, an empty
 * list, a count of 0). Only a directory that has never loaded throws
 * {@link DirectoryUnavailableException}.</p>
 */
public interface DirectoryService {

    /**
     * Finds employee positions with a word in their first name, last name or email that starts with each word
     * of the keyword (or whose email starts with the keyword),
     * ignoring case and accents. A person with several titles appears once per title. Results are
     * sorted by last name, then first name, then email, then title.
     *
     * @param keyword at least the configured minimum length (3 by default) after trimming;
     *                shorter keywords return an empty result
     * @return up to the configured maximum (10 by default, at most 100), with the total number of matches
     */
    EmployeeSearchResult searchEmployees(String keyword) throws DirectoryUnavailableException;

    /**
     * Looks up one employee position by email, ignoring case, with its manager.
     *
     * @param email      the employee's work email
     * @param title      optional: picks the position when the person holds several
     * @param department optional: picks the position when the person holds several
     * @return the position, or a record of empty strings when no employee has that email. When the
     *         person holds several positions and none matches the title and department, the first
     *         by title; its {@code title} says which.
     */
    EmployeeDetail getEmployee(String email, String title, String department) throws DirectoryUnavailableException;

    /** @return unique department names, sorted */
    NameList getDepartments() throws DirectoryUnavailableException;

    /** @return unique division names in the department, sorted */
    NameList getDivisions(String department) throws DirectoryUnavailableException;

    /** @return unique branch names in the department and division, sorted */
    NameList getBranches(String department, String division) throws DirectoryUnavailableException;

    /** @return unique unit names in the department, division and branch, sorted */
    NameList getUnits(String department, String division, String branch) throws DirectoryUnavailableException;
}
