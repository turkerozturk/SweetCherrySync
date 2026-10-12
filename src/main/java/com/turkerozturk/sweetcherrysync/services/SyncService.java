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
    private final ReentrantLock lock = new ReentrantLock();
    private final JdbcTemplate source;
    private final JdbcTemplate destination;
    private final TransactionTemplate sourceTransaction;
    private final TransactionTemplate destinationTransaction;
    private final MyService statistics;
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
        try {
            // A bad configuration must fail before reading or writing the destination.
            String sourceUrl = source.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                c -> c.getMetaData().getURL());
            String destinationUrl = destination.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                c -> c.getMetaData().getURL());
            if (sameSqliteFile(sourceUrl, destinationUrl)) {
                throw new SyncSafetyException("Source and destination must be different files");
            }
            Map<Table, Map<List<Object>, List<Object>>> snapshot = sourceTransaction.execute(s -> read(source));
            Map<String, Integer> counters = new LinkedHashMap<>();
            destinationTransaction.executeWithoutResult(s -> {
                Map<Table, Map<List<Object>, List<Object>>> before = read(destination);
                checkDeletes(snapshot.get(TABLES.get(0)), before.get(TABLES.get(0)));
                String product = destination.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                    c -> c.getMetaData().getDatabaseProductName());
                boolean destinationHasMaster = hasMasterColumn(destination, product);
                if (!destinationHasMaster && snapshot.get(TABLES.get(1)).values().stream()
                        .anyMatch(row -> !Objects.equals(canonical(row.get(3)), canonical(0)))) {
                    throw new SyncSafetyException("Destination children.master_id is required to preserve shared nodes");
                }
                // Delete dependants first, then nodes. Insert nodes before dependants.
                List<Table> reversed = new ArrayList<>(TABLES);
                Collections.reverse(reversed);
                for (Table table : reversed) deleteMissing(table, snapshot.get(table), before.get(table), product, counters);
                for (Table table : TABLES) writeChanged(table, snapshot.get(table), before.get(table), product, counters, destinationHasMaster);
                Map<Table, Map<List<Object>, List<Object>>> after = read(destination);
                if (!equalSnapshot(snapshot, after)) {
                    throw new IllegalStateException("Destination content verification failed");
                }
                for (Table table : TABLES) {
                    counters.put("from" + table.countName() + "Count", snapshot.get(table).size());
                    counters.put("destination" + table.countName() + "Count", after.get(table).size());
                }
            });
            status = new Status("SUCCEEDED", started, Instant.now(), "Destination committed and content verified.");
            // Monitoring is separate from the committed data; its failure must not label a commit as a rollback.
            try {
                statistics.resetStatistics();
                counters.forEach((key, value) -> statistics.set(key, value.toString()));
            } catch (RuntimeException e) {
                log.warn("Data committed, but monitoring counters could not be saved", e);
                status = new Status("SUCCEEDED", started, status.finishedAt(), "Data committed; monitoring counters unavailable.");
            }
        } catch (RuntimeException e) {
            status = new Status("FAILED", started, Instant.now(), e instanceof SyncSafetyException
                ? e.getMessage() : "Synchronization failed; check the local application log.");
            log.error("Synchronization did not complete; verify the destination if connection was lost during commit", e);
            throw e;
        } finally {
            lock.unlock();
        }
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
                Map<List<Object>, List<Object>> rows = new LinkedHashMap<>();
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
                            values.add(value);
                        }
                        List<Object> key = values.subList(0, table.keys()).stream().map(SyncService::canonical).toList();
                        if (key.stream().anyMatch(Objects::isNull) || rows.put(key, values) != null) {
                            throw new SyncSafetyException("Null or duplicate key in " + table.name());
                        }
                    }
                }
                tables.put(table, rows);
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
