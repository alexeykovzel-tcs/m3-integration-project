package com.group26.node.session;

import com.group26.node.packet.PacketSender;
import com.group26.node.packet.formats.DataAckPacket;
import com.group26.node.packet.formats.DataPacket;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class that transmits data packets to the corresponding receiver nodes using the sliding window protocol.
 * Also, it ensures the transmission reliability by listening to the receivers' acknowledgements
 * and retransmitting data packets if necessary.
 */
public class DataTransmitter {
    private static final int RETRANSMISSION_ATTEMPTS = 2;
    private static final int TIMEOUT_PER_RECEIVER = 1000;

    private final int seqCount = SessionConfig.SEQ_COUNT;
    private final Lock lock = new ReentrantLock();
    private final Condition freeSendWindow = lock.newCondition();
    private final Condition receivedAllAcks = lock.newCondition();
    private final PacketSender sender = PacketSender.getInstance();
    private final Map<Integer, Set<Byte>> awaitedAcks = new HashMap<>();
    private final DataPacket[] packets;
    private final Set<Byte> leftReceivers;
    private boolean sentAllPackets;
    private int lastAckReceived;
    private int lastSequenceSent;

    public DataTransmitter(DataPacket[] packets, Set<Byte> receivers) {
        leftReceivers = new HashSet<>(receivers);
        lastAckReceived = seqCount - 1;
        this.packets = packets;
    }

    /**
     * Transmits given packets using the sliding window protocol.
     */
    public void transmit() {
        Queue<DataPacket> leftPackets = new ConcurrentLinkedQueue<>(Arrays.asList(packets));
        while (true) {
            boolean awaited = awaitFreeWindowSpace();
            if (awaited) {
                DataPacket nextPacket = leftPackets.poll();
                if (nextPacket != null) {
                    sendDataPacket(nextPacket);
                } else {
                    sentAllPackets = true;
                    break;
                }
            } else {
                System.out.println("Error occurred during transmission...");
                return;
            }
        }
        awaitLastAcks();
    }

    /**
     * Waits until all acks are received after sending data packets.
     *
     */
    private void awaitLastAcks() {
        try {
            lock.lock();
            if (!awaitedAcks.isEmpty()) {
                receivedAllAcks.await(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ignored) {
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sends a single data packet with reliability assurance.
     *
     * @param packet sending data packet
     */
    private void sendDataPacket(DataPacket packet) {
        try {
            lock.lock();
            awaitedAcks.put((int) packet.getSequence(), new HashSet<>(leftReceivers));
            lastSequenceSent = packet.getSequence();

            new Thread(() -> {
                // send a reliable packet with collision avoidance
                Set<Byte> lostReceivers = sender.sendReliablePacketAndWait(packet, 500, 1000,
                        RETRANSMISSION_ATTEMPTS, TIMEOUT_PER_RECEIVER * leftReceivers.size(), leftReceivers);

                // do not try to send a message to the receivers that did not respond
                if (!lostReceivers.isEmpty()) {
                    handleDataAck(packet.getSequence());        // forcefully acknowledge a packet
                    leftReceivers.removeAll(lostReceivers);     // do not expect acks from lost receivers
                }
            }).start();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles data ack packet. Then, complete data acknowledgement if it was the last awaiting sender.
     *
     * @param packet data acknowledgement packet
     */
    public void receiveDataAckPacket(DataAckPacket packet) {
        try {
            lock.lock();
            byte senderId = packet.getSenderId();
            int ack = packet.getSequence();

            // check if such ack is expected
            if (awaitedAcks.containsKey(ack)) {
                Set<Byte> leftSenders = awaitedAcks.get(ack);
                leftSenders.remove(senderId);

                // handle getting acks from all senders
                if (leftSenders.isEmpty()) {
                    handleDataAck(ack);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles data acknowledgement. Signals if there is free space in the sending window.
     *
     * @param ack seq of the acknowledged packet.
     */
    private void handleDataAck(int ack) {
        try {
            lock.lock();
            awaitedAcks.remove(ack);
            if (sentAllPackets && awaitedAcks.isEmpty()) {
                receivedAllAcks.signalAll();
            } else if (ack == (lastAckReceived + 1) % seqCount) {
                updateLastAckReceived();
                freeSendWindow.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates the last ack received by passing the already acknowledged sequences.
     */
    private void updateLastAckReceived() {
        try {
            lock.lock();
            while (lastAckReceived != lastSequenceSent) {
                int seq = (lastAckReceived + 1) % seqCount;
                if (awaitedAcks.containsKey(seq)) break;
                lastAckReceived = seq;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Signals when there is free space in the sending window.
     *
     * @return true if awaited successfully
     */
    private boolean awaitFreeWindowSpace() {
        try {
            lock.lock();
            while (getAwaitingAckCount() >= SessionConfig.SEND_WINDOW_SIZE) {
                boolean awaited = freeSendWindow.await(20, TimeUnit.SECONDS);
                if (!awaited) {
                    return false;
                }
            }
            return true;
        } catch (InterruptedException e) {
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the number of packets that are not yet acknowledged.
     */
    private int getAwaitingAckCount() {
        return (lastSequenceSent - lastAckReceived + seqCount) % seqCount;
    }
}
