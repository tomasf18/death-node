package com.deathnode.tool;

import java.security.*;
import java.time.Instant;
import java.util.*;

import com.deathnode.common.model.Report;
import com.deathnode.common.model.Envelope;
import com.deathnode.common.model.Metadata;
import com.deathnode.tool.utils.KeyLoader;

/**
 * ExampleTest - Demonstrates programmatic usage of the SecureDocumentProtocol library.
 * 
 * This is an example showing how to:
 * 1. Generate keys
 * 2. Create and protect a report
 * 3. Check the envelope
 * 4. Unprotect and verify the report
 */
public class ExampleTest {

    public static void main(String[] args) {
        try {
            System.out.println("=== DeathNode Protocol Example ===\n");

            // Step 1: Generate keys for two nodes
            System.out.println("1. Generating keys for nodeA and nodeB...");
            
            KeyPair nodeA_Ed25519 = KeyLoader.generateEd25519KeyPair();
            KeyPair nodeA_RSA = KeyLoader.generateRsaKeyPair();
            
            KeyPair nodeB_Ed25519 = KeyLoader.generateEd25519KeyPair();
            KeyPair nodeB_RSA = KeyLoader.generateRsaKeyPair();
            
            System.out.println("   > Keys generated\n");

            // Step 2: Create a sample report
            System.out.println("2. Creating sample report...");
            
            Report report = new Report();
            report.setReportId("report-001");
            report.setReportCreationTimestamp(Instant.now().toString());
            report.setReporterPseudonym("shadow_operative");
            report.setVersion(1);
            report.setStatus("pending_validation");
            
            Report.Content content = new Report.Content();
            content.setSuspect("target_alpha");
            content.setDescription("Suspicious activity detected in sector 7");
            content.setLocation("Lisbon, Portugal");
            report.setContent(content);
            
            System.out.println("   Report ID: " + report.getReportId());
            System.out.println("   Reporter: " + report.getReporterPseudonym());
            System.out.println("   > Report created\n");

            // Step 3: Create metadata for the envelope
            System.out.println("3. Creating metadata...");
            
            Metadata metadata = new Metadata();
            metadata.setReportId(report.getReportId());
            metadata.setMetadataTimestamp(Instant.now().toString());
            metadata.setReportCreationTimestamp(report.getReportCreationTimestamp());
            metadata.setNodeSequenceNumber(1);
            metadata.setPrevEnvelopeHash(""); // First message
            metadata.setSignerNodeId("nodeA");
            metadata.setSignerAlg("Ed25519");
            
            System.out.println("   Signer: " + metadata.getSignerNodeId());
            System.out.println("   Sequence: " + metadata.getNodeSequenceNumber());
            System.out.println("   > Metadata created\n");

            // Step 4: Protect the report (nodeA signs and encrypts for nodeA and nodeB)
            System.out.println("4. Protecting report (sign + encrypt)...");
            
            Map<String, PublicKey> recipients = new HashMap<>();
            recipients.put("nodeA", nodeA_RSA.getPublic());
            recipients.put("nodeB", nodeB_RSA.getPublic());
            
            Envelope envelope = SecureDocumentProtocol.protect(
                report,
                metadata,
                recipients,
                nodeA_Ed25519.getPrivate()
            );
            
            String envelopeHash = envelope.computeHashHex();
            System.out.println("   > Report protected");
            System.out.println("   Envelope hash: " + envelopeHash.substring(0, 16) + "...\n");

            // Step 5: Check the envelope structure
            System.out.println("5. Checking envelope structure...");
            
            Map<String, Object> checkResult = SecureDocumentProtocol.check(envelope);
            boolean valid = (boolean) checkResult.get("valid");
            
            if (valid) {
                System.out.println("   > Envelope is structurally valid\n");
            } else {
                @SuppressWarnings("unchecked")
                List<String> reasons = (List<String>) checkResult.get("reasons");
                System.err.println("   !! Envelope is INVALID:");
                for (String reason : reasons) {
                    System.err.println("     - " + reason);
                }
                return;
            }

            // Step 6: Unprotect as nodeB (decrypt and verify)
            System.out.println("6. Unprotecting as nodeB (decrypt + verify)...");
            
            Report decryptedReport = SecureDocumentProtocol.unprotect(
                envelope,
                nodeB_RSA.getPrivate(),
                "nodeB",
                nodeA_Ed25519.getPublic()
            );
            
            System.out.println("   > Report decrypted and signature verified");
            System.out.println("   Report ID: " + decryptedReport.getReportId());
            System.out.println("   Reporter: " + decryptedReport.getReporterPseudonym());
            System.out.println("   Suspect: " + decryptedReport.getContent().getSuspect());
            System.out.println("   Description: " + decryptedReport.getContent().getDescription() + "\n");
            // Step 7: Verify report content matches
            System.out.println("7. Verifying decrypted content matches original...");
            
            boolean contentMatches = 
                report.getReportId().equals(decryptedReport.getReportId()) &&
                report.getReporterPseudonym().equals(decryptedReport.getReporterPseudonym()) &&
                report.getContent().getSuspect().equals(decryptedReport.getContent().getSuspect()) &&
                report.getContent().getDescription().equals(decryptedReport.getContent().getDescription()) &&
                report.getContent().getLocation().equals(decryptedReport.getContent().getLocation());
            
            if (contentMatches) {
                System.out.println("   > Content matches - End-to-end test successful!\n");
            } else {
                System.err.println("   !! Content mismatch - Test failed!\n");
            }

            // Step 8: Demonstrate tamper detection
            System.out.println("8. Testing tamper detection...");
            
            // Try to modify the ciphertext
            String originalCiphertext = envelope.getReportEnc().getCiphertext();
            envelope.getReportEnc().setCiphertext("TAMPERED" + originalCiphertext.substring(8));
            
            try {
                SecureDocumentProtocol.unprotect(
                    envelope,
                    nodeB_RSA.getPrivate(),
                    "nodeB",
                    nodeA_Ed25519.getPublic()
                );
                System.err.println("   !! Tamper detection FAILED - should have thrown exception!\n");
            } catch (SecurityException e) {
                System.out.println("   > Tamper detected: " + e.getMessage() + "\n");
            }

            // Step 9: Demonstrate signature forgery detection
            System.out.println("9. Testing signature forgery detection...");
            
            // Restore original ciphertext but use wrong sender key
            envelope.getReportEnc().setCiphertext(originalCiphertext);
            
            try {
                SecureDocumentProtocol.unprotect(
                    envelope,
                    nodeB_RSA.getPrivate(),
                    "nodeB",
                    nodeB_Ed25519.getPublic() // Wrong signer key!
                );
                System.err.println("   !! Forgery detection FAILED - should have thrown exception!\n");
            } catch (SecurityException e) {
                System.out.println("   > Forgery detected: " + e.getMessage() + "\n");
            }

            System.out.println("=== All tests completed successfully! ===");

        } catch (Exception e) {
            System.err.println("Error during example execution:");
            e.printStackTrace();
        }
    }
}
