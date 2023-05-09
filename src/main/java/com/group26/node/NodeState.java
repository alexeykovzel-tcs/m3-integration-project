package com.group26.node;

/**
 * Class that determines the states of the node when joining and participating in the network.
 */
public enum NodeState {
    /**
     * State at which the node only tries to find its neighbors by repeatedly pinging. Also, the node does
     * not respond to any incoming packets until the next state.
     */
    FINDING_NEIGHBORS,

    /**
     * State at which the node tries to acquire an id address from the nearest neighbor that it found during
     * the previous state. If the node did not find any neighbors, then it takes any possible id.
     */
    ASSIGNING_ID,

    /**
     * State at which the node awaits the network topology from its id provider. If there is any missing
     * information, then it sends a direct request.
     */
    PULLING_TOPOLOGY,

    /**
     * State at which the node is ready to send chat messages and participate in the network converging. Also,
     * the node periodically pings its neighbors to determine if they are still in the network and updates its
     * topology correspondingly.
     */
    READY_TO_SEND,
}
