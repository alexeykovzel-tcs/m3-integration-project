package com.group26.node.addressing;

import com.group26.node.NetworkTopology;
import com.group26.node.packet.PacketSender;
import com.group26.node.packet.formats.IssueAddressPacket;
import com.group26.node.packet.formats.RequestAddressPacket;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Class which encapsulates the ID address for a specific node.
 * Process the ID requests and responses.
 */
public class AddressProtocol {
    private static final int TRANSMISSION_TIMEOUT = 3000;

    private final PacketSender sender = PacketSender.getInstance();
    private final NetworkTopology topology;
    private boolean isConfirmed = false;
    private byte idProvider;
    private byte id;
    private final byte[] timestamp;
    private final Map<Integer, Byte> issuedIds;

    /**
     * Creates an instance of {@code AddressProtocol}.
     * Requires the reference for the topology object, to obtain and modify the list of neighbours and taken ids.
     *
     * @param topology an object reference to the nodes neighbours and taken IDs.
     */
    public AddressProtocol(NetworkTopology topology) {
        this.topology = topology;
        this.idProvider = 0;
        this.id = 0;

        issuedIds = new HashMap<>();

        byte[] time = BigInteger.valueOf(System.currentTimeMillis()).toByteArray();
        timestamp = Arrays.copyOfRange(time, time.length - 3, time.length);
    }

    public byte getAddress() {
        return id;
    }

    /**
     * Returns the array of already taken IDs, based on current topology.
     *
     * @return array of bytes(primitive type)
     */
    public byte[] getAlreadyTakenIds() {
        byte[] idList = new byte[topology.getTakenIds().size()];

        int index = 0;
        for (byte aByte : topology.getTakenIds()) {
            idList[index] = aByte;
            index++;
        }

        return idList;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }

    /**
     * Returns the highest ID available in a set or 0 in case the set is empty.
     *
     * @param idList set to pick ID from
     * @return the highest byte or 0
     */
    public byte getTheHighestId(Set<Byte> idList) {
        byte maxId = 0;

        for (byte nextId : idList) {
            if (maxId < nextId) {
                maxId = nextId;
            }
        }

        for (Integer integer : issuedIds.keySet()) {
            if (issuedIds.get(integer) > maxId) {
                maxId = issuedIds.get(integer);
            }
        }

        return maxId;
    }

    /**
     * Sends request for ID, tries N times.
     * If no response, assigns ID to 1
     *
     * @param destinationId the IDs provider
     * @param attempts      the number of tries the node
     */
    public void requestAddress(byte destinationId, int attempts) {
        if (attempts == 0) {
            id = (byte) 1;
            topology.setNodeId(id);
            topology.getTakenIds().add(id);
            isConfirmed = true;
            return;
        }

        idProvider = destinationId;
        sender.sendReliableMessage(new RequestAddressPacket(destinationId, timestamp),
                200, 500, attempts, TRANSMISSION_TIMEOUT, Set.of(destinationId));
    }

    /**
     * Starts sending ID requests.
     * If there is no neighbours pick ID 1 for itself
     *
     * @param neighbours the list of neighbours to ask an ID from
     * @return true in case there are neighbors, which also means that the process of ID issuing has started.
     */
    public boolean startAddressing(Set<Byte> neighbours) {
        byte neighbourToAskForId = getTheHighestId(neighbours);
        boolean hasNeighbors = neighbourToAskForId != 0;
        int attempts = hasNeighbors ? 3 : 0;
        requestAddress(neighbourToAskForId, attempts);
        return hasNeighbors;
    }

    /**
     * Process the response for ID requests.
     *
     * @param packet the packet which contains the source ID, suggested ID, timestamp and list of already taken IDs
     * @return true in case the address of node is updated.
     */
    public boolean handleAddressResponse(IssueAddressPacket packet) {
        if (!isConfirmed() && packet.getSenderId() == idProvider && Arrays.equals(timestamp, packet.getTimeStamp())) {
            id = packet.getSuggestedId();
            topology.setNodeId(id);
            isConfirmed = true;

            // Adding newly assigned ID to already taken list
            topology.getTakenIds().add(id);
            for (byte alreadyTakenId : packet.getAlreadyTakenIds()) {
                if (alreadyTakenId != 0) {
                    topology.getTakenIds().add(alreadyTakenId);
                }
            }

            return true;
        } else if (isConfirmed()) {
            // listening to the ID request processed by our neighbours

            int issuedTimeStamp =  new BigInteger(packet.getTimeStamp()).intValue();
            int issuedId = packet.getSuggestedId();
            int issuer = packet.getSenderId();

            if (!issuedIds.containsKey(issuedTimeStamp) && !issuedIds.containsValue((byte) issuedId)) {
                // if our neighbour gave some new node an ID, it will be added to issued ID sooner than the topology will get a chance to update.
                issuedIds.put(issuedTimeStamp, (byte) issuedId);
            } else {
                // Should never happen*
                System.out.println("Possible addressing collision: " + issuedId + ", issuer: " + issuer);
            }
        }
        return false;
    }

    /**
     * Process ID request packets.
     *
     * @param packet request packet, supposed to contain timestamp and destination ID
     * @return the packet was processed and the response was sent.
     */
    public boolean handleAddressRequest(RequestAddressPacket packet) {
        if (isConfirmed() && packet.getDestinationId() == id) {

            int timestamp = new BigInteger(packet.getTimeStamp()).intValue();
            byte suggestedId;

            if (issuedIds.containsKey(timestamp)) {
                suggestedId = issuedIds.get(timestamp);
            } else {
                suggestedId = getTheHighestId(topology.getTakenIds());
                suggestedId = ((byte) ((int) suggestedId + 1));

                issuedIds.put(timestamp, suggestedId);
            }

            sender.sendSafeMessage(new IssueAddressPacket(id, suggestedId, packet.getTimeStamp(),
                    getAlreadyTakenIds()).toMessage(), 0);
            return true;
        }
        return false;
    }

    /**
     * Reference the ID of the node, which issued the ID address for this node.
     *
     * @return the issuer of the current ID address.
     */
    public byte getIdProvider() {
        return idProvider;
    }
}