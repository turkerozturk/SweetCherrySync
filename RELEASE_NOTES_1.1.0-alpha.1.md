# SweetCherrySync 1.1.0-alpha.1 — Release notes

**Alpha prerelease** · Successor to the April 2025 `1.0.0` build.

## Highlights

- Introduced a **fast one-way Incremental Sync** mode, using `node`, `children` and `bookmark` metadata to determine candidate changes; `image`, `grid` and `codebox` are inspected for affected nodes instead of downloading unchanged BLOBs on every run.
- Preserved **Legacy Full Sync** for complete source/destination comparison and repair, including attachment contents.
- Added a **read-only incremental preview endpoint**, progress phases, elapsed time, five-second UI polling, and auto-refreshing post-sync counters.
- Added **optional web-configured scheduled synchronization**, disabled by default, with one-active-job coordination.
- Added **post-commit JSONL change journaling** outside CTB/target databases. It records node identifiers/names and changes, not note/attachment contents.
- Improved **source read-only enforcement**, transaction handling, deletion guardrails, `children.master_id` compatibility, and consistency checks.
- Included and updated destination SQL schemas for SQLite, MySQL/MariaDB, PostgreSQL and SQL Server, plus Raspberry Pi / Webmin setup guidance.

## Verified in development (not a compatibility certification)

- User-reported Maven run: **38 tests passed** before this release-packaging change.
- Manual one-way synchronization from a laptop SQLite CTB to Raspberry Pi MariaDB and PostgreSQL destinations was successful; SweetCherry could read both mirrors.
- In the reported setup, one Incremental Sync operation finished in **2.16 seconds**. Full Sync over many attachment BLOBs took minutes (PostgreSQL Full Sync about 18 minutes). Hardware and data sizes materially affect speed.

## Known limitations and safety

- **One-way mirror only:** destination changes may be replaced or deleted; this is not a substitute for versioned backups.
- **Incremental mode trusts source metadata for dependent attachment changes.** Out-of-band SQL edits or tampering with unchanged destination BLOBs may be missed. Use Legacy Full Sync for complete verification/repair.
- **Alpha quality:** multiple database/dialect paths are present, but not all combinations have had equivalent manual testing.
- The web UI does **not** provide user authentication. Default binding is localhost (`127.0.0.1`); do not expose it to untrusted networks.
- GUI scheduling changes **reset on restart**. Audit logging happens *after* a successful commit and is best-effort, not atomic with the database transaction.
- Existing destination schemas are **not migrated** by the SQL files. Choose a dedicated database and take backups first.
- An installed **Java 17+** runtime is required; no bundled JRE/Windows installer is provided in this alpha ZIP.

## Getting started

See [README](README.md). The prerelease ZIP contains a Spring Boot JAR, matching schema scripts, an example configuration, and `start.cmd` / `start.sh` launch scripts. Copy the example to `application.yml`, edit it for your environment and run the appropriate script. Do not download the old `1.0.0` package expecting these features.

## Compared with previous release

[Compare from 1.0.0](https://github.com/turkerozturk/SweetCherrySync/compare/1.0.0...v1.1.0-alpha.1) (available when the new tag is published).
