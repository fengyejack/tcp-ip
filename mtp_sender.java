
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class mtp_sender implements Runnable {

    @Override
    public void run() {

    }

    private int port = 8000;

    private DatagramSocket socket;

    //the file need to be processed.
    private String file = "1.txt";

    //maximum widow size
    private int MWS = 100;

    //maximum segment size
    private int MSS = 80;
    
    private int timeout = 10000000;

    private InetAddress IP = null;

    private String logFile = "mtp_sender_log.txt";

    //the packet amout that can be trasmitted once. 
    private int once_amount;

    //total amout of bytes.
    private byte[] data = null;

    private final static int HAND_SHAKE = 1;
    private final static int HAND_SHAKE_LAST = 2;

    private final static int PACK_SEND = 3;

    private final static int PACK_SEND_AGAIN = 4;

    private final static int PACK_DROP = 5;
    private final static int PACK_SEND_FINISH = 6;

    private PLD pld = null;

    private int handLastSeq;
    private int handLastAck;

    //the split txt 
    private List<String> txts = new ArrayList<String>();

    private StringBuilder log = new StringBuilder();

    public mtp_sender() throws IOException {
        super();
        this.socket = new DatagramSocket();
    }

    public mtp_sender(String recIP, int recPort, String file, int MWS, int MSS, int timeout, float pdrop,
            int seed) throws IOException {
        super();
        this.port = recPort;
        this.file = file;
        this.MWS = MWS;
        this.MSS = MSS;
        this.timeout = timeout;
        this.pld = new PLD(pdrop, seed);
        this.IP = InetAddress.getByName(recIP);
        buildDatagramPackets();
        buildClient();

    }

    private void buildClient() throws IOException {
        this.socket = new DatagramSocket();
        socket.setSoTimeout(this.timeout);
    }

    //split the packet
    private void buildDatagramPackets() {
        String fileString = generateFile();
        if (fileString == null)
            return;
        this.data = fileString.getBytes();
        int length = this.data.length;
        int size = this.data == null ? 0 : length;
        //the maximum amout of split packet
        int count = (size / this.MSS) + ((size % this.MSS) > 0 ? 1 : 0);

        this.once_amount = this.MWS % this.MSS;

        for (int i = 1; i <= count; i++) {
            String s = null;
            int beginIndex = (i - 1) * this.MSS;
            if (i == count) {
                s = fileString.substring(beginIndex, length);
            } else {
                int endIndex = i * this.MSS;
                s = fileString.substring(beginIndex, endIndex);
            }
            String packet = new String(s);
            this.txts.add(packet);
        }

    }

    public class PLD {

        private Random random = null;
        private float pdrop;

        public PLD(float pdrop, int seed) {
            super();
            this.random = new Random(seed);
            this.pdrop = pdrop;
        }

        public boolean drop() {
            float r = random.nextFloat();
            return r < this.pdrop;
        }
    }

    private MTP getReceive(int type) throws IOException {
        DatagramPacket inputPacket = new DatagramPacket(new byte[1024], 1024);
        this.socket.receive(inputPacket);
        MTP mtp2 = new MTP();
        mtp2.decode(inputPacket.getData());
        mtp2.setType(type);
        mtp2.initReceive();
        return mtp2;
    }

    public void send() throws IOException {
        try {
            for (String s : txts) {
                MTP send = new MTP();
                send.setSyn(0);
                send.setSeq(handLastSeq);
                send.setAck(handLastAck);
                send.setSize(s.length());
                send.setType(PACK_SEND);
                send.setData(s.getBytes());
                byte[] d = send.encode();
                DatagramPacket packet = new DatagramPacket(d, d.length, this.IP, this.port);
                if (this.pld.drop()) {
                    send.setType(PACK_DROP);
                    send.initSend();
                    // System.out.println("drop a package!");
                    // log.append("drop a package!" + "\n");
                } else {
                    this.socket.send(packet);
                    send.initSend();
                }
                // reveive
                MTP reveive = null;
                try {
                    reveive = getReceive(PACK_SEND);
                } catch (IOException e) {
                    // System.out.println("send again");
                    send.setType(PACK_SEND_AGAIN);
                    sendAgainWhenTimeOut(send, packet);
                    reveive = getReceive(PACK_SEND);
                }

                handLastSeq = reveive.getAck();
            }

            sendFinish();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.socket.close();
        }

    }

    private void sendFinish() throws IOException {
        MTP finish = new MTP();
        finish.setType(PACK_SEND_FINISH);
        byte[] d = finish.encode();
        DatagramPacket packet = new DatagramPacket(d, d.length, this.IP, this.port);
        this.socket.send(packet);
        finish.initSend();
    }

    private void sendAgainWhenTimeOut(MTP mtp, DatagramPacket packet) throws IOException {
        this.socket.send(packet);
        mtp.initSend();
    }

    private boolean handshake() throws IOException {
        DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
        int seq = getRand();
        int ack = getRand();
        MTP mtp1 = new MTP();
        mtp1.setSyn(1);
        mtp1.setSeq(seq);
        mtp1.setAck(ack);
        mtp1.setType(HAND_SHAKE);
        byte[] d = mtp1.encode();
        packet = new DatagramPacket(d, d.length, this.IP, this.port);
        this.socket.send(packet);
        mtp1.initSend();

        MTP mtp2 = getReceive(HAND_SHAKE);

        MTP mtp3 = new MTP();
        mtp3.setSyn(0);
        mtp3.setType(HAND_SHAKE_LAST);
        mtp3.setSeq(mtp2.getAck());
        mtp3.setAck(mtp2.getSeq() + mtp2.getSize());
        byte[] d3 = mtp3.encode();
        packet.setData(d3);
        packet.setLength(d3.length);
        this.socket.send(packet);
        mtp3.initSend();

        // MTP mtp4 = getReceive(HAND_SHAKE);

        this.handLastSeq = mtp3.getSeq();
        this.handLastAck= mtp3.getAck();
        return mtp3.getAck() == mtp2.getSeq() + mtp2.getSize();
    }

    public class MTP {
        /* Header */
        // package type
        private int type = 0;
        private int syn = 1;
        private int seq = 0;
        private int ack = 0;
        private int size = 1;
        //the content that need to be transmitted.
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
            switch (this.type) {
            case HAND_SHAKE:
                builder.append(getTimeNow()).append("receive an ACK, package type:SYNACK    ");
                builder.append("syn:" + this.syn + "    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + " ");
                System.out.println(builder.toString());
                break;
            case PACK_SEND:
                builder.append(getTimeNow()).append("receive an ACK, package type:DATAACK    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + " ");
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
                builder.append(getTimeNow()).append("transmit packet, package type:SYN    ");
                builder.append("syn:" + this.syn + "    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + " ");
                System.out.println(builder.toString());
                break;
            case HAND_SHAKE_LAST:
                builder.append(getTimeNow()).append("transmit packet, package type:SYN|    ");
                builder.append("syn:" + this.syn + "    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + " ");
                System.out.println(builder.toString());
                break;
            case PACK_SEND:
                builder.append(getTimeNow()).append("transmit packet, package type:DATA    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + "    ");
                builder.append("data size:" + this.size + "    ");
                builder.append("data content:" + new String(this.data) + " ");
                System.out.println(builder.toString());
                break;
            case PACK_SEND_AGAIN:
                builder.append(getTimeNow()).append("retransmit packet!! package type:DATA    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + "    ");
                builder.append("data size:" + this.size + "    ");
                builder.append("data content:" + new String(this.data) + " ");
                System.out.println(builder.toString());
                break;
            case PACK_DROP:
                builder.append(getTimeNow()).append("drop packet!! package type:DATA    ");
                builder.append("seq number:" + this.seq + "    ");
                builder.append("ack number:" + this.ack + "    ");
                builder.append("data size:" + this.size + "    ");
                builder.append("data content:" + new String(this.data) + " ");
                System.out.println(builder.toString());
                break;
            case PACK_SEND_FINISH:
                builder.append(getTimeNow()).append("transmission finished! ");
                builder.append("generateLogFile file...");
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

    public void send(MTP mtp) throws IOException {

    }

   

    private String generateFile() {
        FileInputStream reader = null;
        StringBuilder date = new StringBuilder();
        try {
            File file = new File(this.file);
            reader = new FileInputStream(file);
            InputStreamReader readers = new InputStreamReader(reader,"UTF-8");
            int reads = 0;
            char ch = '\u0000';
            while ((reads = reader.read()) != -1) {
                ch = (char) reads;
                date.append(ch);
            }
            reader.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return date.toString();
    }
    
    public static void main(String args[]) throws IOException {

        String ip = args[0];
        int port = Integer.parseInt(args[1]);
        String file = args[2];
        int MWS = Integer.parseInt(args[3]);
        int MSS = Integer.parseInt(args[4]);
        int timeout = Integer.parseInt(args[5]);
        float pdrop = Float.parseFloat(args[6]);
        int seed = Integer.parseInt(args[7]);

        mtp_sender ms = new mtp_sender(ip, port, file, MWS, MSS, timeout, pdrop, seed);
        // Mtp_sender ms = new Mtp_sender("localhost", 8000, "1.txt", 100, 80,
        // 1000, 0.1f, 1);
        if (ms.handshake()) {
            System.out.println("Hand shake success. ready to send file. filename:" + ms.file);
            ms.log.append(ms.getTimeNow()).append(
                    "Hand shake success. ready to send file. filename:" + ms.file + "\n");
            ms.send();
            File log = new File(ms.logFile);
            if(log.exists()) log.delete();
            generateFile(ms.logFile, ms.log.toString());
            // System.out.println("--------------------------");
            // System.out.println(ms.log.toString());
        } else {
            System.out.println("oh no! Hand shake fail");
        }
    }

    private static void generateFile(String filename, String common) {
        BufferedWriter out = null;
        try {
            File file = new File(filename);
            out = new BufferedWriter(new FileWriter(file, true));
            out.write(common);
            out.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (out != null)
                    out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getTimeNow() {
        Date now = new Date(System.currentTimeMillis());
        DateFormat d8 = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        // display the time
        String str8 = d8.format(now);
        return "[ " + str8 + " ] ";
    }

    private int getRand() {
        Random rand = new Random();
        int i = rand.nextInt(100);
        return i;
    }

}
