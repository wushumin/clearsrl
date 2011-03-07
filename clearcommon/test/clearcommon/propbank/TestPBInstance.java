package clearcommon.propbank;

import static org.junit.Assert.*;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import clearcommon.treebank.*;

public class TestPBInstance {
    @Test
    public void testPBArg() throws FileNotFoundException {
        System.out.println(Arrays.toString("19:1*27:0;31:4".split("(?=[\\*,;])")));
        assertTrue("19:1*27:0-LINK-SLC".matches(PBArg.ARG_PATTERN));
        assertFalse("19:1*27:0LINK-SLC".matches(PBArg.ARG_PATTERN));
        
        String treeDir = "/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/english/annotations/";
        
        TBReader tbReader = new TBReader(treeDir, false);
        PBFileReader reader = new PBFileReader(tbReader,
                "/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/english/annotations/nw/wsj/23/wsj_2356.prop",
                new OntoNoteTreeFileResolver());
        List<PBInstance> instances = new ArrayList<PBInstance> ();
        PBInstance instance=null;
        try {
            while ((instance = reader.nextProp())!=null)
            {
                instances.add(instance);
                System.out.println(instance.tree.getFilename()+" "+instance.tree.getIndex());
                System.out.println(instance);
                System.out.flush();
            }
        } catch (PBFormatException e) {
            System.err.println(instances.size());
            e.printStackTrace();
            assertTrue(false);
        } catch (ParseException e) {
            System.err.println(instances.size());
            e.printStackTrace();
            assertTrue(false);
        }
        
        int instanceNum = instances.size();
        
        int iNum = 0;
        
        reader = new PBFileReader(new TBReader(treeDir, false),
                "/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/english/annotations/nw/wsj/23/wsj_2356.prop",
                new OntoNoteTreeFileResolver());
 
        while ((instances = reader.nextPropSet())!=null)
        {
            System.out.println("--------------------------");
            iNum += instances.size();
            for (PBInstance aInstance:instances)
            {
                System.out.println(aInstance.tree.getFilename()+" "+aInstance.tree.getIndex());
                System.out.println(aInstance);
                System.out.flush();
            }
        } 
        System.out.println(instanceNum+" "+iNum);
        assertEquals(instanceNum, iNum);
        
        iNum = 0;
        
        PBReader pbReader = new PBReader(new TBReader(treeDir, false),
                "/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/english/annotations/nw/wsj/23/wsj_2356.prop",
                ".+",
                new OntoNoteTreeFileResolver());
 
        while ((instances = pbReader.nextPropSet())!=null)
        {
            System.out.println("--------------------------");
            iNum += instances.size();
            for (PBInstance aInstance:instances)
            {
                System.out.println(aInstance.tree.getFilename()+" "+aInstance.tree.getIndex());
                System.out.println(aInstance);
                System.out.flush();
            }
        } 
        System.out.println(instanceNum+" "+iNum);
        assertEquals(instanceNum, iNum);
    }
}
