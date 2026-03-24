package protocols;

import java.io.IOException;
import java.util.List;

// Sender side of Selective Repeat ARQ.
public class SelectiveAndRepeatARQ_Sender {

    private static final byte ACK = 0x06; // ACK
    private static final byte NAK = 0X21; // NAK
    private final NetworkSender sender;
    private int winBase = 0;
    private final int winSize;

    public SelectiveAndRepeatARQ_Sender(NetworkSender sender, int winSize){
        this.sender = sender;
        this.winBase = 0;
        this.winSize = winSize;
    }

    public void transmit(List<BISYNCPacket> packets) throws IOException {
        int N = packets.size();

        // Receiver needs to know total packet count and window size first.
        sender.sendHandshakeRequest(N, winSize);
        char[] response = sender.waitForResponse();
        if(response[0] != ACK) {
            System.out.println("Handshake failed, exit");
            return;
        } else {
            System.out.println("Handshake succeed, proceed!");
        }

        boolean finished = false;
        boolean[] sent = new boolean[N];

        while(!finished){
            // Fill the current sending window.
            for (int i = winBase; i < Math.min(N, winBase + winSize); i++) {
                if (!sent[i]) {
                    boolean isLastPacket = (i == N - 1);
                    char seqNum = (char) (i % 256);

                    // Non-last packets can be intentionally dropped to simulate loss.
                    if (isLastPacket) {
                        sender.sendPacket(packets.get(i).getPacket(), seqNum, true);
                    } else {
                        sender.sendPacketWithLost(packets.get(i), seqNum, false);
                    }
                    sent[i] = true;
                }
            }

            char[] ackNak = sender.waitForResponse();
            if (ackNak[0] == ACK) {
                int ackNum = ackNak[1];
                int newBase = winBase;

                // ACK contains the first sequence number not yet received in order,
                // so slide the sender window up to that point.
                while (newBase < N && (newBase % 256) != ackNum) {
                    newBase++;
                }
                if (newBase > winBase) {
                    winBase = newBase;
                }
                if (winBase >= N) {
                    finished = true;
                }
            } else if (ackNak[0] == NAK) {
                int missingSeq = ackNak[1];

                // Only resend the missing packet requested by the receiver.
                for (int i = winBase; i < Math.min(N, winBase + winSize); i++) {
                    if ((i % 256) == missingSeq) {
                        boolean isLastPacket = (i == N - 1);
                        sender.sendPacket(packets.get(i).getPacket(), (char) missingSeq, isLastPacket);
                        break;
                    }
                }
            }
        }
    }
}
