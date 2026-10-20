package com.turkerozturk.sweetcherrysync.controllers;

import com.turkerozturk.sweetcherrysync.services.IncrementalPreviewService;
import com.turkerozturk.sweetcherrysync.services.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Explicitly read-only incremental candidate discovery endpoint. */
@RestController
public class IncrementalPreviewController {
    private final IncrementalPreviewService preview;
    private final SyncService sync;

    public IncrementalPreviewController(IncrementalPreviewService preview, SyncService sync) {
        this.preview = preview;
        this.sync = sync;
    }

    @GetMapping("/sync/incremental/preview")
    public ResponseEntity<?> preview() {
        if ("RUNNING".equals(sync.getStatus().state()))
            return ResponseEntity.status(409).body("Full synchronization is running");
        return ResponseEntity.ok(preview.preview());
    }
}
