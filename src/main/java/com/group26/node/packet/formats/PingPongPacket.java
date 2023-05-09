package com.group26.node.packet.formats;

import com.group26.client.Message;
import com.group26.client.MessageType;
import com.group26.node.packet.PacketFormat;

import java.nio.ByteBuffer;

/**
 * Packet that is used to ping the nearest nodes to identify the ones that are located within
 * the transmission range of the current node.
 */
public class PingPongPacket implements NetworkPacket {
    private static final PacketFormat FORMAT = PacketFormat.PING_PONG;
    private static final int PACKET_SIZE = 2;

    private final byte senderId;    // packet source id
    private final boolean pong;     // whether a node should respond

    public PingPongPacket(byte senderId, boolean pong) {
        this.senderId = senderId;
        this.pong = pong;
    }

    public PingPongPacket(ByteBuffer data) {
        this.senderId = toBytePair(data.get(1)).getLeft();
        this.pong = (data.get(0) & 1) == 1;
    }

    @Override
    public Message toMessage() {
        byte flags = 0b0000;
        if (pong) flags |= 1;
        ByteBuffer data = ByteBuffer.allocate(PACKET_SIZE);
        data.put(toByte((byte) FORMAT.id, flags));
        data.put(toByte(senderId, (byte) 0));
        return new Message(MessageType.DATA_SHORT, data);
    }

    @Override
    public boolean isAck(NetworkPacket packet) {
        return true;
    }

    @Override
    public PacketFormat getFormat() {
        return FORMAT;
    }

    @Override
    public String toString() {
        return String.format("%s; Source ID: %d", pong ? "PONG" : "PING", senderId);
    }

    @Override
    public byte getSenderId() {
        return senderId;
    }

    /**
     * @return true if the current node is a response to another ping packet
     */
    public boolean isPong() {
        return pong;
    }
}
