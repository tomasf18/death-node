package com.deathnode.tool;

import com.google.gson.JsonObject;
import java.security.*;
import java.time.Instant;
import java.util.*;

import com.deathnode.common.model.Envelope;
import com.deathnode.common.model.Metadata;
import com.deathnode.common.model.Report;
import com.deathnode.tool.util.KeyLoader;

/**
 * DeathNodeToolCLI - Command-line interface for the DeathNode secure document library.
 * 
 * Usage:
 *   deathnode-tool protect --in report.json --out envelope.json --signer-node nodeA 
 *                          --signer-priv signer_ed_priv.pem --recipient-pub nodeB:pub_rsa.pem 
 *                          --seq 1 --prev-hash abc123
 *   
 *   deathnode-tool unprotect --in envelope.json --out report.json --recipient-node nodeB
 *                            --recipient-priv priv_rsa.pem --sender-pub sender_ed_pub.pem
 *   
 *   deathnode-tool check --in envelope.json
 */
public class DeathNodeToolCLI {

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            System.exit(1);
        }

        String command = args[0];

        try {
            switch (command.toLowerCase()) {
                case "protect":
                    handleProtect(args);
                    break;
                case "unprotect":
                    handleUnprotect(args);
                    break;
                case "check":
                    handleCheck(args);
                    break;
                case "help":
                case "--help":
                case "-h":
                    printHelp();
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printHelp();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void handleProtect(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args, 1);

        // Required parameters
        String inFile = getRequired(params, "--in", "Input report JSON file");
        String outFile = getRequired(params, "--out", "Output envelope JSON file");
        String signerNode = getRequired(params, "--signer-node", "Signer node ID");
        String signerPrivPath = getRequired(params, "--signer-priv", "Signer Ed25519 private key");
        long seq = Long.parseLong(getRequired(params, "--seq", "Sequence number"));
        
        // Optional parameters
        String prevHash = params.getOrDefault("--prev-hash", "");

        if ((prevHash == null || prevHash.isEmpty()) && seq != 1) {
            throw new IllegalArgumentException("--prev-hash is required for sequence numbers > 1");
        }
        
        // Parse recipient public keys (format: --recipient-pub nodeB:path/to/key.pem)
        Map<String, PublicKey> recipientPubKeys = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey().equals("--recipient-pub")) {
                String value = entry.getValue();
                String[] parts = value.split(":", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid recipient-pub format. Use: nodeId:keyPath");
                }
                String nodeId = parts[0];
                String keyPath = parts[1];
                PublicKey pubKey = KeyLoader.readRsaPublicKey(keyPath);
                recipientPubKeys.put(nodeId, pubKey);
            }
        }
        
        if (recipientPubKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one --recipient-pub is required");
        }

        // System.out.println("=== PROTECT ===");
        // System.out.println("Input: " + inFile);
        // System.out.println("Output: " + outFile);
        // System.out.println("Signer: " + signerNode);
        // System.out.println("Sequence: " + seq);
        // System.out.println("Recipients: " + recipientPubKeys.keySet());

        // Load report
        JsonObject reportJson = KeyLoader.readJsonObject(inFile);
        Report report = Report.fromJson(reportJson);

        // Load signer private key (Ed25519)
        PrivateKey signerPriv = KeyLoader.readEd25519PrivateKey(signerPrivPath);

        // Build metadata
        Metadata metadata = new Metadata();
        metadata.setReportId(report.getReportId());
        metadata.setMetadataTimestamp(Instant.now().toString());
        metadata.setReportCreationTimestamp(report.getReportCreationTimestamp());
        metadata.setNodeSequenceNumber(seq);
        metadata.setPrevEnvelopeHash(prevHash);
        metadata.setSignerNodeId(signerNode);
        metadata.setSignerAlg("Ed25519");

        // Protect
        Envelope envelope = SecureDocumentProtocol.protect(report, metadata, recipientPubKeys, signerPriv);

        // Write envelope
        KeyLoader.writeJsonObject(outFile, envelope.toJson());

        System.out.println("> Report protected successfully");
        System.out.println("Envelope hash: " + envelope.computeHashHex());
    }

    private static void handleUnprotect(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args, 1);

        // Required parameters
        String inFile = getRequired(params, "--in", "Input envelope JSON file");
        String outFile = getRequired(params, "--out", "Output report JSON file");
        String recipientNode = getRequired(params, "--recipient-node", "Recipient node ID");
        String recipientPrivPath = getRequired(params, "--recipient-priv", "Recipient RSA private key");
        String senderPubPath = getRequired(params, "--sender-pub", "Sender Ed25519 public key");

        // System.out.println("=== UNPROTECT ===");
        // System.out.println("Input: " + inFile);
        // System.out.println("Output: " + outFile);
        // System.out.println("Recipient: " + recipientNode);

        // Load envelope
        JsonObject envelopeJson = KeyLoader.readJsonObject(inFile);
        Envelope envelope = Envelope.fromJson(envelopeJson);

        // Load keys
        PrivateKey recipientPriv = KeyLoader.readRsaPrivateKey(recipientPrivPath);
        PublicKey senderPub = KeyLoader.readEd25519PublicKey(senderPubPath);

        // Unprotect
        Report report = SecureDocumentProtocol.unprotect(envelope, recipientPriv, recipientNode, senderPub);

        // Write report
        KeyLoader.writeJsonObject(outFile, report.toJson());

        System.out.println("> Report unprotected successfully");
        System.out.println("Report ID: " + report.getReportId());
        System.out.println("Reporter: " + report.getReporterPseudonym());
        System.out.println("Status: " + report.getStatus());
    }

    private static void handleCheck(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args, 1);

        // Required parameters
        String inFile = getRequired(params, "--in", "Input envelope JSON file");

        // System.out.println("=== CHECK ===");
        // System.out.println("Input: " + inFile);

        // Load envelope
        JsonObject envelopeJson = KeyLoader.readJsonObject(inFile);
        Envelope envelope = Envelope.fromJson(envelopeJson);

        // Check
        Map<String, Object> result = SecureDocumentProtocol.check(envelope);
        boolean valid = (boolean) result.get("valid"); // cast because result is Map<String, Object>
        @SuppressWarnings("unchecked") // safe because we know the type
        List<String> reasons = (List<String>) result.get("reasons");

        if (valid) {
            System.out.println("> Envelope is valid");
            System.out.println("\nMetadata:");
            Metadata meta = envelope.getMetadata();
            System.out.println("  Report ID: " + meta.getReportId());
            System.out.println("  Signer: " + meta.getSignerNodeId());
            System.out.println("  Sequence: " + meta.getNodeSequenceNumber());
            System.out.println("  Prev Hash: " + (meta.getPrevEnvelopeHash().isEmpty() ? "(none)" : meta.getPrevEnvelopeHash()));
            System.out.println("  Metadata Timestamp: " + meta.getMetadataTimestamp());
            System.out.println("  Recipients: " + envelope.getKeyEnc().getKeys().size() + " node(s)");
        } else {
            System.err.println("!! Envelope is INVALID");
            System.err.println("\nReasons:");
            for (String reason : reasons) {
                System.err.println("  - " + reason);
            }
            System.exit(1);
        }
    }

    // ========== Helper Methods ==========

    private static Map<String, String> parseArgs(String[] args, int startIndex) {
        Map<String, String> params = new HashMap<>();
        int i = startIndex;
        while (i < args.length) {
            if (args[i].startsWith("--")) {
                String key = args[i];
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    params.put(key, args[i + 1]);
                    i += 2;
                } else {
                    params.put(key, "true");
                    i++;
                }
            } else {
                i++;
            }
        }
        return params;
    }

    private static String getRequired(Map<String, String> params, String key, String description) {
        String value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key + " (" + description + ")");
        }
        return value;
    }

    private static void printHelp() {
        System.out.println("DeathNode Secure Document Tool");
        System.out.println("===============================\n");
        
        System.out.println("COMMANDS:\n");
        
        System.out.println("  protect    - Encrypt and sign a report");
        System.out.println("  unprotect  - Decrypt and verify a report");
        System.out.println("  check      - Validate envelope structure");
        System.out.println("  help       - Show this help message\n");
        
        System.out.println("USAGE:\n");
        
        System.out.println("  Protect:");
        System.out.println("    deathnode-tool protect --in <report.json> --out <envelope.json>");
        System.out.println("                           --signer-node <nodeId>");
        System.out.println("                           --signer-priv <ed25519_private_key.pem>");
        System.out.println("                           --recipient-pub <nodeId>:<rsa_public_key.pem>");
        System.out.println("                           [--recipient-pub <nodeId>:<key.pem> ...]");
        System.out.println("                           --seq <sequence_number>");
        System.out.println("                           [--prev-hash <previous_envelope_hash>]\n");
        
        System.out.println("  Unprotect:");
        System.out.println("    deathnode-tool unprotect --in <envelope.json> --out <report.json>");
        System.out.println("                             --recipient-node <nodeId>");
        System.out.println("                             --recipient-priv <rsa_private_key.pem>");
        System.out.println("                             --sender-pub <ed25519_public_key.pem>\n");
        
        System.out.println("  Check:");
        System.out.println("    deathnode-tool check --in <envelope.json>\n");
        
        System.out.println("EXAMPLES:\n");
        
        System.out.println("  # Protect a report for two recipients");
        System.out.println("  deathnode-tool protect --in report.json --out envelope.json \\");
        System.out.println("    --signer-node nodeA --signer-priv keys/nodeA_ed25519_priv.pem \\");
        System.out.println("    --recipient-pub nodeA:keys/nodeA_rsa_pub.pem \\");
        System.out.println("    --recipient-pub nodeB:keys/nodeB_rsa_pub.pem \\");
        System.out.println("    --seq 1 --prev-hash abc123\n");
        
        System.out.println("  # Unprotect an envelope");
        System.out.println("  deathnode-tool unprotect --in envelope.json --out report.json \\");
        System.out.println("    --recipient-node nodeB --recipient-priv keys/nodeB_rsa_priv.pem \\");
        System.out.println("    --sender-pub keys/nodeA_ed25519_pub.pem\n");
        
        System.out.println("  # Check envelope validity");
        System.out.println("  deathnode-tool check --in envelope.json\n");
    }
}
