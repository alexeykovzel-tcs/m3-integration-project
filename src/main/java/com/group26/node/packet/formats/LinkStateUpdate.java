package com.group26.node.packet.formats;

import com.group26.client.Message;
import com.group26.client.MessageType;
import com.group26.node.packet.PacketFormat;
import com.group26.node.routing.NodeLinkState;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Packet that is used to update the node's link state and transmit it across the network. Link states are
 * used to determine packet transmitters for multi-hoping.
 */
public class LinkStateUpdate implements NetworkPacket {
    private static final PacketFormat FORMAT = PacketFormat.LINK_STATE_UPDATE;
    private static final int PACKET_SIZE = 32;

    private final NodeLinkState linkState;  // node link state
    private byte senderId;                  // sender node id
    private byte ttl;                       // hope count that a node is allowed to exist

    public LinkStateUpdate(NodeLinkState linkState, byte senderId, byte ttl) {
        this.linkState = linkState;
        this.senderId = senderId;
        this.ttl = ttl;
    }

    public LinkStateUpdate(ByteBuffer data) {
        // fill in the time-to-live and the link state
        Pair<Byte, Byte> b1 = toBytePair(data.get(1));
        Pair<Byte, Byte> b2 = toBytePair(data.get(2));
        this.senderId = b1.getLeft();
        byte sourceId = b1.getRight();
        byte sequence = b2.getLeft();
        this.ttl = b2.getRight();

        // get neighbor addresses of the source node
        Set<Byte> neighborIds = new HashSet<>();
        for (int i = 3; i < PACKET_SIZE / 2; i++) {
            Pair<Byte, Byte> b = toBytePair(data.get(i));
            if (b.getLeft() == 0) break;
            neighborIds.add(b.getLeft());
            if (b.getRight() == 0) break;
            neighborIds.add(b.getRight());
        }

        // define the node line-state
        this.linkState = new NodeLinkState(sourceId, sequence, neighborIds);
    }

    @Override
    public Message toMessage() {
        byte flags = 0b0000;
        ByteBuffer data = ByteBuffer.allocate(PACKET_SIZE);
        data.put(toByte((byte) FORMAT.id, flags));
        data.put(toByte(senderId, linkState.getNodeId()));
        data.put(toByte(linkState.getSequence(), ttl));

        // put neighbor as pairs per byte
        List<Byte> neighborIds = new ArrayList<>(linkState.getNeighborIds());
        for (int i = 0; i < neighborIds.size(); i += 2) {
            byte id1 = neighborIds.get(i);
            byte id2 = (i + 1 < neighborIds.size()) ? neighborIds.get(i + 1) : 0;
            data.put(toByte(id1, id2));
        }
        return new Message(MessageType.DATA, data);
    }

    @Override
    public boolean isAck(NetworkPacket packet) {
        if (packet instanceof LinkStateUpdate) {
            var ack = (LinkStateUpdate) packet;
            boolean sequencesMatch = ack.getLinkState().getSequence() == linkState.getSequence();
            boolean sourcesMatch = ack.getLinkState().getNodeId() == linkState.getNodeId();
            return sequencesMatch && sourcesMatch;
        }
        return false;
    }

    @Override
    public PacketFormat getFormat() {
        return FORMAT;
    }

    @Override
    public String toString() {
        return String.format("LINK_STATE_UPDATE; Sender ID: %d; Source ID: %d; SEQ: %d; NEIGHBORS: %s",
                senderId, linkState.getNodeId(), linkState.getSequence(), linkState.getNeighborIds());
    }

    @Override
    public byte getSenderId() {
        return senderId;
    }

    /**
     * @return the link state of the source node that sent this packet in the first place.
     */
    public NodeLinkState getLinkState() {
        return linkState;
    }

    /**
     * Sets the packet's sender node. It is used when the packet is being multi-hoped.
     *
     * @param senderId sender node id
     */
    public void setSenderId(byte senderId) {
        this.senderId = senderId;
    }

    /**
     * @return the time-to-live of the current packet in the network
     */
    public byte getTtl() {
        return ttl;
    }

    /**
     * Sets the packet's time-to-live (ttl) in the network. When the ttl hits 0, it is not
     * allowed to be transmitted further.
     *
     * @param ttl packet time-to-live
     */
    public void setTtl(byte ttl) {
        this.ttl = ttl;
    }
}
