package com.group26.node.packet;

import com.group26.node.session.SessionConfig;
import com.group26.node.packet.formats.DataPacket;

import java.nio.charset.StandardCharsets;

/**
 * Class that parses text into data packets and the other way around. Also, it fully fills the packet
 * payload by adding/removing padding bytes where necessary.
 */
public class PacketParser {
    /**
     * Parses data packets into the text.
     *
     * @param packets given data packets
     * @return parsing string result
     */
    public String parsePackets(DataPacket[] packets) {
        int lastPacketIdx = packets.length - 1;
        byte[] lastPayload = removePadding(packets[lastPacketIdx].getPayload());
        int textSize = DataPacket.PAYLOAD_SIZE * lastPacketIdx + lastPayload.length;
        byte[] textBytes = new byte[textSize];

        // add payload bytes to the text bytes except the last packet
        int offset = 0;
        for (int i = 0; i < packets.length - 1; i++) {
            byte[] payload = packets[i].getPayload();
            System.arraycopy(payload, 0, textBytes, offset, payload.length);
            offset += payload.length;
        }

        // attach the last payload
        System.arraycopy(lastPayload, 0, textBytes, offset, lastPayload.length);

        // convert the text bytes to the UTF format
        return new String(textBytes, StandardCharsets.UTF_8);
    }

    /**
     * Parses text into data packets, attaching destination, sender and the source ids, as well as sequences
     * to reassemble packets in the right order.
     *
     * @param text          text that should be parsed
     * @param destinationId id of the destination node
     * @param senderId      id of the sending node
     * @param sourceId      id of the node that sent this text in the first place
     * @return the array of data packets after parsing
     */
    public DataPacket[] parseText(String text, byte destinationId, byte senderId, byte sourceId) {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        int packetCount = (textBytes.length + DataPacket.PAYLOAD_SIZE - 1) / DataPacket.PAYLOAD_SIZE;
        DataPacket[] packets = new DataPacket[packetCount];

        // attach zero bytes as padding
        int paddingSize = DataPacket.PAYLOAD_SIZE * packetCount - textBytes.length;
        byte[] payloadBytes = addPadding(textBytes, paddingSize);

        // distribute the text data into packets
        byte sequence = 0;
        for (int i = 0; i < packetCount; i++) {
            // fill the packet payload
            byte[] payload = new byte[DataPacket.PAYLOAD_SIZE];
            for (int j = 0; j < DataPacket.PAYLOAD_SIZE; j++) {
                int offset = i * DataPacket.PAYLOAD_SIZE;
                payload[j] = payloadBytes[offset + j];
            }

            // add the packet into the collection and increase the packet sequence
            packets[i] = new DataPacket(destinationId, senderId, sourceId, sequence, payload);
            sequence = (byte) ((sequence + 1) % SessionConfig.SEQ_COUNT);
        }
        return packets;
    }

    /**
     * Adds padding bytes at the end of the byte array.
     *
     * @param bytes       the given byte array
     * @param paddingSize the number of padding bytes
     * @return the byte array with padding
     */
    private byte[] addPadding(byte[] bytes, int paddingSize) {
        byte[] bytesWithPadding = new byte[bytes.length + paddingSize];
        System.arraycopy(bytes, 0, bytesWithPadding, 0, bytes.length);
        for (int i = bytes.length; i < bytesWithPadding.length; i++) {
            bytesWithPadding[i] = 0;
        }
        return bytesWithPadding;
    }

    /**
     * Removes padding bytes at the end of the byte array.
     *
     * @param data the given byte array
     * @return the byte array without padding
     */
    private byte[] removePadding(byte[] data) {
        int dataSize = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                dataSize = i;
                break;
            }
        }
        byte[] bytesWithoutPadding = new byte[dataSize];
        System.arraycopy(data, 0, bytesWithoutPadding, 0, dataSize);
        return bytesWithoutPadding;
    }
}
