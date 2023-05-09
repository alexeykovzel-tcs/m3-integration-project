package com.group26.node.session;

import com.group26.node.packet.formats.DataPacket;

import java.util.HashSet;
import java.util.Set;

/**
 * Class that receives data packets using the sliding window protocol.
 */
public class DataReceiver {
    private final DataPacket[] receivedPackets;
    private final Set<Integer> awaitedSeqs = new HashSet<>();
    private final int windowSize = SessionConfig.RECEIVE_WINDOW_SIZE;
    private final int seqCount = SessionConfig.SEQ_COUNT;
    private int firstAcceptableIndex = 0;
    private int largestAcceptableSeq;
    private int lastSeqReceived = -1;

    public DataReceiver(byte packetCount) {
        receivedPackets = new DataPacket[packetCount];
        largestAcceptableSeq = windowSize - 1;

        // await the first sequences of the receiving window
        for (int seq = 0; seq < windowSize; seq++) {
            awaitedSeqs.add(seq);
        }
    }

    /**
     * Handles the next data packet.
     *
     * @param packet received data packet
     * @return true if the packet has been stored
     */
    public boolean receivePacket(DataPacket packet) {
        int seq = packet.getSequence();
        int gap = getReceiveWindowGap(seq);
        if (gap < windowSize) {
            // store received packet according to its index
            int packetIdx = firstAcceptableIndex + gap;
            if (packetIdx < receivedPackets.length){
                receivedPackets[packetIdx] = packet;
                updateReceiveWindow(seq);
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if all the packets were received according to the initial packet count.
     *
     * @return true if all the packets were received
     */
    public boolean hasAllPackets() {
        for (DataPacket packet : receivedPackets) {
            if (packet == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return received packets so far.
     */
    public DataPacket[] getPackets() {
        return receivedPackets;
    }

    /**
     * Calculates the number of sequences from the last sequence received.
     *
     * @param seq comparing sequence
     * @return the number of sequences from the last sequence received.
     */
    private int getReceiveWindowGap(int seq) {
        int gap = seq - lastSeqReceived - 1;
        if (lastSeqReceived > seq) gap += seqCount;
        return gap;
    }

    /**
     * Updates the receiving window corresponding to the sequence of the received packet.
     *
     * @param receivedSeq sequence of the received packet
     */
    private void updateReceiveWindow(int receivedSeq) {
        awaitedSeqs.remove(receivedSeq);
        if (receivedSeq == getFirstAwaitedSeq()) {
            shiftReceiveWindow();
            updateLargestAcceptableSeq();
        }
    }

    /**
     * Shifts the receiving window corresponding to the last sequence received and the largest acceptable sequence.
     */
    private void shiftReceiveWindow() {
        while (lastSeqReceived != largestAcceptableSeq) {
            int seq = getFirstAwaitedSeq();
            if (awaitedSeqs.contains(seq)) break;
            lastSeqReceived = seq;
            awaitedSeqs.add((seq + windowSize) % seqCount);
            firstAcceptableIndex += 1;
        }
    }

    /**
     * Updates the largest acceptable sequence by bypassing already acknowledged sequences.
     */
    private void updateLargestAcceptableSeq() {
        largestAcceptableSeq = (lastSeqReceived + windowSize) % seqCount;
    }

    /**
     * @return the first sequence in the receiving window.
     */
    private int getFirstAwaitedSeq() {
        return (lastSeqReceived + 1) % seqCount;
    }
}
