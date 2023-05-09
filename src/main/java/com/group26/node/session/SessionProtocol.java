package com.group26.node.session;

import com.group26.node.NetworkTopology;
import com.group26.node.packet.PacketSender;
import com.group26.node.packet.formats.DataAckPacket;
import com.group26.node.packet.formats.DataPacket;
import com.group26.node.packet.formats.SessionUpdate;
import com.group26.node.routing.BroadcastProtocol;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class that handles transmission sessions (either incoming or outgoing). Also, it received all the data packets
 * and its acks and redirects to the corresponding data transmitters or receivers.
 */
public class SessionProtocol {
    private final Lock lock = new ReentrantLock();
    private final Condition hasSessionAcks = lock.newCondition();
    private final PacketSender sender = PacketSender.getInstance();
    private final Queue<DataPacket[]> sendingQueue = new LinkedList<>();
    private final Map<Byte, DataReceiver> dataReceivers = new HashMap<>();
    private final NetworkTopology topology;
    private final Set<Byte> sessionAcks = new HashSet<>();
    private DataTransmitter transmitter;
    private boolean inSession = false;

    public SessionProtocol(NetworkTopology topology) {
        this.topology = topology;
    }

    /**
     * Handles session updates. First, by determining whether it is the node's or the foreign session update.
     * Then, the node receives it as an acknowledgement to its session or the invitation to the foreign
     * session correspondingly.
     *
     * @param packet packet containing session update
     */
    public void handleUpdate(SessionUpdate packet) {
        try {
            lock.lock();
            boolean foreignSession = packet.getSourceId() != topology.getNodeId();
            if (foreignSession) {
                var collector = new DataReceiver(packet.getPacketCount());
                dataReceivers.put(packet.getSourceId(), collector);
                replyToForeignSession(packet);
            } else if (inSession) {
                sessionAcks.add(packet.getSenderId());
                if (sessionAcks.containsAll(topology.getNeighborIds())) {
                    hasSessionAcks.signalAll();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Starts a session by first establishing the connection with the neighbor nodes and then transmitting
     * an arbitrary number of packets to the node's neighbors.
     *
     * @param packets transmitting packets
     */
    public void sendPackets(DataPacket[] packets, Set<Byte> receivers, boolean inSession) {
        if (packets.length == 0 || receivers.isEmpty()) return;
        if (this.inSession) {
            sendingQueue.add(packets);
            return;
        }

        // start a new session
        this.inSession = true;
        new Thread(() -> {
            if (inSession) {
                // fail to start a session if there are no receivers or did not receive all acks
                if (receivers.isEmpty() || !startSession((byte) packets.length, receivers)) {
                    System.out.println("Error occurred during session...");
                    closeSession();
                    return;
                }
            }

            // init transmitter
            transmitAfterDelay(packets, receivers);
        }).start();
    }

    /**
     * Redirects the received data packet to the corresponding data receiver.
     *
     * @param packet received data packet
     * @return all the packets if it was the last one
     */
    public DataPacket[] handleDataPacket(DataPacket packet) {
        byte sourceId = packet.getSourceId();
        DataReceiver receiver = dataReceivers.get(sourceId);
        if (receiver != null && receiver.receivePacket(packet)) {
            byte nodeId = topology.getNodeId();
            Map<Byte, Set<Byte>> transmitters = new BroadcastProtocol(topology.getLinkStates()).getTransmitters(sourceId);
            Set<Byte> peerIds = transmitters.get(packet.getSenderId());
            long order = peerIds.stream().filter((id) -> id < nodeId).count();

            // send acknowledgement to received data
            sender.scheduleMessage(new DataAckPacket(nodeId, packet.getSourceId(),
                    packet.getSequence()).toMessage(), order * 100L, 100 + order * 100L);

            // return all packets if available
            if (receiver.hasAllPackets()) {
                DataPacket[] packets = receiver.getPackets();
                dataReceivers.remove(packet.getSourceId());

                // transmit the packet further using multi-hop
                Set<Byte> receivers = transmitters.get(nodeId);
                if (receivers != null && !receivers.isEmpty()) {
                    sendPackets(packets, receivers, false);
                }
                return packets;
            }
        }
        return null;
    }

    /**
     * Handles data acknowledgement if the node is currently in session.
     *
     * @param packet data acknowledgement packet
     */
    public void handleDataAckPacket(DataAckPacket packet) {
        if (packet.getSourceId() == topology.getNodeId() && inSession) {
            transmitter.receiveDataAckPacket(packet);
        }
    }

    /**
     * Starts a new session by broadcasting the corresponding packet and listening to acks.
     *
     * @param packetCount the number of transmitting packets during the session
     * @param receivers   session participant ids
     * @return whether the session has been started
     */
    private boolean startSession(byte packetCount, Set<Byte> receivers) {
        byte nodeId = topology.getNodeId();
        var packet = new SessionUpdate(nodeId, nodeId, packetCount);
        int timeout = topology.getNeighborIds().size() * 1000;
        sender.sendReliableMessage(packet, 200, 500, 2, timeout, receivers);

        try {
            lock.lock();
            hasSessionAcks.await(5, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            return false;
        } finally {
            lock.unlock();
            sessionAcks.clear();
        }
    }

    /**
     * Starts the transmission of packets after a safe delay. Then, it closes the session and starts the next
     * session if there are messages in the queue.
     *
     * @param packets   sending packets
     * @param receivers packet receivers
     */
    private void transmitAfterDelay(DataPacket[] packets, Set<Byte> receivers) {
        transmitter = new DataTransmitter(packets, receivers);
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            transmitter.transmit();
            closeSession();

            // handles next message if necessary
            DataPacket[] nextPackets = sendingQueue.poll();
            if (nextPackets != null) {
                sendPackets(nextPackets, receivers, true);
            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Replies to the foreign session and transmits it further if necessary.
     *
     * @param packet packet containing session update
     */
    private void replyToForeignSession(SessionUpdate packet) {
        byte nodeId = topology.getNodeId();

        // determine whether the node should respond
        Map<Byte, Set<Byte>> transmitters = new BroadcastProtocol(topology.getLinkStates()).getTransmitters(packet.getSourceId());
        Set<Byte> responders = transmitters.get(packet.getSenderId());
        if (responders == null || !responders.contains(nodeId)) return;

        // update the sender as the current node
        packet.setSenderId(nodeId);
        if (transmitters.containsKey(nodeId)) {
            Set<Byte> receivers = transmitters.get(nodeId);
            sender.sendReliableMessage(packet, 200, 500, 2, 1000, receivers);
        } else {
            sender.scheduleMessage(packet.toMessage(), 200, 500);
        }
    }

    /**
     * Closes the node's current session.
     */
    private void closeSession() {
        inSession = false;
        transmitter = null;
    }
}
