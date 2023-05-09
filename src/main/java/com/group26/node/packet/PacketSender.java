package com.group26.node.packet;

import com.group26.client.Message;
import com.group26.node.packet.formats.NetworkPacket;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class that is used to send messages to the nearest nodes in the network. It provides a possibility
 * to send messages reliably, by waiting for the corresponding ack. Also, a message can be sent repeatably,
 * within a given timeout, etc.
 */
public final class PacketSender {
    private static PacketSender SENDER;
    private final Lock lock = new ReentrantLock();
    private final Condition freeNetwork = lock.newCondition();
    private final Condition finishSending = lock.newCondition();
    private final BlockingQueue<Message> sendingQueue = new LinkedBlockingQueue<>();
    private final Queue<DelayedMessage> bufferQueue = new LinkedList<>();
    private final PacketLogger logger = PacketLogger.getInstance();
    private boolean busyNetwork = false;
    private long lastBusyPeriod = -1;
    private long lastFreePeriod = -1;

    /**
     * @return instance of the packet sender
     */
    public synchronized static PacketSender getInstance() {
        if (SENDER == null) {
            SENDER = new PacketSender();
        }
        return SENDER;
    }

    /**
     * Sends a packet with reliability assurance and waits for the receiver acks to arrive. If there are missing
     * acks within the given timeout, then retransmit the packet until there are no more attempts.
     *
     * @param packet        sending packet
     * @param maxDelay         delay within which the packet should be sent
     * @param attempts      retransmission attempts
     * @param timeout       timeout after which to retransmit
     * @param expectingAcks receiver ids that should send an ack
     * @return ids of nodes that did not send an ack
     */
    public Set<Byte> sendReliablePacketAndWait(NetworkPacket packet, int minDelay, int maxDelay, int attempts,
                                               int timeout, Set<Byte> expectingAcks) {
        if (attempts == 0) return expectingAcks;
        scheduleMessage(packet.toMessage(), minDelay, maxDelay);
        Set<Byte> missingAcks = awaitMissingAcks(packet, expectingAcks, timeout);
        if (missingAcks != null && missingAcks.size() != 0) {
            sendReliableMessage(packet, minDelay, maxDelay, attempts - 1, timeout, missingAcks);
        }
        return Set.of();
    }

    /**
     * Sends a packet with reliability assurance and waits for acks to arrive in a separate thread. If there are
     * missing acks, then the packet is retransmitted until there are no more attempts.
     *
     * @param packet        sending packet
     * @param maxDelay      max delay within which the packet should be sent
     * @param attempts      retransmission attempts
     * @param timeout       timeout after which to retransmit
     * @param expectingAcks receiver ids that should send an ack
     */
    public void sendReliableMessage(NetworkPacket packet, int minDelay, int maxDelay, int attempts,
                                    int timeout, Set<Byte> expectingAcks) {
        if (attempts == 0) return;
        scheduleMessage(packet.toMessage(), minDelay, maxDelay);
        new Thread(() -> {
            Set<Byte> missingAcks = awaitMissingAcks(packet, expectingAcks, timeout);
            if (missingAcks != null && missingAcks.size() != 0) {
                sendReliableMessage(packet, minDelay, maxDelay, attempts - 1, timeout, missingAcks);
            }
        }).start();
    }

    /**
     * Awaits the given timeout, after which checks if acks to the corresponding packet have arrived.
     * If not, then return a set of node ids that did not send their ack.
     *
     * @param packet        sending packet
     * @param expectingAcks receiver ids that should send an ack
     * @param timeout       timeout after which to retransmit
     * @return a set of node ids that did not send their ack
     */
    private Set<Byte> awaitMissingAcks(NetworkPacket packet, Set<Byte> expectingAcks, long timeout) {
        if (awaitFinishSending()) {
            long sendingTime = System.currentTimeMillis();
            try {
                TimeUnit.MILLISECONDS.sleep(timeout);   // await the given timeout
                lock.lock();                            // lock the packet sender
                // await free network as there might be still acks arriving
                while (busyNetwork) freeNetwork.await();
                // try to send a message again if there are missing acks

                timeout = System.currentTimeMillis() - sendingTime;
                return logger.getMissingAcks(packet, expectingAcks, timeout);
            } catch (InterruptedException ignored) {
            } finally {
                lock.unlock();
            }
        } else {
            System.out.println("Failed to finish sending a message...");
        }
        return null;
    }

    /**
     * Schedules sending a safe message between the given values.
     *
     * @param message sending message
     * @param from    lower time limit
     * @param to      upper time limit
     */
    public void scheduleMessage(Message message, long from, long to) {
        long delay = (long) (Math.random() * (to - from) + from);
        sendSafeMessage(message, delay);
    }

    /**
     * Repeats sending a message with a given delay.
     *
     * @param message sending message
     * @param delay   delay between sending messages
     * @param times   the number of times that a message is being sent
     */
    public void repeatSendMessage(Message message, long delay, int times) {
        sendSafeMessage(message, 200);
        for (int i = 0; i < times - 1; i++) {
            bufferQueue.add(new DelayedMessage(message, delay));
        }
    }

    /**
     * Sends a message by using a collision avoidance technique. The node waits until the network is free,
     * then waits a given delay and sends a message if the network is still free.
     *
     * @param message sending message
     * @param delay   period after which a node sends a ping packet
     */
    public void sendSafeMessage(Message message, long delay) {
        new Thread(() -> {
            awaitFreeNetwork();
            try {
                TimeUnit.MILLISECONDS.sleep(delay);
                // try to send a message after a given delay
                if (isInterrupted(delay) || !sendMessage(message)) {
                    // try again if was interrupted or failed to send
                    sendSafeMessage(message, delay);
                }
            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    /**
     * Sends a message if the network is free.
     *
     * @param message sending message
     * @return true if the transmission is successful
     */
    private synchronized boolean sendMessage(Message message) {
        try {
            if (!busyNetwork) {
                sendingQueue.put(message);
                return true;
            }
        } catch (InterruptedException ignored) {
        }
        return false;
    }

    /**
     * Waits until the node finishes sending a message.
     *
     * @return true if actually finished
     */
    private boolean awaitFinishSending() {
        try {
            lock.lock();
            return finishSending.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles finishing sending a message.
     */
    public void finishSending() {
        try {
            lock.lock();
            logger.recordLastSending();
            if (!bufferQueue.isEmpty()) {
                DelayedMessage delayedMessage = bufferQueue.poll();
                sendSafeMessage(delayedMessage.message, delayedMessage.delay);
            }
            finishSending.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Changes the network state depending on whether there is any activity in the network.
     *
     * @param isBusy true if the network is busy
     */
    public void setNetworkState(boolean isBusy) {
        try {
            lock.lock();
            this.busyNetwork = isBusy;
            if (!isBusy) {
                lastBusyPeriod = System.currentTimeMillis();
                freeNetwork.signalAll();
            } else {
                lastFreePeriod = System.currentTimeMillis();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits until there is no activity in the network.
     */
    private void awaitFreeNetwork() {
        try {
            lock.lock();
            if (busyNetwork) {
                freeNetwork.await();
            }
        } catch (InterruptedException ignored) {
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if the sending was interrupted within a given delay.
     *
     * @param delay delay after which a check is made
     * @return true if the sending has been interrupted
     */
    private boolean isInterrupted(long delay) {
        long currentTime = System.currentTimeMillis();
        boolean interruptedAfterBusy = (lastBusyPeriod != -1) && (currentTime - lastBusyPeriod) < delay;
        boolean interruptedBeforeBusy = (lastFreePeriod != -1) && (currentTime - lastFreePeriod) < delay;
        return interruptedAfterBusy && interruptedBeforeBusy;
    }

    /**
     * @return sending queue
     */
    public BlockingQueue<Message> getQueue() {
        return sendingQueue;
    }

    /**
     * Class that contains a message that should be sent with a given delay.
     */
    private static class DelayedMessage {
        Message message;
        long delay;

        public DelayedMessage(Message message, long delay) {
            this.message = message;
            this.delay = delay;
        }
    }
}
