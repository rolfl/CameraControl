package camera;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * This is a mock up of some potential commands.
 * 
 * use your version of the CameraCommands.
 * 
 * It is useful to know how many datagrams to expect, and how large they will be.
 */
public class CameraCommands {
    
    private final byte[] command;
    private final int datagramsize;
    private final int datagramcount;

    public CameraCommands(String command, int expectcount, int expectsize) {
        this.command = command.getBytes(StandardCharsets.US_ASCII);
        datagramcount = expectcount;
        datagramsize = expectsize;
    }

    ByteBuffer getCommand() {
        return ByteBuffer.wrap(command);
    }
    
    public int getDatagramSize() {
        return datagramsize;
    }
    
    public int getDatagramCount() {
        return datagramcount;
    }
    
    @Override
    public String toString() {
        return String.format("Command %s expect %d x %dBytes", new String(command, StandardCharsets.US_ASCII), datagramcount, datagramsize);
    }

}
