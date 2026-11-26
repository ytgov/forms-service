package ca.yukon.aem.core.directory.impl;

import ca.yukon.aem.core.directory.EmployeeDetail;
import ca.yukon.aem.core.directory.EmployeeSearchResult;
import ca.yukon.aem.core.directory.EmployeeSummary;
import ca.yukon.aem.core.directory.NameList;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectorySnapshotTest {

    private static DirectorySnapshot snapshot;

    @BeforeAll
    static void load() throws IOException {
        snapshot = DirectorySnapshot.parse(resource("employees.json"), resource("divisions.json"), 0L);
    }

    static String resource(String name) throws IOException {
        try (InputStream in = DirectorySnapshotTest.class.getResourceAsStream("/directory/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void skipsEmployeesWithoutEmail() {
        assertEquals(9, snapshot.employeeCount());
        assertEquals(0, snapshot.search("No Email", 3, 10).getTotal());
    }

    @Test
    void searchSortsByLastNameThenFirstName() {
        EmployeeSearchResult result = snapshot.search("smi", 3, 10);

        assertEquals(5, result.getTotal());
        assertEquals(5, result.getCount());
        assertEquals(Arrays.asList("Smith", "Smith", "Smith", "Smith", "Smithers"),
                result.getEmployees().stream().map(EmployeeSummary::getLastName).collect(Collectors.toList()));
        assertEquals(Arrays.asList("Chris", "Chris", "Pat", "Zoé", "Amélie"),
                result.getEmployees().stream().map(EmployeeSummary::getFirstName).collect(Collectors.toList()));
    }

    @Test
    void searchReturnsTheTotalBeyondTheLimit() {
        EmployeeSearchResult result = snapshot.search("smith", 3, 2);

        assertEquals(2, result.getCount());
        assertEquals(5, result.getTotal());
    }

    @Test
    void searchIgnoresCaseAccentsAndWordOrder() {
        assertEquals(Collections.singletonList("zoe.smith@yukon.ca"), emails(snapshot.search("SMITH zoe", 3, 10)));
        assertEquals(Collections.singletonList("zoe.smith@yukon.ca"), emails(snapshot.search("zoé", 3, 10)));
        assertEquals(Collections.singletonList("amelie.smithers@yukon.ca"), emails(snapshot.search("AMELIE", 3, 10)));
    }

    @Test
    void searchMatchesTheStartOfWordsOnly() {
        assertEquals(0, snapshot.search("mith", 3, 10).getTotal());
        assertEquals(0, snapshot.search("velace", 3, 10).getTotal());
        assertEquals(Collections.singletonList("Ada.Lovelace@yukon.ca"), emails(snapshot.search("love", 3, 10)));
        assertEquals(Collections.singletonList("Ada.Lovelace@yukon.ca"), emails(snapshot.search("ada lov", 3, 10)));
    }

    @Test
    void searchMatchesEmail() {
        assertEquals(Arrays.asList("Grace.Hopper@yukon.ca", "grace.hopper@yukon.ca"),
                emails(snapshot.search("grace.hopper@", 3, 10)));
    }

    @Test
    void searchReturnsDisplayNameAndDepartment() {
        EmployeeSummary zoe = snapshot.search("zoe", 3, 10).getEmployees().get(0);

        assertEquals("Zoé Smith", zoe.getFullName());
        assertEquals("Education", zoe.getDepartment());
    }

    @Test
    void shortOrUnmatchedKeywordsReturnAnEmptyResult() {
        for (String keyword : new String[] { null, "", "  ", "sm", " s m ", "xyzzy" }) {
            EmployeeSearchResult result = snapshot.search(keyword, 3, 10);
            assertEquals(0, result.getCount(), keyword);
            assertEquals(0, result.getTotal(), keyword);
            assertTrue(result.getEmployees().isEmpty(), keyword);
        }
    }

    @Test
    void getEmployeeIgnoresCaseAndResolvesTheManager() {
        EmployeeDetail ada = snapshot.employee(" ADA.LOVELACE@YUKON.CA ", "", "");

        assertEquals("Ada", ada.getFirstName());
        assertEquals("Lovelace", ada.getLastName());
        assertEquals("Ada.Lovelace@yukon.ca", ada.getEmail());
        assertEquals("Highways and Public Works", ada.getDepartment());
        assertEquals("Transportation", ada.getDivision());
        assertEquals("Engineering", ada.getBranch());
        assertEquals("Bridges", ada.getUnit());
        assertEquals("Grace", ada.getManagerFirstName());
        assertEquals("Hopper", ada.getManagerLastName());
        assertEquals("grace.hopper@yukon.ca", ada.getManagerEmail());
    }

    @Test
    void aSharedManagerNameResolvesWithinTheSameDepartment() {
        assertEquals("chris.smith2@yukon.ca", snapshot.employee("zoe.smith@yukon.ca", "", "").getManagerEmail());
        assertEquals("chris.smith@yukon.ca",
                snapshot.employee("grace.hopper@yukon.ca", "Director", "").getManagerEmail());
    }

    @Test
    void eachPositionIsASeparateSearchResult() {
        EmployeeSearchResult result = snapshot.search("hopper", 3, 10);

        assertEquals(2, result.getTotal());
        assertEquals(Arrays.asList("Acting ADM", "Director"),
                result.getEmployees().stream().map(EmployeeSummary::getTitle).collect(Collectors.toList()));
        assertEquals(Arrays.asList("Community Services", "Highways and Public Works"),
                result.getEmployees().stream().map(EmployeeSummary::getDepartment).collect(Collectors.toList()));
    }

    @Test
    void getEmployeeReturnsTheSelectedPosition() {
        EmployeeDetail director = snapshot.employee("grace.hopper@yukon.ca", "director", "highways and public works");
        assertEquals("Director", director.getTitle());
        assertEquals("Transportation", director.getDivision());
        assertEquals("Engineering", director.getBranch());
        assertEquals("chris.smith@yukon.ca", director.getManagerEmail());

        EmployeeDetail acting = snapshot.employee("grace.hopper@yukon.ca", "Acting ADM", "Community Services");
        assertEquals("Acting ADM", acting.getTitle());
        assertEquals("Community Services", acting.getDepartment());
        assertEquals("", acting.getDivision());
        assertEquals("pat.smith@yukon.ca", acting.getManagerEmail());

        // Only the department given
        assertEquals("Director", snapshot.employee("grace.hopper@yukon.ca", "", "Highways and Public Works").getTitle());
    }

    @Test
    void withoutAMatchingPositionGetEmployeeReturnsTheFirstByTitle() {
        assertEquals("Acting ADM", snapshot.employee("grace.hopper@yukon.ca", "", "").getTitle());
        assertEquals("Acting ADM", snapshot.employee("grace.hopper@yukon.ca", "Retired", "").getTitle());
        assertEquals("Engineer", snapshot.employee("ada.lovelace@yukon.ca", "Someone else", "").getTitle());
    }

    @Test
    void aManagerWithSeveralPositionsIsStillOnePerson() {
        assertEquals("grace.hopper@yukon.ca", snapshot.employee("ada.lovelace@yukon.ca", "", "").getManagerEmail());
    }

    @Test
    void keepsBusinessContactInformationOnly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(snapshot.search("hopper", 3, 10))
                + mapper.writeValueAsString(snapshot.employee("grace.hopper@yukon.ca", "Acting ADM", ""));
        for (String notAllowed : new String[] { "ghopper2", "867-555-0100", "2071 2nd Avenue", "Grace.Hopper\"" }) {
            assertFalse(json.contains(notAllowed), notAllowed);
        }
    }

    @Test
    void anAmbiguousOrUnknownManagerIsLeftEmpty() {
        EmployeeDetail pat = snapshot.employee("pat.smith@yukon.ca", "", "");
        assertEquals("Pat", pat.getFirstName());
        assertEquals("", pat.getManagerFirstName());
        assertEquals("", pat.getManagerLastName());
        assertEquals("", pat.getManagerEmail());

        assertEquals("", snapshot.employee("amelie.smithers@yukon.ca", "", "").getManagerEmail());
        assertEquals("", snapshot.employee("chris.smith@yukon.ca", "", "").getManagerEmail());
    }

    @Test
    void anUnknownEmailReturnsEmptyStrings() {
        for (String email : new String[] { null, "", "nobody@yukon.ca" }) {
            EmployeeDetail detail = snapshot.employee(email, "", "");
            assertEquals("", detail.getFirstName(), email);
            assertEquals("", detail.getEmail(), email);
            assertEquals("", detail.getDepartment(), email);
            assertEquals("", detail.getManagerEmail(), email);
        }
    }

    @Test
    void departmentsAreUniqueTrimmedAndSorted() {
        assertEquals(Arrays.asList("Community Services", "Education", "Highways and Public Works"),
                snapshot.departments().getNames());
        assertEquals(3, snapshot.departments().getCount());
    }

    @Test
    void divisionsBranchesAndUnitsFollowTheHierarchy() {
        assertEquals(Arrays.asList("Information Technology", "Transportation"),
                snapshot.divisions("highways and public works").getNames());
        assertEquals(Arrays.asList("Elementary", "Secondary"),
                snapshot.branches("Education", "Schools").getNames());
        assertEquals(Arrays.asList("Asphalt", "Bridges"),
                snapshot.units("Highways and Public Works", "Transportation", "Engineering").getNames());
    }

    @Test
    void missingOrUnknownParentsReturnAnEmptyList() {
        assertEmpty(snapshot.divisions(""));
        assertEmpty(snapshot.divisions("Nowhere"));
        assertEmpty(snapshot.divisions("Community Services"));
        assertEmpty(snapshot.branches("Education", null));
        assertEmpty(snapshot.units("Education", "Schools", ""));
        assertEmpty(snapshot.units("Education", "Schools", "Secondary"));
    }

    @Test
    void rejectsAResponseWithoutTheExpectedArrays() {
        assertThrows(IOException.class, () -> DirectorySnapshot.parse("{\"count\":0}", "{\"divisions\":[]}", 0L));
        assertThrows(IOException.class, () -> DirectorySnapshot.parse("<html>", "{\"divisions\":[]}", 0L));
    }

    private static void assertEmpty(NameList list) {
        assertEquals(0, list.getCount());
        assertTrue(list.getNames().isEmpty());
    }

    private static List<String> emails(EmployeeSearchResult result) {
        return result.getEmployees().stream().map(EmployeeSummary::getEmail).collect(Collectors.toList());
    }
}
