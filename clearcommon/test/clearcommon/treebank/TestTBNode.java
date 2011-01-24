package clearcommon.treebank;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

public class TestTBNode {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testPOS() {

        TBNode node = new TBNode(null, "NP");
        node.cleanUpPOS();
        assertEquals("POS:", node.getPOS(), "NP");
        assertFalse(node.hasFunctionTag("NP"));

        node = new TBNode(null, "NP-sbj-whatever");
        node.cleanUpPOS();
        assertEquals("POS:", node.getPOS(), "NP");
        assertTrue(node.hasFunctionTag("sbj"));
        assertTrue(node.hasFunctionTag("whatever"));
        assertTrue(node.getFunctionTags().size()==2);
        
        node = new TBNode(null, "NP-sbj=2");
        node.cleanUpPOS();
        assertEquals("POS:", node.getPOS(), "NP");
        assertTrue(node.hasFunctionTag("sbj"));
        assertTrue(node.getFunctionTags().size()==1);
        
        node = new TBNode(null, "NP-sbj-1");
        node.cleanUpPOS();
        assertEquals("POS:", node.getPOS(), "NP");
        assertTrue(node.hasFunctionTag("sbj"));
        assertEquals(node.getFunctionTags().size(),1);
        
        node = new TBNode(null, "-NONE-");
        node.cleanUpPOS();
        assertEquals("POS:", node.getPOS(), "-NONE-");
        assertEquals(node.getFunctionTags(), null);
    }
}
