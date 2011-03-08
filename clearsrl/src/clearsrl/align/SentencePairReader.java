package clearsrl.align;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Properties;
import java.util.Scanner;

import clearsrl.align.SentencePair.BadInstanceException;

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
    
    @Override
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
        
		boolean sentenceAligned = !(props.getProperty("sentence_aligned")==null||props.getProperty("sentence_aligned").equals("false"));

		if (sentenceAligned)
		{
			srcSentenceReader = new AlignedSentenceReader("src.", props);
			dstSentenceReader = new AlignedSentenceReader("dst.", props);
			
			srcSentenceReader.initialize();
			dstSentenceReader.initialize();
		}
		else
		{
			//TODO: init srcSentenceReader, dstSentenceReader, srcTokenIndexScanner, dstTokenIndexScanner
		}
		
		srcAlignmentScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("src.token_alignment")))).useDelimiter("[\n\r]");
		dstAlignmentScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("dst.token_alignment")))).useDelimiter("[\n\r]");
    }

    
    public SentencePair nextPair()
    {
        SentencePair sentencePair = new SentencePair(count);
        
        sentencePair.src = srcSentenceReader.nextSentence();
        sentencePair.dst = dstSentenceReader.nextSentence();
        
        if (sentencePair.src==null || sentencePair.dst==null) return null;
        
        srcAlignmentScanner.next(); srcAlignmentScanner.next(); // skip comment & text
        dstAlignmentScanner.next(); dstAlignmentScanner.next(); // skip comment & text
        
        
        String srcLine = srcAlignmentScanner.next();
        String dstLine = dstAlignmentScanner.next();
        try {
            sentencePair.parseSrcAlign(srcLine);
            sentencePair.parseDstAlign(dstLine);
        } catch (BadInstanceException e) {
        	System.err.println(count);
            e.printStackTrace();
            System.err.println(srcLine);
            System.err.println(dstLine);
        } finally {
            ++count;
        }
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
