import com.group26.node.routing.BroadcastProtocol;
import com.group26.node.routing.NodeLinkState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BroadcastProtocolTest {
    private BroadcastProtocol protocol;

    @BeforeEach
    void setUp() {
        Map<Byte, NodeLinkState> linkStates = new HashMap<>();
        List<Set<Byte>> neighborSets = List.of(
                getByteSet(4, 5, 7),
                getByteSet(4, 6, 7),
                getByteSet(4, 8),
                getByteSet(1, 2, 3, 7, 8),
                getByteSet(1),
                getByteSet(2),
                getByteSet(1, 2, 4),
                getByteSet(3, 4));
        for (byte i = 0; i < 8; i++) {
            byte id = (byte) (i + 1);
            linkStates.put(id, new NodeLinkState(id, (byte) 0, neighborSets.get(i)));
        }
        protocol = new BroadcastProtocol(linkStates);
    }

    @Test
    void getBestTransmittersFromAlmostCenter() {
        Map<Byte, Set<Byte>> actual = protocol.getTransmitters((byte) 1);
        Map<Byte, Set<Byte>> expected = Map.of(
                (byte) 1, getByteSet(4, 5, 7),
                (byte) 2, getByteSet(6),
                (byte) 4, getByteSet(2, 3, 8));
        assertEquals(expected, actual);
    }

    @Test
    void getBestTransmittersFromCenter() {
        Map<Byte, Set<Byte>> actual = protocol.getTransmitters((byte) 4);
        Map<Byte, Set<Byte>> expected = Map.of(
                (byte) 4, getByteSet(1, 2, 3, 7, 8),
                (byte) 1, getByteSet(5),
                (byte) 2, getByteSet(6));
        assertEquals(expected, actual);
    }

    @Test
    void getBestTransmittersFromCorner() {
        Map<Byte, Set<Byte>> actual = protocol.getTransmitters((byte) 5);
        Map<Byte, Set<Byte>> expected = Map.of(
                (byte) 5, getByteSet(1),
                (byte) 1, getByteSet(4, 7),
                (byte) 4, getByteSet(2, 3, 8),
                (byte) 2, getByteSet(6));
        assertEquals(expected, actual);
    }

    private static Set<Byte> getByteSet(int... bytes) {
        Set<Byte> byteSet = new HashSet<>();
        for (int b : bytes) byteSet.add((byte) b);
        return byteSet;
    }
}
