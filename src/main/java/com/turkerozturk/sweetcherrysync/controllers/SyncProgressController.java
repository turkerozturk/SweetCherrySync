package com.turkerozturk.sweetcherrysync.controllers;

import com.turkerozturk.sweetcherrysync.services.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Nonblocking web entry point; existing /sync endpoint remains backward compatible. */
@RestController
public class SyncProgressController {
    private final SyncService service;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sweetcherrysync-web-job");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean queued = new AtomicBoolean();

    public SyncProgressController(SyncService service) { this.service = service; }

    @GetMapping("/sync/progress")
    public SyncService.Progress progress() { return service.getProgress(); }

    @PostMapping("/sync/start")
    public ResponseEntity<SyncService.Progress> start() {
        if ("RUNNING".equals(service.getStatus().state()) || !queued.compareAndSet(false, true))
            return ResponseEntity.status(409).body(service.getProgress());
        try {
            executor.execute(() -> {
                try { service.syncNodes(); }
                catch (RuntimeException ignored) { /* SyncService records and logs the failure. */ }
                finally { queued.set(false); }
            });
            return ResponseEntity.accepted().body(service.getProgress());
        } catch (RejectedExecutionException ex) {
            queued.set(false);
            return ResponseEntity.status(503).body(service.getProgress());
        }
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() { executor.shutdown(); }
}
