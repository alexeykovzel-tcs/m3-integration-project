package com.group26.node.packet.formats;

import com.group26.client.Message;
import com.group26.client.MessageType;
import com.group26.node.packet.PacketFormat;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Packet that carries a part of information that is addressed to a given destination node.
 * If the destination id is 0, it means that this information is intended for every node in the network.
 */
public class DataPacket implements NetworkPacket {
    private static final PacketFormat FORMAT = PacketFormat.DATA;
    private static final int PACKET_SIZE = 32;
    private static final int HEADER_SIZE = 3;
    public static final int PAYLOAD_SIZE = PACKET_SIZE - HEADER_SIZE;

    private final byte destinationId;   // packet destination id
    private final byte senderId;        // node id which sent the packet
    private final byte sourceId;        // packet source id
    private final byte sequence;        // sequence number for ordering
    private final byte[] payload;       // packet data (max. 28 bytes)

    public DataPacket(byte destinationId, byte senderId, byte sourceId, byte sequence, byte[] payload) {
        this.destinationId = destinationId;
        this.senderId = senderId;
        this.sourceId = sourceId;
        this.sequence = sequence;
        this.payload = payload;
    }

    public DataPacket(ByteBuffer data) {
        Pair<Byte, Byte> b1 = toBytePair(data.get(1));
        Pair<Byte, Byte> b2 = toBytePair(data.get(2));
        this.senderId = b1.getLeft();
        this.sourceId = b1.getRight();
        this.destinationId = b2.getLeft();
        this.sequence = b2.getRight();
        this.payload = new byte[PAYLOAD_SIZE];
        for (int i = 0; i < PAYLOAD_SIZE; i++) {
            payload[i] = data.get(HEADER_SIZE + i);
        }
    }

    @Override
    public Message toMessage() {
        byte flags = 0b0000;
        ByteBuffer data = ByteBuffer.allocate(PACKET_SIZE);
        data.put(toByte((byte) FORMAT.id, flags));
        data.put(toByte(senderId, sourceId));
        data.put(toByte(destinationId, sequence));
        data.put(payload);
        return new Message(MessageType.DATA, data);
    }

    @Override
    public boolean isAck(NetworkPacket packet) {
        if (packet instanceof DataAckPacket) {
            var ack = (DataAckPacket) packet;
            boolean sourcesMatch = ack.getSourceId() == sourceId;
            boolean sequencesMatch = ack.getSequence() == sequence;
            return sourcesMatch && sequencesMatch;
        }
        return false;
    }

    @Override
    public PacketFormat getFormat() {
        return FORMAT;
    }

    @Override
    public String toString() {
        return String.format("DATA; Sender ID: %d; Source ID: %d; SEQ: %d; Destination ID: %d",
                senderId, sourceId, sequence, destinationId);
    }

    @Override
    public byte getSenderId() {
        return senderId;
    }

    /**
     * @return a node id that is supposed to receive this packet and respond with an acknowledgement
     */
    public byte getDestinationId() {
        return destinationId;
    }

    /**
     * @return a node id that sent this link state in the first place
     */
    public byte getSourceId() {
        return sourceId;
    }

    /**
     * @return a version of the given link state. Each next link state contain a sequence that is higher by one
     * than the previous one
     */
    public byte getSequence() {
        return sequence;
    }

    /**
     * @return packet payload that contains a certain part of the final message
     */
    public byte[] getPayload() {
        return payload;
    }
}
