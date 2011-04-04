package clearsrl.align;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Properties;
import java.util.Scanner;

import clearcommon.util.PropertyUtil;
import clearsrl.align.SentencePair.BadInstanceException;

public class DefaultSentencePairReader extends SentencePairReader{
	
    Scanner srcAlignmentScanner;
    Scanner dstAlignmentScanner;
    
    SentenceReader srcSentenceReader;
    SentenceReader dstSentenceReader;

    Scanner srcTokenIndexScanner;
    Scanner dstTokenIndexScanner; 
    
    boolean sentenceAligned;
    int     count;
    
    public DefaultSentencePairReader(Properties props)
    {
        this(props, true);
    }
    
    public DefaultSentencePairReader(Properties props, boolean reWriteObjStream)
    {
    	super(props, reWriteObjStream);        
        sentenceAligned = !(props.getProperty("sentence_aligned")==null||props.getProperty("sentence_aligned").equals("false"));
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
    public void initialize() throws FileNotFoundException
    {
        close();
        super.initialize();
        if (objStreamAvailable) return;
        
        count = 0;
        
		if (sentenceAligned)
		{
			if (srcSentenceReader==null)
			    srcSentenceReader = new AlignedSentenceReader(PropertyUtil.filterProperties(props, "src.", true));
			if (dstSentenceReader==null)
			    dstSentenceReader = new AlignedSentenceReader(PropertyUtil.filterProperties(props, "dst.", true));
		}
		else
		{
		    if (srcSentenceReader==null)
                srcSentenceReader = new TokenedSentenceReader(PropertyUtil.filterProperties(props, "src.", true));
            if (dstSentenceReader==null)
                dstSentenceReader = new TokenedSentenceReader(PropertyUtil.filterProperties(props, "dst.", true));   
		}
		    
		srcSentenceReader.initialize();
		dstSentenceReader.initialize();
		
		srcAlignmentScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("src.token_alignment")))).useDelimiter("[\n\r]");
		dstAlignmentScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("dst.token_alignment")))).useDelimiter("[\n\r]");
		
    }
    
    @Override
    public SentencePair nextPair()
    {	
    	if (objStreamAvailable) return readSentencePair();
        
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
        
        writeSentencePair(sentencePair);
        
        return sentencePair;
    }
    
    @Override
    void close()
    {
        if (srcSentenceReader!=null)
            srcSentenceReader.close();
        if (dstSentenceReader!=null)
            dstSentenceReader.close();
        
        if (srcAlignmentScanner!=null)
        {
            srcAlignmentScanner.close();
            srcAlignmentScanner = null;
        }
        
        if (dstAlignmentScanner!=null)
        {
            dstAlignmentScanner.close();  
            dstAlignmentScanner = null;
        }
        
        if (srcTokenIndexScanner!= null)
        {
        	srcTokenIndexScanner.close();
        	srcTokenIndexScanner = null;
        }
        
        if (dstTokenIndexScanner!=null)
        {
        	dstTokenIndexScanner.close();
            dstTokenIndexScanner = null;
        }

        super.close();
        
    }
}
