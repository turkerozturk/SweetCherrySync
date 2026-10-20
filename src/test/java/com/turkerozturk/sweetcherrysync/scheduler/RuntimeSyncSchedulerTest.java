package com.turkerozturk.sweetcherrysync.scheduler;

import com.turkerozturk.sweetcherrysync.controllers.SyncProgressController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeSyncSchedulerTest {
    @Test void disabledByDefaultAndInvalidIntervalRejected() {
        var controller = mock(SyncProgressController.class);
        var scheduler = new RuntimeSyncScheduler(controller, false, "INCREMENTAL", 300);
        scheduler.tick();
        verifyNoInteractions(controller);
        assertFalse(scheduler.get().enabled());
        assertEquals(400, scheduler.update(new RuntimeSyncScheduler.Update(true, "INCREMENTAL", 2)).getStatusCode().value());
        assertFalse(scheduler.get().enabled());
    }

    @Test void scheduledJobsUseSameQueueAndBusyJobsAreRetried() throws Exception {
        var controller = mock(SyncProgressController.class);
        when(controller.schedule("INCREMENTAL")).thenReturn(false, true);
        var scheduler = new RuntimeSyncScheduler(controller, false, "INCREMENTAL", 60);
        assertEquals(200, scheduler.update(new RuntimeSyncScheduler.Update(true, "INCREMENTAL", 30)).getStatusCode().value());
        Field next = RuntimeSyncScheduler.class.getDeclaredField("nextRunAt");
        next.setAccessible(true);
        next.set(scheduler, Instant.now().minusSeconds(1));
        scheduler.tick();
        verify(controller, times(1)).schedule("INCREMENTAL");
        assertTrue(scheduler.get().nextRunAt().isAfter(Instant.now()));
        next.set(scheduler, Instant.now().minusSeconds(1));
        scheduler.tick();
        verify(controller, times(2)).schedule("INCREMENTAL");
        assertEquals(200, scheduler.update(new RuntimeSyncScheduler.Update(false, "LEGACY_FULL", 600)).getStatusCode().value());
        assertNull(scheduler.get().nextRunAt());
    }
}
