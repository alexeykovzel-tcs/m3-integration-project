package com.group26.node.packet;

import com.group26.node.packet.formats.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * Class that stores packets arriving at the node during its session on the server. Also, it is able to
 * determine the missing acks of the corresponding packet given a certain timeout.
 */
public final class PacketLogger {
    private static PacketLogger LOGGER;
    private final List<Pair<NetworkPacket, Long>> records = new LinkedList<>();
    private long lastSendingRecord;

    /**
     * @return instance of the packet logger
     */
    public synchronized static PacketLogger getInstance() {
        if (LOGGER == null) {
            LOGGER = new PacketLogger();
        }
        return LOGGER;
    }

    /**
     * Locally stores the received packet with a time of arrival.
     *
     * @param packet received packet by the node
     */
    public synchronized void addRecord(NetworkPacket packet) {
        records.add(new ImmutablePair<>(packet, System.currentTimeMillis()));
    }

    /**
     * Determines the node ids that did not send an ack to the corresponding packet during the given timeout.
     *
     * @param packet      packet that was supposed to be acknowledged
     * @param expectedIds node ids that should have sent an ack
     * @param timeout     timeout during which the acks should've been sent
     * @return the set of node ids that did not send an ack
     */
    public Set<Byte> getMissingAcks(NetworkPacket packet, Set<Byte> expectedIds, long timeout) {
        Set<Byte> senderIds = new HashSet<>();
        for (NetworkPacket record : getRelevantPackets(timeout)) {
            byte senderId = record.getSenderId();
            if (packet.isAck(record) && senderId > 0) {
                senderIds.add(record.getSenderId());
            }
        }
        // get ids of the nodes that did not send an ack
        Set<Byte> missingIds = new HashSet<>(expectedIds);
        missingIds.removeAll(senderIds);
        return missingIds;
    }

    /**
     * Determines the records received during the given timeout.
     *
     * @param timeout timeout of the received packets
     * @return the records received during the given timeout.
     */
    private Set<NetworkPacket> getRelevantPackets(long timeout) {
        Set<NetworkPacket> relevantRecords = new HashSet<>();
        long currentTime = System.currentTimeMillis();

        // do not pick packets received out of timeout
        for (Pair<NetworkPacket, Long> record : records) {
            boolean expired = currentTime - record.getRight() > timeout;
            if (!expired) relevantRecords.add(record.getLeft());
        }
        return relevantRecords;
    }

    /**
     * Sets the last time the current node sent a packet.
     */
    public void recordLastSending() {
        lastSendingRecord = System.currentTimeMillis();
    }

    /**
     * @return true if the current node sent any packet within the given timeout
     */
    public boolean hasTrafficWithin(int timeout) {
        return System.currentTimeMillis() - lastSendingRecord < timeout;
    }
}
