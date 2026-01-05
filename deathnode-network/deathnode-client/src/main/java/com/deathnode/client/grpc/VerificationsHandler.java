package com.deathnode.client.grpc;

import com.deathnode.client.config.Config;
import com.deathnode.client.service.DatabaseService;
import com.deathnode.common.grpc.SignedBufferRoot;
import com.deathnode.common.model.Envelope;
import com.deathnode.common.model.Metadata;
import com.deathnode.common.util.HashUtils;
import com.deathnode.common.util.MerkleUtils;
import com.deathnode.tool.SecureDocumentProtocol;
import com.deathnode.tool.util.KeyLoader;
import com.deathnode.client.service.DatabaseService.LastBlockInfo;
import com.deathnode.client.service.DatabaseService.ReportRow;

import java.security.PublicKey;
import java.util.List;
import java.util.Arrays;

public class VerificationsHandler {

    DatabaseService db;

    public VerificationsHandler(DatabaseService db) {
        this.db = db;
    }

    public VerificationsResult performAllVerifications(String roundId, List<byte[]> orderedEnvelopes, long blockNumber, byte[] blockRoot, byte[] signedBlockRoot, List<SignedBufferRoot> perNodeSignedBufferRoots, byte[] prevBlockRoot) {
        try {
            System.out.println("\n[VERIFICATION PIPELINE] Round " + roundId);
            
            String serverSigningPublicKeyPEM = db.getSignPubKey(Config.SERVER_NODE_ID);
            PublicKey serverSigningPublicKey = KeyLoader.pemStringToPublicKey(serverSigningPublicKeyPEM, Config.SIGNING_KEYS_ALG);
            if (!SecureDocumentProtocol.verifySignature(blockRoot, signedBlockRoot, serverSigningPublicKey)) {
                System.out.println("  [X] Step 1: Block signature verification FAILED");
                return new VerificationsResult(false, "INVALID_SIGNATURE", "Buffer signature verification failed");
            }
            System.out.println("  [V] Step 1: Block signature verified");

            LastBlockInfo lastBlockInfo = db.getLastBlockInfo();
            if (!verifyPreviousBlockMatch(lastBlockInfo, blockNumber, prevBlockRoot)) {
                System.out.println("  [X] Step 2: Previous block match verification FAILED");
                return new VerificationsResult(false, "PREVIOUS_BLOCK_MISMATCH", "Previous block info does not match last known block");
            }
            System.out.println("  [V] Step 2: Previous block match verified");

            if (!MerkleUtils.verifyMerkleRoot(orderedEnvelopes, blockRoot)) {
                System.out.println("  [X] Step 3: Block Merkle root verification FAILED");
                return new VerificationsResult(false, "INVALID_MERKLE_ROOT", "Block Merkle root verification failed");
            }
            System.out.println("  [V] Step 3: Block Merkle root verified");

            if (!verifyNodesBufferSignatures(perNodeSignedBufferRoots)) {
                System.out.println("  [X] Step 4: Node buffer signatures verification FAILED");
                return new VerificationsResult(false, "INVALID_NODE_BUFFER_ROOT_SIGNATURE", "One or more node buffer root signatures invalid");
            }
            System.out.println("  [V] Step 4: All node buffer signatures verified");

            List<String> nodeIds = perNodeSignedBufferRoots.stream().map(SignedBufferRoot::getNodeId).toList();
            if (!verifyPerNodeEnvelopeChain(nodeIds, orderedEnvelopes)) {
                System.out.println("  [X] Step 5: Per-node envelope chain verification FAILED");
                return new VerificationsResult(false, "INVALID_ENVELOPE_CHAIN", "Per-node envelope chain verification failed for one or more nodes");
            }
            System.out.println("  [V] Step 5: Per-node envelope chains verified");
            System.out.println("  [V] ALL VERIFICATIONS PASSED\n");
        } catch (Exception e) {
            return new VerificationsResult(false, "VERIFICATION_ERROR",
                    "Exception during verifications: " + e.getMessage());
        }
        return new VerificationsResult(true, null, null);
    }

    private boolean verifyPreviousBlockMatch(LastBlockInfo lastBlockInfo, long blockNumber, byte[] prevBlockRoot) {
        long expectedBlockNumber = (lastBlockInfo == null) ? 1 : lastBlockInfo.getBlockNumber() + 1;
        byte[] expectedPrevBlockRoot = (lastBlockInfo == null) ? null : HashUtils.hexToBytes(lastBlockInfo.getBlockRoot());
        if (blockNumber != expectedBlockNumber) {
            System.out.println(" -> Previous block number mismatch: expected " + expectedBlockNumber + ", got " + blockNumber);
            return false;
        }
        if (expectedPrevBlockRoot == null) {
            if (prevBlockRoot != null && prevBlockRoot.length > 0) {
                System.out.println(" -> Previous block root mismatch: expected null, got " + HashUtils.bytesToHex(prevBlockRoot));
                return false;
            }
        } else {
            if (prevBlockRoot == null || prevBlockRoot.length == 0 || !Arrays.equals(expectedPrevBlockRoot, prevBlockRoot)) {
                System.out.println(" -> Previous block root mismatch: expected " + HashUtils.bytesToHex(expectedPrevBlockRoot) + ", got " + (prevBlockRoot == null || prevBlockRoot.length == 0 ? "null" : HashUtils.bytesToHex(prevBlockRoot)));
                return false;
            }
        }
        return true;
    }

    private boolean verifyNodesBufferSignatures(List<SignedBufferRoot> perNodeSignedBufferRoots) throws Exception {
        for (SignedBufferRoot sbr : perNodeSignedBufferRoots) {
            String nodeId = sbr.getNodeId();
            String nodeSigningPubKeyPEM = db.getSignPubKey(nodeId);
            PublicKey nodeSigningPubKey = KeyLoader.pemStringToPublicKey(nodeSigningPubKeyPEM, Config.SIGNING_KEYS_ALG);
            byte[] bufferRoot = sbr.getBufferRoot().toByteArray();
            byte[] signedBufferRoot = sbr.getSignedBufferRoot().toByteArray();

            if (!SecureDocumentProtocol.verifySignature(bufferRoot, signedBufferRoot, nodeSigningPubKey)) {
                System.out.println("Invalid buffer root signature from node " + nodeId);
                return false;
            }
        }
        return true;
    }

    private boolean verifyPerNodeEnvelopeChain(List<String> nodeIds, List<byte[]> orderedEnvelopes) {
        List<Envelope> envelopes = orderedEnvelopes.stream()
                .map(Envelope::fromBytes)
                .toList();  
        for (String nodeId : nodeIds) {
            List<Envelope> nodeEnvelopes = envelopes.stream()
                    .filter(env -> env.getMetadata().getSignerNodeId().equals(nodeId))
                    .toList();
            
            if (!verifyEnvelopeChain(nodeId, nodeEnvelopes)) {
                return false;
            }
        }
        return true;
    }

    public boolean verifyEnvelopeChain(String nodeId, List<Envelope> envelopes) {
        ReportRow lastNodeSyncedReport = null;
        try {
            lastNodeSyncedReport = db.getLastSyncedReportOfNode(nodeId);
        } catch (Exception e) {
            System.out.println("Error retrieving last report for node " + nodeId + ": " + e.getMessage());
            return false;
        }
        String lastHash = lastNodeSyncedReport != null ? lastNodeSyncedReport.envelopeHash : null;
        long expectedSeq = lastNodeSyncedReport != null ? lastNodeSyncedReport.nodeSequenceNumber + 1 : 1L;
        
        for (Envelope env : envelopes) {
            Metadata meta = env.getMetadata();
            
            if ((lastHash == null && meta.getPrevEnvelopeHash() != null && !meta.getPrevEnvelopeHash().isEmpty()) || 
                (lastHash != null && !lastHash.equals(meta.getPrevEnvelopeHash()))) {
                System.out.println(" -> Envelope chain check failed for node " + nodeId +
                        ": expected prev hash " + lastHash + ", got " + meta.getPrevEnvelopeHash());
                return false;
            }

            if (meta.getNodeSequenceNumber() != expectedSeq) {
                System.out.println(" -> Envelope chain check failed for node " + nodeId +
                        ": expected seq " + expectedSeq + ", got " + meta.getNodeSequenceNumber());
                return false;
            }

            lastHash = env.computeHashHex();
            expectedSeq++;
        }

        return true;
    }

    public static class VerificationsResult {
        private boolean success;
        private String errorCode;
        private String errorMessage;

        public VerificationsResult(boolean success, String errorCode, String errorMessage) {
            this.success = success;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
