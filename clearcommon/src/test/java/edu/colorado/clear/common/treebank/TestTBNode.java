package edu.colorado.clear.common.treebank;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import org.junit.Before;
import org.junit.Test;

import edu.colorado.clear.common.treebank.TBNode;

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
        
        assertTrue("NP-SBJ=2-1-5".matches(TBNode.POS_PATTERN.pattern()));
        assertTrue("NP-SBJ-1=2-5".matches(TBNode.POS_PATTERN.pattern()));
        
        Matcher matcher = TBNode.POS_PATTERN.matcher("NP-SBJ-1=2-5");
        assertTrue(matcher.matches());
        
        System.out.println(matcher.group(4));
        System.out.println(matcher.group(6));
        
        System.out.println(Arrays.toString(matcher.group(4).split("(?=[-\\=])")));
        
        node = new TBNode(null, "-NONE-");
        node.cleanUpPOS();
        assertEquals("POS:", node.getPOS(), "-NONE-");
        assertEquals(node.getFunctionTags(), null);
        
        TBNode nodes[] = {new TBNode(null,""), new TBNode(null,""), new TBNode(null,"sdfse")};
        Set<TBNode> nodeSet = new HashSet<TBNode>();
        
        nodeSet.addAll(Arrays.asList(nodes));
        
        assertTrue(nodeSet.contains(nodes[0]));
        assertTrue(nodeSet.contains(nodes[2]));
        assertTrue(!nodeSet.contains(new TBNode(null,"")));
        
    }
}
