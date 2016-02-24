package clearsrl.align;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Properties;


import clearcommon.propbank.DefaultPBTokenizer;
import clearcommon.propbank.PBFileReader;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.ParseException;
import clearcommon.treebank.TBFileReader;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.ThreadedTBFileReader;

public class AlignedSentenceReader extends SentenceReader {
    
    PBFileReader pbReader;
    TBFileReader tbReader;
    
    List<PBInstance> lastInstanceSet;
    
    int count;
    
    public AlignedSentenceReader(Properties props)
    {
       super(props);
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    @Override
    public void initialize() throws IOException
    {
        close();
        
        tbReader = new ThreadedTBFileReader(props.getProperty("tbfile"), 1000);
        pbReader = new PBFileReader(new TBReader(props.getProperty("tbfile"), false), 
                props.getProperty("pbfile"), new DefaultPBTokenizer());
        
        lastInstanceSet = null;
        count = 0;
    }
    
    @Override
    public Sentence nextSentence()
    {
        TBTree tree;
        try {
            tree = tbReader.getTree(count);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        } finally {
            ++count;
        }
        
        if (tree==null) return null;
        
        if (lastInstanceSet==null)
            lastInstanceSet = pbReader.nextPropSet();
        
        if (lastInstanceSet==null||lastInstanceSet.get(0).getTree().getIndex()!=tree.getIndex())
            return Sentence.parseSentence(tree, null);
        
        List<PBInstance> tmp = lastInstanceSet;
        lastInstanceSet = null;
        return Sentence.parseSentence(tmp.get(0).getTree(), tmp);

    }
    
    @Override
    public void close()
    {
        if (pbReader!=null)
        {
            pbReader.close();
            tbReader.close();
            pbReader = null;
            tbReader = null;
        }
    }
}
