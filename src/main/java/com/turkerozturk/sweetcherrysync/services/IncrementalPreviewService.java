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

    /**
     * Preview deliberately separates hierarchy changes from content changes.
     * A timestamp is diagnostic information, never the sole inclusion criterion.
     * "removed" means an absent real node, not merely a removed shared-node link.
     */
    public record Preview(int sourceNodes, int destinationNodes, Set<Long> added,
                          Set<Long> removed, Set<Long> changed, Set<Long> childrenChanged,
                          boolean bookmarksChanged, Set<Long> affectedNodes, String warning,
                          Set<Long> childrenAdded, Set<Long> childrenRemoved,
                          Set<Long> bookmarksAdded, Set<Long> bookmarksRemoved,
                          Set<Long> bookmarksModified, Set<Long> metadataAffectedNodes,
                          Set<Long> contentAffectedNodes, Long destinationLatestModified,
                          Set<Long> timestampCandidates) {}

    public Preview preview() {
        String sourceUrl = source.execute((ConnectionCallback<String>) c -> c.getMetaData().getURL());
        String destinationUrl = destination.execute((ConnectionCallback<String>) c -> c.getMetaData().getURL());
        if (SyncService.sameSqliteFile(sourceUrl, destinationUrl))
            throw new SyncService.SyncSafetyException("Source and destination must be different files");
        // Read-only snapshots: node, children and bookmark, without BLOB tables.
        Snapshot a = snapshot(source);
        Snapshot b = snapshot(destination);
        return analyze(a, b);
    }

    static Preview analyze(Snapshot a, Snapshot b) {
        Set<Long> added = difference(a.nodes.keySet(), b.nodes.keySet());
        Set<Long> removed = difference(b.nodes.keySet(), a.nodes.keySet());
        Set<Long> changed = changed(a.nodes, b.nodes);
        Set<Long> childrenAdded = difference(a.children.keySet(), b.children.keySet());
        Set<Long> childrenRemoved = difference(b.children.keySet(), a.children.keySet());
        Set<Long> childrenChanged = changed(a.children, b.children);
        Set<Long> bookmarksAdded = difference(a.bookmarks.keySet(), b.bookmarks.keySet());
        Set<Long> bookmarksRemoved = difference(b.bookmarks.keySet(), a.bookmarks.keySet());
        Set<Long> bookmarksModified = changed(a.bookmarks, b.bookmarks);
        boolean bookmarksChanged = !bookmarksAdded.isEmpty() || !bookmarksRemoved.isEmpty()
            || !bookmarksModified.isEmpty();

        // Deleted real nodes need dependent-row removal. A moved/reordered child
        // does not need an image/grid/codebox read unless its node metadata changed.
        Set<Long> contentAffected = union(added, removed, changed);
        Set<Long> metadataAffected = union(contentAffected, childrenAdded, childrenRemoved,
            childrenChanged, bookmarksAdded, bookmarksRemoved, bookmarksModified);

        // ts_lastsave is the LAST item in the explicitly selected node columns.
        // The maximum target timestamp is only advisory: older nodes can be edited,
        // and deletes/bookmark reorders do not necessarily update timestamps.
        Long latest = latestModified(b.nodes);
        Set<Long> timestampCandidates = new TreeSet<>();
        if (latest != null) for (var row : a.nodes.entrySet()) {
            Long timestamp = asTimestamp(row.getValue().get(row.getValue().size() - 1));
            if (timestamp != null && timestamp >= latest) timestampCandidates.add(row.getKey());
        }
        return new Preview(a.nodes.size(), b.nodes.size(), added, removed, changed,
            childrenChanged, bookmarksChanged, metadataAffected,
            "Read-only preview: no writes or BLOB reads. Timestamps are advisory, not a safe cutoff. "
                + "Changes made only to dependent records without updating node metadata, or direct "
                + "tampering with unchanged target BLOBs, cannot be detected. Use Legacy Full Sync for verification.",
            childrenAdded, childrenRemoved, bookmarksAdded, bookmarksRemoved, bookmarksModified,
            metadataAffected, contentAffected, latest, timestampCandidates);
    }

    @SafeVarargs
    private static Set<Long> union(Set<Long>... sets) {
        Set<Long> combined = new TreeSet<>();
        for (Set<Long> set : sets) combined.addAll(set);
        return combined;
    }

    private static Long latestModified(Map<Long, List<Object>> nodes) {
        Long latest = null;
        for (List<Object> row : nodes.values()) {
            Long value = asTimestamp(row.get(row.size() - 1));
            if (value != null && (latest == null || value > latest)) latest = value;
        }
        return latest;
    }

    private static Long asTimestamp(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        throw new SyncService.SyncSafetyException("Unsupported ts_lastsave data type");
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

    record Snapshot(Map<Long, List<Object>> nodes, Map<Long, List<Object>> children,
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
