package com.turkerozturk.sweetcherrysync.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SyncChangeJournalTest {
    @TempDir Path directory;

    @Test void writesOnlyCommittedChangesAsJsonLinesWithoutRowContents() throws Exception {
        Path file = directory.resolve("audit/changes.jsonl");
        ObjectMapper mapper = new ObjectMapper();
        SyncChangeJournal journal = new SyncChangeJournal(true, file.toString(), mapper);
        Map<List<Object>, List<Object>> source = new LinkedHashMap<>();
        Map<List<Object>, List<Object>> target = new LinkedHashMap<>();
        source.put(List.of(1), List.of(1, "changed", "private body"));
        source.put(List.of(2), List.of(2, "new", "attachment contents"));
        target.put(List.of(1), List.of(1, "old", "private body"));
        target.put(List.of(3), List.of(3, "removed", "private body"));
        List<SyncChangeJournal.Change> changes = SyncChangeJournal.differences("node", source, target);
        assertEquals(3, changes.size());
        journal.appendCommitted("INCREMENTAL", Instant.parse("2026-10-20T12:00:00Z"), changes,
            SyncChangeJournal.names(source, target));
        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertTrue(lines.stream().noneMatch(s -> s.contains("private body") || s.contains("attachment contents")));
        assertTrue(lines.stream().anyMatch(s -> s.contains("\"action\":\"CREATED\"")));
        assertTrue(lines.stream().anyMatch(s -> s.contains("\"action\":\"UPDATED\"")));
        assertTrue(lines.stream().anyMatch(s -> s.contains("\"action\":\"DELETED\"")));
        JsonNode first = mapper.readTree(lines.get(0));
        assertEquals("INCREMENTAL", first.get("mode").asText());
        assertNotNull(first.get("committedAt"));
        assertNotNull(first.get("runId"));
    }

    @Test void disabledAuditDoesNotWriteFile() {
        Path file = directory.resolve("disabled.jsonl");
        SyncChangeJournal journal = new SyncChangeJournal(false, file.toString(), new ObjectMapper());
        journal.appendCommitted("INCREMENTAL", Instant.now(),
            List.of(new SyncChangeJournal.Change("node", "CREATED", List.of(1))), Map.of(1L, "test"));
        assertFalse(Files.exists(file));
    }
}
