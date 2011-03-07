package clearsrl.align;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Properties;


import clearcommon.propbank.PBFileReader;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.OntoNoteTreeFileResolver;
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
    
    public AlignedSentenceReader(String propRoot, Properties props)
    {
       super(propRoot, props);
    }
    
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public void initialize() throws FileNotFoundException
    {
        close();
        
        tbReader = new ThreadedTBFileReader(null, props.getProperty("tbfile"), 1000);
        pbReader = new PBFileReader(new TBReader(props.getProperty("tbfile"), false), 
        		props.getProperty("pbfile"), new OntoNoteTreeFileResolver());
        
        lastInstanceSet = null;
        count = 0;
    }
    
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
        
		if (lastInstanceSet==null)
			lastInstanceSet = pbReader.nextPropSet();
		
		if (lastInstanceSet==null||lastInstanceSet.get(0).getTree().getIndex()!=tree.getIndex())
			return Sentence.parseSentence(tree, null);
		
		lastInstanceSet = null;
		return Sentence.parseSentence(tree, lastInstanceSet);

    }
    
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
