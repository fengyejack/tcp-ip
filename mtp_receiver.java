

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.text.DateFormat;
import java.util.Date;

public class mtp_receiver {

    private int port = 8000;

    private DatagramSocket socket;

    private final static int HAND_SHAKE = 1;

    private final static int HAND_SHAKE_LAST = 2;

    private final static int PACK_SEND = 3;

    private final static int PACK_SEND_FINISH = 6;
    
    //private final static int PACK_SEND_AGAIN = 4;

    private String logFile = "mtp_receiver_log.txt";
    private String newFile;

    private StringBuilder log = new StringBuilder();
    private StringBuilder txt = new StringBuilder();
    
    public mtp_receiver(int port,String filename) throws IOException {
        super();
        this.port = port;
        this.newFile = filename;
        this.socket = new DatagramSocket(this.port);
        System.out.println("server started!!");
    }

    public void receiver() {
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                this.socket.receive(packet);
                byte[] data = packet.getData();
                MTP reveive = new MTP();
                reveive.decode(data);
                reveive.initReceive();
                if(reveive.getType() == HAND_SHAKE_LAST) continue;
                if(reveive.getType() == PACK_SEND_FINISH) break;
                MTP send = new MTP();
                send.setType(reveive.getType());
                send.setSyn(reveive.getSyn());
                send.setSeq(reveive.getAck());
                send.setAck(reveive.getSeq() + reveive.getSize());
                send.setSize(reveive.getSize());
                
                initSendData(send, packet);
                send.initSend();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
    }

    private DatagramPacket initSendData(MTP mtp, DatagramPacket packet) throws IOException {
        byte[] d = mtp.encode();
        packet.setData(d);
        packet.setLength(d.length);
        this.socket.send(packet);
        return packet;
    }

    public static void main(String args[]) throws IOException {
        int port = 0;
        String file = "";
        
        port = Integer.parseInt(args[0]);
        file = args[1];
        
        
        mtp_receiver receiver = new mtp_receiver(port,file);
        
        //File file3 = new File(receiver.logFile);
        
        receiver.receiver();
        //System.out.println(receiver.txt.toString());
        
        File log = new File(receiver.logFile);
        if(log.exists()) log.delete();
        File txt = new File(receiver.newFile);
        if(txt.exists()) txt.delete();
        
        generateFile(receiver.logFile, receiver.log.toString());
        String s = receiver.txt.toString();
        System.out.println(s.length());
        generateFile(receiver.newFile, s);
    }

    private static void generateFile(String filename, String common) {
        FileOutputStream fos = null;
        OutputStreamWriter osw = null;
        try {
            fos = new FileOutputStream(filename);
            osw = new OutputStreamWriter(fos, "UTF-8"); 
            BufferedWriter bWriter = new BufferedWriter(osw);
            bWriter.write(common);
            bWriter.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (osw != null)
                    osw.close();
                if (fos != null)
                    fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private String getTimeNow(){
        Date now = new Date(System.currentTimeMillis());
        DateFormat d8 = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.MEDIUM);
        //display the time
        String str8 = d8.format(now);
        return "[ "+ str8 + " ] ";
    }
    
    public class MTP {
        /* Header */
        // package type
        private int type = 0;
        private int syn = 1;
        private int seq = 0;
        private int ack = 0;
        private int size = 1;
        //the content need to be transmitted
        private byte[] data = null;

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public int getSyn() {
            return syn;
        }

        public void setSyn(int syn) {
            this.syn = syn;
        }

        public int getSeq() {
            return seq;
        }

        public void setSeq(int seq) {
            this.seq = seq;
        }

        public int getAck() {
            return ack;
        }

        public void setAck(int ack) {
            this.ack = ack;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public byte[] getData() {
            return data;
        }

        public void setData(byte[] data) {
            this.data = data;
        }

        public void initReceive() {
            StringBuilder builder = new StringBuilder();
            String string = new String(this.data);
            switch (this.type) {
            case HAND_SHAKE:
                builder.append(getTimeNow()).append("receive an SYN Package, package type:SYN    ");
                builder.append("syn:" + this.syn + "    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + " ");
                System.out.println(builder.toString());
                break;
            case HAND_SHAKE_LAST:
                builder.append(getTimeNow()).append("receive an SYN Package, package type:SYN    ");
                builder.append("syn:" + this.syn + "    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + " ");
                builder.append("\r\n" + getTimeNow()).append("handshake Success!");
                System.out.println(builder.toString());
                break;
            case PACK_SEND :
                builder.append(getTimeNow()).append("receive an DATA Package, package type:DATA    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + "    ");
                builder.append("data size:" + this.size + "    ");
                builder.append("data content:" + string + " ");
                txt.append(string);
                System.out.println(builder.toString());
                break;
            case PACK_SEND_FINISH :
                builder.append(getTimeNow()).append("receive Package finished!! ");
                builder.append("generateLogFile file...");
                System.out.println(builder.toString());
                break;
            default:
                break;
            }

            log.append(builder.toString() + "\n");
        }

        public void initSend() {
            StringBuilder builder = new StringBuilder();
            switch (this.type) {
            case HAND_SHAKE:
                builder.append(getTimeNow()).append("transmit ACK, package type:SYNACK    ");
                builder.append("syn:" + this.syn + "    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + " ");
                System.out.println(builder.toString());
                break;
            case PACK_SEND :
                builder.append(getTimeNow()).append("transmit ACK, package type:DATAACK    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + " ");
                System.out.println(builder.toString());
                break;
            default:
                break;
            }

            log.append(builder.toString() + "\n");
        }

        public byte[] encode() {
            ByteArrayOutputStream bos = null;
            try {
                bos = new ByteArrayOutputStream();
                bos.write(int2bytes(this.type));
                bos.write(int2bytes(this.syn));
                bos.write(int2bytes(this.seq));
                bos.write(int2bytes(this.ack));
                bos.write(int2bytes(this.size));
                if (this.data != null) {
                    bos.write(this.data);
                }
                return bos.toByteArray();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } finally {
                closeStream(bos);
            }
        }

        public void decode(byte[] bytes) {
            ByteArrayInputStream bin = null;
            try {
                bin = new ByteArrayInputStream(bytes);
                this.type = readInt(bin);
                this.syn = readInt(bin);
                this.seq = readInt(bin);
                this.ack = readInt(bin);
                this.size = readInt(bin);
                this.data = new byte[size];
                //System.out.println(new String(this.data) + this.data.length);
                bin.read(data);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                closeStream(bin);
            }
        }

        private void closeStream(Closeable bin) {
            if (bin != null)
                try {
                    bin.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }

        private int readInt(ByteArrayInputStream bin) throws IOException {
            byte[] tmps = new byte[4];
            bin.read(tmps);
            return bytes2Int(tmps);
        }

        private byte[] int2bytes(int val) {
            byte[] data = new byte[4];
            int idx = 0;
            for (int i = 0; i < 4; i++) {
                data[idx++] = (byte) ((val >> 8 * i) & 0x000000FF);
            }
            return data;
        }

        private int bytes2Int(byte[] val) {
            int n = 0;
            for (int i = 3; i >= 0; i--) {
                n = (n << 8 | (val[i] & 0x000000FF));
            }
            return n;
        }
    }
}
