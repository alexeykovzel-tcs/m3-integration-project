package com.group26.node.routing;

import com.group26.client.Message;
import com.group26.node.NetworkTopology;
import com.group26.node.NodeState;
import com.group26.node.packet.PacketLogger;
import com.group26.node.packet.PacketSender;
import com.group26.node.packet.formats.LinkStateRequest;
import com.group26.node.packet.formats.LinkStateUpdate;
import com.group26.node.packet.formats.PingPongPacket;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Protocol that obtains the information about the network topology through broadcasting the link state updates
 * and observing the neighbors' activity.
 */
public class LinkStateProtocol {
    private static final byte TIME_TO_LIVE = (byte) 3;
    private static final int INACTIVITY_PERIOD = 15000;

    private final PacketSender sender = PacketSender.getInstance();
    private final PacketLogger logger = PacketLogger.getInstance();
    private final NetworkTopology topology;
    private NodeState nodeState;
    private byte nodeId = -1;

    public LinkStateProtocol(NetworkTopology topology) {
        this.topology = topology;
    }

    /**
     * Broadcasts the current link-state to the neighbor nodes.
     */
    public void sendUpdate() {
        NodeLinkState linkState = topology.getLinkStates().get(nodeId);
        linkState.incrementSequence(); // increase the link state version
        LinkStateUpdate update = new LinkStateUpdate(linkState.copy(), nodeId, TIME_TO_LIVE);
        sendLinkState(update, topology.getNeighborIds());
    }

    /**
     * Handles neighbor activity. If the neighbor is new - save it to the local link-state.
     *
     * @param neighborId spotted neighbor id
     */
    public void handleNeighborActivity(byte neighborId) {
        Set<Byte> neighbors = topology.getNeighborIds();
        boolean stored = neighbors.contains(neighborId);
        if (!stored && neighborId > 0) {
            neighbors.add(neighborId);

            // if the node already exists, it means that the topology changed
            boolean existingNeighbor = topology.getTakenIds().contains(neighborId);
            if (existingNeighbor && nodeState == NodeState.READY_TO_SEND) {
                sendUpdate();
            } else {
                topology.getTakenIds().add(neighborId);
            }
        }
    }

    /**
     * Pushes the network topology to the neighbor nodes without expecting their responses.
     */
    public void pushNetworkTopology() {
        for (NodeLinkState linkState : topology.getLinkStates().values()) {
            Message message = new LinkStateUpdate(linkState, nodeId, (byte) 1).toMessage();
            sender.sendSafeMessage(message, 250);
        }
    }

    /**
     * Awaits the network topology from the id provider. If there are any missing link states,
     * request the source directly.
     *
     * @param providerId id provider
     */
    public void pullNetworkTopology(byte providerId) {
        int neighborCount = topology.getNeighborIds().size();
        Set<Byte> takenIds = topology.getTakenIds();
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            // go through pulled link states
            for (byte id : takenIds) {
                // check if received the following link state
                if (!topology.getLinkStates().containsKey(id)) {
                    // request the link state directly
                    LinkStateRequest request = new LinkStateRequest(providerId, id);
                    sender.sendReliableMessage(request, 200, 400, 3,
                            neighborCount * 2000, Set.of(providerId));
                }
            }
        }, takenIds.size() * 3000L, TimeUnit.MILLISECONDS);
    }

    /**
     * Handles routing requests by pushing the corresponding link states.
     *
     * @param request routing request
     */
    public void handleRequest(LinkStateRequest request) {
        if (nodeId == request.getDestinationId()) {
            byte sourceId = request.getSourceId();
            if (topology.getLinkStates().containsKey(sourceId)) {
                NodeLinkState linkState = topology.getLinkStates().get(sourceId);
                LinkStateUpdate update = new LinkStateUpdate(linkState, nodeId, (byte) 1);
                sender.scheduleMessage(update.toMessage(), 200, 500);
            }
        }
    }

    /**
     * Handles the link-state update. If necessary, broadcast it further to the other nodes.
     *
     * @param update received link-state update
     */
    public void handleUpdate(LinkStateUpdate update) {
        NodeLinkState linkState = update.getLinkState();
        byte senderId = update.getSenderId();
        byte sourceId = linkState.getNodeId();
        handleNeighborActivity(senderId);
        if (sourceId == nodeId) return;

        // update locally stored link state
        updateLinkState(linkState);

        // broadcast further link state if necessary
        if (nodeState == NodeState.READY_TO_SEND && update.getTtl() > 1) {
            update.setTtl((byte) (update.getTtl() - 1));    // decrement time-to-live
            update.setSenderId(nodeId);                     // set the current node as the sender

            // determine link state receivers
            Set<Byte> receivers = new HashSet<>(topology.getNeighborIds());
            receivers.removeAll(linkState.getNeighborIds());
            receivers.remove(sourceId);
            receivers.remove(senderId);
            sendLinkState(update, receivers);
        }
    }

    /**
     * Updates the locally stored link state and the other related link states.
     *
     * @param newLinkState received link state
     */
    public void updateLinkState(NodeLinkState newLinkState) {
        byte sourceId = newLinkState.getNodeId();

        // update link state of the source node
        Map<Byte, NodeLinkState> linkStates = topology.getLinkStates();
        NodeLinkState oldLinkState = linkStates.get(sourceId);
        Set<Byte> newNeighbors = newLinkState.getNeighborIds();

        boolean shouldRespond = true;
        if (oldLinkState != null) {
            boolean equalNeighbors = oldLinkState.getNeighborIds().equals(newNeighbors);
            boolean newSequence = oldLinkState.getSequence() < newLinkState.getSequence();
            shouldRespond = !equalNeighbors && newSequence;
        }

        if (shouldRespond) {
            // add the current node as a neighbor
            if (topology.getNeighborIds().contains(sourceId) && nodeId > 0) {
                newNeighbors.add(nodeId);
            }
            // remove/add source id from neighbors of the local link states
            updateLinkStateNeighbors(sourceId, newNeighbors);

            // update and print the link state
            linkStates.put(sourceId, newLinkState);
        }
    }

    /**
     * Sets the current node id and saves its link state.
     *
     * @param nodeId id of the current node
     */
    public void setNodeId(byte nodeId) {
        this.nodeId = nodeId;
        var linkState = new NodeLinkState(nodeId, (byte) 0, topology.getNeighborIds());
        topology.getLinkStates().put(nodeId, linkState); // save node's link state
    }

    /**
     * Updates the node state on change.
     *
     * @param nodeState updates node state
     */
    public void setNodeState(NodeState nodeState) {
        this.nodeState = nodeState;
        if (nodeState == NodeState.READY_TO_SEND) {
            sendPeriodicalPings();
            handleInactiveNeighbors();
        }
    }

    /**
     * Checks if there are missing link states in the node's topology.
     *
     * @return true if the node's topology is missing some link-states
     */
    public boolean hasFullTopology() {
        return topology.getTakenIds().stream().allMatch(topology.getLinkStates()::containsKey);
    }

    /**
     * Sends periodical pings to notify neighbors about this node's existence.
     */
    private void sendPeriodicalPings() {
        new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.MILLISECONDS.sleep(4000);
                    if (!logger.hasTrafficWithin(4000)) {
                        var ping = new PingPongPacket(nodeId, true);
                        sender.scheduleMessage(ping.toMessage(), 200, 500);
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }).start();
    }

    /**
     * Send periodical pings to update the node's neighbors.
     */
    private void handleInactiveNeighbors() {
        new Thread(() -> {
            while (true) {
                try {
                    // await some time before revealing inactive neighbors
                    Set<Byte> neighborsToCheck = Set.copyOf(topology.getNeighborIds());
                    TimeUnit.MILLISECONDS.sleep(INACTIVITY_PERIOD);

                    // get neighbors that did not send any packets during this period
                    var ping = new PingPongPacket(nodeId, false);
                    Set<Byte> lostNeighbors = logger.getMissingAcks(ping, neighborsToCheck, INACTIVITY_PERIOD);

                    // update the current topology if there are lost neighbors
                    if (!lostNeighbors.isEmpty()) {
                        // remove lost neighbors from the node link state
                        NodeLinkState linkState = topology.getLinkStates().get(nodeId);
                        Set<Byte> neighbors = linkState.getNeighborIds();
                        neighbors.removeAll(lostNeighbors);

                        // update the network topology and send the corresponding update
                        updateLinkStateNeighbors(nodeId, neighbors);
                        sendUpdate();
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }).start();
    }

    /**
     * Updates node neighbors stored in its link state and the others.
     *
     * @param nodeId    node id of the given link state
     * @param neighbors updated node neighbors
     */
    private void updateLinkStateNeighbors(byte nodeId, Set<Byte> neighbors) {
        for (NodeLinkState linkState : topology.getLinkStates().values()) {
            if (neighbors.contains(linkState.getNodeId())) {
                linkState.getNeighborIds().add(nodeId);
            } else {
                linkState.getNeighborIds().remove(nodeId);
            }
        }
    }

    /**
     * Sends a reliable link state update to the nearest neighbors.
     *
     * @param packet       link state update
     * @param receivingIds node ids that are expected to respond to this update
     */
    private void sendLinkState(LinkStateUpdate packet, Set<Byte> receivingIds) {
        if (receivingIds.size() == 0) packet.setTtl((byte) 1);
        sender.scheduleMessage(packet.toMessage(), 600, 1000);
    }
}