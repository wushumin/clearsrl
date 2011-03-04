package clearcommon.treebank;

import java.io.FileNotFoundException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadedTBFileReader extends TBFileReader implements Runnable {
    
    TBTree threadedLastTree;
    BlockingQueue<TBTree> treeQueue;
    AtomicBoolean keepRunning;
    Thread thread;
    
    public ThreadedTBFileReader(String dirName, String treeFile, int capacity) throws FileNotFoundException {
        super(dirName, treeFile);
        threadedLastTree = null;
        
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
                tree = super.nextTree();
            } catch(Exception e) {
                System.err.println(e);
            }
            try {
                if (tree==null)
                {
                    try {
                        tree = new TBTree(null, Integer.MAX_VALUE, null, 0, 0);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
                treeQueue.put(tree);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            // the last tree inserted will be null, indicating there are no more trees
            if (tree.index==Integer.MAX_VALUE) break;
        }
        super.close();
    }
    
    public TBTree nextTree()
    {
        while (true)
        {
            try {
                threadedLastTree = treeQueue.take();
                if (threadedLastTree.index==Integer.MAX_VALUE)
                return null;
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
                if (threadedLastTree!=null)
                {
                    if (threadedLastTree.index==index)
                        return threadedLastTree;
                    if (threadedLastTree.index>index)
                        return null;
                }
                threadedLastTree = treeQueue.take();
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
