package com.group26.node.routing;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Algorithm that determines the broadcasting path through the network using the link states of its participants.
 * The information contains the receivers of that each node should transmit to.
 */
public class BroadcastProtocol {
    private final Map<Byte, NodeLinkState> linkStates;

    public BroadcastProtocol(Map<Byte, NodeLinkState> linkStates) {
        this.linkStates = new HashMap<>(linkStates);
    }

    /**
     * Determines packet transmitters to broadcast the packet through the network according to the source node
     * and the link states of the network participants.
     *
     * @param source source node id of the transmitting message
     * @return the list of packet receivers for each node
     */
    public Map<Byte, Set<Byte>> getTransmitters(byte source) {
        Map<Byte, Set<Byte>> transmitters = new HashMap<>();
        Set<Byte> leftReceivers = new HashSet<>(linkStates.keySet());
        Set<Byte> candidates = new HashSet<>();
        leftReceivers.remove(source);
        candidates.add(source);

        while (leftReceivers.size() != 0) {
            // choose the next best transmitter
            Pair<Byte, Set<Byte>> bestTransmitter = getBestTransmitter(candidates, leftReceivers);
            byte transmitterId = bestTransmitter.getLeft();

            // add new candidates as neighbors of the transmitter node
            if (linkStates.containsKey(transmitterId)) {
                Set<Byte> transmitterNeighbors = linkStates.get(transmitterId).getNeighborIds();
                Set<Byte> newCandidates = new HashSet<>(transmitterNeighbors);
                newCandidates.removeAll(transmitters.keySet());
                candidates.addAll(newCandidates);

                // save the transmitter and its receivers
                Set<Byte> receivers = bestTransmitter.getRight();
                leftReceivers.removeAll(receivers);
                transmitters.put(transmitterId, receivers);
            } else {
                candidates.remove(transmitterId);
            }
        }
        return transmitters;
    }

    /**
     * Calculates the best transmitter among candidates for the corresponding receivers.
     *
     * @param candidates candidate transmitters
     * @param receivers  packet receivers
     * @return the best transmitter for receivers
     */
    private Pair<Byte, Set<Byte>> getBestTransmitter(Set<Byte> candidates, Set<Byte> receivers) {
        Set<Byte> bestReceivers = Set.of();
        byte bestTransmitter = -1;

        // go through the candidates and choose the best
        for (byte candidate : candidates) {
            NodeLinkState candidateLinkState = linkStates.get(candidate);
            if (candidateLinkState != null) {
                Set<Byte> candidateNeighbors = candidateLinkState.getNeighborIds();
                Set<Byte> candidateReceivers = new HashSet<>(candidateNeighbors);
                candidateReceivers.retainAll(receivers);

                // replace the best candidate if the receiver count is higher,
                // or if the receiver count is the same, but an id is lower
                if ((bestReceivers.size() == candidateReceivers.size() && candidate > bestTransmitter)
                        || (candidateReceivers.size() > bestReceivers.size())) {
                    bestTransmitter = candidate;
                    bestReceivers = candidateReceivers;
                }
            }
        }
        return new ImmutablePair<>(bestTransmitter, bestReceivers);
    }
}
