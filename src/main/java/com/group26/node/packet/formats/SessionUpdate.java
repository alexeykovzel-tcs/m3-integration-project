package com.group26.node.packet.formats;

import com.group26.client.Message;
import com.group26.client.MessageType;
import com.group26.node.packet.PacketFormat;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.ByteBuffer;

/**
 * Packet that is used to initiate transmission sessions for an arbitrary number of packets for any node
 * in the network (mostly data transmissions). When a node gets this packet, it retransmits it further if
 * necessary and creates the corresponding class to receive packets in the right order.
 */
public class SessionUpdate implements NetworkPacket {
    private static final PacketFormat FORMAT = PacketFormat.SESSION_UPDATE;
    private static final int PACKET_SIZE = 2;

    private final byte sourceId;    // session initializer
    private final byte packetCount; // packet count for the session
    private byte senderId;          // node id which sent the packet

    public SessionUpdate(byte senderId, byte sourceId, byte packetCount) {
        this.senderId = senderId;
        this.sourceId = sourceId;
        this.packetCount = packetCount;
    }

    public SessionUpdate(ByteBuffer data) {
        packetCount = toBytePair(data.get(0)).getRight();
        Pair<Byte, Byte> b1 = toBytePair(data.get(1));
        senderId = b1.getLeft();
        sourceId = b1.getRight();
    }

    @Override
    public Message toMessage() {
        ByteBuffer data = ByteBuffer.allocate(PACKET_SIZE);
        data.put(toByte((byte) FORMAT.id, packetCount));
        data.put(toByte(senderId, sourceId));
        return new Message(MessageType.DATA_SHORT, data);
    }

    @Override
    public boolean isAck(NetworkPacket packet) {
        if (packet instanceof SessionUpdate) {
            var ack = (SessionUpdate) packet;
            return ack.getSourceId() == sourceId;
        }
        return false;
    }

    @Override
    public PacketFormat getFormat() {
        return FORMAT;
    }

    @Override
    public String toString() {
        return String.format("SESSION_UPDATE; Sender ID: %d; Source ID: %d; Packet count: %d",
                senderId, sourceId, packetCount);
    }

    @Override
    public byte getSenderId() {
        return senderId;
    }

    /**
     * @return id of the node that initiated the session
     */
    public byte getSourceId() {
        return sourceId;
    }

    /**
     * @param senderId id of the sending node
     */
    public void setSenderId(byte senderId) {
        this.senderId = senderId;
    }

    /**
     * @return the number of packets that will be sent during the session
     */
    public byte getPacketCount() {
        return packetCount;
    }
}
