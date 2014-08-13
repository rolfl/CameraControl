package camera;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;

/**
 * This is a dummy remote camera. It returns basic data most of the time, but about 10% of requests will fail to produce anything (and timeout).
 */
public class DummyCam {
    
    private static final byte[][] OKDATA = { "ISOK".getBytes(StandardCharsets.US_ASCII) };
    private static final byte[][] STATUSDATA = { "MYSTATUS".getBytes(StandardCharsets.US_ASCII) };
    private static final byte[][] IMAGEDATA = new byte[480][640];

    enum Command {
        
        IMAGE(IMAGEDATA),
        RESET(OKDATA),
        FILTER(OKDATA),
        STATUS(STATUSDATA);
        
        private final byte[][] resp;
        Command(byte[][] resp) {
            this.resp = resp;
        }
        
        public byte[][] getResponse() {
            return resp;
        }
    }
    
    
    
    static {
        int cnt = 0;
        for (byte[] row : IMAGEDATA) {
            for (int i = 2; i < row.length; i++) {
                row[i] = (byte)(i & 0x7f);
            }
            row[0] = (byte)(cnt / 100);
            row[1] = (byte)(cnt % 100);
            cnt++;
        }
    }

    private final int port;
    public DummyCam(int port) {
        this.port = port;
    }
    
    
    
    public static void main(String[] args) throws IOException {
        DummyCam cam = new DummyCam(12345);
        cam.listen();
    }



    private void listen() throws IOException {
        
        DatagramChannel channel = DatagramChannel.open();
        channel.bind(new InetSocketAddress(port));
        log("bound to " + port);
        SocketAddress remote = null;
        final byte[] backing = new byte[2048];
        ByteBuffer buffer = ByteBuffer.wrap(backing);
        while ((remote = channel.receive(buffer)) != null) {
            buffer.flip();
            String command = new String(backing, 0, buffer.limit(), StandardCharsets.US_ASCII);
            // about a 10% chance of error.
            boolean error = Math.random() < 0.1;
            Command cmd = getCommand(command);
            if (!error && cmd != null) {
                int tmt = 0;
                int cnt = 0;
                for (byte[] row : cmd.getResponse()) {
                    buffer.clear();
                    buffer.put(row);
                    buffer.flip();
                    cnt += channel.send(buffer, remote);
                    tmt++;
                }
                log (command + " from " + remote + " Sent " + cnt + " bytes in " + tmt + " datagrams");
            } else {
                log (command + " error!");
            }
            buffer.clear();
        }
    }



    private static final Command getCommand(String command) {
        for (Command cmd : Command.values()) {
            if (cmd.name().equals(command)) {
                return cmd;
            }
        }
        return null;
    }



    private static void log(String message) {
        System.out.println(message);
        
    }

}
