package com.deathnode.common.util;

import com.deathnode.common.model.Envelope;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class MerkleUtils {
    public static byte[] computeMerkleRoot(List<byte[]> envelopes) {
        List<byte[]> currentLayer = new ArrayList<>(envelopes);

        while (currentLayer.size() > 1) {
            List<byte[]> nextLayer = new ArrayList<>();
            for (int i = 0; i < currentLayer.size(); i += 2) {
                byte[] left = currentLayer.get(i);
                byte[] right = (i + 1 < currentLayer.size()) ? currentLayer.get(i + 1) : left; // duplicate last if odd
                byte[] combined = new byte[left.length + right.length];
                System.arraycopy(left, 0, combined, 0, left.length);
                System.arraycopy(right, 0, combined, left.length, right.length);
                byte[] parentHash = HashUtils.sha256(combined);
                nextLayer.add(parentHash);
            }
            currentLayer = nextLayer;
        }

        byte[] merkleRoot = currentLayer.get(0);

        return merkleRoot; 
    }

    public static boolean verifyMerkleRoot(List<byte[]> envelopes, byte[] expectedRoot) {
        byte[] computedRootHex = computeMerkleRoot(envelopes);
        return Arrays.equals(computedRootHex, expectedRoot);
    }
}
