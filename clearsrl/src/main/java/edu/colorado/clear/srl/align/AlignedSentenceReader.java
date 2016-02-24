package edu.colorado.clear.srl.align;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import edu.colorado.clear.common.propbank.DefaultPBTokenizer;
import edu.colorado.clear.common.propbank.PBFileReader;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.treebank.ParseException;
import edu.colorado.clear.common.treebank.TBFileReader;
import edu.colorado.clear.common.treebank.TBReader;
import edu.colorado.clear.common.treebank.TBTree;
import edu.colorado.clear.common.treebank.ThreadedTBFileReader;

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
