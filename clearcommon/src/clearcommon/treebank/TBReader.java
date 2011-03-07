package clearcommon.treebank;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.TreeMap;

public class TBReader {
    String               dir;
    boolean              cached;
    ThreadedTBFileReader reader;
    SortedMap<String, TBTree[]> treeMap;
    
    public TBReader(String dir, boolean cached)
    {
    	File dirFile = new File(dir);
    	if (dirFile.isFile())
    		this.dir = dirFile.getParentFile().getName();
    	else
    		this.dir = dir;
        this.cached = cached;
        if (cached) treeMap = new TreeMap<String, TBTree[]>();
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
    
    public void close()
    {
        if (reader!=null) 
            reader.close();
        reader = null;
    }
    
    public TBTree getTree(String fileName, int index) throws ParseException
    {
        if (cached)
        {
            TBTree[] trees      = null;
            if ((trees = treeMap.get(fileName))==null)
            {
                System.out.println("Reading "+dir+File.separatorChar+fileName);
                try {
                    TBFileReader tbreader = new SerialTBFileReader(dir, fileName);
                    ArrayList<TBTree> a_tree = new ArrayList<TBTree>();
                    TBTree tree;
                    while ((tree = tbreader.nextTree()) != null)
                        a_tree.add(tree);
                    trees = a_tree.toArray(new TBTree[a_tree.size()]);
                    
                    treeMap.put(fileName, trees);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return null;
                }
            }
            return trees.length>index?trees[index]:null;
        }
        else
        {
            if (reader!=null && !reader.fileName.equals(fileName))
            {
                reader.close();
                reader = null;
            }
            
            if (reader==null)
            {
                try {
                    reader = new ThreadedTBFileReader(dir, fileName, 1000);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return null;
                }
            }
            
            return reader.getTree(index);
        }
    }
    
    public SortedMap<String, TBTree[]> getTreeMap()
    {
        return treeMap;
    }
}
