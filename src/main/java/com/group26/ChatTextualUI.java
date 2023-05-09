package com.group26;

import com.group26.node.NetworkTopology;
import com.group26.node.routing.NodeLinkState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Textual UI of the chat application that interacts with the user via the system console.
 */
public class ChatTextualUI implements Runnable {
    private final String[] reservedUsernames = new String[]{"Red", "Green", "Blue", "Cyan", "Purple"};
    private final Messenger messenger;
    private boolean readyToSend;

    public ChatTextualUI(Messenger messenger) {
        this.messenger = messenger;
    }

    /**
     * Reads user input from the console and calls corresponding methods of the messenger. Also, it calls a method
     * waiting the "Ready to send" phase and the method handling the incoming chat messages.
     */
    @Override
    public void run() {
        handleChatMessages();
        awaitReadyToSend();
        try {
            String line;
            var in = new BufferedReader(new InputStreamReader(System.in));
            while ((line = in.readLine()) != null) {
                if (!readyToSend) {
                    System.out.println("Please wait a bit longer...");
                    continue;
                }
                switch (line) {
                    case "/help":
                        System.out.println("You can use the following commands:\n\n" +
                                "/help          get possible commands\n" +
                                "/users         get a list of online users\n" +
                                "/neighbors     get neighbors of each user\n" +
                                "/quit          quit the server\n");
                        break;
                    case "/users":
                        System.out.println("Online users: " + String.join(", ", getOnlineUsers()));
                        break;
                    case "/neighbors":
                        System.out.println("Current neighbors:");
                        System.out.println(String.join("\n", getUserNeighbors()));
                        break;
                    case "/quit":
                        System.out.println("Quiting server...");
                        System.exit(0);
                    default:
                        if (!messenger.sendChatMessage(line)) {
                            System.out.println("Could not send a message...");
                        }
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to get user input...");
        }
    }

    /**
     * Waits until the user is allowed to send messages after joining the server. After that, it prints the
     * introduction text, notifying the user about their username.
     */
    private void awaitReadyToSend() {
        new Thread(() -> {
            messenger.awaitReadyToSend();
            try {
                TimeUnit.SECONDS.sleep(2);
                System.out.println("\nWelcome to chat 'Group 26'\n" +
                        "Your username is " + getUsername(messenger.getUserId()) +
                        "\nType a message or '/help' to see additional commands");
                readyToSend = true;
            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    /**
     * Takes received chat messages from the shared queue with the messenger and prints them into
     * terminal using a custom format.
     */
    private void handleChatMessages() {
        BlockingQueue<ChatMessage> messages = messenger.getChatMessages();
        new Thread(() -> {
            while (true) {
                try {
                    ChatMessage message = messages.take();
                    System.out.printf("[%s] %s: %s\n", message.getTimestamp(),
                            getUsername(message.getSenderId()), message.getText());
                } catch (InterruptedException ignored) {
                }
            }
        }).start();
    }

    /**
     * Assign a human-readable username to the provided id. If this id is one of the reserved for testing (1..5),
     * then it is assigned with a corresponding color.
     *
     * @param id id of the user
     * @return converted id into string representation
     */
    private String getUsername(byte id) {
        String username = "User " + id;
        if (reservedUsernames.length >= id) {
            username = reservedUsernames[id - 1];
        }
        return username;
    }

    /**
     * @return records about user neighbors.
     */
    private List<String> getUserNeighbors() {
        List<String> records = new ArrayList<>();
        for (NodeLinkState linkState : messenger.getTopology().getLinkStates().values()) {
            String username = getUsername(linkState.getNodeId());
            Set<Byte> neighbor = linkState.getNeighborIds();
            String neighborRecords = !neighbor.isEmpty()
                    ? neighbor.stream().map(this::getUsername).collect(Collectors.joining(", "))
                    : "None";
            records.add(username + ": " + neighborRecords);
        }
        return records;
    }

    /**
     * Returns a list of users that are assumed to be currently online. User is considered online
     * if it is referred at least once in somebody's link state.
     *
     * @return an array of usernames that are currently on the server
     */
    private List<String> getOnlineUsers() {
        List<String> onlineUsers = new ArrayList<>();
        NetworkTopology topology = messenger.getTopology();
        onlineUsers.add(getUsername(messenger.getUserId()));

        // go through the taken ids on the network
        for (byte id : topology.getTakenIds()) {
            if (id == messenger.getUserId()) continue;
            for (NodeLinkState linkState : topology.getLinkStates().values()) {
                // conclude that this user is online it he/she is somebody's neighbor
                if (linkState.getNeighborIds().contains(id)) {
                    onlineUsers.add(getUsername(id));
                    break;
                }
            }
        }
        return onlineUsers;
    }
}
