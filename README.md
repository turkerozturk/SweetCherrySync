# SweetCherrySync

**One-way database mirror for [CherryTree](https://github.com/giuspen/cherrytree) notes.**

SweetCherrySync is an independent, GPL-3.0-licensed Spring Boot application by Türker Öztürk. It reads a CherryTree-compatible source database and synchronizes its `node`, `children`, `bookmark`, `image`, `grid` and `codebox` tables into a **dedicated destination database**. The source is authoritative: destination edits and deletions may be overwritten during synchronization.

> **Current prerelease: `1.1.0-alpha.1` (release candidate source).** See [Releases](https://github.com/turkerozturk/SweetCherrySync/releases) for published downloads. The previous **1.0.0 compiled package (April 2025)** does **not** include the features described below. Do not use that package to evaluate Incremental Sync.

## What's available in this alpha

- **Incremental Sync:** compares all `node`, `children` and `bookmark` metadata, then reads and synchronizes dependent `image`, `grid` and `codebox` rows only for nodes identified as content-affected. In particular, it avoids downloading unchanged destination BLOBs on every run.
- **Legacy Full Sync:** performs comprehensive snapshot comparison, repair and verification, including BLOB contents. It is slower but remains available as the full-integrity reference mode.
- **Read-only preview:** `GET /sync/incremental/preview` reports candidate differences without writing or reading BLOB contents.
- **Web UI:** run either mode, follow phases, elapsed time and counters; completion updates counters without manually refreshing the page.
- **Optional runtime scheduling:** disabled by default; enable an interval and select a mode in the web UI. UI selections reset to configured defaults after restart.
- **JSONL audit:** after successful commits, records changes such as created, updated and deleted rows, with node IDs/names and timestamps, outside the CTB file. Journal writes are best-effort after commit.
- **Safety measures:** read-only source connections (including explicit SQLite read-only handling), deletion guards, transactional destination updates, and post-write verification appropriate to the selected mode.

### Supported databases and scope

The source and destination use JDBC connections and need not be the same database type. SQLite CTB is the primary source scenario. **MariaDB/MySQL and PostgreSQL destinations have been manually used**, including databases on a Raspberry Pi 3; matching schema SQL files are included. SQLite and SQL Server schema examples are also provided, but do not assume equal validation across every database/version combination. Older CherryTree `children` schemas without `master_id` have compatibility handling.

This is **one-way mirroring, not bidirectional synchronization**. It is **not a versioned backup**: source deletions can propagate to the destination. Keep independent backups of the original CTB and any important destination data. Incremental Sync cannot detect malicious or out-of-band changes to an unchanged destination attachment; run Legacy Full Sync when complete verification/repair is needed.

## Quick start (Java 17+)

1. Download the **new `v1.1.0-alpha.1` prerelease ZIP** from [Releases](https://github.com/turkerozturk/SweetCherrySync/releases) **once published**. The old `1.0.0` ZIP is a different build. Or build from source with `mvnw.cmd -DskipTests package` on Windows (`./mvnw -DskipTests package` on Linux/macOS).
2. Create an **empty, dedicated destination database**. Choose the matching schema: `schema-sqlite.sql`, `schema-mysql.sql`, `schema-postgresql.sql` or `schema-mssql.sql`. Schema initialization creates missing tables; **it does not migrate existing tables**. Do not point it at a database you cannot afford to modify.
3. Copy `application.example.yml` to `application.yml` **beside the JAR**. Edit the source JDBC URL, destination URL, credentials, driver, dialect and `destination.datasource.schema` as necessary. The sample uses SQLite CTB as source and MariaDB/MySQL as destination. Do not commit your configured file with passwords.
4. From the unpacked release folder, run `start.cmd` (Windows) or `sh start.sh` (Linux/macOS), or use `java -jar sweetcherrysync-1.1.0-alpha.1.jar --spring.config.additional-location=file:./application.yml` from the release folder.
5. Open **http://127.0.0.1:8081/**. Check the preview and run a first **Legacy Full Sync** to establish a baseline; use **Incremental Sync** for day-to-day changes. Keep an independent backup.

The web server binds to **127.0.0.1** by default and has **no user login**. Do **not** expose its write-capable endpoints directly to the internet. A VPN or appropriately secured access path is required for remote administration. The source CTB is opened read-only, but the destination is deliberately changed.

## Configuration and operational notes

- Relative paths (including the H2 statistics file, schema SQL files and audit log) are relative to the **working directory**. Run the packaged scripts from the release folder.
- Default `sync.auto.enabled: false`, `sync.auto.mode: INCREMENTAL`, `sync.auto.interval-seconds: 300`; GUI edits do not persist across application restarts.
- Default audit file: `logs/sync-node-changes.jsonl` (`sync.audit.enabled: true`). It can contain private node titles; keep it local. A crash between database commit and journal append can leave a missing audit entry.
- Full Sync on small Raspberry Pi / SD storage may be slow when there are many large attachments. An observed incremental operation completed in about 2 seconds on one LAN setup; this is **not a performance guarantee**.
- Changing only an attachment without changing the source node's modification metadata can evade Incremental detection. CherryTree normally updates node modification timestamps for content edits, but direct SQL editing and out-of-band destination tampering are outside the incremental guarantees.
- For schema safety, options and troubleshooting, read the guides below.

## Documentation

- [Alpha release notes](RELEASE_NOTES_1.1.0-alpha.1.md)
- [Release and packaging instructions (TR)](documentation/release_preparation_tr.md)
- [One-way reliability and limitations (TR)](documentation/reliability_tr.md)
- [Incremental modes and design (TR)](documentation/incremental_design_tr.md)
- [Incremental writes (TR)](documentation/incremental_write_tr.md)
- [Runtime scheduling and audit (TR)](documentation/runtime_schedule_audit_tr.md)
- [Raspberry Pi / Webmin setup (TR)](documentation/webmin_setup_tr.md)

**Historical documentation** (some examples and screenshots predate the 2026 sync modes):
[Installation EN](https://github.com/turkerozturk/SweetCherrySync/blob/main/src/docs/asciidoc/en/installation/installation.adoc) · [Data source examples EN](https://github.com/turkerozturk/SweetCherrySync/blob/main/src/docs/asciidoc/en/datasourceexamples.adoc) · [FAQ EN](https://github.com/turkerozturk/SweetCherrySync/blob/main/src/docs/asciidoc/en/technicalinformation.adoc) · [Old screenshots](https://github.com/turkerozturk/SweetCherrySync/blob/main/src/docs/screenshots/20250415.jpg).

## Related project and licensing

[SweetCherry](https://github.com/turkerozturk/SweetCherry) is a separate web-based companion for viewing CherryTree notes, including mirrored databases where supported. SweetCherrySync is not affiliated with the CherryTree maintainers. Source code is licensed under [GPL-3.0](LICENSE); third-party libraries retain their own licenses.
