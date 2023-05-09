package com.group26.node.packet;

/**
 * Class that determines packet formats used to allocate bytes per fields corresponding to the data these packets
 * contain. Also, they have different usages for the node communication in the network.
 */
public enum PacketFormat {
    LINK_STATE_UPDATE(1),
    LINK_STATE_REQUEST(2),
    SESSION_UPDATE(3),
    REQUEST_ID(4),
    ISSUE_ID(8),
    PING_PONG(5),
    DATA_ACK(6),
    DATA(7);

    public final int id;

    PacketFormat(int id) {
        this.id = id;
    }

    /**
     * Returns a packet format according to its id.
     *
     * @param id given id of the packet format
     * @return packet format
     */
    public static PacketFormat byId(int id) {
        for (PacketFormat format : values()) {
            if (format.id == id) {
                return format;
            }
        }
        return null;
    }
}
