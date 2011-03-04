package clearsrl.align;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import clearcommon.propbank.PBFileReader;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBReader;
import clearcommon.treebank.OntoNoteTreeFileResolver;
import clearcommon.treebank.TBFileReader;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.ThreadedTBFileReader;

public class SentencePairReader {
    
    Properties props;
    
    PBReader srcPBReader;
    PBReader dstPBReader;

    TBReader srcTBReader;
    TBReader dstTBReader;
   
    Scanner srcAlignmentScanner;
    Scanner dstAlignmentScanner;
    
    Sentence srcLastSentence;
    Sentence dstLastSentence;
    
    int count = 0;
    
    public SentencePairReader(Properties props)
    {
        this.props = props;
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
        
        srcTBReader = new TBReader(props.getProperty("src.tbdir"), false);
        dstTBReader = new TBReader(props.getProperty("src.tbdir"), false);
  
        srcPBReader = new PBReader(srcTBReader, props.getProperty("src.pbdir"), ".+", new OntoNoteTreeFileResolver());
        dstPBReader = new PBReader(dstTBReader, props.getProperty("dst.pbdir"), ".+", new OntoNoteTreeFileResolver());
        
        srcAlignmentScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("src.token_alignment"))));
        dstAlignmentScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("dst.token_alignment"))));

    }

    
    public SentencePair nextPair()
    {
        SentencePair sentencePair = new SentencePair(count);
        
        if (srcLastSentence==null)
        {
            List<PBInstance> instances = srcPBReader.nextPropSet();
            
        }
        
        if (dstLastSentence==null)
        {
            List<PBInstance> instances = dstPBReader.nextPropSet();
            
        }
        
        
        ++count;
        
        return sentencePair;
    }
    
    
    void close()
    {
        if (srcPBReader!=null)
        {
            
            srcPBReader.close();
            dstPBReader.close();
            srcTBReader.close();
            dstTBReader.close();
            
            srcAlignmentScanner.close();
            dstAlignmentScanner.close();   
        }

        srcPBReader = null;
        dstPBReader = null;
        srcTBReader = null;
        dstTBReader = null;
       
        srcAlignmentScanner = null;
        dstAlignmentScanner = null;
    }
}
