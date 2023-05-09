import com.group26.node.packet.PacketParser;
import com.group26.node.packet.formats.DataPacket;
import com.group26.node.session.DataReceiver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DataReceiverTest {
    private final String text1 = "Lorem ipsum dolor sit amet, consectetur adipiscing elit sit.";
    private final String text2 = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec lacinia dui leo," +
            " dictum tincidunt libero bibendum non. Vestibulum vestibulum.";
    private final PacketParser parser = new PacketParser();
    private DataPacket[] packets1;
    private DataPacket[] packets2;

    @BeforeEach
    void setUp() {
        packets1 = parser.parseText(text1, (byte) 0, (byte) 0, (byte) 0);
        packets2 = parser.parseText(text2, (byte) 0, (byte) 0, (byte) 0);
    }

    @Test
    void sendPacketsInOrder() {
        DataReceiver receiver = new DataReceiver((byte) packets1.length);
        assertTrue(receiver.receivePacket(packets1[0]));
        assertTrue(receiver.receivePacket(packets1[1]));
        assertTrue(receiver.receivePacket(packets1[2]));
        assertTrue(receiver.hasAllPackets());
        assertEquals(text1, parser.parsePackets(receiver.getPackets()));
    }

    @Test
    void sendPacketsOutOfOrderWithinWindow() {
        DataReceiver receiver = new DataReceiver((byte) packets1.length);
        assertTrue(receiver.receivePacket(packets1[1]));
        assertTrue(receiver.receivePacket(packets1[0]));
        assertTrue(receiver.receivePacket(packets1[2]));
        assertTrue(receiver.hasAllPackets());
        assertEquals(text1, parser.parsePackets(receiver.getPackets()));
    }

    @Test
    void failSendPacketsOutOfOrderOutsideWindow() {
        DataReceiver receiver = new DataReceiver((byte) packets1.length);
        assertTrue(receiver.receivePacket(packets1[1]));
        assertFalse(receiver.receivePacket(packets1[2]));
        assertTrue(receiver.receivePacket(packets1[0]));
        assertFalse(receiver.hasAllPackets());
    }

    @Test
    void sendPacketsOutOfOrderAfterError() {
        DataReceiver receiver = new DataReceiver((byte) packets1.length);
        assertTrue(receiver.receivePacket(packets1[1]));
        assertFalse(receiver.receivePacket(packets1[2]));
        assertTrue(receiver.receivePacket(packets1[0]));
        assertTrue(receiver.receivePacket(packets1[2]));
        assertTrue(receiver.hasAllPackets());
        assertEquals(text1, parser.parsePackets(receiver.getPackets()));
    }

    @Test
    void sendPacketsInBigReceiveWindow() {
        DataReceiver receiver = new DataReceiver((byte) packets2.length);
        assertTrue(receiver.receivePacket(packets2[1]));
        assertTrue(receiver.receivePacket(packets2[0]));
        assertTrue(receiver.receivePacket(packets2[2]));
        assertTrue(receiver.receivePacket(packets2[4]));
        assertTrue(receiver.receivePacket(packets2[3]));
        assertTrue(receiver.hasAllPackets());
        assertEquals(text2, parser.parsePackets(receiver.getPackets()));
    }
}
