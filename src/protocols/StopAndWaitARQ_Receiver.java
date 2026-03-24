package protocols;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class StopAndWaitARQ_Receiver {

    private static final byte ACK = 0x06; // ACK
    private static final byte NAK = 0X21; // NAK
    private final int port;
    private final String outputFile;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private final List<byte[]> receivedData;
    private int totalPacketsReceived;
    private int currentPacketIndex;

    public StopAndWaitARQ_Receiver(int port, String outputFile) {
        this.port = port;
        this.outputFile = outputFile;
        this.running = false;
        this.receivedData = new ArrayList<>();
        this.totalPacketsReceived = 0;
        this.currentPacketIndex = 0;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Receiver listening on port " + port);
        Socket clientSocket = serverSocket.accept();
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

        while (running) {
            try{
                int packetLength = in.readInt();
                char packetIndex = in.readChar();
                boolean isLastPacket = in.readBoolean();

                byte[] packetData = new byte[packetLength];
                in.readFully(packetData);
                BISYNCPacket packet = new BISYNCPacket(packetData, true);

                if (!packet.isValid()) {
                    out.writeChar(NAK);
                    out.writeChar((char) (currentPacketIndex % 256));
                    out.flush();
                    continue;
                }

                if ((int) packetIndex == (currentPacketIndex % 256)) {
                    ensureCapacity(currentPacketIndex);
                    receivedData.set(currentPacketIndex, packet.getData());
                    totalPacketsReceived++;
                    currentPacketIndex++;

                    out.writeChar(ACK);
                    out.writeChar((char) (currentPacketIndex % 256));
                    out.flush();

                    if (isLastPacket) {
                        stop();
                    }
                } else {
                    out.writeChar(ACK);
                    out.writeChar((char) (currentPacketIndex % 256));
                    out.flush();
                }

            } catch (IOException e) {
                if (running) {
                    System.err.println("Error handling client: " + e.getMessage());
                }
            }
        }

        saveFile();
    }

    private void ensureCapacity(int index) {
        while (receivedData.size() <= index) {
            receivedData.add(null);
        }
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
