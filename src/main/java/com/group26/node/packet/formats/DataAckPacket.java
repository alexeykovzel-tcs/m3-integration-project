package com.group26.node.packet.formats;

import com.group26.client.Message;
import com.group26.client.MessageType;
import com.group26.node.packet.PacketFormat;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.ByteBuffer;

/**
 * Packet that is used to acknowledge the corresponding data packet so that the sender can identify whether
 * the current node received a certain part of information or not.
 */
public class DataAckPacket implements NetworkPacket {
    private static final PacketFormat FORMAT = PacketFormat.DATA_ACK;
    private static final int PACKET_SIZE = 2;

    private final byte senderId;
    private final byte sourceId;
    private final byte sequence;

    public DataAckPacket(byte senderId, byte sourceId, byte sequence) {
        this.senderId = senderId;
        this.sourceId = sourceId;
        this.sequence = sequence;
    }

    public DataAckPacket(ByteBuffer data) {
        Pair<Byte, Byte> b0 = toBytePair(data.get(0));
        Pair<Byte, Byte> b1 = toBytePair(data.get(1));
        this.senderId = b0.getRight();
        this.sourceId = b1.getLeft();
        this.sequence = b1.getRight();
    }

    @Override
    public Message toMessage() {
        ByteBuffer data = ByteBuffer.allocate(PACKET_SIZE);
        data.put(toByte((byte) FORMAT.id, senderId));
        data.put(toByte(sourceId, sequence));
        return new Message(MessageType.DATA_SHORT, data);
    }

    @Override
    public boolean isAck(NetworkPacket packet) {
        return false;
    }

    @Override
    public PacketFormat getFormat() {
        return FORMAT;
    }

    @Override
    public String toString() {
        return String.format("DATA_ACK; Sender ID: %d; Source ID: %d; SEQ: %d", senderId, sourceId, sequence);
    }

    @Override
    public byte getSenderId() {
        return senderId;
    }

    /**
     * @return a node id that sent this piece of information in the first place
     */
    public byte getSourceId() {
        return sourceId;
    }

    /**
     * @return a sequence number that is used to identify the order position of the corresponding data packet
     */
    public byte getSequence() {
        return sequence;
    }
}
