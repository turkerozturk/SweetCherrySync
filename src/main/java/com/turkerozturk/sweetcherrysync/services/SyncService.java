/*
 * This file is part of the SweetCherrySync project.
 * Please refer to the project's README.md file for additional details.
 * https://github.com/turkerozturk/SweetCherrySync
 *
 * Copyright (c) 2024 Turker Ozturk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.en.html>.
 */
package com.turkerozturk.sweetcherrysync.services;

import com.turkerozturk.sweetcherrysync.my.services.MyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** One source snapshot, one atomic destination transaction. No timestamp-only shortcuts. */
@Service
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    static final List<Table> TABLES = List.of(
        new Table("node", "Node", "Nodes", 1, "node_id,name,txt,syntax,tags,is_ro,is_richtxt,has_codebox,has_table,has_image,level,ts_creation,ts_lastsave"),
        new Table("children", "Children", "Children", 1, "node_id,father_id,sequence,master_id"),
        new Table("bookmark", "Bookmark", "Bookmark", 1, "node_id,sequence"),
        new Table("codebox", "CodeBox", "CodeBoxes", 2, "node_id,offset,justification,txt,syntax,width,height,is_width_pix,do_highl_bra,do_show_linenum"),
        new Table("grid", "Grid", "Grids", 2, "node_id,offset,justification,txt,col_min,col_max"),
        new Table("image", "Image", "Images", 2, "node_id,offset,justification,anchor,png,filename,link,time"));

    record Table(String name, String countName, String counterName, int keys, String csv) {
        List<String> columns() { return List.of(csv.split(",")); }
    }
    public record Status(String state, Instant startedAt, Instant finishedAt, String message) {}
    private volatile Status status = new Status("IDLE", null, null, "No synchronization has run yet.");
    private volatile String phase = "Idle";
    private volatile long processed = 0;
    private volatile long total = 0;
    public record Progress(Status status, String phase, long processed, long total, long elapsedSeconds) {}
    public Progress getProgress() {
        Status current = status;
        long elapsed = current.startedAt() == null ? 0 : java.time.Duration.between(
            current.startedAt(), current.finishedAt() == null ? Instant.now() : current.finishedAt()).toSeconds();
        return new Progress(current, phase, processed, total, elapsed);
    }
    private void phase(String value) { phase = value; log.info("Sync phase: {}", value); }
    private final ReentrantLock lock = new ReentrantLock();
    private SyncChangeJournal changeJournal;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setChangeJournal(SyncChangeJournal changeJournal) { this.changeJournal = changeJournal; }

    private void auditAfterCommit(String mode, Instant started, List<SyncChangeJournal.Change> changes,
                                  Map<Long, String> names) {
        if (changeJournal != null) changeJournal.appendCommitted(mode, started, changes, names);
    }

    // Timings are diagnostic only: no SQL values, credentials or row content are logged.
    private static long tick() { return System.nanoTime(); }
    private static void timing(String step, long started) {
        log.info("Sync timing: {} = {} ms", step, (System.nanoTime() - started) / 1_000_000);
    }

    private final JdbcTemplate source;
    private final JdbcTemplate destination;
    private final TransactionTemplate sourceTransaction;
    private final TransactionTemplate destinationTransaction;
    private final MyService statistics;
    private final IncrementalPreviewService incrementalPreview;
    private final int maxDeletePercent;
    private final boolean allowEmptySource;

    public SyncService(@Qualifier("fromDataSource") DataSource from,
                       @Qualifier("destinationDataSource") DataSource to,
                       MyService statistics,
                       @Value("${sync.max-delete-percent:20}") int maxDeletePercent,
                       @Value("${sync.allow-empty-source:false}") boolean allowEmptySource) {
        if (maxDeletePercent < 0 || maxDeletePercent > 100) {
            throw new IllegalArgumentException("sync.max-delete-percent must be between 0 and 100");
        }
        this.source = new JdbcTemplate(from);
        this.destination = new JdbcTemplate(to);
        this.statistics = statistics;
        this.incrementalPreview = new IncrementalPreviewService(from, to);
        this.maxDeletePercent = maxDeletePercent;
        this.allowEmptySource = allowEmptySource;
        sourceTransaction = new TransactionTemplate(new JdbcTransactionManager(from));
        sourceTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        sourceTransaction.setReadOnly(true);
        destinationTransaction = new TransactionTemplate(new JdbcTransactionManager(to));
        destinationTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    public Status getStatus() { return status; }

    public void syncNodes() {
        if (!lock.tryLock()) throw new SyncBusyException();
        Instant started = Instant.now();
        status = new Status("RUNNING", started, null, "Reading a consistent source snapshot.");
        processed = 0; total = 0; phase("Checking source and destination connections");
        long syncTick = tick();
        try {
            // A bad configuration must fail before reading or writing the destination.
            long stepTick = tick();
            String sourceUrl = source.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                c -> c.getMetaData().getURL());
            String destinationUrl = destination.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                c -> c.getMetaData().getURL());
            if (sameSqliteFile(sourceUrl, destinationUrl)) {
                throw new SyncSafetyException("Source and destination must be different files");
            }
            timing("Connection checks", stepTick);
            phase("Reading and validating source schema");
            stepTick = tick();
            Map<Table, Map<List<Object>, List<Object>>> snapshot = sourceTransaction.execute(s -> read(source));
            timing("Source snapshot total", stepTick);
            Map<String, Integer> counters = new LinkedHashMap<>();
            List<SyncChangeJournal.Change> audit = new ArrayList<>();
            Map<Long, String> auditNames = new HashMap<>();
            phase("Checking destination schema and preparing transaction");
            stepTick = tick();
            destinationTransaction.executeWithoutResult(s -> {
                long transactionTick = tick();
                long readTick = tick();
                Map<Table, Map<List<Object>, List<Object>>> before = read(destination);
                timing("Destination snapshot before changes", readTick);
                auditNames.putAll(SyncChangeJournal.names(snapshot.get(TABLES.get(0)), before.get(TABLES.get(0))));
                for (Table table : TABLES)
                    audit.addAll(SyncChangeJournal.differences(table.name(), snapshot.get(table), before.get(table)));
                long prepareTick = tick();
                checkDeletes(snapshot.get(TABLES.get(0)), before.get(TABLES.get(0)));
                String product = destination.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                    c -> c.getMetaData().getDatabaseProductName());
                boolean destinationHasMaster = hasMasterColumn(destination, product);
                if (!destinationHasMaster && snapshot.get(TABLES.get(1)).values().stream()
                        .anyMatch(row -> !Objects.equals(canonical(row.get(3)), canonical(0)))) {
                    throw new SyncSafetyException("Destination children.master_id is required to preserve shared nodes");
                }
                total = TABLES.stream().mapToLong(table -> snapshot.get(table).size() + before.get(table).size()).sum();
                timing("Destination safety and metadata checks", prepareTick);
                phase("Applying changes to destination");
                long applyTick = tick();
                // Delete dependants first, then nodes. Insert nodes before dependants.
                List<Table> reversed = new ArrayList<>(TABLES);
                Collections.reverse(reversed);
                for (Table table : reversed) deleteMissing(table, snapshot.get(table), before.get(table), product, counters);
                for (Table table : TABLES) writeChanged(table, snapshot.get(table), before.get(table), product, counters, destinationHasMaster);
                timing("Apply deletes and updates", applyTick);
                phase("Verifying destination content");
                long verifyTick = tick();
                Map<Table, Map<List<Object>, List<Object>>> after = read(destination);
                timing("Destination verification snapshot read", verifyTick);
                long compareTick = tick();
                if (!equalSnapshot(snapshot, after)) {
                    throw new IllegalStateException("Destination content verification failed");
                }
                timing("Destination snapshot comparison", compareTick);
                for (Table table : TABLES) {
                    counters.put("from" + table.countName() + "Count", snapshot.get(table).size());
                    counters.put("destination" + table.countName() + "Count", after.get(table).size());
                }
                timing("Destination transaction callback", transactionTick);
            });
            timing("Destination transaction including commit", stepTick);
            phase("Committed and verified");
            auditAfterCommit("LEGACY_FULL", started, audit, auditNames);
            // Monitoring is separate from the committed data; its failure must not label a commit as a rollback.
            String finalMessage = "Destination committed and content verified.";
            try {
                statistics.resetStatistics();
                counters.forEach((key, value) -> statistics.set(key, value.toString()));
            } catch (RuntimeException e) {
                log.warn("Data committed, but monitoring counters could not be saved", e);
                finalMessage = "Data committed; monitoring counters unavailable.";
            }
            status = new Status("SUCCEEDED", started, Instant.now(), finalMessage);
        } catch (RuntimeException e) {
            phase("Failed");
            status = new Status("FAILED", started, Instant.now(), e instanceof SyncSafetyException
                ? e.getMessage() : "Synchronization failed; check the local application log.");
            log.error("Synchronization did not complete; verify the destination if connection was lost during commit", e);
            throw e;
        } finally {
            timing("Overall sync", syncTick);
            lock.unlock();
        }
    }

    /**
     * Incremental synchronization uses a complete metadata comparison, not MAX(ts_lastsave).
     * The source is authoritative. Dependent content is fetched only for new/changed real nodes.
     * Legacy full synchronization is intentionally unaffected by this method.
     */
    public void syncIncremental() {
        if (!lock.tryLock()) throw new SyncBusyException();
        Instant started = Instant.now();
        status = new Status("RUNNING", started, null, "Checking incremental change candidates.");
        processed = 0; total = 0;
        long startedTick = tick();
        Map<String, Integer> counters = new LinkedHashMap<>();
        List<SyncChangeJournal.Change> audit = new ArrayList<>();
        Map<Long, String> auditNames = new HashMap<>();
        try {
            phase("Incremental: checking database connections");
            String sourceUrl = source.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                c -> c.getMetaData().getURL());
            String destinationUrl = destination.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                c -> c.getMetaData().getURL());
            if (sameSqliteFile(sourceUrl, destinationUrl))
                throw new SyncSafetyException("Source and destination must be different files");

            // Nest destination writes inside a read-only source transaction: all source reads
            // (metadata and scoped content) belong to the same source snapshot.
            sourceTransaction.executeWithoutResult(sourceTx -> {
                phase("Incremental: validating source schema and reading metadata");
                validateIncrementalSchema(source);
                IncrementalPreviewService.Snapshot sourceMeta = incrementalPreview.snapshot(source);
                destinationTransaction.executeWithoutResult(destinationTx -> {
                    phase("Incremental: validating target and comparing metadata");
                    validateIncrementalSchema(destination);
                    IncrementalPreviewService.Snapshot targetMeta = incrementalPreview.snapshot(destination);
                    IncrementalPreviewService.Preview plan = IncrementalPreviewService.analyze(sourceMeta, targetMeta);
                    auditNames.putAll(SyncChangeJournal.names(
                        metadataRows(sourceMeta.nodes(), TABLES.get(0)),
                        metadataRows(targetMeta.nodes(), TABLES.get(0))));
                    checkIncrementalDeletes(plan.removed().size(), targetMeta.nodes().size(), sourceMeta.nodes().size());
                    if (plan.contentAffectedNodes().size() > 1000)
                        throw new SyncSafetyException("Too many content candidates for incremental mode; run Legacy Full Sync");
                    String product = destination.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                        c -> c.getMetaData().getDatabaseProductName());
                    boolean destinationHasMaster = hasMasterColumn(destination, product);
                    if (!destinationHasMaster && sourceMeta.children().values().stream()
                        .anyMatch(row -> row.size() > 3 && !Objects.equals(canonical(row.get(3)), canonical(0))))
                        throw new SyncSafetyException("Destination children.master_id is required to preserve shared nodes");

                    // Only real nodes with changed metadata can require a content transfer.
                    Set<Long> active = new TreeSet<>(plan.contentAffectedNodes());
                    active.removeAll(plan.removed());
                    Map<Table, Map<List<Object>, List<Object>>> fromScoped = new LinkedHashMap<>();
                    Map<Table, Map<List<Object>, List<Object>>> toScoped = new LinkedHashMap<>();
                    for (Table table : TABLES) {
                        if (table.name().equals("children")) {
                            fromScoped.put(table, metadataRows(sourceMeta.children(), table));
                            toScoped.put(table, metadataRows(targetMeta.children(), table));
                        } else if (table.name().equals("bookmark")) {
                            fromScoped.put(table, metadataRows(sourceMeta.bookmarks(), table));
                            toScoped.put(table, metadataRows(targetMeta.bookmarks(), table));
                        } else {
                            fromScoped.put(table, readSelected(source, table, active));
                            toScoped.put(table, readSelected(destination, table, active));
                        }
                    }
                    for (Table table : TABLES) {
                        if (table.name().equals("node")) {
                            audit.addAll(SyncChangeJournal.differences(table.name(),
                                metadataRows(sourceMeta.nodes(), table), metadataRows(targetMeta.nodes(), table)));
                        } else {
                            audit.addAll(SyncChangeJournal.differences(table.name(),
                                fromScoped.get(table), toScoped.get(table)));
                        }
                    }
                    // Real node removals must not download their old dependent BLOB values.
                    // Metadata-only removal and scoped content updates have separate paths.
                    total = 1 + plan.removed().size() * 4L + TABLES.stream()
                        .mapToLong(t -> fromScoped.get(t).size() + toScoped.get(t).size()).sum();
                    phase("Incremental: applying changes to destination");
                    long applyTick = tick();
                    for (Table table : List.of(TABLES.get(5), TABLES.get(4), TABLES.get(3))) {
                        int deleted = deleteByNodeIds(table, plan.removed(), product, audit);
                        counters.put("deleted" + table.counterName(), deleted);
                    }
                    // Remove absent hierarchy/bookmarks before deleting their real node.
                    for (Table table : List.of(TABLES.get(2), TABLES.get(1)))
                        deleteMissing(table, fromScoped.get(table), toScoped.get(table), product, counters);
                    int deletedNodes = deleteByNodeIds(TABLES.get(0), plan.removed(), product, audit);
                    counters.put("deletedNodes", deletedNodes);
                    processed += plan.removed().size() * 4L;
                    // Parent nodes must exist before children or their dependent records.
                    for (Table table : TABLES) {
                        if (!table.name().equals("node") && !table.name().equals("children")
                            && !table.name().equals("bookmark")) {
                            int removedByNode = counters.getOrDefault("deleted" + table.counterName(), 0);
                            deleteMissing(table, fromScoped.get(table), toScoped.get(table), product, counters);
                            counters.merge("deleted" + table.counterName(), removedByNode, Integer::sum);
                        }
                        writeChanged(table, fromScoped.get(table), toScoped.get(table), product, counters, destinationHasMaster);
                    }
                    timing("Incremental apply", applyTick);

                    phase("Incremental: verifying changed rows and all metadata");
                    IncrementalPreviewService.Snapshot afterMeta = incrementalPreview.snapshot(destination);
                    IncrementalPreviewService.Preview remaining = IncrementalPreviewService.analyze(sourceMeta, afterMeta);
                    if (!remaining.metadataAffectedNodes().isEmpty())
                        throw new IllegalStateException("Incremental metadata verification failed");
                    for (Table table : TABLES) {
                        if (table.name().equals("children") || table.name().equals("bookmark")) continue;
                        Map<List<Object>, List<Object>> after = readSelected(destination, table, active);
                        if (!equalTableRows(fromScoped.get(table), after))
                            throw new IllegalStateException("Incremental verification failed: " + table.name());
                    }
                    for (Table table : List.of(TABLES.get(0), TABLES.get(3), TABLES.get(4), TABLES.get(5))) {
                        if (countRowsForNodes(destination, table, plan.removed()) != 0)
                            throw new IllegalStateException("Removed node still has rows in " + table.name());
                    }
                    for (Table table : TABLES) {
                        counters.put("from" + table.countName() + "Count", countTable(source, table));
                        counters.put("destination" + table.countName() + "Count", countTable(destination, table));
                    }
                    processed = total;
                });
            });
            phase("Incremental: committed and verified");
            auditAfterCommit("INCREMENTAL", started, audit, auditNames);
            String finalMessage = "Incremental changes committed; affected rows and all metadata verified. Run Legacy Full Sync for complete repair.";
            try {
                statistics.resetStatistics();
                counters.forEach((key, value) -> statistics.set(key, value.toString()));
            } catch (RuntimeException e) {
                log.warn("Incremental data committed, but monitoring counters could not be saved", e);
                finalMessage = "Incremental changes committed; monitoring counters unavailable.";
            }
            status = new Status("SUCCEEDED", started, Instant.now(), finalMessage);
        } catch (RuntimeException e) {
            phase("Incremental: failed");
            status = new Status("FAILED", started, Instant.now(), e instanceof SyncSafetyException
                ? e.getMessage() : "Incremental synchronization failed; check the local application log.");
            log.error("Incremental synchronization did not complete; verify target if connection was lost during commit", e);
            throw e;
        } finally {
            timing("Incremental overall", startedTick);
            lock.unlock();
        }
    }

    private void checkIncrementalDeletes(int deletes, int targetCount, int sourceCount) {
        if (targetCount == 0) return;
        if (sourceCount == 0 && !allowEmptySource)
            throw new SyncSafetyException("Empty source deletion blocked; sync.allow-empty-source is false");
        if (deletes * 100L > targetCount * (long) maxDeletePercent)
            throw new SyncSafetyException("Deletion exceeds sync.max-delete-percent; no destination changes applied");
    }

    private static Map<List<Object>, List<Object>> metadataRows(Map<Long, List<Object>> rows, Table table) {
        Map<List<Object>, List<Object>> result = new LinkedHashMap<>();
        for (var entry : rows.entrySet()) {
            List<Object> row = new ArrayList<>(entry.getValue());
            if (table.name().equals("children") && row.size() == 3) row.add(0);
            result.put(List.of(canonical(entry.getKey())), row);
        }
        return result;
    }

    /** Avoid retrieving removed BLOB contents; SQL DELETE returns only the number of rows affected. */
    private int deleteByNodeIds(Table table, Set<Long> ids, String product,
                                List<SyncChangeJournal.Change> audit) {
        int count = 0;
        for (Long id : ids) {
            int affected = destination.update("DELETE FROM " + quote(table.name(), product)
                + " WHERE " + quote("node_id", product) + "=?", id);
            count += affected;
            if (affected > 0 && !table.name().equals("node")) {
                // Old attachment payloads are intentionally not fetched; report affected row count.
                audit.add(new SyncChangeJournal.Change(table.name(), "REMOVED_NODE_DEPENDENTS",
                    List.of(id), affected));
            }
        }
        return count;
    }

    private int countRowsForNodes(JdbcTemplate jdbc, Table table, Set<Long> ids) {
        if (ids.isEmpty()) return 0;
        String product = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
            c -> c.getMetaData().getDatabaseProductName());
        int count = 0;
        for (List<Long> batch : idBatches(ids)) {
            String query = "SELECT COUNT(*) FROM " + quote(table.name(), product) + " WHERE "
                + quote("node_id", product) + " IN (" + placeholders(batch.size()) + ")";
            count += Objects.requireNonNull(jdbc.queryForObject(query, Integer.class, batch.toArray()));
        }
        return count;
    }

    private int countTable(JdbcTemplate jdbc, Table table) {
        String product = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
            c -> c.getMetaData().getDatabaseProductName());
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + quote(table.name(), product), Integer.class));
    }

    private static boolean equalTableRows(Map<List<Object>, List<Object>> a, Map<List<Object>, List<Object>> b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (var row : a.entrySet()) if (!equalRow(row.getValue(), b.get(row.getKey()))) return false;
        return true;
    }

    private static String placeholders(int size) { return String.join(",", Collections.nCopies(size, "?")); }

    private static List<List<Long>> idBatches(Set<Long> ids) {
        List<Long> sorted = new ArrayList<>(ids);
        List<List<Long>> batches = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i += 200)
            batches.add(sorted.subList(i, Math.min(i + 200, sorted.size())));
        return batches;
    }

    /** Read whole rows only for selected real node IDs. The old full-read code is unchanged. */
    private Map<List<Object>, List<Object>> readSelected(JdbcTemplate jdbc, Table table, Set<Long> ids) {
        Map<List<Object>, List<Object>> result = new LinkedHashMap<>();
        if (ids.isEmpty()) return result;
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Map<List<Object>, List<Object>>>) c -> {
            String product = c.getMetaData().getDatabaseProductName();
            for (List<Long> batch : idBatches(ids)) {
                String sql = "SELECT * FROM " + quote(table.name(), product) + " WHERE "
                    + quote("node_id", product) + " IN (" + placeholders(batch.size()) + ")";
                try (PreparedStatement st = c.prepareStatement(sql)) {
                    for (int i = 0; i < batch.size(); i++) st.setLong(i + 1, batch.get(i));
                    try (ResultSet rs = st.executeQuery()) {
                        Set<String> actual = new HashSet<>();
                        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                            actual.add(rs.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT));
                        while (rs.next()) {
                            List<Object> values = new ArrayList<>();
                            for (String column : table.columns()) {
                                Object value = column.equals("master_id") && !actual.contains(column) ? 0
                                    : column.equals("png") ? rs.getBytes(column) : rs.getObject(column);
                                if (column.equals("master_id") && value == null) value = 0;
                                if (value instanceof Clob) value = rs.getString(column);
                                values.add(value);
                            }
                            List<Object> key = values.subList(0, table.keys()).stream().map(SyncService::canonical).toList();
                            if (key.stream().anyMatch(Objects::isNull) || result.put(key, values) != null)
                                throw new SyncSafetyException("Null or duplicate key in " + table.name());
                        }
                    }
                }
            }
            return result;
        });
    }

    /** Validates every table even when no node IDs qualify for scoped content reads. */
    private void validateIncrementalSchema(JdbcTemplate jdbc) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c -> {
            String product = c.getMetaData().getDatabaseProductName();
            for (Table table : TABLES) {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM " + quote(table.name(), product) + " WHERE 1=0")) {
                    Set<String> columns = new HashSet<>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                        columns.add(rs.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT));
                    Set<String> expected = new HashSet<>(table.columns());
                    if (table.name().equals("children") && !columns.contains("master_id")) expected.remove("master_id");
                    if (!columns.equals(expected)) throw new SyncSafetyException("Unsupported schema for table " + table.name());
                }
                if ("MySQL".equals(product)) {
                    String engine = jdbc.queryForObject(
                        "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",
                        String.class, table.name());
                    if (!"InnoDB".equalsIgnoreCase(engine))
                        throw new SyncSafetyException("MySQL tables must use InnoDB for rollback support");
                }
            }
            return null;
        });
    }

    public static boolean sameSqliteFile(String a, String b) {
        if (a == null || b == null || !a.startsWith("jdbc:sqlite:") || !b.startsWith("jdbc:sqlite:")) return false;
        try {
            return sqlitePath(a).equals(sqlitePath(b)) || java.nio.file.Files.isSameFile(sqlitePath(a), sqlitePath(b));
        } catch (java.io.IOException e) { return sqlitePath(a).equals(sqlitePath(b)); }
    }
    public static java.nio.file.Path sqlitePath(String url) {
        String path = url.substring("jdbc:sqlite:".length()).split("\\?", 2)[0];
        return (path.startsWith("file:") ? java.nio.file.Path.of(java.net.URI.create(path)) : java.nio.file.Path.of(path))
            .toAbsolutePath().normalize();
    }

    private Map<Table, Map<List<Object>, List<Object>>> read(JdbcTemplate jdbc) {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Map<Table, Map<List<Object>, List<Object>>>>) c -> {
            Map<Table, Map<List<Object>, List<Object>>> tables = new LinkedHashMap<>();
            String product = c.getMetaData().getDatabaseProductName();
            if ("MySQL".equals(product)) {
                for (Table table : TABLES) {
                    String engine = jdbc.queryForObject(
                        "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",
                        String.class, table.name());
                    if (!"InnoDB".equalsIgnoreCase(engine)) {
                        throw new SyncSafetyException("MySQL tables must use InnoDB for consistent reads and rollback support");
                    }
                }
            }
            for (Table table : TABLES) {
                long tableTick = tick();
                Map<List<Object>, List<Object>> rows = new LinkedHashMap<>();
                long blobBytes = 0;
                try (Statement statement = c.createStatement();
                     ResultSet rs = statement.executeQuery("SELECT * FROM " + quote(table.name(), product))) {
                    Set<String> actual = new HashSet<>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        actual.add(rs.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT));
                    }
                    Set<String> expected = new HashSet<>(table.columns());
                    if (table.name().equals("children") && !actual.contains("master_id")) expected.remove("master_id");
                    if (!actual.equals(expected)) {
                        throw new SyncSafetyException("Unsupported schema for table " + table.name());
                    }
                    while (rs.next()) {
                        List<Object> values = new ArrayList<>();
                        for (String column : table.columns()) {
                            Object value = column.equals("master_id") && !actual.contains(column) ? 0
                                : column.equals("png") ? rs.getBytes(column) : rs.getObject(column);
                            if (column.equals("master_id") && value == null) value = 0;
                            if (value instanceof Clob) value = rs.getString(column);
                            if (column.equals("png") && value instanceof byte[] bytes) blobBytes += bytes.length;
                            values.add(value);
                        }
                        List<Object> key = values.subList(0, table.keys()).stream().map(SyncService::canonical).toList();
                        if (key.stream().anyMatch(Objects::isNull) || rows.put(key, values) != null) {
                            throw new SyncSafetyException("Null or duplicate key in " + table.name());
                        }
                    }
                }
                tables.put(table, rows);
                long elapsedMs = (System.nanoTime() - tableTick) / 1_000_000;
                log.info("Sync timing: {} snapshot table {} = {} ms ({} rows)",
                    jdbc == source ? "source" : "destination", table.name(), elapsedMs, rows.size());
                if (table.name().equals("image")) {
                    // Count bytes already read by the full-sync algorithm; do not issue additional SQL.
                    // This includes attachments stored in image.png and excludes JDBC protocol overhead.
                    log.info("Sync timing: {} image payload = {} MiB, {} MiB/s ({} rows)",
                        jdbc == source ? "source" : "destination",
                        String.format(Locale.ROOT, "%.2f", blobBytes / 1048576.0),
                        String.format(Locale.ROOT, "%.2f", elapsedMs == 0 ? 0.0 : blobBytes * 1000.0 / (1048576.0 * elapsedMs)),
                        rows.size());
                }
            }
            return tables;
        });
    }

    private void checkDeletes(Map<List<Object>, List<Object>> from, Map<List<Object>, List<Object>> to) {
        if (to.isEmpty()) return;
        if (from.isEmpty() && !allowEmptySource) throw new SyncSafetyException("Empty source deletion blocked; sync.allow-empty-source is false");
        long deletes = to.keySet().stream().filter(k -> !from.containsKey(k)).count();
        if (deletes * 100L > (long) to.size() * maxDeletePercent) {
            throw new SyncSafetyException("Deletion exceeds sync.max-delete-percent (" + maxDeletePercent + "%); no destination changes applied");
        }
    }

    private void deleteMissing(Table table, Map<List<Object>, List<Object>> from,
                               Map<List<Object>, List<Object>> to, String product, Map<String, Integer> counters) {
        int count = 0;
        for (var entry : to.entrySet()) {
            if (!from.containsKey(entry.getKey())) {
                destination.update("DELETE FROM " + quote(table.name(), product) + where(table, product),
                    entry.getValue().subList(0, table.keys()).toArray());
                count++;
            }
        }
        processed += to.size();
        counters.put("deleted" + table.counterName(), count);
    }

    private void writeChanged(Table table, Map<List<Object>, List<Object>> from,
                              Map<List<Object>, List<Object>> to, String product, Map<String, Integer> counters, boolean hasMaster) {
        int created = 0, updated = 0;
        for (var entry : from.entrySet()) {
            List<String> writeColumns = table.columns().stream()
                .filter(c -> hasMaster || !table.name().equals("children") || !c.equals("master_id")).toList();
            List<Object> values = writeColumns.stream().map(c -> entry.getValue().get(table.columns().indexOf(c))).toList();
            if (!to.containsKey(entry.getKey())) {
                String columns = String.join(",", writeColumns.stream().map(c -> quote(c, product)).toList());
                String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
                destination.update("INSERT INTO " + quote(table.name(), product) + " (" + columns + ") VALUES (" + placeholders + ")", values.toArray());
                created++;
            } else if (!equalRow(entry.getValue(), to.get(entry.getKey()))) {
                List<String> columns = writeColumns.subList(table.keys(), writeColumns.size());
                String assignments = String.join(",", columns.stream().map(c -> quote(c, product) + "=?").toList());
                List<Object> args = new ArrayList<>(values.subList(table.keys(), values.size()));
                args.addAll(values.subList(0, table.keys()));
                destination.update("UPDATE " + quote(table.name(), product) + " SET " + assignments + where(table, product), args.toArray());
                updated++;
            }
        }
        processed += from.size();
        counters.put("created" + table.counterName(), created);
        counters.put("updated" + table.counterName(), updated);
    }

    private static boolean hasMasterColumn(JdbcTemplate jdbc, String product) {
        return Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) c -> {
            try (Statement statement = c.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT * FROM " + quote("children", product) + " WHERE 1=0")) {
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    if (rs.getMetaData().getColumnName(i).equalsIgnoreCase("master_id")) return true;
                }
                return false;
            }
        }));
    }

    private static String where(Table table, String product) {
        return " WHERE " + String.join(" AND ", table.columns().subList(0, table.keys()).stream().map(c -> quote(c, product) + "=?").toList());
    }
    private static String quote(String identifier, String product) {
        if (product.equals("MySQL")) return "`" + identifier + "`";
        if (product.equals("Microsoft SQL Server")) return "[" + identifier + "]";
        return "\"" + identifier + "\"";
    }
    private static Object canonical(Object value) {
        if (value instanceof Number) return new BigDecimal(value.toString()).stripTrailingZeros();
        return value;
    }
    static boolean equalRow(List<Object> a, List<Object> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.deepEquals(canonical(a.get(i)), canonical(b.get(i)))) return false;
        }
        return true;
    }
    private static boolean equalSnapshot(Map<Table, Map<List<Object>, List<Object>>> a, Map<Table, Map<List<Object>, List<Object>>> b) {
        for (Table table : TABLES) {
            if (!a.get(table).keySet().equals(b.get(table).keySet())) return false;
            for (var row : a.get(table).entrySet()) if (!equalRow(row.getValue(), b.get(table).get(row.getKey()))) return false;
        }
        return true;
    }
    public static class SyncSafetyException extends IllegalStateException {
        public SyncSafetyException(String message) { super(message); }
    }
    public static class SyncBusyException extends RuntimeException {
        public SyncBusyException() { super("A synchronization is already running"); }
    }
}
