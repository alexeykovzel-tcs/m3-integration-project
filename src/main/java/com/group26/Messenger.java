package com.group26;

import com.group26.node.NetworkTopology;

import java.util.Set;
import java.util.concurrent.BlockingQueue;

/**
 * Interface that determines the means of interaction with the chat application.
 */
public interface Messenger {
    /**
     * Broadcasts a chat message to the network.
     *
     * @param text message text
     * @return true if successfully sent
     */
    boolean sendChatMessage(String text);

    /**
     * Waits until the user can send messages to the chat.
     */
    void awaitReadyToSend();

    /**
     * @return the user id that it acquired after joining the server.
     */
    byte getUserId();

    /**
     * @return the shared queue where received messages from other nodes are stored.
     */
    BlockingQueue<ChatMessage> getChatMessages();

    /**
     * @return topology of the network
     */
    NetworkTopology getTopology();
}
