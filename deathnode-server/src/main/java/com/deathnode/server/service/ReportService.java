package com.deathnode.server.service;

import com.deathnode.server.entity.Report;
import com.deathnode.server.entity.NodeSyncState;
import com.deathnode.server.repository.ReportRepository;
import com.deathnode.server.repository.NodeSyncStateRepository;
import com.deathnode.server.storage.FileStorageService;
import com.deathnode.server.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final NodeSyncStateRepository nodeSyncStateRepository;
    private final FileStorageService fileStorageService;

    public ReportService(
            ReportRepository reportRepository,
            NodeSyncStateRepository nodeSyncStateRepository,
            FileStorageService fileStorageService) {
        this.reportRepository = reportRepository;
        this.nodeSyncStateRepository = nodeSyncStateRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public String storeReport(byte[] fileBytes, String signerNodeId, long sequenceNumber, byte[] prevHash) throws IOException {

        // ===============================
        // TODO (SECURITY - LATER)
        // ===============================
        // - Verify AEAD decryption
        // - Verify payload signature (Ed25519)
        // - Verify prev_hash matches stored NodeSyncState
        // - Reject if sequence is not monotonic
        // ===============================

        // compute deterministic envelope hash (baseline)
        String envelopeHashHex = HashUtils.sha256Hex(fileBytes);
        String filename = envelopeHashHex + ".env";

        // store file on disk (IMMUTABLE)
        fileStorageService.store(fileBytes, filename);

        // store metadata in DB
        Report report = new Report();
        report.setEnvelopeHash(envelopeHashHex);
        report.setSignerNodeId(signerNodeId);
        report.setSequenceNumber(sequenceNumber);
        report.setPrevReportHash(prevHash);
        report.setMetadataTimestamp(OffsetDateTime.now());
        report.setFilePath(filename);

        reportRepository.save(report);

        // update per-node sync state
        NodeSyncState state = nodeSyncStateRepository
                .findById(signerNodeId)
                .orElseGet(() -> new NodeSyncState(signerNodeId));

        state.setLastSequenceNumber(sequenceNumber);
        state.setLastEnvelopeHash(envelopeHashHex.getBytes());

        nodeSyncStateRepository.save(state);

        return envelopeHashHex;
    }

    public byte[] loadEnvelope(String envelopeHash) throws IOException {

        // TODO (SECURITY - LATER)
        // - Verify block 
        // - Prevent replay requests

        return fileStorageService.read(envelopeHash + ".env");
    }
}
