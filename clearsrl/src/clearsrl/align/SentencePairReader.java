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

    Scanner srcAlignmentScanner;
    Scanner dstAlignmentScanner;
    
    SentenceReader srcSentenceReader;
    SentenceReader dstSentenceReader;

    Scanner srcTokenIndexScanner;
    Scanner dstTokenIndexScanner; 
    
    int count;
    
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
        count = 0;
        
		boolean sentenceAligned = !(props.getProperty("alignment.sentence_aligned")==null||props.getProperty("alignment.sentence_aligned").equals("false"));

		if (sentenceAligned)
		{
			srcSentenceReader = new AlignedSentenceReader("src.", props);
			dstSentenceReader = new AlignedSentenceReader("dst.", props);
		}
		else
		{
			//TODO: init srcSentenceReader, dstSentenceReader, srcTokenIndexScanner, dstTokenIndexScanner
		}
		
		srcAlignmentScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("src.token_alignment"))));
		dstAlignmentScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("dst.token_alignment"))));
    }

    
    public SentencePair nextPair()
    {
        SentencePair sentencePair = new SentencePair(count);
        
        sentencePair.src = srcSentenceReader.nextSentence();
        sentencePair.dst = dstSentenceReader.nextSentence();
        
        ++count;
        
        return sentencePair;
    }
    
    
    void close()
    {
        if (srcSentenceReader!=null)
        {
        	srcSentenceReader.close();
        	dstSentenceReader.close();
            srcAlignmentScanner.close();
            dstAlignmentScanner.close();   
        }
        if (srcTokenIndexScanner!= null)
        {
        	srcTokenIndexScanner.close();
        	dstTokenIndexScanner.close();
        }

        srcSentenceReader = null;
        dstSentenceReader = null;
        
        srcAlignmentScanner = null;
        dstAlignmentScanner = null;
        
        srcTokenIndexScanner = null;
        dstTokenIndexScanner = null;

    }
}
