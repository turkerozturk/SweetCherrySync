package com.turkerozturk.sweetcherrysync.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;

/** Append-only, post-commit audit. Never writes CTB content or BLOB bytes. */
@Component
public class SyncChangeJournal {
    private static final Logger log = LoggerFactory.getLogger(SyncChangeJournal.class);

    public record Change(String table, String action, List<Object> key, Integer affectedRows) {
        public Change(String table, String action, List<Object> key) {
            this(table, action, key, null);
        }
        public long nodeId() { return ((Number) key.get(0)).longValue(); }
    }

    private final boolean enabled;
    private final Path path;
    private final ObjectMapper mapper;

    public SyncChangeJournal(@Value("${sync.audit.enabled:true}") boolean enabled,
                             @Value("${sync.audit.path:./logs/sync-node-changes.jsonl}") String path,
                             ObjectMapper mapper) {
        this.enabled = enabled;
        this.path = Path.of(path).toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    /** Compare only keys and values already loaded by the sync; no extra SQL or BLOB downloads. */
    static List<Change> differences(String table,
                                    Map<List<Object>, List<Object>> source,
                                    Map<List<Object>, List<Object>> target) {
        List<Change> changes = new ArrayList<>();
        for (var entry : target.entrySet()) {
            if (!source.containsKey(entry.getKey()))
                changes.add(new Change(table, "DELETED", List.copyOf(entry.getKey())));
        }
        for (var entry : source.entrySet()) {
            List<Object> old = target.get(entry.getKey());
            if (old == null) changes.add(new Change(table, "CREATED", List.copyOf(entry.getKey())));
            else if (!SyncService.equalRow(entry.getValue(), old))
                changes.add(new Change(table, "UPDATED", List.copyOf(entry.getKey())));
        }
        return changes;
    }

    /** Called only after the destination transaction has returned successfully. */
    public void appendCommitted(String mode, Instant started, List<Change> changes,
                                Map<Long, String> nodeNames) {
        if (!enabled || changes.isEmpty()) return;
        String runId = UUID.randomUUID().toString();
        Instant committedAt = Instant.now();
        StringBuilder lines = new StringBuilder();
        try {
            for (Change change : changes) {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("committedAt", committedAt.toString());
                event.put("startedAt", started.toString());
                event.put("runId", runId);
                event.put("mode", mode);
                event.put("table", change.table());
                event.put("action", change.action());
                event.put("nodeId", change.nodeId());
                event.put("nodeName", nodeNames.get(change.nodeId()));
                event.put("key", change.key());
                if (change.affectedRows() != null) event.put("affectedRows", change.affectedRows());
                lines.append(mapper.writeValueAsString(event)).append('\n');
            }
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, lines.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("Sync audit: {} committed changes appended to {}", changes.size(), path);
        } catch (IOException | RuntimeException ex) {
            // A logging failure after commit must not be misreported as a transaction rollback.
            log.warn("Destination committed, but the node-change audit could not be written to {}", path, ex);
        }
    }

    static Map<Long, String> names(Map<List<Object>, List<Object>> sourceNodes,
                                   Map<List<Object>, List<Object>> targetNodes) {
        Map<Long, String> names = new HashMap<>();
        for (List<Object> row : targetNodes.values()) names.put(((Number) row.get(0)).longValue(), (String) row.get(1));
        for (List<Object> row : sourceNodes.values()) names.put(((Number) row.get(0)).longValue(), (String) row.get(1));
        return names;
    }
}
