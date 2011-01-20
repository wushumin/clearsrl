package clearcommon.propbank;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;


public class TestPBInstance {
    @Test
    public void testPBArg() {
        System.out.println(Arrays.toString("19:1*27:0;31:4".split("(?=[\\*,;])")));
        assertTrue("19:1*27:0-LINK-SLC".matches(PBArg.ARG_PATTERN));
        assertFalse("19:1*27:0LINK-SLC".matches(PBArg.ARG_PATTERN));
    }
}
