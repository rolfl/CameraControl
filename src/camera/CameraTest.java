package camera;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Simple main routine that sends a few commands to the controller.
 * @author rolf
 *
 */
public class CameraTest {
    
    private static final CameraCommands[] cmds = {
            new CameraCommands("RESET", 1, 4),
            new CameraCommands("RESET", 1, 4),
            new CameraCommands("IMAGE", 480, 640),
    };

    public static void main(String[] args) throws IOException, InterruptedException {
        CameraControl control = new CameraControl(new InetSocketAddress("localhost", 12345));
        for (CameraCommands cmd : cmds) {
            System.out.println(cmd + "\n    " + control.waitForACK(3000, cmd));
        }
    }

}
