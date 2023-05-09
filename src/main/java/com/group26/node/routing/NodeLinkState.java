package com.group26.node.routing;

import java.util.Set;

/**
 * Class used for establishing network topology for each of the nodes. It contains the information about the node's
 * neighbors, the node's id and the "freshness" of this information (sequence).
 * The older versions of link states are discarded.
 */
public class NodeLinkState {
    private final Set<Byte> neighborIds;
    private final byte nodeId;
    private byte sequence;

    public NodeLinkState(byte nodeId, byte sequence, Set<Byte> neighborIds) {
        this.nodeId = nodeId;
        this.sequence = sequence;
        this.neighborIds = neighborIds;
    }

    /**
     * Increment the sequence value.
     */
    public void incrementSequence() {
        sequence += 1;
    }

    /**
     * @return neighbor ids of the owner node
     */
    public Set<Byte> getNeighborIds() {
        return neighborIds;
    }

    /**
     * @return node id (owner of the link state)
     */
    public byte getNodeId() {
        return nodeId;
    }

    /**
     * @return version of the link state
     */
    public byte getSequence() {
        return sequence;
    }

    public NodeLinkState copy() {
        return new NodeLinkState(nodeId, sequence, Set.copyOf(neighborIds));
    }
}
