package com.turkerozturk.sweetcherrysync.scheduler;

import com.turkerozturk.sweetcherrysync.controllers.SyncProgressController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** Optional runtime schedule. Settings reset to application.yml defaults on restart. */
@RestController
@RequestMapping("/sync/auto")
public class RuntimeSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(RuntimeSyncScheduler.class);
    public record Config(boolean enabled, String mode, int intervalSeconds, Instant nextRunAt) {}
    public record Update(boolean enabled, String mode, int intervalSeconds) {}

    private final SyncProgressController controller;
    private boolean enabled;
    private String mode;
    private int intervalSeconds;
    private Instant nextRunAt;

    public RuntimeSyncScheduler(SyncProgressController controller,
                                @Value("${sync.auto.enabled:false}") boolean enabled,
                                @Value("${sync.auto.mode:INCREMENTAL}") String mode,
                                @Value("${sync.auto.interval-seconds:300}") int intervalSeconds) {
        validate(mode, intervalSeconds);
        this.controller = controller;
        this.enabled = enabled;
        this.mode = mode;
        this.intervalSeconds = intervalSeconds;
        this.nextRunAt = enabled ? Instant.now().plusSeconds(intervalSeconds) : null;
    }

    static void validate(String mode, int intervalSeconds) {
        if (!"INCREMENTAL".equals(mode) && !"LEGACY_FULL".equals(mode))
            throw new IllegalArgumentException("mode must be INCREMENTAL or LEGACY_FULL");
        if (intervalSeconds < 30 || intervalSeconds > 86400)
            throw new IllegalArgumentException("intervalSeconds must be between 30 and 86400");
    }

    @GetMapping
    public synchronized Config get() { return new Config(enabled, mode, intervalSeconds, nextRunAt); }

    @PutMapping
    public synchronized ResponseEntity<?> update(@RequestBody Update update) {
        try { validate(update.mode(), update.intervalSeconds()); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }
        enabled = update.enabled();
        mode = update.mode();
        intervalSeconds = update.intervalSeconds();
        nextRunAt = enabled ? Instant.now().plusSeconds(intervalSeconds) : null;
        log.info("Runtime auto sync: enabled={}, mode={}, interval={} seconds", enabled, mode, intervalSeconds);
        return ResponseEntity.ok(get());
    }

    @Scheduled(fixedDelay = 5000)
    public synchronized void tick() {
        if (!enabled || nextRunAt == null || Instant.now().isBefore(nextRunAt)) return;
        try {
            if (controller.schedule(mode)) {
                nextRunAt = Instant.now().plusSeconds(intervalSeconds);
                log.info("Automatic {} sync accepted", mode);
            } else {
                // Busy: do not pile up jobs; retry after a short delay.
                nextRunAt = Instant.now().plusSeconds(10);
            }
        } catch (RuntimeException e) {
            nextRunAt = Instant.now().plusSeconds(10);
            log.warn("Automatic synchronization could not be submitted", e);
        }
    }
}
