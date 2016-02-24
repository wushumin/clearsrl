package clearcommon.treebank;

import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import clearcommon.propbank.PBFileReader;

public class ThreadedTBFileReader extends TBFileReader implements Runnable {
    
    private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());
    
    SerialTBFileReader tbReader;
    TBTree lastTree;
    BlockingQueue<TBTree> treeQueue;
    AtomicBoolean keepRunning;
    Thread thread;
    
    public ThreadedTBFileReader(Reader reader, int capacity)
    {
        super(null);
        tbReader = new SerialTBFileReader(reader, fileName);
        init(capacity);
    }
    
    public ThreadedTBFileReader(Reader reader, String fileName, int capacity)
    {
        super(fileName);
        tbReader = new SerialTBFileReader(reader, fileName);
        init(capacity);
    }
    
    public ThreadedTBFileReader(String fileName, int capacity) throws IOException {
        super(fileName);
        tbReader = new SerialTBFileReader(fileName);
        init(capacity);
    }
    
    public ThreadedTBFileReader(String dirName, String fileName, int capacity) throws IOException {
        super(fileName);
        tbReader = new SerialTBFileReader(dirName, fileName);
        init(capacity);
    }
    
    protected void init(int capacity)
    {
        lastTree = null;
        
        treeQueue = new ArrayBlockingQueue<TBTree>(capacity);
        
        keepRunning = new AtomicBoolean(true);
        thread = new Thread(this);
        thread.start();
    }
    
    @Override
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
                tree = tbReader.nextTree();
            } catch(Exception e) {
                tree = null;
                logger.severe(e.toString());
            }
            try {
                if (tree==null)
                {
                    try {
                        tree = new TBTree(null, Integer.MAX_VALUE, null, 0, 0);
                        keepRunning.set(false);
                    } catch (ParseException e) {
                        logger.severe(e.toString());
                    }
                }
                //else 
                //    logger.info("Read tree "+tree.index);
                treeQueue.put(tree);
            } catch (InterruptedException e) {
                logger.severe(e.toString());
            }
            
            // the last tree inserted will be null, indicating there are no more trees
            if (tree.index==Integer.MAX_VALUE) break;
        }
        tbReader.close();
        logger.info("done");
    }
    
    @Override
    public TBTree nextTree()
    {
        while (true)
        {
            try {
                lastTree = treeQueue.take();
                if (lastTree.index==Integer.MAX_VALUE)
                    return null;
                return lastTree;
            } catch (InterruptedException e) {
                if (!keepRunning.get()) return null;
            }
        }
    }
    
    @Override
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
            } catch (InterruptedException e) {
                if (!keepRunning.get()) return null;
            }
        }
    }
    
    @Override
    public void close()
    {
        if (!closed)
        {
            keepRunning.set(false);
            treeQueue.clear();
            closed = true;
        }
    }
}
