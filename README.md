# Government of Yukon Forms Services Project

This is a project template for AEM-based applications. It is intended as a best-practice set of examples as well as a potential starting point to develop your own functionality.

## Modules

The main parts of the template are:

* [core:](core/README.md) Java bundle containing all core functionality like OSGi services, listeners or schedulers, as well as component-related Java code such as servlets or request filters.
* [it.tests:](it.tests/README.md) Java based integration tests
* [ui.apps:](ui.apps/README.md) contains the /apps (and /etc) parts of the project, ie JS&CSS clientlibs, components, and templates
* [ui.content:](ui.content/README.md) contains sample content using the components from the ui.apps
* ui.config: contains runmode specific OSGi configs for the project
* [ui.frontend:](ui.frontend.general/README.md) an optional dedicated front-end build mechanism (Angular, React or general Webpack project)
* [ui.tests.cypress:](ui.tests.cypress/README.md) Cypress based UI tests
* [ui.tests.wdio:](ui.tests.wdio/README.md) Selenium based UI tests
* all: a single content package that embeds all of the compiled modules (bundles and content packages) including any vendor dependencies
* analyse: this module runs analysis on the project which provides additional validation for deploying into AEMaaCS

## How to set up local development

Ensure node is install and its version is great or equal to v20.18.0+

    node --version
    v20.18.0

Install dev dependencies (mainly for linters and formatters)

    npm install

Initialize Husky (commit hooks)

    npm run prepare

## How to commit code

Run the following git commit would trigger lint-staged to auto lint/format staged files

    git commit -m "commit message here"

Run the following git push command would trigger mvn clean verify command to ensure it builds successfully before pushing to the git repo

    git push

## How to deploy code (Optional)

Add cloud manager origin

    git remote add cm-origin https://git.cloudmanager.adobe.com/governmentofyukon/FormsSandbox-p125405-uk97084/

Force push to dev branch on CM git repo to trigger deploy to DEV environment

    git push cm-origin dev --force

Force push to main branch on CM git repo to trigger deploy to STG environment

    git push cm-origin main --force

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

## Documentation

The build process also generates documentation in the form of README.md files in each module directory for easy reference. Depending on the options you select at build time, the content may be customized to your project.

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

The `analyse` module performs static analysis on the project for deploying into AEMaaCS. It is automatically
run when executing

    mvn clean install

from the project root directory. Additional information about this analysis and how to further configure it
can be found here https://github.com/adobe/aemanalyser-maven-plugin

### UI tests

They will test the UI layer of your AEM application using either Cypress or Selenium technology.

Check README file in `ui.tests.cypress` or `ui.tests.wdio` module for more details.

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