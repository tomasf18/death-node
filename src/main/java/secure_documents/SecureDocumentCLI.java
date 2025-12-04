package secure_documents;

import entity.Report;
import jakarta.json.JsonObject;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;

public class SecureDocumentCLI {
    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }

        String command = args[0].toLowerCase();
        switch (command) {
            case "protect":
                protectCli(args);
                break;
            case "check":
                checkCli(args);
                break;
            case "unprotect":
                unprotectCli(args);
                break;
            case "help":
                printHelp();
                break;
            default:
                System.out.println("Unknown command: " + command);
                printHelp();
                break;
        }
    }

    public static void protectCli(String[] args) {
        if (args.length != 5) {
            System.out.println("Usage: protect <input-file> <sender-skey-id> <recipient-pkey-id> <output-file>");
            return;
        }
        String inputFile = args[1];
        String senderKeyId = args[2];
        String recipientKeyId = args[3];
        String outputFile = args[4];

        try {
            // Load keys
            PrivateKey senderPrivKey = KeyLoader.readPrivateKey(senderKeyId);
            PublicKey recipientPubKey = KeyLoader.readPublicKey(recipientKeyId);

            // Read input JSON and convert to Report Object
            JsonObject reportJSON = KeyLoader.readJsonObject(inputFile);
            Report report = SecureDocumentProtocol.JSONToReport(reportJSON);
            // Protect
            JsonObject envelopeJson = SecureDocumentProtocol.protect(report, recipientPubKey, senderPrivKey);

            // Write output
            KeyLoader.writeJsonObject(outputFile, envelopeJson);

            System.out.println("Document protected successfully. Output saved to: " + outputFile);
        } catch (Exception e) {
            System.err.println("Error during protection: " + e.getMessage());
        }
    }

    public static void unprotectCli(String[] args) {
        if (args.length != 5) {
            System.out.println("Usage: unprotect <envelope-file> <recipient-key-id> <sender-key-id> <output-file>");
            return;
        }
        String envelopeFile = args[1];
        String recipientKeyId = args[2];
        String senderKeyId = args[3];
        String outputFile = args[4];

        try {
            // Load keys
            PrivateKey recipientPrivKey = KeyLoader.readPrivateKey(recipientKeyId);
            PublicKey senderPubKey = KeyLoader.readPublicKey(senderKeyId);

            // Read envelope JSON
            JsonObject envelopeJson = KeyLoader.readJsonObject(envelopeFile);

            // Unprotect
            Report plaintextReport = SecureDocumentProtocol.unprotect(envelopeJson, recipientPrivKey, senderPubKey);

            JsonObject plainTextJson = SecureDocumentProtocol.reportToJson(plaintextReport);
            // Write output
            KeyLoader.writeJsonObject(outputFile, plainTextJson);

            System.out.println("Document unprotected successfully. Output saved to: " + outputFile);
        } catch (Exception e) {
            System.err.println("Error during unprotection: " + e.getMessage());
        }
    }

    public static void checkCli(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: check <envelope-file>");
            return;
        }
        String envelopeFile = args[1];

        try {
            // Read envelope JSON
            JsonObject envelopeJson = KeyLoader.readJsonObject(envelopeFile);

            // Check
            Map<String, Object> result = SecureDocumentProtocol.check(envelopeJson);

            // Output result
            boolean isValid = (boolean) result.get("valid");
            @SuppressWarnings("unchecked")
            List<String> reasons = (List<String>) result.get("reasons");

            if (isValid) {
                System.out.println("Envelope is valid.");
            } else {
                System.out.println("Envelope is INVALID. Reasons:");
                for (String r : reasons) {
                    System.out.println(" - " + r);
                }
            }
        } catch (Exception e) {
            System.err.println("Error during check: " + e.getMessage());
        }
    }

    // ---------- Help ----------
    private static void printHelp() {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║         SecureDocumentProtocol - Command Line Tool             ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("DESCRIPTION:");
        System.out.println("  A secure document protection tool using hybrid cryptography:");
        System.out.println("  • AES-256-GCM for confidentiality (ephemeral DEK)");
        System.out.println("  • RSA-OAEP for key encapsulation");
        System.out.println("  • RSA-PSS for digital signatures (non-repudiation)");
        System.out.println();
        System.out.println("COMMANDS:");
        System.out.println();

        // PROTECT
        System.out.println("     protect <input-file> <sender-skey-id> <recipient-pkey-id> <output-file>");
        System.out.println("     Encrypt and sign a JSON document.");
        System.out.println();
        System.out.println("     Arguments:");
        System.out.println("       input-file        JSON file to protect");
        System.out.println("       sender-key-id     Key ID of sender (for signing)");
        System.out.println("       recipient-key-id  Key ID of recipient (for encryption)");
        System.out.println("       output-file       Where to save the protected envelope");
        System.out.println();
        System.out.println("     Security Operations:");
        System.out.println("       1. Canonicalize JSON (deterministic ordering)");
        System.out.println("       2. Generate ephemeral AES-256 key (DEK)");
        System.out.println("       3. AES-GCM encrypt plaintext (confidentiality + integrity)");
        System.out.println("       4. RSA-OAEP encrypt DEK with recipient's public key");
        System.out.println("       5. RSA-PSS sign envelope with sender's private key");
        System.out.println();
        System.out.println("     Example:");
        System.out.println("       secdoc protect report.json alice.privkey bob.pubkey envelope.json");
        System.out.println();

        // CHECK
        System.out.println("     check <envelope-file>");
        System.out.println("     Verify envelope ");
        System.out.println();
        System.out.println("     Arguments:");
        System.out.println("       envelope-file  Protected envelope to verify");
        System.out.println();
        System.out.println("     What it verifies:");
        System.out.println(
                "       • Verify that the envelope respects the structure of the 'protect' and that all fields are well defined");
        System.out.println();
        System.out.println("     Note: Does NOT decrypt the document or verify GCM tag.");
        System.out.println("           Full integrity is verified during unprotect.");
        System.out.println();
        System.out.println("     Example:");
        System.out.println("       secdoc check envelope.json");
        System.out.println();

        // UNPROTECT
        System.out.println("     unprotect <envelope-file> <recipient-key-id> <sender-key-id> <output-file>");
        System.out.println("     Verify, decrypt and extract the original document.");
        System.out.println();
        System.out.println("     Arguments:");
        System.out.println("       envelope-file     Protected envelope");
        System.out.println("       recipient-key-id  Key ID of recipient (must have private key)");
        System.out.println("       sender-key-id     Key ID of sender (for signature verification)");
        System.out.println("       output-file       Where to save decrypted document");
        System.out.println();
        System.out.println("     Security Operations:");
        System.out.println("       1. Verify RSA-PSS signature (sender authenticity)");
        System.out.println("       2. RSA-OAEP decrypt DEK with recipient's private key");
        System.out.println("       3. AES-GCM decrypt ciphertext (verifies tag automatically)");
        System.out.println();
        System.out.println("     Errors:");
        System.out.println("       • SecurityException if signature invalid (wrong/tampered sender)");
        System.out.println("       • AEADBadTagException if GCM tag invalid (tampered ciphertext)");
        System.out.println();
        System.out.println("     Example:");
        System.out.println("       secdoc unprotect envelope.json bob.privkey alice.pubkey plaintext.json");
        System.out.println();

        // EXAMPLES
        System.out.println("COMPLETE WORKFLOW EXAMPLE:");
        System.out.println("  # 1. Generate test keys");
        System.out.println();
        System.out.println("  # 2. Create a test document");
        System.out.println("  echo '{\"message\":\"secret data\"}' > doc.json");
        System.out.println();
        System.out.println("  # 3. Protect (Alice sends to Bob)");
        System.out.println("  secdoc protect doc.json alice.privkey bob.pubkey envelope.json");
        System.out.println();
        System.out.println("  # 4. Verify the structure of the envelope");
        System.out.println("  secdoc check envelope.json");
        System.out.println();
        System.out.println("  # 5. Decrypt (only Bob can do this)");
        System.out.println("  secdoc unprotect envelope.json bob.privkey alice.pubkey decrypted.json");
        System.out.println();
    }
}

