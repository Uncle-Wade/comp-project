package protocols;
/**
 * Author: Wade Douglas, Jay Laird
 * Course: COMP 342 Data Communications and Networking
 * Date: 3-23-2026
 * File: BISYNCPacket.java
 * Description: Defines the BISYNCPacket structure and handles packet creation, checksum logic, and byte stuffing/unstuffing.
 */
import java.io.ByteArrayOutputStream;

// Packet class used to build and parse BISYNC-style frames.
public class BISYNCPacket {
    private static final byte SYN = 0x16;  // Sync character
    private static final byte STX = 0x02;  // Start of text
    private static final byte ETX = 0x03;  // End of text
    private static final byte DLE = 0x10;  // Escape character used for stuffing

    private byte[] header;
    private byte[] stuffedData;   // Data after byte stuffing
    private byte[] originalData;  // Actual payload before stuffing
    private byte[] trailer;
    public int checksum;
    public boolean isValid;

    public BISYNCPacket(byte[] data) {
        this(data, false);
    }

    public BISYNCPacket(byte[] data, boolean stuffed) {
        if (!stuffed) {
            // Creating a fresh packet to send.
            this.originalData = data;
            this.stuffedData = byteStuff(data);
            this.header = createHeader();
            this.checksum = calculateChecksum();
            this.trailer = createTrailer();
            this.isValid = true;
        } else {
            // Parsing a received packet.
            isValid = this.fromPacket(data);
        }
    }

    private byte[] byteStuff(byte[] data) {
        ByteArrayOutputStream stuffed = new ByteArrayOutputStream();

        for (byte b : data) {
            // If the payload byte matches a control byte, insert DLE first
            // so the receiver will treat it as data instead of framing.
            if (b == SYN || b == STX || b == ETX || b == DLE) {
                stuffed.write(DLE);
            }
            stuffed.write(b);
        }

        return stuffed.toByteArray();
    }

    private byte[] byteUnstuff(byte[] stuffedData) {
        ByteArrayOutputStream unstuffed = new ByteArrayOutputStream();

        for (int i = 0; i < stuffedData.length; i++) {
            // If we see an escape byte followed by a control byte,
            // skip the escape byte and only keep the original data byte.
            if (stuffedData[i] == DLE && i + 1 < stuffedData.length) {
                byte next = stuffedData[i + 1];
                if (next == SYN || next == STX || next == ETX || next == DLE) {
                    unstuffed.write(next);
                    i++;
                    continue;
                }
            }
            unstuffed.write(stuffedData[i]);
        }

        return unstuffed.toByteArray();
    }

    private byte[] createHeader() {
        // Frame starts with SYN, SYN, STX.
        return new byte[]{SYN, SYN, STX};
    }

    private byte[] getHeader(byte[] packet){
        byte[] header = new byte[3];
        System.arraycopy(packet, 0, header, 0, header.length);
        return header;
    }

    private byte[] getTrailerAndSetChecksum(byte[] packet){
        byte[] trailer = new byte[3];
        trailer[0] = packet[packet.length - 3];
        trailer[1] = packet[packet.length - 2];
        trailer[2] = packet[packet.length - 1];

        // Last two bytes store the checksum.
        checksum = ((trailer[1] & 0xFF) << 8) + (trailer[2] & 0xFF);
        return trailer;
    }

    private byte[] createTrailer() {
        byte[] trailer = new byte[3];
        trailer[0] = ETX;
        trailer[1] = (byte) ((checksum >> 8) & 0xFF);
        trailer[2] = (byte) (checksum & 0xFF);
        return trailer;
    }

    private int calculateChecksum() {
        long sum = 0;

        // Internet-style 16-bit checksum over the stuffed payload.
        for (int i = 0; i < stuffedData.length - 1; i += 2) {
            sum += (stuffedData[i] & 0xFF) << 8;
            sum += stuffedData[i + 1] & 0xFF;
        }

        // If there is one byte left, pad it in the high-order position.
        if (stuffedData.length % 2 != 0) {
            sum += (stuffedData[stuffedData.length - 1] & 0xFF) << 8;
        }

        // Fold carries back into 16 bits.
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        return (int) (~sum & 0xFFFF);
    }

    public byte[] getPacket() {
        byte[] packet = new byte[header.length + stuffedData.length + trailer.length];
        System.arraycopy(header, 0, packet, 0, header.length);
        System.arraycopy(stuffedData, 0, packet, header.length, stuffedData.length);
        System.arraycopy(trailer, 0, packet, header.length + stuffedData.length, trailer.length);
        return packet;
    }

    public byte[] getData() {
        return originalData;
    }

    public boolean fromPacket(byte[] packet) {
        if (packet.length < 6) {
            throw new IllegalArgumentException("Packet too small");
        }

        // Check the expected frame header.
        if (packet[0] != SYN || packet[1] != SYN || packet[2] != STX) {
            return false;
        }
        this.header = getHeader(packet);

        // The packet must end with ETX before the checksum bytes.
        if(packet[packet.length - 3] != ETX){
            return false;
        }
        this.trailer = getTrailerAndSetChecksum(packet);

        // Extract only the stuffed payload.
        byte[] stuffedData = new byte[packet.length - 6];
        System.arraycopy(packet, 3, stuffedData, 0, packet.length - 6);
        this.stuffedData = stuffedData;

        // Recover original data and verify checksum.
        this.originalData = byteUnstuff(stuffedData);
        return isValid();
    }

    public boolean isValid() {
        return calculateChecksum() == checksum;
    }
}
