import com.group26.node.packet.PacketParser;
import com.group26.node.packet.formats.DataPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PacketParserTest {
    private PacketParser parser;
    private final String text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin pulvinar varius " +
            "lacinia. Duis at ipsum eu leo ultrices tempus. Nunc efficitur semper fermentum. Ut consequat, odio et " +
            "pretium luctus, libero purus congue magna, vel congue justo dolor non tortor. Aliquam imperdiet id leo " +
            "et iaculis. Curabitur sodales nisl at augue tincidunt gravida. Sed blandit magna in neque suscipit, " +
            "vitae posuere lacus congue. Cras commodo urna lacus, id commodo justo laoreet vitae. Proin volutpat " +
            "mauris nec eros.";

    @BeforeEach
    void setUp() {
        parser = new PacketParser();
    }

    @Test
    void parseText() {
        DataPacket[] packets = parser.parseText(text, (byte) 0, (byte) 1, (byte) 2);
        assertEquals(18, packets.length);

        DataPacket packet = packets[0];
        assertEquals(0, packet.getDestinationId());
        assertEquals(1, packet.getSenderId());
        assertEquals(2, packet.getSourceId());
    }

    @Test
    void parsePackets() {
        DataPacket[] packets = parser.parseText(text, (byte) 0, (byte) 1, (byte) 2);
        assertEquals(text, parser.parsePackets(packets));
    }
}