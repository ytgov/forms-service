# Government of Yukon Forms Services Project

This is a project template for AEM-based applications. It is intended as a best-practice set of examples as well as a potential starting point to develop your own functionality.

## Modules

The main parts of the template are:

* core: Java bundle containing all core functionality like OSGi services, listeners or schedulers, as well as component-related Java code such as servlets or request filters.
* it.tests: Java based integration tests
* ui.apps: contains the /apps (and /etc) parts of the project, ie JS&CSS clientlibs, components, and templates
* ui.apps.structure: the repository structure package that defines the root paths the other packages install into
* ui.content: contains sample content using the components from the ui.apps
* ui.config: contains runmode specific OSGi configs for the project
* [ui.frontend:](ui.frontend/README.md) general Webpack + TypeScript front-end build that generates clientlibs
* ui.frontend.react.forms.af: React-based adaptive forms renderer (Webpack build that generates clientlibs)
* [ui.tests:](ui.tests/README.md) Cypress based UI tests
* dispatcher: Dispatcher configuration for AEMaaCS
* fonts: custom fonts package
* all: a single content package that embeds all of the compiled modules (bundles and content packages) including any vendor dependencies, and runs the AEMaaCS analyser (see Static Analysis)

## How to set up local development

Ensure node is install and its version is great or equal to v20.18.0+

    node --version
    v20.18.0

Install dev dependencies (only Husky, for the git hooks)

    npm install

Initialize Husky (commit hooks)

    npm run prepare

## How to commit code

Commit as usual (the pre-commit hook does not lint or format files)

    git commit -m "commit message here"

The pre-commit hook also prints a reminder (it does not block the commit) if you add new code, components or configs, or change `pom.xml`, without updating `README.md`.

Pull requests are pre-filled from `.github/pull_request_template.md`, which includes a short review checklist.

Run the following git push command would trigger mvn clean verify command to ensure it builds successfully before pushing to the git repo

    git push

## How to deploy code (Optional)

Add cloud manager origin

    git remote add cm-origin https://git.cloudmanager.adobe.com/governmentofyukon/yukon-forms-cloud/

Force push to dev branch on CM git repo to trigger deploy to DEV environment

    git fetch origin && git fetch cm-origin
    git push cm-origin refs/remotes/origin/dev:refs/heads/dev --force-with-lease=refs/heads/dev:cm-origin/dev

Force push to main branch on CM git repo to trigger deploy to STG environment

    git fetch origin && git fetch cm-origin
    git push cm-origin refs/remotes/origin/main:refs/heads/main --force-with-lease=refs/heads/main:cm-origin/main

## How to build

To build all the modules run in the project root directory the following command with Maven 3:

    mvn clean install

To build all the modules and deploy the `all` package to a local instance of AEM, run in the project root directory the following command:

    mvn clean install -PautoInstallSinglePackage

Or to deploy it to a publish instance, run

    mvn clean install -PautoInstallSinglePackagePublish

Or alternatively

    mvn clean install -PautoInstallSinglePackage -Daem.port=4503

Or to deploy only the bundle to the author, run

    mvn clean install -PautoInstallBundle

Or to deploy only a single content package, run in the sub-module directory (i.e `ui.apps`)

    mvn clean install -PautoInstallPackage

## Save and resume (Forms Portal drafts)

Authenticated users can save a draft of a long form and reopen it on the same site page (YUKONAEMF-81). See `docs/YUKONAEMF-81-save-resume-plan.md`.

- **Storage:** drafts are stored through the Unified Storage Connector (Tools › Cloud Services › Unified Storage Connector → Forms Portal → Azure). It isn't in Git; each environment needs its own configuration. To use Azurite locally, see §8.5 of the plan.
- **Page setup:** embed the form with "Adaptive Form - Embed" (`aemformscontainer`) **inline** ("Form covers entire width of the page" ticked). Place "Drafts and Submissions (Custom)" (`customdraftsandsubmissions`) on the same page. Add a "Save draft" button (Rule Editor → Save Form) to the form.
- **Resume:** clicking a draft, or its "Open" action, reloads the same page in the same tab with `?dataRef=service://FP/draft/<id>`.
- **`EmbeddedFormPrefillServlet`** (core): inside the embed, Adobe's form runtime requests `<embed>.af.prefilldata.json`. The servlet forwards that to the form's `guideContainer`. Without it the draft isn't restored, and on author the default POST servlet writes the query parameters onto the embed node. AEM only returns a draft to its owner.
- **Don't use the iframe embed for this:** a page URL with `?dataRef=` makes Adobe's iframe embed template (Core Forms Components 3.0.172) return HTTP 500.

## Local AEM Forms setup

Some AEM Forms features need extra local setup on top of the AEM SDK quickstart.

### PDF generation (Document of Record)

The Forms add-on SDK renders PDFs (Document of Record, Output service) in a separate Docker container, `forms-native-sdk` on port 8007. If it isn't running, PDF generation fails with `Error getting shared temp directory, check whether the SDK started successfully` (`Connection refused`).

Start it from the extracted Forms add-on SDK folder (needs Docker), pointing `AEM_HOME` at your AEM author directory:

    export AEM_HOME=/path/to/aem/author
    ./sdk.sh start

`sdk.sh` calls the legacy `docker-compose` command. If you only have `docker compose` (Docker Desktop), install it with `brew install docker-compose`. The containers don't restart on their own after a reboot, so start them again before testing PDFs. Start them **after** AEM is up: AEM empties `crx-quickstart/temp` at startup, which deletes `temp/sdk`, the folder shared with the container, and PDFs then fail with `NoSuchFileException …/temp/sdk/rf_temp_dir…`. If that happens, stop and start the SDK again. Check they're running with:

    docker ps | grep forms-native-sdk

### Email (Send Email submit action)

AEM sends email through the Day CQ Mail Service. Its config (`ui.config/.../com.day.cq.mailer.DefaultMailService.cfg.json`) is set up for AEMaaCS: host and port come from the env vars `AEM_PROXY_HOST` and `SMTP_PORT`, the password from the secret `EMAIL_PASSWORD`, and STARTTLS is required. Without those env vars, local AEM tries `proxy.tunnel:30587` and email fails.

To test email locally without a real mail server, run [Mailpit](https://mailpit.axllent.org/), which catches every email and shows it, attachments included, at http://localhost:8025:

1. Start Mailpit with STARTTLS, using a certificate for `localhost` that the AEM JVM trusts (add it to the trust store passed with `-Djavax.net.ssl.trustStore`). It accepts any username and password:

       docker run -d --name mailpit -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 \
         -v /path/to/certs:/certs:ro axllent/mailpit:v1.20 \
         --smtp-tls-cert /certs/cert.pem --smtp-tls-key /certs/key.pem --smtp-require-starttls --smtp-auth-accept-any

2. Start AEM with these env vars, so the repo config stays unchanged:

       export AEM_PROXY_HOST=localhost SMTP_PORT=1025 EMAIL_USERNAME=local

3. Submit a form whose submit action is "Send Email". The email and its Document of Record appear in Mailpit.

### Adobe Fonts for form themes

The Yukon form themes use the Adobe Fonts (Typekit) configuration `yg-fonts`. If it doesn't exist locally, forms using those themes fail to render (`TypekitConfiguration ... is null`). Create it under Tools > Cloud Services > Adobe Fonts in `/conf/global`, with name `yg-fonts` and kit ID `plb4eig` (the kit imported by `ui.apps/.../clientlibs/clientlibs-forms/theme-base-clientlibs/yukon-default-theme/css/style.css`).

## Adaptive form custom functions

Custom functions for the Foundation adaptive form Rule Editor live in `ui.apps/.../clientlibs/clientlib-yukon` (category `yukon-forms.custom`). They include `validatePHN`, `validateDateByDaysPassed`, `calculatePug`, `downloadDorPreview` and `previewDorInNewTab`. A form uses them by listing the category in its container's **Client Library Category** (`clientLibRef`).

Set **one** category there, normally `yukon-forms.custom`. Server-side validation loads only a single client library: it treats a comma-separated list as one (non-existent) category name, and it doesn't load dependencies. Custom functions used by validation rules then don't exist on the server, and valid submissions are rejected. The browser still gets `yukon-forms.utils` and `yukon-forms.typeahead`, because `yukon-forms.custom` declares them as dependencies.

### Download the submission PDF before submitting

`downloadDorPreview(fileName)` downloads the form's submission PDF (Document of Record) for the data entered so far, without submitting. Add a button to the form, typically on the last panel before Submit, with a click rule that calls the function. In the visual Rule Editor, use "When [button] is clicked" → **Function Output** → "Download submission PDF before submitting", with an optional file name; no Code Editor is needed. The generated code is e.g. `downloadDorPreview("my-application.pdf")`.

The downloaded file is named `<name>-YYYYMMDD-HHmmss.pdf` in the user's local time, so repeated downloads don't share a name. `<name>` is the file name given (without `.pdf`), or the form's page name if none is given, e.g. `update-yhcip-information_e-20261023-142530.pdf`.

It posts the current data from `guideBridge.getDataXML()` to AEM's built-in endpoint `/adobe/forms/af/dor/{id}`. The PDF is the same one the form emails on submit, so the form needs a Document of Record configured. The existing dispatcher filter for `/adobe/forms/*` covers the endpoint.

To show the PDF in a new browser tab instead of downloading it, call `previewDorInNewTab()` (Rule Editor: "Preview submission PDF in a new tab before submitting"). It opens the tab during the click, so pop-up blockers allow it, then loads the PDF into it when it's ready. If the browser still blocks the tab, it downloads the PDF instead, named after the form's page as above (e.g. `update-yhcip-information_e-20261023-142530.pdf`).

## Testing

There are three levels of testing contained in the project:

### Unit tests

This show-cases classic unit testing of the code contained in the bundle. To
test, execute:

    mvn clean test

### Integration tests

This allows running integration tests that exercise the capabilities of AEM via
HTTP calls to its API. To run the integration tests, run:

    mvn clean verify -Plocal

Test classes must be saved in the `src/main/java` directory (or any of its
subdirectories), and must be contained in files matching the pattern `*IT.java`.

The configuration provides sensible defaults for a typical local installation of
AEM. If you want to point the integration tests to different AEM author and
publish instances, you can use the following system properties via Maven's `-D`
flag.

| Property | Description | Default value |
| --- | --- | --- |
| `it.author.url` | URL of the author instance | `http://localhost:4502` |
| `it.author.user` | Admin user for the author instance | `admin` |
| `it.author.password` | Password of the admin user for the author instance | `admin` |
| `it.publish.url` | URL of the publish instance | `http://localhost:4503` |
| `it.publish.user` | Admin user for the publish instance | `admin` |
| `it.publish.password` | Password of the admin user for the publish instance | `admin` |

The integration tests in this archetype use the [AEM Testing
Clients](https://github.com/adobe/aem-testing-clients) and showcase some
recommended [best
practices](https://github.com/adobe/aem-testing-clients/wiki/Best-practices) to
be put in use when writing integration tests for AEM.

## Static Analysis

The `all` module runs the AEMaaCS analyser (`aemanalyser-maven-plugin`) on the project for deploying into AEMaaCS. It is automatically
run when executing

    mvn clean install

from the project root directory. Additional information about this analysis and how to further configure it
can be found here https://github.com/adobe/aemanalyser-maven-plugin

### UI tests

They test the UI layer of your AEM application using Cypress.

Check the README file in the `ui.tests` module for more details.

## ClientLibs

The frontend module is made available using an [AEM ClientLib](https://helpx.adobe.com/experience-manager/6-5/sites/developing/using/clientlibs.html). When executing the NPM build script, the app is built and the [`aem-clientlib-generator`](https://github.com/wcm-io-frontend/aem-clientlib-generator) package takes the resulting build output and transforms it into such a ClientLib.

A ClientLib will consist of the following files and directories:

- `css/`: CSS files which can be requested in the HTML
- `css.txt` (tells AEM the order and names of files in `css/` so they can be merged)
- `js/`: JavaScript files which can be requested in the HTML
- `js.txt` (tells AEM the order and names of files in `js/` so they can be merged
- `resources/`: Source maps, non-entrypoint code chunks (resulting from code splitting), static assets (e.g. icons), etc.

## Maven settings

The project comes with the auto-public repository configured. To setup the repository in your Maven settings, refer to:

    http://helpx.adobe.com/experience-manager/kb/SetUpTheAdobeMavenRepository.html

## Tail error.logs on Cloud Manager/AEMaaCS via aio CLI
```
❯ node --version
v20.18.0

❯ npm install -g @adobe/aio-cli

❯ aio --version
@adobe/aio-cli/10.3.1 darwin-arm64 node-v20.18.0

❯ aio plugins:install @adobe/aio-cli-plugin-cloudmanager

## [Optional], remove existing aio config
❯ rm -f ~/.config/aio

## [Optional], log out from previous session
❯ aio logout

## Login, -f is optional
❯ aio auth login -f

## Select Yukon Org
❯ aio cloudmanager:org:select

## List Programs
❯ aio cloudmanager:list-programs

## Set Program
❯ aio config:set cloudmanager_programid <programId>

## List Environments
❯ aio cloudmanager:list-environments

## List Available Log Options for DEV
❯ aio cloudmanager:list-available-log-options <environmentId>

## Tail error log
❯ aio cloudmanager:tail-log <environmentId> author aemerror

```
