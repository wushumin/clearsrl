package clearcommon.treebank;


import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class TestTBReader {

    static String treeString = 
        "(TOP (S (NP-SBJ (NNP Baker)) (ADVP (RB nonetheless)) (VP (VP (VBZ remains) "+
        "(ADJP-PRD (ADJP (JJ furious)) (DT both) (PP-3 (IN at) (NP-1 (NNP Shamir)))) "+
        "(, ,) (PP-PRP-4 (IN for) (S-NOM (NP-SBJ (-NONE- *PRO*-1)) (VP (VBG backing) "+
        "(PRT (RP down)) (PP (IN on) (NP (DT the) (NNS elections))))))) (, ,) "+
        "(CC and) (VP (PP=3 (IN at) (NP-2 (NP (NP (NNP Shamir) (POS 's)) (NN rival)) "+
        "(, ,) (NP (NNP Peres)) (, ,))) (PP-PRP=4 (IN for) (NP (NP (JJ political) "+
        "(NN ineptitude)) (PP (IN in) (S-NOM (NP-SBJ (-NONE- *PRO*-2)) "+
        "(VP (VBG forcing) (NP (NP (DT a) (JJ premature) (NN cabinet) (NN vote)) "+
        "(PP (IN on) (NP (NP (NNP Baker) (POS 's)) (NN plan))))))))))) (. .)))";
    
    @Before
    public void setUp() throws Exception {
    }
    
    @Test
    public void testTree() {
        TBReader reader = new TBReader(new StringReader(treeString));
        TBTree tree=null;
        try {
            tree = reader.nextTree();
        } catch (ParseException e)
        {
            e.printStackTrace();
        }
        assertFalse(tree==null);
        System.out.println(tree.rootNode.toParse());
        System.out.println(tree.rootNode.toString());
        
        List<TBNode> nodes = new ArrayList<TBNode>();
        getNodes(tree.rootNode, nodes);
        
        int count = 0;
        for (TBNode node:nodes)
            if (node.indexingNode!=null)
            {
                if (node.isEC())
                    System.out.println(node.word+" -> "+node.indexingNode);
                else
                    System.out.println(node+" <-> "+node.indexingNode);
                ++count;
            }
        assertEquals(count, 4);
    }

    void getNodes(TBNode node, List<TBNode> nodes)
    {
        nodes.add(node);
        if (node.children==null) return;
        for (TBNode cNode:node.children)
            getNodes(cNode, nodes);
    }
    
}
