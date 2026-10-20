package com.turkerozturk.sweetcherrysync.controllers;

import com.turkerozturk.sweetcherrysync.services.SyncService;
import com.turkerozturk.sweetcherrysync.my.services.MyService;
import com.turkerozturk.sweetcherrysync.my.enums.Statistics;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Nonblocking web entry point; existing /sync endpoint remains backward compatible. */
@RestController
public class SyncProgressController {
    private final SyncService service;
    private final MyService statistics;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sweetcherrysync-web-job");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean queued = new AtomicBoolean();

    public SyncProgressController(SyncService service, MyService statistics) {
        this.service = service;
        this.statistics = statistics;
    }

    /** Only publish known counters, never unrelated settings or connection secrets. */
    @GetMapping("/sync/counters")
    public Map<String, String> counters() {
        Set<String> known = java.util.Arrays.stream(Statistics.values())
            .map(Statistics::getValue).collect(Collectors.toSet());
        return statistics.getAllSettings().stream()
            .filter(s -> known.contains(s.getKey()))
            .collect(Collectors.toMap(s -> s.getKey(), s -> s.getValue() == null ? "0" : s.getValue(), (a, b) -> b));
    }

    @GetMapping("/sync/progress")
    public SyncService.Progress progress() { return service.getProgress(); }

    @PostMapping("/sync/start")
    public ResponseEntity<SyncService.Progress> start() {
        return submit(service::syncNodes);
    }

    @PostMapping("/sync/incremental/start")
    public ResponseEntity<SyncService.Progress> startIncremental() {
        return submit(service::syncIncremental);
    }

    /** Shared queue for web clicks and the optional runtime scheduler. */
    public boolean schedule(String mode) {
        Runnable job = "LEGACY_FULL".equals(mode) ? service::syncNodes : service::syncIncremental;
        return submit(job).getStatusCode().value() == 202;
    }

    private ResponseEntity<SyncService.Progress> submit(Runnable job) {
        if ("RUNNING".equals(service.getStatus().state()) || !queued.compareAndSet(false, true))
            return ResponseEntity.status(409).body(service.getProgress());
        try {
            executor.execute(() -> {
                try { job.run(); }
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
