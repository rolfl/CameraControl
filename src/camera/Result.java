package camera;

import java.util.Arrays;

/**
 * This is a mock up of what a camera result will be. you will need to change this to match your system.
 */
public class Result {
    
    private final byte[] data;
    private final boolean success;
    private final Exception exception;


    public Result(byte[] data, Exception e) {
        super();
        this.data = data;
        this.success = e == null; // no exception
        this.exception = e;
    }

    public byte[] getData() {
        return data;
    }
    
    public Exception getException() {
        return exception;
    }

    public boolean isSuccess() {
        return success;
    }
    
    @Override
    public String toString() {
        if (!success) {
            return String.format("Result FAIL: %d bytes: %s -> %s", data.length, Arrays.toString(Arrays.copyOf(data, Math.min(8, data.length))), exception.toString());
        }
        return String.format("Result SUCCESS: %d bytes: %s", data.length, Arrays.toString(Arrays.copyOf(data, Math.min(8, data.length))));
    }

}
