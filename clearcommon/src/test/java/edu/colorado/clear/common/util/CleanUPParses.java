package edu.colorado.clear.common.util;

import java.io.File;
import java.util.Collections;
import java.util.List;

import edu.colorado.clear.common.util.FileUtil;

public class CleanUPParses {
    public static void main(String[] argv) {
        List<String> files = FileUtil.getFiles(new File(argv[0]),".*parse", false);
        Collections.sort(files);
        
        for (int i=1; i<files.size(); ++i)
            if (files.get(i).endsWith(".parse") && files.get(i-1).endsWith(".on_v3_parse"))
            {
                System.out.println(files.get(i));
                new File(argv[0]+File.separatorChar+files.get(i)).renameTo(new File(argv[0]+File.separatorChar+files.get(i)+".old"));
                new File(argv[0]+File.separatorChar+files.get(i-1)).renameTo(new File(argv[0]+File.separatorChar+files.get(i)));
            }       
        files = FileUtil.getFiles(new File(argv[0]),".*prop", false);
        
        for (int i=0; i<files.size(); ++i)
            if (files.get(i).endsWith(".on_v3_prop"))
            {
                System.out.println(files.get(i)+": "+files.get(i).substring(0, files.get(i).length()-10)+"prop");
                new File(argv[0]+File.separatorChar+files.get(i)).renameTo(new File(argv[0]+File.separatorChar+files.get(i).substring(0, files.get(i).length()-10)+"prop"));
            }       
    }
}
