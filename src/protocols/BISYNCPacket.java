package protocols;

import java.io.ByteArrayOutputStream;

public class BISYNCPacket {
    private static final byte SYN = 0x16;  // SYNC character
    private static final byte STX = 0x02;  // Start of Text
    private static final byte ETX = 0x03;  // End of Text
    private static final byte DLE = 0x10;  // Data Link Escape

    private byte[] header;
    private byte[] stuffedData;  // Stores the byte-stuffed data
    private byte[] originalData; // Stores the original data
    private byte[] trailer;
    public int checksum;
    public boolean isValid; // whether this is a valid BISYNCPacket

    public BISYNCPacket(byte[] data) {
        this(data, false);
    }

    public BISYNCPacket(byte[] data, boolean stuffed) {
        if (!stuffed) {
            this.originalData = data;
            this.stuffedData = byteStuff(data);
            this.header = createHeader();
            this.checksum = calculateChecksum();
            this.trailer = createTrailer();
            this.isValid = true;
        } else {
            isValid = this.fromPacket(data);
        }
    }

    private byte[] byteStuff(byte[] data) {
        ByteArrayOutputStream stuffed = new ByteArrayOutputStream();

        for (byte b : data) {
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

        for (int i = 0; i < stuffedData.length - 1; i += 2) {
            sum += (stuffedData[i] & 0xFF) << 8;
            sum += stuffedData[i + 1] & 0xFF;
        }

        if (stuffedData.length % 2 != 0) {
            sum += (stuffedData[stuffedData.length - 1] & 0xFF) << 8;
        }

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

        if (packet[0] != SYN || packet[1] != SYN || packet[2] != STX) {
            return false;
        }
        this.header = getHeader(packet);

        if(packet[packet.length - 3] != ETX){
            return false;
        }
        this.trailer = getTrailerAndSetChecksum(packet);

        byte[] stuffedData = new byte[packet.length - 6];
        System.arraycopy(packet, 3, stuffedData, 0, packet.length - 6);
        this.stuffedData = stuffedData;

        this.originalData = byteUnstuff(stuffedData);
        return isValid();
    }

    public boolean isValid() {
        return calculateChecksum() == checksum;
    }
}
