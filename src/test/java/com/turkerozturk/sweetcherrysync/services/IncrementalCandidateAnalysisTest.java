package com.turkerozturk.sweetcherrysync.services;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalCandidateAnalysisTest {
    // Only the last column is significant for timestamp checks in these fixtures.
    private static List<Object> node(long id, String name, long modified) {
        return Arrays.asList(id, name, modified);
    }
    private static IncrementalPreviewService.Snapshot snapshot(
        Map<Long, List<Object>> nodes, Map<Long, List<Object>> children,
        Map<Long, List<Object>> bookmarks) {
        return new IncrementalPreviewService.Snapshot(nodes, children, bookmarks);
    }

    @Test void detectsOldTimestampEditEvenWhenBelowDestinationMaximum() {
        var source = snapshot(Map.of(1L, node(1, "edited", 15), 2L, node(2, "newest", 100)), Map.of(), Map.of());
        var target = snapshot(Map.of(1L, node(1, "original", 10), 2L, node(2, "newest", 100)), Map.of(), Map.of());
        var result = IncrementalPreviewService.analyze(source, target);
        assertEquals(Set.of(1L), result.changed());
        assertEquals(Set.of(1L), result.contentAffectedNodes());
        assertEquals(100L, result.destinationLatestModified());
        assertEquals(Set.of(2L), result.timestampCandidates());
    }

    @Test void childDeletionOrMoveDoesNotAutomaticallyTriggerBlobRead() {
        var nodes = Map.of(10L, node(10, "parent", 10), 20L, node(20, "shared", 10));
        var source = snapshot(nodes, Map.of(10L, List.of(10, 1, 0, 0)), Map.of());
        var target = snapshot(nodes, Map.of(10L, List.of(10, 2, 0, 0), 20L, List.of(20, 1, 0, 10)), Map.of());
        var result = IncrementalPreviewService.analyze(source, target);
        assertEquals(Set.of(20L), result.childrenRemoved());
        assertEquals(Set.of(10L), result.childrenChanged());
        assertEquals(Set.of(), result.removed());
        assertTrue(result.contentAffectedNodes().isEmpty());
        assertEquals(Set.of(10L, 20L), result.metadataAffectedNodes());
    }

    @Test void deletedRealNodeRequiresDependentDeletion() {
        var source = snapshot(Map.of(), Map.of(), Map.of());
        var target = snapshot(Map.of(8L, node(8, "deleted", 20)), Map.of(8L, List.of(8, 0, 0)), Map.of());
        var result = IncrementalPreviewService.analyze(source, target);
        assertEquals(Set.of(8L), result.removed());
        assertEquals(Set.of(8L), result.contentAffectedNodes());
    }

    @Test void bookmarkReorderAndDeletionAreDetectedWithoutNodeDateChange() {
        var nodes = Map.of(1L, node(1, "same", 5));
        var source = snapshot(nodes, Map.of(), Map.of(1L, List.of(1, 3)));
        var target = snapshot(nodes, Map.of(), Map.of(1L, List.of(1, 1), 2L, List.of(2, 2)));
        var result = IncrementalPreviewService.analyze(source, target);
        assertTrue(result.bookmarksChanged());
        assertEquals(Set.of(2L), result.bookmarksRemoved());
        assertEquals(Set.of(1L), result.bookmarksModified());
        assertTrue(result.contentAffectedNodes().isEmpty());
    }

    @Test void sameTimestampDifferentMetadataIsStillDetected() {
        var source = snapshot(Map.of(4L, node(4, "renamed", 99)), Map.of(), Map.of());
        var target = snapshot(Map.of(4L, node(4, "old", 99)), Map.of(), Map.of());
        assertEquals(Set.of(4L), IncrementalPreviewService.analyze(source, target).changed());
    }
}
