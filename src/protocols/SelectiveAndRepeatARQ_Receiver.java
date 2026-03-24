package protocols;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectiveAndRepeatARQ_Receiver {

    private static final byte ACK = 0x06; // ACK
    private static final byte NAK = 0X21; // NAK
    private final int port;
    private final String outputFile;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private final List<byte[]> receivedData;
    private int totalPacketsReceived;

    public SelectiveAndRepeatARQ_Receiver(int port, int winSize, String outputFile){
        this.port = port;
        this.outputFile = outputFile;
        this.running = false;
        this.receivedData = new ArrayList<>();
        this.totalPacketsReceived = 0;
    }

    private void ensureCapacity(int N) {
        while (receivedData.size() <= N) {
            receivedData.add(null);
        }
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Receiver listening on port " + port);
        Socket clientSocket = serverSocket.accept();
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

        Set<Integer> nakPackets = new HashSet<>();

        int N = in.readInt();
        int winSize = in.readInt();
        out.writeChar(ACK);
        out.writeChar(0);
        out.flush();

        int winBase = 0;
        Boolean[] flags = new Boolean[N];
        for(int i = 0; i < N; i++) {
            flags[i] = false;
        }
        ensureCapacity(N);

        while(running){
            try{
                int packetLength = in.readInt();
                char packetIndex = in.readChar();
                boolean isLastPacket = in.readBoolean();

                byte[] packetData = new byte[packetLength];
                in.readFully(packetData);
                BISYNCPacket packet = new BISYNCPacket(packetData, true);

                if (!packet.isValid()) {
                    out.writeChar(NAK);
                    out.writeChar((char) (winBase % 256));
                    out.flush();
                    continue;
                }

                int absoluteIndex = -1;
                for (int i = winBase; i < Math.min(N, winBase + winSize); i++) {
                    if ((i % 256) == (int) packetIndex) {
                        absoluteIndex = i;
                        break;
                    }
                }

                if (absoluteIndex == -1) {
                    out.writeChar(ACK);
                    out.writeChar((char) (winBase % 256));
                    out.flush();
                    continue;
                }

                if (!flags[absoluteIndex]) {
                    flags[absoluteIndex] = true;
                    receivedData.set(absoluteIndex, packet.getData());
                    totalPacketsReceived++;
                }

                if (absoluteIndex == winBase) {
                    while (winBase < N && flags[winBase]) {
                        nakPackets.remove(winBase);
                        winBase++;
                    }
                    out.writeChar(ACK);
                    out.writeChar((char) (winBase % 256));
                    out.flush();
                } else {
                    for (int i = winBase; i < absoluteIndex; i++) {
                        if (!flags[i] && !nakPackets.contains(i)) {
                            out.writeChar(NAK);
                            out.writeChar((char) (i % 256));
                            out.flush();
                            nakPackets.add(i);
                        }
                    }
                }

                if (totalPacketsReceived == N || (isLastPacket && winBase >= N)) {
                    running = false;
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error handling client: " + e.getMessage());
                }
            }
        }

        saveFile();
        stop();
    }

    private void saveFile() {
        try {
            int totalSize = 0;
            for (byte[] data : receivedData) {
                if (data != null) {
                    totalSize += data.length;
                }
            }

            byte[] completeFile = new byte[totalSize];
            int offset = 0;
            for (byte[] data : receivedData) {
                if (data != null) {
                    System.arraycopy(data, 0, completeFile, offset, data.length);
                    offset += data.length;
                }
            }

            Files.write(Paths.get(outputFile), completeFile);
            System.out.println("File saved successfully: " + outputFile);
        } catch (IOException e) {
            System.err.println("Error saving file: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
        }
    }
}
