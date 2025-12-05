package com.deathnode.server.controller;

import com.deathnode.server.service.SyncService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping
    public String sync() {

        // TODO (SECURITY - LATER)
        // - Receive signed roots
        // - Validate
        // - Perform block creation

        syncService.processSyncRequest();
        return "Sync accepted (baseline)";
    }
}
