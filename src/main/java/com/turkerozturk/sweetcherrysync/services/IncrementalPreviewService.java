package com.turkerozturk.sweetcherrysync.services;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Read-only candidate discovery. Never treats a preview as a verified synchronization. */
@Service
public class IncrementalPreviewService {
    private final JdbcTemplate source;
    private final JdbcTemplate destination;

    public IncrementalPreviewService(@Qualifier("fromDataSource") DataSource from,
                                     @Qualifier("destinationDataSource") DataSource to) {
        source = new JdbcTemplate(from);
        destination = new JdbcTemplate(to);
    }

    public record Preview(int sourceNodes, int destinationNodes, Set<Long> added,
                          Set<Long> removed, Set<Long> changed, Set<Long> childrenChanged,
                          boolean bookmarksChanged, Set<Long> affectedNodes, String warning) {}

    public Preview preview() {
        String sourceUrl = source.execute((ConnectionCallback<String>) c -> c.getMetaData().getURL());
        String destinationUrl = destination.execute((ConnectionCallback<String>) c -> c.getMetaData().getURL());
        if (SyncService.sameSqliteFile(sourceUrl, destinationUrl))
            throw new SyncService.SyncSafetyException("Source and destination must be different files");
        Snapshot a = snapshot(source);
        Snapshot b = snapshot(destination);
        Set<Long> added = difference(a.nodes.keySet(), b.nodes.keySet());
        Set<Long> removed = difference(b.nodes.keySet(), a.nodes.keySet());
        Set<Long> changed = changed(a.nodes, b.nodes);
        Set<Long> childrenChanged = changed(a.children, b.children);
        boolean bookmarksChanged = !a.bookmarks.equals(b.bookmarks);
        Set<Long> affected = new TreeSet<>();
        affected.addAll(added);
        affected.addAll(removed);
        affected.addAll(changed);
        affected.addAll(childrenChanged);
        return new Preview(a.nodes.size(), b.nodes.size(), added, removed, changed,
            childrenChanged, bookmarksChanged, affected,
            "Preview only: no writes, no BLOB inspection. Changes to attachments without node metadata changes may be missed. Run legacy Full Sync for complete verification.");
    }

    static Set<Long> difference(Set<Long> a, Set<Long> b) {
        Set<Long> result = new TreeSet<>(a);
        result.removeAll(b);
        return result;
    }

    static Set<Long> changed(Map<Long, List<Object>> a, Map<Long, List<Object>> b) {
        Set<Long> result = new TreeSet<>();
        for (var e : a.entrySet()) {
            if (b.containsKey(e.getKey()) && !SyncService.equalRow(e.getValue(), b.get(e.getKey())))
                result.add(e.getKey());
        }
        return result;
    }

    private record Snapshot(Map<Long, List<Object>> nodes, Map<Long, List<Object>> children,
                            Map<Long, List<Object>> bookmarks) {}

    private Snapshot snapshot(JdbcTemplate jdbc) {
        return jdbc.execute((ConnectionCallback<Snapshot>) c -> {
            String product = c.getMetaData().getDatabaseProductName();
            // The node and children metadata is intentionally read without image/grid/codebox BLOBs.
            Map<Long, List<Object>> nodes = read(c, "node", "node_id", List.of(
                "node_id", "name", "syntax", "tags", "is_ro", "is_richtxt", "has_codebox",
                "has_table", "has_image", "level", "ts_creation", "ts_lastsave"), product);
            List<String> childrenColumns = new ArrayList<>(List.of("node_id", "father_id", "sequence"));
            if (hasColumn(c, "children", "master_id")) childrenColumns.add("master_id");
            Map<Long, List<Object>> children = read(c, "children", "node_id", childrenColumns, product);
            Map<Long, List<Object>> bookmarks = read(c, "bookmark", "node_id", List.of("node_id", "sequence"), product);
            return new Snapshot(nodes, children, bookmarks);
        });
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (ResultSet rs = c.getMetaData().getColumns(null, null, table, column)) {
            if (rs.next()) return true;
        }
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM " + table + " WHERE 1=0")) {
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
                if (rs.getMetaData().getColumnName(i).equalsIgnoreCase(column)) return true;
        }
        return false;
    }

    private static Map<Long, List<Object>> read(Connection c, String table, String key,
                                                List<String> columns, String product) throws SQLException {
        String quote = product.equals("MySQL") ? "`" : product.equals("Microsoft SQL Server") ? "[" : "\"";
        String close = quote.equals("[") ? "]" : quote;
        String sql = "SELECT " + String.join(",", columns.stream().map(s -> quote + s + close).toList())
            + " FROM " + quote + table + close;
        Map<Long, List<Object>> rows = new TreeMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                long id = rs.getLong(key);
                if (rs.wasNull()) throw new SyncService.SyncSafetyException("Null key in " + table);
                List<Object> values = new ArrayList<>();
                for (String column : columns) {
                    Object value = rs.getObject(column);
                    if (value instanceof Clob) value = rs.getString(column);
                    if (column.equals("master_id") && value == null) value = 0;
                    values.add(value);
                }
                if (rows.put(id, values) != null)
                    throw new SyncService.SyncSafetyException("Duplicate key in " + table);
            }
        }
        return rows;
    }
}
