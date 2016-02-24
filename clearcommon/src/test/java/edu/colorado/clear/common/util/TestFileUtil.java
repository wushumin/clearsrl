package edu.colorado.clear.common.util;


import java.io.File;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import edu.colorado.clear.common.util.FileUtil;


public class TestFileUtil {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testFileUtil() {
        List<String> files = FileUtil.getFiles(new File("/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/english/annotations/nw"), ".+\\.parse\\z", true);
        System.out.println(files.get(50));
        files = FileUtil.getFiles(new File("/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/english/annotations/nw"), ".+\\.parse\\z");
        System.out.println(files.get(50));
        
        files = FileUtil.getFiles(new File("/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/english/annotations/nw/p2.5_c2e/00/p2.5_c2e_0002.parse"), ".+\\.parse\\z");
        System.out.println(files.get(0));
        files = FileUtil.getFiles(new File("/home/verbs/student/shumin/corpora/ontonotes-release-4.0/data/english/annotations/nw/p2.5_c2e/00/p2.5_c2e_0002.parse"), ".+\\.parse\\z", true);
        System.out.println(files.get(0));
    }
    
}
