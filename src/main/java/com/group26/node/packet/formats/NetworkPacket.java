package com.group26.node.packet.formats;

import com.group26.client.Message;
import com.group26.node.packet.PacketFormat;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Packet that is used for the communication between the nodes in the network.
 */
public interface NetworkPacket {
    /**
     * @return the framework representation of the packet
     */
    Message toMessage();

    /**
     * Determines whether the given packet is considered as an acknowledgement to this packet.
     *
     * @param packet the acknowledgement packet
     * @return whether the given packet is an acknowledgement
     */
    boolean isAck(NetworkPacket packet);

    /**
     * @return the packet format that is determined by the first bits of the packet
     */
    PacketFormat getFormat();

    /**
     * @return the sender id of this packet
     */
    byte getSenderId();

    /**
     * Takes the first 4 bits of the given bytes and merges them into one byte.
     *
     * @param b1 the first 4 bits
     * @param b2 the last 4 bits
     * @return the merged byte
     */
    default byte toByte(byte b1, byte b2) {
        return (byte) ((b1 << 4) + b2);
    }

    /**
     * Divides the given byte into a pair of 4 bits.
     *
     * @param b initial byte
     * @return pair of 4 bits
     */
    default Pair<Byte, Byte> toBytePair(byte b) {
        byte b2 = (byte) (b & 0b1111);
        byte b1 = (byte) ((b >> 4) & 0b1111);
        return new ImmutablePair<>(b1, b2);
    }
}
