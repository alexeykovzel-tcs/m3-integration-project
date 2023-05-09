package com.group26.node;

import com.group26.node.routing.NodeLinkState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class that contains the information about the network topology as well as the node's identifier
 * in the network and its neighbors.
 */
public class NetworkTopology {
    private final Map<Byte, NodeLinkState> linkStates = new ConcurrentHashMap<>();
    private final Set<Byte> neighborIds = new HashSet<>();
    private final Set<Byte> takenIds = new HashSet<>();
    private byte nodeId;

    /**
     * @return link states of the network participants that this node is aware of
     */
    public Map<Byte, NodeLinkState> getLinkStates() {
        return linkStates;
    }

    /**
     * @return ids of the node neighbors in the network
     */
    public Set<Byte> getNeighborIds() {
        return neighborIds;
    }

    /**
     * @return ids of the network participants
     */
    public Set<Byte> getTakenIds() {
        return takenIds;
    }

    /**
     * @return id of the current node in the network
     */
    public byte getNodeId() {
        return nodeId;
    }

    /**
     * @param nodeId id of the current node in the network
     */
    public void setNodeId(byte nodeId) {
        this.nodeId = nodeId;
    }
}
