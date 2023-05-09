package com.group26;

import com.group26.client.*;
import com.group26.node.NodeController;
import com.group26.node.MessageListener;
import com.group26.node.packet.PacketSender;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Class that launches the chat application and connects to the UT simulation server on the reserved frequency.
 * The application consists of the chat interface, the framework listener, and the node controller.
 */
public class ChatApplication {
    private static final String SERVER_IP = "netsys.ewi.utwente.nl";    // 127.0.0.1 for audio interface tool
    private static final int SERVER_PORT = 8954;                        // server port
    private final int frequency;                                        // server frequency

    public ChatApplication(int frequency) {
        this.frequency = frequency;
    }

    public static void main(String[] args) {
        int frequency = (args.length > 0) ? Integer.parseInt(args[0]) : 3300;
        new ChatApplication(frequency).launch();
    }

    /**
     * Connects application to the network. Then, it launches the node controller and the chat interface.
     */
    private void launch() {
        BlockingQueue<Message> receivedQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> sendingQueue = PacketSender.getInstance().getQueue();
        NodeController controller = new NodeController();

        // give chat client the queues to use
        new Client(SERVER_IP, SERVER_PORT, frequency, receivedQueue, sendingQueue);

        // interact with chat via system GUI
        var chatInterface = new Thread(new ChatTextualUI(controller));
        chatInterface.start();

        // handle received messages
        var messageListener = new Thread(new MessageListener(receivedQueue, controller));
        messageListener.start();

        try {
            messageListener.join();
            chatInterface.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}