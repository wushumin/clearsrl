package clearcommon.treebank;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import clearcommon.propbank.PBFileReader;

public class TBReader {
	
	private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());
	
    String               dir;
    boolean              cached;
    ThreadedTBFileReader reader;
    Map<String, TBTree[]> treeMap;
    
    public TBReader(Map<String, TBTree[]> treeMap)
    {
        dir = null;
        cached = true;
        reader = null;
        this.treeMap = treeMap;
    }
    
    public TBReader(String dir, boolean cached)
    {
    	File dirFile = new File(dir);
    	if (dirFile.isFile())
    		this.dir = dirFile.getAbsoluteFile().getParentFile().getAbsolutePath();
    	else
    		this.dir = dir;
    	
        this.cached = cached;
        reader = null;
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
                if (dir==null) return null;
                logger.info("Reading "+dir+File.separatorChar+fileName);
                try {
                    TBFileReader tbreader = new SerialTBFileReader(dir, fileName);
                    ArrayList<TBTree> a_tree = new ArrayList<TBTree>();
                    TBTree tree;
                    while ((tree = tbreader.nextTree()) != null)
                        a_tree.add(tree);
                    trees = a_tree.toArray(new TBTree[a_tree.size()]);
                    
                    treeMap.put(fileName, trees);
                } catch (IOException e) {
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
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }
            
            return reader.getTree(index);
        }
    }
    
    public Map<String, TBTree[]> getTreeMap()
    {
        return treeMap;
    }
}
