package com.group26.node;

import com.group26.ChatMessage;
import com.group26.Messenger;
import com.group26.node.addressing.AddressProtocol;
import com.group26.client.Message;
import com.group26.node.packet.PacketFormat;
import com.group26.node.packet.PacketParser;
import com.group26.node.packet.formats.*;
import com.group26.node.packet.PacketLogger;
import com.group26.node.routing.LinkStateProtocol;
import com.group26.node.packet.PacketSender;
import com.group26.node.session.SessionProtocol;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Class that provides a means of communication between the framework and the user in the context of chat
 * application. It controls the node provided by the framework from joining until leaving the server. Also, it is
 * able to interact with other nodes on the server by exchanging data packets.
 */
public class NodeController implements VirtualNode, Messenger {
    private static final int RETRANSMISSION_TIMEOUT = 1000;
    private static final int RELIABLE_PING_SEQUENCE = 2;

    private final Lock lock = new ReentrantLock();
    private final Condition readyToSend = lock.newCondition();
    private final HashMap<PacketFormat, Consumer<ByteBuffer>> packetHandlers = new HashMap<>();
    private final BlockingQueue<ChatMessage> chatMessages = new LinkedBlockingQueue<>();
    private final PacketSender sender = PacketSender.getInstance();
    private final PacketLogger logger = PacketLogger.getInstance();
    private final NetworkTopology topology = new NetworkTopology();
    private final LinkStateProtocol routingProtocol;
    private final SessionProtocol sessionProtocol;
    private final AddressProtocol addressProtocol;
    private NodeState nodeState;
    private int pingSequence;

    public NodeController() {
        routingProtocol = new LinkStateProtocol(topology);
        sessionProtocol = new SessionProtocol(topology);
        addressProtocol = new AddressProtocol(topology);
        initPacketHandlers();
    }

    /**
     * Finds out about the packet format by the first 4 bits of the packet. Then, according to this information,
     * calls the corresponding packet handler.
     *
     * @param data packet stored in bytes
     */
    @Override
    public void handlePacket(ByteBuffer data) {
        try {
            lock.lock();
            PacketFormat format = PacketFormat.byId(Math.abs(data.get(0) >> 4));
            Consumer<ByteBuffer> handler = packetHandlers.get(format);
            if (handler != null) {
                handler.accept(data);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sends chat messages by first parsing them into data packets, and then starting a new transmission session
     * using the session protocol. If the message takes more than 16 packets to send, then it gets discarded
     * (as there is only 4 bits reserved for the packet count)
     *
     * @param text message text
     * @return whether the message has been sent
     */
    @Override
    public boolean sendChatMessage(String text) {
        try {
            lock.lock();
            chatMessages.put(new ChatMessage(text, addressProtocol.getAddress()));
            DataPacket[] packets = new PacketParser().parseText(text,
                    (byte) 0, topology.getNodeId(), topology.getNodeId());

            if (packets.length > 16) return false;
            sessionProtocol.sendPackets(packets, topology.getNeighborIds(), true);
            return true;
        } catch (InterruptedException ignored) {
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public NetworkTopology getTopology() {
        return topology;
    }

    @Override
    public BlockingQueue<ChatMessage> getChatMessages() {
        return chatMessages;
    }

    /**
     * Waits until the node is ready to send chat messages.
     */
    @Override
    public void awaitReadyToSend() {
        try {
            lock.lock();
            readyToSend.await();
        } catch (InterruptedException ignored) {
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the user id that is assigned using the address protocol.
     */
    @Override
    public byte getUserId() {
        return addressProtocol.getAddress();
    }

    /**
     * Handles starting sending a message.
     */
    @Override
    public void startSending() {
        // ...
    }

    /**
     * Notifies the sender about finishing sending a message. Also, if the node is in the "Finding Neighbors" state,
     * then the sending packet was a ping, and if it reached the reliable sequence, then switch to the "Assign ID"
     * step after delay.
     */
    @Override
    public void finishSending() {
        sender.finishSending();
        // during the 'finding neighbors' stage, the node ONLY sends ping requests
        if (nodeState == NodeState.FINDING_NEIGHBORS) {
            pingSequence += 1;
            if (pingSequence == RELIABLE_PING_SEQUENCE) {
                pingSequence = 0; // reset ping sequence
                // wait until last pong responses and start 'assigning id' stage
                Executors.newSingleThreadScheduledExecutor().schedule(
                        this::assignNodeId, 1000, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Handles joining the server. Switches to "Finding neighbors" as the first phase
     */
    @Override
    public void joinServer() {
        findNeighborNodes();
    }

    /**
     * Handles quitting the server.
     */
    @Override
    public void quitServer() {
        // ...
    }

    /**
     * Notifies the packet sender about changes in the network state.
     *
     * @param isBusy true if the channel is busy
     */
    @Override
    public void setNetworkState(boolean isBusy) {
        sender.setNetworkState(isBusy);
    }

    /**
     * Initialises methods that are intended for the corresponding packets that the node receives.
     */
    private void initPacketHandlers() {
        packetHandlers.put(PacketFormat.LINK_STATE_UPDATE, this::handleLinkStateUpdate);
        packetHandlers.put(PacketFormat.LINK_STATE_REQUEST, this::handleLinkStateRequest);
        packetHandlers.put(PacketFormat.SESSION_UPDATE, this::handleSessionUpdate);
        packetHandlers.put(PacketFormat.REQUEST_ID, this::handleAddressRequestPacket);
        packetHandlers.put(PacketFormat.ISSUE_ID, this::handleAddressResponsePacket);
        packetHandlers.put(PacketFormat.PING_PONG, this::handlePingPongPacket);
        packetHandlers.put(PacketFormat.DATA_ACK, this::handleDataAckPacket);
        packetHandlers.put(PacketFormat.DATA, this::handleDataPacket);
    }

    /**
     * Records the link state update in the logger, then it redirects the packet to the routing protocol
     * and switches to "Ready to send" state if it was a part of "Pulling topology" step and the node
     * received full topology.
     *
     * @param data packet data in bytes.
     */
    private void handleLinkStateUpdate(ByteBuffer data) {
        try {
            lock.lock();
            LinkStateUpdate packet = new LinkStateUpdate(data);
            logger.addRecord(packet);
            routingProtocol.handleUpdate(packet);

            // check if the node got full topology
            if (nodeState == NodeState.PULLING_TOPOLOGY && routingProtocol.hasFullTopology()) {
                setNodeState(NodeState.READY_TO_SEND);
                routingProtocol.sendUpdate();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records the received link state request in the logger. Then, it redirects the packet to the routing protocol.
     *
     * @param data packet data in bytes
     */
    private void handleLinkStateRequest(ByteBuffer data) {
        try {
            lock.lock();
            LinkStateRequest packet = new LinkStateRequest(data);
            logger.addRecord(packet);
            routingProtocol.handleRequest(packet);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles address requests intended for the current node. If so, it provides the source node with
     * a free id in the network and pushes the full network topology. If is necessary so that the node can
     * easily adapt and be ready to participate in sending messages.
     *
     * @param data packet data in bytes
     */
    private void handleAddressRequestPacket(ByteBuffer data) {
        try {
            lock.lock();
            RequestAddressPacket packet = new RequestAddressPacket(data);
            boolean requested = addressProtocol.handleAddressRequest(packet);
            if (requested) {
                routingProtocol.pushNetworkTopology();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records the received address response in the logger. Then, it redirects it to the addressing protocol.
     * If the current node was the one requesting this address, then it assigns this id to itself and expects
     * the full network topology from the id provider.
     *
     * @param data packet data in bytes
     */
    private void handleAddressResponsePacket(ByteBuffer data) {
        try {
            lock.lock();
            IssueAddressPacket packet = new IssueAddressPacket(data);
            boolean assignedId = addressProtocol.handleAddressResponse(packet);
            logger.addRecord(packet);

            if (assignedId) {
                // save the node's link-state and start pulling topology
                setNodeState(NodeState.PULLING_TOPOLOGY);
                routingProtocol.setNodeId(addressProtocol.getAddress());
                routingProtocol.pullNetworkTopology(addressProtocol.getIdProvider());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records the received ping pong packet in the logger. Then, it notifies the routing protocol about the
     * neighbor activity and responds with a pong if necessary.
     *
     * @param data packet data in bytes
     */
    private void handlePingPongPacket(ByteBuffer data) {
        try {
            lock.lock();
            var packet = new PingPongPacket(data);
            logger.addRecord(packet);

            // notify routing protocol about neighbor activity
            routingProtocol.handleNeighborActivity(packet.getSenderId());

            // send pong response
            if (addressProtocol.getAddress() != 0 && nodeState == NodeState.READY_TO_SEND && !packet.isPong()) {
                Message pong = new PingPongPacket(addressProtocol.getAddress(), true).toMessage();
                sender.scheduleMessage(pong, 200, 500);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records the received session update in the logger. Then, it redirects the packet to the session protocol.
     *
     * @param data packet data in bytes
     */
    private void handleSessionUpdate(ByteBuffer data) {
        try {
            lock.lock();
            var packet = new SessionUpdate(data);
            logger.addRecord(packet);
            sessionProtocol.handleUpdate(packet);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records the received packet in the logger. Then, it redirects the packet to the session protocol to
     * link to the corresponding transmission session. If it was the last packet in the session, then
     * reassembles the received packets to get an initial message and prints it.
     *
     * @param data packet data in bytes
     */
    private void handleDataPacket(ByteBuffer data) {
        try {
            lock.lock();
            var packet = new DataPacket(data);
            logger.addRecord(packet);

            // check if was the last packet in the session
            DataPacket[] packets = sessionProtocol.handleDataPacket(packet);
            if (packets != null) {
                // reassemble an initial message and print it
                String text = new PacketParser().parsePackets(packets);
                chatMessages.put(new ChatMessage(text, packet.getSourceId()));
            }
        } catch (InterruptedException ignored) {
        } finally {
            lock.unlock();
        }
    }
    /**
     * Records the received data ack in the logger. Then, it redirects the packet to the session protocol.
     *
     * @param data packet data in bytes
     */
    private void handleDataAckPacket(ByteBuffer data) {
        try {
            lock.lock();
            var packet = new DataAckPacket(data);
            logger.addRecord(packet);
            sessionProtocol.handleDataAckPacket(packet);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Assigns the id of the current node, triggered after finding neighbours.
     */
    private void assignNodeId() {
        try {
            lock.lock();
            setNodeState(NodeState.ASSIGNING_ID);
            boolean hasNeighbors = addressProtocol.startAddressing(topology.getNeighborIds());
            if (!hasNeighbors) {
                setNodeState(NodeState.READY_TO_SEND);
                routingProtocol.setNodeId(addressProtocol.getAddress());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Finds neighbors by pinging several times and waiting for their responses.
     */
    private void findNeighborNodes() {
        try {
            lock.lock();
            setNodeState(NodeState.FINDING_NEIGHBORS);
            Message ping = new PingPongPacket(addressProtocol.getAddress(), false).toMessage();
            sender.repeatSendMessage(ping, RETRANSMISSION_TIMEOUT, RELIABLE_PING_SEQUENCE);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates the link-state of the current node.
     *
     * @param nodeState link-state of the current node
     */
    private void setNodeState(NodeState nodeState) {
        try {
            lock.lock();
            this.nodeState = nodeState;
            routingProtocol.setNodeState(nodeState);
            System.out.println("[STATE] : " + nodeState);
            if (nodeState == NodeState.READY_TO_SEND) {
                readyToSend.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }
}
