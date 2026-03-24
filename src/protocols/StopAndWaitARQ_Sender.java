package protocols;

import java.io.IOException;
import java.util.List;

// Sender side of Stop-and-Wait ARQ.
public class StopAndWaitARQ_Sender {
    private static final byte ACK = 0x06; // ACK
    private static final byte NAK = 0X21; // NAK
    private final NetworkSender sender;
    private char currSeqNumber = 0; // sequence number range is 0-255

    public StopAndWaitARQ_Sender(NetworkSender sender){
        this.sender = sender;
        this.currSeqNumber = 0;
    }

    public void transmit(List<BISYNCPacket> packets){
        for (int i = 0; i < packets.size(); i++) {
            BISYNCPacket packet = packets.get(i);
            boolean packetReceived = false;
            boolean isLastPacket = (i == packets.size() - 1);

            while (!packetReceived) {
                try {
                    // Send the current frame and wait until the receiver
                    // confirms it with the next expected sequence number.
                    sender.sendPacketWithError(packet, currSeqNumber, isLastPacket);
                    char[] response = sender.waitForResponse();

                    if (response[0] == ACK && response[1] == (char) ((currSeqNumber + 1) % 256)) {
                        // Correct ACK means the receiver got this packet,
                        // so we can move on to the next one.
                        packetReceived = true;
                        currSeqNumber = (char) ((currSeqNumber + 1) % 256);
                    } else if (response[0] == NAK && response[1] == currSeqNumber) {
                        // Receiver detected corruption, so resend same packet.
                        System.out.println("Sender: retransmitting packet " + (int) currSeqNumber);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error transmitting packet " + i, e);
                }
            }
        }
    }
}
