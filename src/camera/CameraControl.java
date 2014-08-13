package camera;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * This class manages a single remote camera at the far end of a datagram/UDP connection.
 * 
 * The class will invoke a command, and return a result, within a given timeout.
 * 
 * The class manages a separate thread that coordinates all communication with the
 * remote camera, allowing only one command at a time.
 * 
 */
public class CameraControl {

    // Set the size of the low-level buffer used in the socket.
    // This needs to be large enough to contain an entire image.... if the camera sends it really fast.
    // that would be 480 packets of 640 bytes, plus some head-room, or 300KB plus some. Be generous at 512KB
    private static final Integer SOCKETBUFFER = 1024 * 512;

    private final SocketAddress remote;
    private final BlockingDeque<Task> queue = new LinkedBlockingDeque<>(32);
    private final ByteBuffer buffer = ByteBuffer.allocate(2048);
    
    // special IOException that indicates particular problems encountered. 
    private static final class ProtocolException extends IOException {
        
        private static final long serialVersionUID = 1L;
        
        // the valid data received so far
        private final byte[] sofar;
        
        private static final String formatData(byte[] data) {
            return String.format(" [ %d bytes so far -> %s]", data.length, Arrays.toString(data.length <= 8 ? data : Arrays.copyOf(data, 8)));
        }

        public ProtocolException(byte[] sofar, String message, Throwable cause) {
            super(message + formatData(sofar), cause);
            this.sofar = sofar;
        }

        public ProtocolException(byte[] sofar, String message) {
            super(message + formatData(sofar));
            this.sofar = sofar;
        }
        
        @SuppressWarnings("unused")
        public byte[] getSoFar() {
            return sofar;
        }

    }
    
    /**
     * Details about a particular task to run on the camera. This is queued, and then used
     * as the synchronization point to track the manager-thread actions. 
     */
    private final class Task {
        
        private final CameraCommands cmd;
        private final int timeout;
        private boolean complete = false;
        private Result result = null;
        
        public Task(CameraCommands cmd, int timeout) {
            this.cmd = cmd;
            this.timeout = timeout;
        }

        /**
         * queue a command. Once the command is submitted to the camera, it needs to complete within the given timeout
         * @return The result (may be a fail-result)
         * @throws InterruptedException if the thread is interrupted.
         */
        public Result invoke() throws InterruptedException {
            // push the job to the queue. The manager thread will pull.
            queue.add(this);
            
            // wait for the manager to notify of success.
            // a more sophisticated controller could make this an async wait.
            // the purpose though is to force all comms with the camera to be
            // synchronous, and on a single 'controlled' thread.
            synchronized (this) {
                while (!complete) {
                    this.wait();
                }
            }
            return result;
        }
    }
    
    /**
     * The guts of the thread that communicates with the camera.
     */
    private final class Manager implements Runnable {
        
        private final DatagramChannel channel;
        // use NIO which is non-blocking.
        // this allows for better timeout management.
        private final Selector selector;
        
        Manager() throws IOException {
            selector = Selector.open();

            channel = DatagramChannel.open();
            // use non-blocking IO.
            channel.configureBlocking(false);
            // Set a large receive buffer for the socket
            channel.setOption(StandardSocketOptions.SO_RCVBUF, SOCKETBUFFER);
            channel.register(selector, SelectionKey.OP_READ);
            // actually establish the connection.
            channel.connect(remote);
        }
        
        @Override
        public void run() {
            while (true) {
                // run forever. We are a daemon thread, so if the JVM dies, we do too.
                try {
                    Task t = queue.take();
                    Result result = null;
                    if (t != null) {
                        try {
                            result = processTask(t.cmd, t.timeout);
                        } catch (ProtocolException e) {
                            e.printStackTrace();
                        }
                    }
                    synchronized(t) {
                        t.result = result;
                        t.complete = true;
                        t.notifyAll();
                    }
                } catch (InterruptedException e) {
                    //Thread.currentThread().interrupt();
                    // clear the interrupt, and keep going.
                    log("Manager Thread interrupted.... and interrupt ignored.");
                }
            }
        }

        private Result processTask(final CameraCommands cmd, final long timeout) throws ProtocolException {
            
            final int getsize = cmd.getDatagramSize();
            final int getcount = cmd.getDatagramCount();
            final byte[] data = new byte[getsize * getcount];
            int byteCount = 0;
            
            try {
                do {
                    // clear any pending crap from the queue.
                    // this could be data from previously failed commands.
                    buffer.clear();
                    channel.read(buffer);
                } while (buffer.position() > 0);
                
                // send the command
                channel.send(cmd.getCommand(), remote);

                // set the limit for how long we will wait to get all the data.
                final long timeoutAt = System.currentTimeMillis() + timeout;
                long now = 0L;
                
                // reset the buffer to get the real data back
                buffer.clear();
                int packetCount = 0;
                
                // note the tricky now = System.currentTimeMillis() which sets the now value, and compares it as well.
                while (byteCount < data.length && (now = System.currentTimeMillis()) < timeoutAt) {
                    
                    // do a blind select on the channel....
                    // this will wait up to the timeoutAt for data to come available.
                    // this is a time-limited blocking operation. It completes quickly if there is data.
                    
                    if (selector.select(timeoutAt - now) == 0) {
                        log("Zero-selector encountered"); // normally indicates a problem (like a timeout)
                        continue;
                    }
                    // only one channel was registered, we can ignore the keys.
                    selector.selectedKeys().clear();
                    
                    // read as much as we can from the channel, should be just one datagram
                    int iostat = channel.read(buffer);
                    if (iostat < 0) {
                        throw new ProtocolException(Arrays.copyOf(data, byteCount), "Unexpected closed channel "
                                + iostat + " expecting " + getsize + " for transfer " + packetCount);
                    }
                    
                    if (buffer.position() < getsize) {
                        // we expect fixed size datagrams for each command. Let's hope the next datagram has the missing data (unlikely)
                        log("Short data obtained " + iostat + " for xfer " + packetCount);
                        continue;
                    }
                    // set the buffer to read mode.
                    buffer.flip();
                    
                    // read the datagram in to the next position in the output data.
                    buffer.get(data, byteCount, iostat);
                    byteCount += buffer.limit();
                    buffer.clear();
                    packetCount++;
                }
                
                long actualDuration = System.currentTimeMillis() - (timeoutAt - timeout);
                if (byteCount < data.length) {
                    throw new ProtocolException(Arrays.copyOf(data, byteCount), "Timeout after " + actualDuration + "ms after transfer " + packetCount);
                }
                
                return new Result(data, null);
                
            } catch (IOException e) {
                e.printStackTrace();
                throw new ProtocolException(Arrays.copyOf(data, byteCount), "Exception : " + e.getMessage(), e);
            }
        }


    }
    
    /**
     * Establish a connection, and management thread to a remote camera.
     * @param remote the location of the camera
     * @throws IOException when the connection cannot be established.
     */
    public CameraControl(SocketAddress remote) throws IOException {
        this.remote = remote;
        Thread thread = new Thread(new Manager(), "Camera Control Manager Thread");
        thread.setDaemon(true);
        thread.start();
    }
    
    private static void log(String message) {
        // TODO -> Use a different logging system.
        System.out.println(message);
    }

    /**
     * Run a command, within a given time limit, on the remote camera.
     * @param waitMS the time limit.
     * @param cmd the command.
     * @return the resulting data from the camera.
     * @throws InterruptedException if we were interrupted.
     */
    public Result waitForACK(int waitMS, CameraCommands cmd) throws InterruptedException {
        Task t = new Task(cmd, waitMS);
        return t.invoke();
    }

}
