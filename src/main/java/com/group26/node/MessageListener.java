package com.group26.node;

import com.group26.client.Message;
import com.group26.client.MessageType;

import java.util.concurrent.BlockingQueue;

/**
 * Class that listens to messages sent by the framework and notifies the node client correspondingly.
 */
public class MessageListener implements Runnable {
    private final BlockingQueue<Message> receivedQueue;
    private final VirtualNode node;

    public MessageListener(BlockingQueue<Message> receivedQueue, VirtualNode node) {
        this.receivedQueue = receivedQueue;
        this.node = node;
    }

    /**
     * Takes messages from the received queue and calls the corresponding methods oа the node client.
     */
    @Override
    public void run() {
        while (true) {
            try {
                Message message = receivedQueue.take();
                MessageType type = message.getType();
                switch (type) {
                    case DATA_SHORT:
                    case DATA:
                        node.handlePacket(message.getData());
                        break;
                    case FREE:
                    case BUSY:
                        node.setNetworkState(type == MessageType.BUSY);
                        break;
                    case SENDING:
                        node.startSending();
                        break;
                    case DONE_SENDING:
                        node.finishSending();
                        break;
                    case HELLO:
                        node.joinServer();
                        break;
                    case END:
                        node.quitServer();
                        System.exit(0);
                }
            } catch (InterruptedException e) {
                System.err.println("Failed to process a packet...");
            }
        }
    }
}
