package ca.yukon.aem.core.directory.impl;

import ca.yukon.aem.core.directory.EmployeeDetail;
import ca.yukon.aem.core.directory.EmployeeSearchResult;
import ca.yukon.aem.core.directory.EmployeeSummary;
import ca.yukon.aem.core.directory.NameList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An immutable, indexed copy of the directory, built from the API's {@code /employees} and
 * {@code /divisions} responses. All lookups ignore case, accents and surrounding spaces.
 *
 * <p>Only business contact information is kept, as the privacy approval requires: first and last
 * name, work email, job title, the org path (department, division, branch, unit) and the manager's
 * name and email. Usernames, phone numbers, office addresses and every other API field are dropped
 * while parsing.</p>
 *
 * <p>Each API record is one position. Some people hold several (same email, different title and
 * department); each is a separate search result, and {@link #employee} picks one by title and
 * department.</p>
 *
 * <p>The API's {@code full_name} is not a display name: it is {@code First.Last}, the same as the
 * email's local part. Employees' {@code manager} holds their manager's {@code full_name}; managers
 * are resolved while parsing and neither value is kept.</p>
 */
final class DirectorySnapshot {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern KEYWORD_SEPARATORS = Pattern.compile("[\\s,;]+");
    /** Characters that also start a new word in names and emails: "Smith-Jones", "o'neil", "ada.lovelace@". */
    private static final Pattern WORD_BREAKS = Pattern.compile("[-'\u2019._@]");

    private static final Comparator<Position> BY_NAME = Comparator
            .comparing((Position p) -> p.lastKey)
            .thenComparing(p -> p.firstKey)
            .thenComparing(p -> p.emailKey)
            .thenComparing(p -> p.titleKey)
            .thenComparing(p -> p.departmentKey);

    private final List<Position> positions;
    private final Map<String, List<Position>> byEmail;
    private final List<OrgUnit> orgUnits;
    private final long loadedAt;

    private DirectorySnapshot(List<Position> positions, List<OrgUnit> orgUnits, long loadedAt) {
        List<Position> sorted = new ArrayList<>(positions);
        sorted.sort(BY_NAME);
        this.positions = Collections.unmodifiableList(sorted);
        Map<String, List<Position>> emails = new HashMap<>();
        for (Position p : sorted) {
            emails.computeIfAbsent(p.emailKey, k -> new ArrayList<>()).add(p);
        }
        this.byEmail = emails;
        this.orgUnits = Collections.unmodifiableList(new ArrayList<>(orgUnits));
        this.loadedAt = loadedAt;
    }

    /**
     * @param employeesJson body of {@code GET /employees}: {@code {"count":n,"employees":[...]}}
     * @param divisionsJson body of {@code GET /divisions}: {@code {"count":n,"divisions":[...]}}
     * @throws IOException when either body is not the expected JSON
     */
    static DirectorySnapshot parse(String employeesJson, String divisionsJson, long loadedAt) throws IOException {
        JsonNode employeeArray = MAPPER.readTree(employeesJson).path("employees");
        JsonNode divisionArray = MAPPER.readTree(divisionsJson).path("divisions");
        if (!employeeArray.isArray() || !divisionArray.isArray()) {
            throw new IOException("Unexpected directory response: no employees or divisions array");
        }
        List<Record> records = new ArrayList<>();
        for (JsonNode node : employeeArray) {
            Record record = new Record(node);
            // Without an email a position can be neither selected nor stored.
            if (!fold(record.email).isEmpty()) {
                records.add(record);
            }
        }
        Map<String, List<Record>> byFullName = new HashMap<>();
        for (Record r : records) {
            String key = fold(r.fullName);
            if (!key.isEmpty()) {
                byFullName.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }
        }
        List<Position> positions = new ArrayList<>(records.size());
        for (Record r : records) {
            positions.add(new Position(r, manager(r, byFullName)));
        }
        List<OrgUnit> orgUnits = new ArrayList<>();
        for (JsonNode node : divisionArray) {
            orgUnits.add(new OrgUnit(text(node, "department"), text(node, "division"),
                    text(node, "branch"), text(node, "unit")));
        }
        return new DirectorySnapshot(positions, orgUnits, loadedAt);
    }

    /**
     * The person whose {@code full_name} equals this record's {@code manager}. Several positions of
     * one person (same email) count as one person. When several people share the name, the one with
     * a position in the same department wins; if that is still ambiguous, or nobody matches, there is
     * no manager.
     */
    private static Record manager(Record r, Map<String, List<Record>> byFullName) {
        List<Record> candidates = byFullName.get(fold(r.manager));
        if (candidates == null) {
            return null;
        }
        Map<String, Record> people = onePerEmail(candidates);
        if (people.size() == 1) {
            return people.values().iterator().next();
        }
        String department = fold(r.department);
        Map<String, Record> sameDepartment = onePerEmail(candidates.stream()
                .filter(c -> fold(c.department).equals(department))
                .collect(Collectors.toList()));
        return sameDepartment.size() == 1 ? sameDepartment.values().iterator().next() : null;
    }

    private static Map<String, Record> onePerEmail(List<Record> records) {
        Map<String, Record> people = new LinkedHashMap<>();
        for (Record r : records) {
            people.putIfAbsent(fold(r.email), r);
        }
        return people;
    }

    /** @return the number of positions (a person with two titles counts twice) */
    int employeeCount() {
        return positions.size();
    }

    int orgUnitCount() {
        return orgUnits.size();
    }

    long loadedAt() {
        return loadedAt;
    }

    EmployeeSearchResult search(String keyword, int minLength, int maxResults) {
        String folded = fold(keyword);
        String[] tokens = KEYWORD_SEPARATORS.split(folded);
        // Separators don't count towards the minimum: "s m" is two characters.
        if (String.join("", tokens).length() < minLength) {
            return EmployeeSearchResult.empty();
        }
        List<EmployeeSummary> page = new ArrayList<>();
        int total = 0;
        for (Position p : positions) {
            if (p.matches(tokens)) {
                total++;
                if (page.size() < maxResults) {
                    page.add(p.toSummary());
                }
            }
        }
        return new EmployeeSearchResult(page, total);
    }

    /**
     * One position by email. When the person holds several, {@code title} and {@code department}
     * (each optional) pick the position; if they match none, or are blank, the first position in
     * title order is returned, and its {@code title} says which one it is.
     */
    EmployeeDetail employee(String email, String title, String department) {
        List<Position> candidates = byEmail.get(fold(email));
        if (candidates == null || candidates.isEmpty()) {
            return EmployeeDetail.empty();
        }
        String t = fold(title);
        String d = fold(department);
        Position p = candidates.stream()
                .filter(c -> (t.isEmpty() || c.titleKey.equals(t)) && (d.isEmpty() || c.departmentKey.equals(d)))
                .findFirst()
                .orElse(candidates.get(0));
        return EmployeeDetail.builder()
                .firstName(p.firstName)
                .lastName(p.lastName)
                .email(p.email)
                .title(p.title)
                .department(p.department)
                .division(p.division)
                .branch(p.branch)
                .unit(p.unit)
                .managerFirstName(p.managerFirstName)
                .managerLastName(p.managerLastName)
                .managerEmail(p.managerEmail)
                .build();
    }

    NameList departments() {
        return names(u -> true, u -> u.department);
    }

    NameList divisions(String department) {
        String d = fold(department);
        if (d.isEmpty()) {
            return NameList.empty();
        }
        return names(u -> u.departmentKey.equals(d), u -> u.division);
    }

    NameList branches(String department, String division) {
        String d = fold(department);
        String v = fold(division);
        if (d.isEmpty() || v.isEmpty()) {
            return NameList.empty();
        }
        return names(u -> u.departmentKey.equals(d) && u.divisionKey.equals(v), u -> u.branch);
    }

    NameList units(String department, String division, String branch) {
        String d = fold(department);
        String v = fold(division);
        String b = fold(branch);
        if (d.isEmpty() || v.isEmpty() || b.isEmpty()) {
            return NameList.empty();
        }
        return names(u -> u.departmentKey.equals(d) && u.divisionKey.equals(v) && u.branchKey.equals(b),
                u -> u.unit);
    }

    private NameList names(Predicate<OrgUnit> filter, Function<OrgUnit, String> name) {
        // Keyed by the folded name so case variants collapse; the first spelling seen is kept.
        Map<String, String> unique = new LinkedHashMap<>();
        for (OrgUnit unit : orgUnits) {
            if (filter.test(unit)) {
                String value = name.apply(unit);
                if (!value.isEmpty()) {
                    unique.putIfAbsent(fold(value), value);
                }
            }
        }
        List<String> sorted = unique.entrySet().stream()
                .sorted(Map.Entry.<String, String>comparingByKey().thenComparing(Map.Entry.comparingByValue()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
        return new NameList(sorted);
    }

    /** Lower case, accents removed, spaces trimmed and collapsed. Null becomes "". */
    static String fold(String value) {
        if (value == null) {
            return "";
        }
        String noAccents = MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(noAccents.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText().trim();
    }

    /** One API record while parsing; discarded once the snapshot is built. */
    private static final class Record {
        final String firstName;
        final String lastName;
        final String fullName;
        final String email;
        final String title;
        final String department;
        final String division;
        final String branch;
        final String unit;
        final String manager;

        Record(JsonNode node) {
            firstName = text(node, "first_name");
            lastName = text(node, "last_name");
            fullName = text(node, "full_name");
            email = text(node, "email");
            title = text(node, "title");
            department = text(node, "department");
            division = text(node, "division");
            branch = text(node, "branch");
            unit = text(node, "unit");
            manager = text(node, "manager");
        }
    }

    /** One position, holding business contact information only. */
    private static final class Position {
        final String firstName;
        final String lastName;
        final String displayName;
        final String email;
        final String title;
        final String department;
        final String division;
        final String branch;
        final String unit;
        final String managerFirstName;
        final String managerLastName;
        final String managerEmail;
        final String searchText;
        final String lastKey;
        final String firstKey;
        final String emailKey;
        final String titleKey;
        final String departmentKey;

        Position(Record r, Record manager) {
            firstName = r.firstName;
            lastName = r.lastName;
            String name = (firstName + " " + lastName).trim();
            displayName = name.isEmpty() ? r.fullName.replace('.', ' ').trim() : name;
            email = r.email;
            title = r.title;
            department = r.department;
            division = r.division;
            branch = r.branch;
            unit = r.unit;
            managerFirstName = manager == null ? "" : manager.firstName;
            managerLastName = manager == null ? "" : manager.lastName;
            managerEmail = manager == null ? "" : manager.email;
            // Each word is preceded by a space, so " " + token finds a word that starts with the token.
            // The whole email is included too, so "ada.lovelace@" matches as typed.
            String words = firstName + " " + lastName + " " + email;
            searchText = " " + fold(WORD_BREAKS.matcher(words).replaceAll(" ") + " " + email);
            lastKey = fold(lastName);
            firstKey = fold(firstName);
            emailKey = fold(email);
            titleKey = fold(title);
            departmentKey = fold(department);
        }

        boolean matches(String[] tokens) {
            for (String token : tokens) {
                if (!token.isEmpty() && !searchText.contains(" " + token)) {
                    return false;
                }
            }
            return true;
        }

        EmployeeSummary toSummary() {
            return new EmployeeSummary(displayName, firstName, lastName, email, title, department);
        }
    }

    private static final class OrgUnit {
        final String department;
        final String division;
        final String branch;
        final String unit;
        final String departmentKey;
        final String divisionKey;
        final String branchKey;

        OrgUnit(String department, String division, String branch, String unit) {
            this.department = department;
            this.division = division;
            this.branch = branch;
            this.unit = unit;
            this.departmentKey = fold(department);
            this.divisionKey = fold(division);
            this.branchKey = fold(branch);
        }
    }
}
