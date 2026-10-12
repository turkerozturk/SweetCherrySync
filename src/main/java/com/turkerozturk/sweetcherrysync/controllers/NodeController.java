/*
 * This file is part of the SweetCherrySync project.
 * Please refer to the project's README.md file for additional details.
 * https://github.com/turkerozturk/SweetCherrySync
 *
 * Copyright (c) 2024 Turker Ozturk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.en.html>.
 */
package com.turkerozturk.sweetcherrysync.controllers;

import com.turkerozturk.sweetcherrysync.services.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class NodeController {
    private final SyncService syncService;
    public NodeController(SyncService syncService) { this.syncService = syncService; }

    @GetMapping("/sync/status")
    public SyncService.Status status() { return syncService.getStatus(); }

    @PostMapping("/sync")
    public ResponseEntity<SyncService.Status> syncDatabases() {
        try {
            syncService.syncNodes();
            return ResponseEntity.ok(syncService.getStatus());
        } catch (SyncService.SyncBusyException e) {
            return ResponseEntity.status(409).body(syncService.getStatus());
        } catch (SyncService.SyncSafetyException e) {
            return ResponseEntity.unprocessableEntity().body(syncService.getStatus());
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(syncService.getStatus());
        }
    }
}
