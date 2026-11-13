# AGENTS.md

Dialectica describes database tables once, in Java, and works with them on SQLite, H2, PostgreSQL,
MySQL and MariaDB: it creates the tables, renders each database's SQL, and reads and writes entities.

## Modules

- `dialectica-api`: what projects use. Entity annotations, the schema model and its changes, column
  types, the dialect contract and exceptions. Public API: every change is compatibility-sensitive and
  needs complete Javadocs.
- `dialectica-common`: what the dialects share, the ANSI dialect, JDBC inspection and the entity
  reader. Its test fixtures are shared by the dialects' tests and are not published.
- `dialectica-dialect/*`: one module per database (`dialect-sqlite`, `dialect-h2`,
  `dialect-postgresql`, `dialect-mysql`, `dialect-mariadb`); MariaDB builds on MySQL.
- `dialectica-adapter/*`: access to entities through a library, `adapter-jdbi` for Jdbi.
- `dialectica`: the entry point and the schema writer; projects add the dialects they support.
- `dialectica-processor`: the annotation processor that writes each entity's `<Entity>Columns`;
  projects add it as an `annotationProcessor`. It depends on no Dialectica module.
- `dialectica-bundle`: the entry point with every dialect as one dependency. Its tests run the schema
  writer against all five databases.
- `build-logic`: the `library` convention, Java 17, publishing, and the `VERSION` environment
  variable that sets the published version.

Keep a database's behavior in its dialect module and a library's in its adapter module.

## Build and test

- `./gradlew test build` builds everything and runs the rendering, reader, processor, SQLite and H2
  tests.
- `-PintegrationTests=true` also runs the PostgreSQL, MySQL and MariaDB tests (tag
  `database-container`), which start containers and need Docker.
- `VERSION=<version> ./gradlew publishToMavenLocal` lets a project build against a local version.

## Pull requests and releases

Build each change in its own `feature/...` branch and merge it into `dev` through a pull request.
Pull requests run the build and the tests without containers. Publishing a release draft tags `dev`,
runs every test including the container tests, publishes the Maven artifacts and attaches the jars.
Development builds are started manually and publish `<branch>-<short commit>`.

### Release notes

A merged pull request lands in the release draft as `* <title> ([#<number>](<url>))`, under the
category of its label. Readers are developers of projects that use Dialectica.

- Titles use `Area: Description`, with a past-tense description of what changed for a project:
  `Introduced`, `Changed`, `Fixed`, `Removed`.
- Areas: `Schema`, `Entities`, `Types`, `Dialects`, `SQLite`, `H2`, `PostgreSQL`, `MySQL`,
  `MariaDB`, `Jdbi`. Use a database's name for a change to one dialect and `Dialects` for a change
  to all of them. Add an area only for a new dialect or adapter, and add it to this list in the same
  pull request.

| Label | Use for |
| --- | --- |
| `feature` | something new for projects |
| `change` | changed behavior or API |
| `bug` | a fix for a bug that shipped in a release |
| `dependencies` | a library update |
| `skip-changelog` | refactors, build, CI, tests and documentation, and fixes of bugs that never shipped |

Add `major` next to the category when projects must change their code or their stored schema on
upgrade.

Text written by hand, the subtitle, the summary and upgrade notes, lives in `.github/release-notes.md`;
it replaces the draft's placeholders while its `version` matches the drafted version. The release
workflow stops while a placeholder is left.
