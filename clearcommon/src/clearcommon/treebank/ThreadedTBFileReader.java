package clearcommon.treebank;

import java.io.FileNotFoundException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadedTBFileReader implements Runnable {
    TBFileReader reader;
    TBTree lastTree;
    BlockingQueue<TBTree> treeQueue;
    AtomicBoolean keepRunning;
    Thread thread;
    
    public ThreadedTBFileReader(String dirName, String treeFile, int capacity) throws FileNotFoundException {

        if (dirName==null)
            reader = new TBFileReader(treeFile);
        else
            reader = new TBFileReader(dirName, treeFile);
        lastTree = null;
        
        treeQueue = new ArrayBlockingQueue<TBTree>(capacity);
        
        keepRunning = new AtomicBoolean(true);
        thread = new Thread(this);
        thread.start();
    }
    
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
    
    public void run() {
        TBTree tree=null;
        while (keepRunning.get())
        {
            try {
                tree = reader.nextTree();
            } catch(Exception e) {
                System.err.println(e);
            }
            try {
                treeQueue.put(tree);
            } catch (InterruptedException e) {
            }
            
            // the last tree inserted will be null, indicating there are no more trees
            if (tree==null) break;
        }
        reader.close();
    }
    
    public TBTree nextTree()
    {
        while (true)
        {
            try {
                lastTree = treeQueue.take();
                return lastTree;
            } catch (InterruptedException e) {
                if (!keepRunning.get()) return null;
            }
        }
    }
    
    public TBTree getTree(int index)
    {
        while (true)
        {
            try {
                if (lastTree!=null)
                {
                    if (lastTree.index==index)
                        return lastTree;
                    if (lastTree.index>index)
                        return null;
                }
                
                lastTree = treeQueue.take();
                
                // if a null tree is read, it means no more trees are available
                if (lastTree==null) 
                {
                    treeQueue.put(null);
                    return null;
                }
            } catch (InterruptedException e) {
                if (!keepRunning.get()) return null;
            }
        }
    }
    
    public void close()
    {
        keepRunning.set(false);
        treeQueue.clear();
    }
}
