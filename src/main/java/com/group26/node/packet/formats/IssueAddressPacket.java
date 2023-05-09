package com.group26.node.packet.formats;

import com.group26.client.Message;
import com.group26.client.MessageType;
import com.group26.node.packet.PacketFormat;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Encapsulation of addressing response(answer to the ID request from another node) packet data.
 */
public class IssueAddressPacket implements NetworkPacket {
    private static final PacketFormat FORMAT = PacketFormat.ISSUE_ID;
    private final ByteBuffer packetData;

    /**
     * Encapsulate the given data into an addressing packet
     *
     * @param data the hole data to be put into
     */
    public IssueAddressPacket(ByteBuffer data) {
        packetData = data;
    }

    /**
     * Creates the answer packet to the ID request
     *
     * @param sourceId     an ID of the sender
     * @param acceptableId a suggested ID for requester
     * @param takenIds     a list of the already taken IDs
     */
    public IssueAddressPacket(byte sourceId, byte acceptableId, byte[] timestamp, byte[] takenIds) {
        byte header = (byte) (FORMAT.id << 4);

        packetData = ByteBuffer.allocate(32);
        packetData.put(header);
        setAnswerPacketData(sourceId, acceptableId, timestamp, takenIds);
        addPadding((byte) 0);
    }

    /**
     * Sets source ID, the suggested ID and the list of already taken IDs to the answer packet.
     *
     * @param sourceId        ID address of the current node
     * @param suggestedId     the issued ID to the new node
     * @param alreadyTakenIds the list of already taken IDs(excluding the suggested ID)
     */
    public void setAnswerPacketData(byte sourceId, byte suggestedId, byte[] timestamp, byte[] alreadyTakenIds) {
        packetData.put(new byte[]{toByte(sourceId, suggestedId)});

        if (timestamp.length == 3) {
            packetData.put(timestamp);
        } else {
            // set timestamp to 0
            packetData.put(new byte[3]);
        }

        for (int i = 0; i < alreadyTakenIds.length; i += 2) {
            if (i + 1 < alreadyTakenIds.length) {
                packetData.put(toByte(alreadyTakenIds[i], alreadyTakenIds[i + 1]));
            } else {
                packetData.put(toByte(alreadyTakenIds[i], (byte) 0));
            }
        }
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

    /**
     * Puts each 4 bits as a separate element in a new ByteBuffer
     *
     * @param data the ByteBuffer with 8 bits elements
     * @return the ByteBuffer with 4 bits elements
     */
    public ByteBuffer to4BitPerElement(ByteBuffer data) {
        ByteBuffer fourBitsElements = ByteBuffer.allocate(data.capacity() * 2);

        for (byte b8bitsSeq : data.array()) {
            Pair<Byte, Byte> split8BitElement = toBytePair(b8bitsSeq);
            fourBitsElements.put(split8BitElement.getLeft()).put(split8BitElement.getRight());
        }

        return fourBitsElements;
    }

    public byte[] getAlreadyTakenIds() {
        ByteBuffer dataIn4BitsChunks = to4BitPerElement(packetData);
        return Arrays.copyOfRange(dataIn4BitsChunks.array(), 10, (dataIn4BitsChunks.limit() - 1));
    }

    public byte getSuggestedId() {
        return toBytePair(packetData.get(1)).getRight();
    }

    public byte[] getTimeStamp() {
        return Arrays.copyOfRange(packetData.array(), 2, 5);
    }

    /**
     * Return the source address of the answer packet (response to the ID request)
     *
     * @return sourceId byte in case it is the answer packet and 0 otherwise (if it is the ID request)
     */
    @Override
    public byte getSenderId() {
        return toBytePair(packetData.get(1)).getLeft();
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
        return false;
    }
}