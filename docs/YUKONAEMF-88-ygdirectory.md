# YUKONAEMF-88: ygDirectory lookups

Summary note for the vendor deliverables: the `ygDirectory` form data model, the `fragEmployeeLookup` type-ahead fragment, the test form, and how the ticket's open questions were resolved against the YG Directory API.

## YG Directory API (as found, 2026-11-16)

- Base URL `https://api.gov.yk.ca/directory`, on Azure API Management. Public, reachable from AEM as a Cloud Service without Advanced Networking. Auth: subscription key in the `Ocp-Apim-Subscription-Key` header.
- Three endpoints, each returning its whole list. There is no search, paging or lookup by email; `?department=` is the only filter.

| Endpoint | Records | Size | Content |
|---|---|---|---|
| `/departments` | 25 | 1 KB | Department names |
| `/divisions` | 713 | 141 KB | One row per level: `department`, `division`, `branch`, `unit` (lower levels null) |
| `/employees` | 5,407 | 4.4 MB | `first_name`, `last_name`, `full_name`, `email`, `department`, `division`, `branch`, `unit`, `title`, `manager`, `username`, phone and office fields |

- No ID fields. `full_name` is not a display name: it is `First.Last`, the same as the email's local part. `manager` holds the manager's `full_name`.
- Some names carry extra spaces (136 in `/divisions`); every value is trimmed.

## Design

Because the API can't search, sort or look up by email, and an AEM Cloud FDM can only call REST, SOAP or OData services, the six operations are implemented in AEM and the FDM calls them over REST:

```
form (browser) ─ Invoke Service ─▶ <form>.af.dermis ─▶ ygDirectory FDM (REST, OpenAPI 3)
   ─▶ https://<this environment>/bin/yukon-forms/ygdirectory/<operation>   (X-YG-Directory-Key)
   ─▶ DirectoryLookupServlet ─▶ DirectoryService (in-memory copy, hourly by default) ─▶ YG Directory API
```

- The API key never reaches the browser. The browser only calls the form's `.af.dermis` endpoint.
- Lookups are for signed-in users only. `DirectorySignInFilter` refuses `.af.dermis` calls to the `ygDirectory` FDM from anonymous users with 403, which the type-ahead reports as `unavailable`. On publish, MyYukon (SAML) is the only way to sign in.
- Only business contact information is kept in memory: first and last name, work email, job title, the org path (department, division, branch, unit) and the manager's name and email. Usernames, phone numbers, office addresses and every other API field are dropped when the data loads; the manager is resolved at load time and the API's `full_name` and `manager` values are not kept.
- Each AEM instance downloads `/divisions` and `/employees` when it starts and every 60 minutes by default (Cloud Manager variable `YG_DIRECTORY_REFRESH_MINUTES`, 5 to 1440). If a download fails, the previous copy stays in use. If there is no copy yet, requests retry at most once a minute and the operations return HTTP 503, which forms see as a failed service call.
- AEM only lets a field invoke FDM operations that its own rules reference. The type-ahead calls `searchEmployees` from `empSearch`, so `empSearch` carries an inert `initScript` naming that operation. Don't remove it.
- Logs name the operation only; no keyword, name or email is logged.
- AEM caches the OpenAPI spec and the FDM model. After changing either, update `jcr:lastModified` on the cloud config's `jcr:content` (the data source reloads on this one, not on `cq:lastModified`) and `jcr:lastModified` on the FDM's `jcr:content` (all set in the repo), or open and save each in the UI.
- The `ui.content` filters use `mode="merge"`, so a deploy doesn't update the FDM, the cloud config or the forms once they exist on an environment. Install changes to them with a package, or make them in the UI.

## Privacy approval

Approved under the existing AEM and Adobe Sign PIA (HPW eServices, March 2026). Under ATIPP Act s. 3(a), an employee's business contact information is not personal information, and it is already public on the Find a government employee site. Conditions, and how they are met:

| Condition | How |
|---|---|
| Store business contact information only | `DirectorySnapshot` keeps name, work email, title, org path and manager name and email; other API fields are dropped while parsing (unit test `keepsBusinessContactInformationOnly`). |
| No PII in logs | Logs name the operation and counts only. |
| Search only on forms where the user is signed in with MyYukon | `DirectorySignInFilter` (403 for anonymous users). RAP and LDF require MyYukon sign-in before the form. |

## Multiple positions

Some people hold more than one position: same email, a different title and department, often a different manager. In the API data of 2026-11-19, 10 people hold 30 positions (up to 5 each); title and department always tell them apart.

- `searchEmployees` returns each position as a separate result with its `title`.
- The type-ahead shows "email · title · department" under each name, and the selected value reads "First Last, Title (email)".
- Selecting a result stores the email, title and department (`empSelectedEmail`, `empSelectedTitle`, `empSelectedDepartment`).
- `getEmployee` takes the optional `title` and `department` to return that position's org path and manager. Without them, or if they match none, it returns the person's first position by title, and its `title` says which.

## Operations

Every operation returns empty values (empty strings, an empty list, `count` 0) with HTTP 200 when nothing matches, or when an input is empty or missing. All inputs are optional in the FDM, so a rule that fires on a cleared field (for example a division emptied by its department's rule, or `empSelectedEmail` emptied when the user types again) gets an empty result rather than an error. All matching ignores case, accents and surrounding spaces.

| Operation | Input | Output |
|---|---|---|
| `searchEmployees` | `keyword` (3+ characters, not counting spaces) | `employees` (up to the maximum, 10 by default: `full_name`, `first_name`, `last_name`, `email`, `title`, `department`), `count` (returned), `total` (matches) |
| `getEmployee` | `email`; optional `title`, `department` (pick the position) | `first_name`, `last_name`, `email`, `title`, `department`, `division`, `branch`, `unit`, `manager_first_name`, `manager_last_name`, `manager_email` |
| `getDepartments` | — | `names`, `count` |
| `getDivisions` | `department` | `names`, `count` |
| `getBranches` | `department`, `division` | `names`, `count` |
| `getUnits` | `department`, `division`, `branch` | `names`, `count` |

In the FDM the operations appear as `GET /bin/yukon-forms/ygdirectory/<operation>`, described by the operation name.

`searchEmployees` matches employees with a word in their first name, last name or email that starts with each typed word: `ada lov` finds Ada Lovelace, `lov` too, `velace` doesn't. A typed email prefix such as `ada.lovelace@` also matches. Results are sorted by last name, then first name, then email, then title.

## Open questions (TBCs) resolved

| Question | Resolution |
|---|---|
| Verify the API against the assumptions (operation names, parameters) | The API has three list endpoints only (above). The six operations, their names and parameters are provided by AEM as specified in the ticket. |
| Downstream fields | Name and email identify the employee. Forms also get the title and org path, and `getEmployee` the manager's name and email. No phone or office details. |
| Active employees only; filtering inactive/vacant records | The API has no status field and no vacant or placeholder names were found, so every record with an email is treated as active. Records without an email are left out. 20 records share a one-character placeholder `username`, but they have real names and emails, so they are kept. |
| Email lookup endpoint, or shape `getEmployee` via keyword search | There is no email endpoint. `getEmployee` looks the email up in the cached copy, ignoring case. |
| Manager name resolution | `manager` holds the manager's `full_name` (`First.Last`). It is matched against employees' `full_name`, ignoring case; several positions of one person count as one person. 4,260 of 4,358 managers match exactly one employee. When several people share the name, the one in the same department is used; if that is still ambiguous, or nobody matches, the manager values are empty. |
| Source of the branch and unit lists | `/divisions`: `getBranches` and `getUnits` filter its rows by the parent names. Every employee's organization path exists there. |
| `full_name` in `searchEmployees` | Returned as the display name "First Last" built from `first_name` and `last_name`, because the API's `full_name` is `First.Last`. |
| No employee ID | Confirmed: the API has no ID field. Email identifies the person; with several positions, email, title and department identify the position. |
| Missing levels (divisions without branches or units) | `getBranches` and `getUnits` return `names` empty and `count` 0 with HTTP 200, also when a parent is empty or missing. `getUnits` lists the units of a branch; whether some units sit directly under a division, with no branch, is still to be confirmed against the data. |

## Differences from the boards

The build follows the boards of 2026-11-16 except where later client decisions changed them:

| Board | Built | Reason |
|---|---|---|
| Type-ahead has two hidden outputs, "nothing else" | Four: also `empSelectedTitle` and `empSelectedDepartment` | Multiple positions: the form must store the selected one |
| Selected value "First Last (email)" | "First Last, Title (email)" | Multiple positions |
| Option line 2 "email · department" | "email · title · department" | Multiple positions |
| `getEmployee` input `email` | `email`, plus optional `title` and `department`; output adds `title` | Multiple positions; forms need the title |
| `searchEmployees` output without `title` | Adds `title` | Multiple positions |
| Manager: the single exact name match in the same department | The single person with that name in any department; when several share it, the one in the same department; otherwise empty | Finds managers in other departments; ambiguous names still give empty values |
| "The YG Directory API is public and returns no personal information" | Lookups need MyYukon sign-in; only business contact information is kept, logs are PII-free | Privacy approval |
| Max 10 options | Configurable, 10 by default | Requested |
| Prefixed field names when a form uses the fragment twice | Same names, each instance working on the fields of its own panel; names can be changed in the dialog | Fragment instances are already scoped |
| Test form can be a copy of the RAP form | A separate test form | Allowed by the board |
| "Search for an employee" shown as required (*) | Not required by default | Fragment properties apply to every form that uses it; eServices decides per form |

## Type-ahead fragment `fragEmployeeLookup`

- `empSearch` (component `yukon-forms/components/adaptiveForm/employeelookup`; label "Search for an employee", help text "Type at least 3 letters of the employee’s first name, last name or email address, then select the employee from the list." behind the "?" icon, no placeholder, not required), and hidden `empSelectedEmail`, `empSelectedTitle`, `empSelectedDepartment` and `empSearchStatus`.
- Searches from 3 characters, 300 ms after the last keystroke, with a 10 s timeout, through `searchEmployees`.
- List: line 1 "First Last" with the matching parts in bold; line 2 "email · title · department", muted. A person with several positions appears once per position. Up to the maximum number of results (10 by default; Cloud Manager variable `YG_DIRECTORY_SEARCH_MAX_RESULTS`, 1 to 100), then "Showing {n} of {total} results. Keep typing to narrow the list."
- WAI-ARIA combobox: `role="combobox"`, `aria-autocomplete="list"`, `aria-expanded`, `aria-controls`, `aria-activedescendant`; the list is a `listbox` of `option`s. Up/Down highlight (light blue-green background, dark text), Enter selects, Esc closes the list and keeps the typed text. A polite live region announces "{n} results available."
- No match: "No employees match “{text}”. Check the spelling or search by email address." in the list area (a box styled like the results list), and `empSearchStatus` = `noResults`.
- Error, timeout or user not signed in: `empSearchStatus` = `unavailable`, with no message (the form's own rules show the fallback).
- Selection: shows "First Last, Title (email)", sets `empSelectedTitle`, `empSelectedDepartment`, then `empSelectedEmail`, and `empSearchStatus` = `ok`. The email is set last so a rule on it can call `getEmployee` with the title and department. Typing again clears all three.
- Text without a selection fails validation with "Select an employee from the list" (`validateExp` on `empSearch`, also checked on the server).
- All messages are field properties (Employee lookup section of the field dialog), so French forms set their own text. Left blank, they show the English defaults. Forms that use a fragment share its text, so a French form uses a French copy of the fragment: copy `frag-employee-lookup` (for example to `frag-employee-lookup-fr`) and enter the French text on its `empSearch`: label, help text, the four messages, and the "Select an employee from the list" message in the Employee lookup section.
- Whether the field is required is a fragment property too, so it applies to every form that uses the fragment. It is not required by default; a form that needs it required uses its own copy of the fragment with `empSearch` marked required.
- Several lookups can be on one form: each instance uses the fields in its own panel. The output field names can be changed in the dialog; update `empSearch`'s validation script if `empSelectedEmail` is renamed.

## Test form

`/content/forms/af/yukon-forms/directory-lookup/ygdirectory-test-form`:

1. Two `fragEmployeeLookup` instances, with their `empSelectedEmail`, `empSearchStatus`, `empSelectedTitle` and `empSelectedDepartment` shown, and a Validate button.
2. `searchEmployees`: keyword, count, total and the returned employees.
3. `getEmployee`: filled from the first lookup's selection (email, title and department), or from a typed email.
4. `getDepartments` → `getDivisions` → `getBranches` → `getUnits` as chained drop-downs, with each list's count.

Its division and branch rules call the next operation only when the field has a value (`if (this.value) { … }`). Since the inputs became optional this only saves a call: without it, the next list is simply empty.

Its rules use the code the rule editor's Invoke Service generates (`guidelib.dataIntegrationUtils.executeOperation` / `setOptionsFromService`), so they open in the code editor rather than the visual editor.

## Configuration

See README, "Directory lookups (ygDirectory)", for the Cloud Manager variables (`DIRECTORY_API_KEY`, `YG_DIRECTORY_FDM_KEY`, and the optional `YG_DIRECTORY_FDM_ENDPOINT`, `YG_DIRECTORY_REFRESH_MINUTES` and `YG_DIRECTORY_SEARCH_MAX_RESULTS`). The FDM data source can only hold a fixed URL, so it is set per environment: the endpoint by `DirectoryEndpointOverrideProvider` (the variable if set, otherwise the instance's own URL from the Externalizer: author URL on author, publish URL on publish), the key by `OsgiConfigurationOverrideProvider~ygdirectory`; the dispatcher allows `/bin/yukon-forms/ygdirectory/*` (rule `/0120`) and forwards the `X-YG-Directory-Key` header (`clientheaders.any`). Without that header entry the servlet answers 401 on publish.
