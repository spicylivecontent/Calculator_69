# Room schemas

KSP exports the schema of `AppDatabase` here for debug and release builds.
CI retains the generated JSON files in the `unit-and-lint-reports` artifact.
The schema contains table definitions, not user data or encryption keys.

Before changing the database version, download and commit the current version's
JSON from a successful build, add a non-destructive Room migration, and commit
the new version's JSON. Do not use destructive fallback or increment the version
without a migration. Enabling export does not change the current version (1)
or the existing encrypted database.
