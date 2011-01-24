package clearcommon.propbank;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import clearcommon.treebank.OntoNoteTreeFileResolver;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;


public class TestPBInstance {
    @Test
    public void testPBArg() throws FileNotFoundException {
        System.out.println(Arrays.toString("19:1*27:0;31:4".split("(?=[\\*,;])")));
        assertTrue("19:1*27:0-LINK-SLC".matches(PBArg.ARG_PATTERN));
        assertFalse("19:1*27:0LINK-SLC".matches(PBArg.ARG_PATTERN));
        Map<String, TBTree[]> trees = new TreeMap<String, TBTree[]>();
        PBReader reader = new PBReader("/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/english/annotations/nw/wsj/23/wsj_2356.prop",
                                       "/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/english/annotations/",trees,
                                       new OntoNoteTreeFileResolver());
        List<PBInstance> instances = new ArrayList<PBInstance> ();
        PBInstance instance=null;
        try {
            while ((instance = reader.nextProp())!=null)
            {
                instances.add(instance);
                System.out.println(instance);
            }
        } catch (PBFormatException e) {
            System.err.println(instances.size());
            e.printStackTrace();
            assertTrue(false);
        }
    }
}
