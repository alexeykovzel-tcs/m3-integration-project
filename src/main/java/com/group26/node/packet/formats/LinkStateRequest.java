package com.group26.node.packet.formats;

import com.group26.client.Message;
import com.group26.client.MessageType;
import com.group26.node.packet.PacketFormat;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.ByteBuffer;

/**
 * Packet that is addressed to a certain node and is used to request the link state of a single node in the network.
 */
public class LinkStateRequest implements NetworkPacket {
    private static final PacketFormat FORMAT = PacketFormat.LINK_STATE_REQUEST;
    private static final int PACKET_SIZE = 2;
    private final byte destinationId;
    private final byte sourceId;

    public LinkStateRequest(byte destinationId, byte sourceId) {
        this.destinationId = destinationId;
        this.sourceId = sourceId;
    }

    public LinkStateRequest(ByteBuffer data) {
        Pair<Byte, Byte> b1 = toBytePair(data.get(1));
        destinationId = b1.getLeft();
        sourceId = b1.getRight();
    }

    @Override
    public Message toMessage() {
        byte flags = 0b0000;
        ByteBuffer data = ByteBuffer.allocate(PACKET_SIZE);
        data.put(toByte((byte) FORMAT.id, flags));
        data.put(toByte(destinationId, sourceId));
        return new Message(MessageType.DATA_SHORT, data);
    }

    @Override
    public boolean isAck(NetworkPacket packet) {
        if (packet instanceof LinkStateUpdate) {
            var ack = (LinkStateUpdate) packet;
            boolean sourcesMatch = ack.getLinkState().getNodeId() == sourceId;
            boolean sendersMatch = ack.getSenderId() == destinationId;
            return sourcesMatch && sendersMatch;
        }
        return false;
    }

    @Override
    public PacketFormat getFormat() {
        return FORMAT;
    }

    @Override
    public String toString() {
        return String.format("LINK_STATE_REQUEST; Source ID: %d; Destination ID: %d", sourceId, destinationId);
    }

    @Override
    public byte getSenderId() {
        return 0;
    }

    /**
     * @return the destination node that should respond to this request
     */
    public byte getDestinationId() {
        return destinationId;
    }

    /**
     * @return the node id that sent this packet in the first place
     */
    public byte getSourceId() {
        return sourceId;
    }
}
