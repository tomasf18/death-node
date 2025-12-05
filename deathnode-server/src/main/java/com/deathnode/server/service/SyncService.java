package com.deathnode.server.service;

import org.springframework.stereotype.Service;

@Service
public class SyncService {

    // TODO (SECURITY - LATER)
    // - Verify signed buffer roots
    // - Merge reports into global block
    // - Compute block Merkle root
    // - Chain with previous block
    // - Sign server block commitment

    public void processSyncRequest() {
        // PLACEHOLDER
    }
}
