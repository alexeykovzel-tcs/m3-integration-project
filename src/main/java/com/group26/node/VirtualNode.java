package com.group26.node;

import java.nio.ByteBuffer;

/**
 * The interface that determines the interaction between the node and the network (p.s. with the framework)
 */
public interface VirtualNode {
    /**
     * Handles packets in the byte format of an arbitrary size.
     *
     * @param data packet stored in bytes
     */
    void handlePacket(ByteBuffer data);

    /**
     * Notifies the node about changes in the network. To be precise, whether there are transmitting nodes nearby
     * that could potentially disturb this node's transmission.
     *
     * @param isBusy true if the channel is busy
     */
    void setNetworkState(boolean isBusy);

    /**
     * Notifies the node if it started transmitting a data packet.
     */
    void startSending();

    /**
     * Notifies the node if it finished transmitting a data packet.
     */
    void finishSending();

    /**
     * Notifies the node if it joins the network.
     */
    void joinServer();

    /**
     * Notifies the node if it leaves the network.
     */
    void quitServer();
}
