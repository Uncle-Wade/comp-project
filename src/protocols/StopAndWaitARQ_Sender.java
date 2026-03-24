package protocols;

import java.io.IOException;
import java.util.List;

public class StopAndWaitARQ_Sender {
    private static final byte ACK = 0x06; // ACK
    private static final byte NAK = 0X21; // NAK
    private final NetworkSender sender;
    private char currSeqNumber = 0; // 0 - 255

    public StopAndWaitARQ_Sender(NetworkSender sender){
        this.sender = sender;
        this.currSeqNumber = 0;
    }

    public void transmit(List<BISYNCPacket> packets){
        for (int i = 0; i < packets.size(); i++) {
            BISYNCPacket packet = packets.get(i);
            boolean packetReceived = false;
            boolean isLastPacket = (i == packets.size() - 1);

            // TODO: Task 2.a, Your code below
            // notice: use sender.sendPacketWithError() to send out packet

            while (!packetReceived) {
                try {
                    sender.sendPacketWithError(packet, currSeqNumber,isLastPacket);

                    char[] reply = sender.waitForResponse();

                    if (reply[0] == ACK) {
                        packetReceived = true;
                    } else if (reply[0] == NAK) {
                        char nakFrame = reply[1];
                        sender.sendPacketWithError(packet,nakFrame,isLastPacket);
                    }

                    currSeqNumber = (char) ((reply[1] + 1) % 256);


                } catch (IOException e) {
                    System.err.println("File error: " + e.getMessage());
                }
            }

        }
    }


}
