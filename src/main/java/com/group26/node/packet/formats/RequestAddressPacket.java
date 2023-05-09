package com.group26.node.packet.formats;

import com.group26.client.Message;
import com.group26.client.MessageType;
import com.group26.node.packet.PacketFormat;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * An encapsulation of the ID request packet data.
 */
public class RequestAddressPacket implements NetworkPacket{
    private static final PacketFormat FORMAT = PacketFormat.REQUEST_ID;
    private final ByteBuffer packetData;

    /**
     * Encapsulate the given data into an addressing packet
     *
     * @param data the hole data to be put into
     */
    public RequestAddressPacket(ByteBuffer data) {
        packetData = data;
    }

    /**
     * Creates a request ID packet to send to a neighbour
     *
     * @param destinationId an ID of the neighbour
     * @param timestamp     the time of node creation
     **/
    public RequestAddressPacket(byte destinationId, byte[] timestamp) {
        byte header = (byte) (FORMAT.id << 4);
        packetData = ByteBuffer.allocate(32);
        packetData.put(header);

        setRequestPacketData(destinationId, timestamp);
        addPadding((byte) 0);
    }

    /**
     * Sets the destination ID and the retransmission sequence of the request packet
     *
     * @param destinationId the ID of the issuer(the node, which provides an ID)
     * @param timestamp     the time of node creation
     */
    public void setRequestPacketData(byte destinationId, byte[] timestamp) {
        packetData.put(toByte(destinationId, (byte) 0));
        packetData.put(timestamp);
    }

    /**
     * Fills the remaining positions of ByteBuffer with byte value.
     *
     * @param byteForPadding a value to be filled with
     */
    public void addPadding(byte byteForPadding) {
        while (packetData.position() < 32) {
            packetData.put(byteForPadding);
        }
    }

    public byte[] getTimeStamp() {
        return Arrays.copyOfRange(packetData.array(), 2, 5);
    }

    /**
     * Return the destination address of the request packet (sent from new node)
     *
     * @return destinationId byte in case it is the request packet and 0 otherwise
     */
    public byte getDestinationId() {
        return toBytePair(packetData.get(1)).getLeft();
    }

    @Override
    public byte getSenderId() {
        return 0;
    }

    @Override
    public Message toMessage() {
        return new Message(MessageType.DATA, packetData);
    }

    @Override
    public PacketFormat getFormat() {
        return FORMAT;
    }

    @Override
    public boolean isAck(NetworkPacket packet) {
        if (packet instanceof IssueAddressPacket) {
            var ack = (IssueAddressPacket) packet;
            return ack.getSenderId() == getDestinationId();
        }
        return false;
    }
}
