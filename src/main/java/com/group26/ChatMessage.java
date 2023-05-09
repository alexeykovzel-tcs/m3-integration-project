package com.group26;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Class that defined the chat message structure, containing the information about its sender, the message content,
 * and the timestamp of when the message has been delivered in the simple format.
 */
public class ChatMessage {
    private final String timestamp;
    private final byte senderId;
    private final String text;

    public ChatMessage(String text, byte senderId) {
        this.timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        this.senderId = senderId;
        this.text = text;
    }

    /**
     * @return the timestamp when this message has been delivered
     */
    public String getTimestamp() {
        return timestamp;
    }

    /**
     * @return id of the node that sent the given message
     */
    public byte getSenderId() {
        return senderId;
    }

    /**
     * @return text of the message
     */
    public String getText() {
        return text;
    }
}
