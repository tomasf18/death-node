package com.deathnode.server.controller;

import com.deathnode.server.service.ReportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadReport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("signerNodeId") String signerNodeId,
            @RequestParam("sequenceNumber") long sequenceNumber) throws Exception {

        // TODO (SECURITY - LATER)
        // - Extract prev_hash from metadata
        // - Extract AEAD header
        // - Verify signature before storing

        return reportService.storeReport(file.getBytes(), signerNodeId, sequenceNumber, new byte[32] /* temp prevHash placeholder */);
    }

    @GetMapping("/{hash}")
    public byte[] downloadReport(@PathVariable String hash) throws Exception {

        // TODO (SECURITY - LATER)

        return reportService.loadEnvelope(hash);
    }
}
