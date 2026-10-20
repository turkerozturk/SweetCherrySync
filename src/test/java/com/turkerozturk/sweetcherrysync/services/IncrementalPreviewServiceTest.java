package com.turkerozturk.sweetcherrysync.services;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalPreviewServiceTest {
    @Test void detectsAdditionsRemovalsAndMetadataChanges() {
        assertEquals(Set.of(3L), IncrementalPreviewService.difference(Set.of(1L, 3L), Set.of(1L, 2L)));
        Map<Long, List<Object>> source = Map.of(1L, List.of(1, "new"), 3L, List.of(3, "added"));
        Map<Long, List<Object>> target = Map.of(1L, List.of(1L, "old"), 2L, List.of(2, "removed"));
        assertEquals(Set.of(1L), IncrementalPreviewService.changed(source, target));
    }
    @Test void equivalentNumericTypesAreNotChanges() {
        assertTrue(IncrementalPreviewService.changed(Map.of(1L, List.of(1)), Map.of(1L, List.of(1L))).isEmpty());
    }
}
