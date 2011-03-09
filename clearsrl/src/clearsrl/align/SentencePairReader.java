package clearsrl.align;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Properties;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import clearsrl.align.SentencePair.BadInstanceException;

public class SentencePairReader {
    
	static final int GZIP_BUFFER = 0x40000;
	
    Properties props;

    Scanner srcAlignmentScanner;
    Scanner dstAlignmentScanner;
    
    SentenceReader srcSentenceReader;
    SentenceReader dstSentenceReader;

    Scanner srcTokenIndexScanner;
    Scanner dstTokenIndexScanner; 
    
    ObjectInputStream inStream;
    ObjectOutputStream outStream;
    
    boolean objStreamAvailable;
    
    int count;
    
    public SentencePairReader(Properties props)
    {
        this(props, true);
    }
    
    public SentencePairReader(Properties props, boolean reWriteObjStream)
    {
        this.props = props;
        objStreamAvailable = reWriteObjStream?false:true;
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
        
        if (objStreamAvailable)
        {
            try {
                inStream = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(props.getProperty("sentencePair_file")),GZIP_BUFFER),GZIP_BUFFER*4));
                return;
            } catch (FileNotFoundException e) {
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                objStreamAvailable = false;
            }
        }
        
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
		
		try {
            outStream = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(props.getProperty("sentencePair_file")),GZIP_BUFFER),GZIP_BUFFER*4));
        } catch (IOException e) {
            e.printStackTrace();
        }
		
    }
    
    public SentencePair nextPair()
    {
        if (inStream!=null)
            try {
                return (SentencePair) inStream.readObject();
            } catch (Exception e) {
            	if (!(e instanceof EOFException))
            		e.printStackTrace();
            	try {
                    inStream.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                } finally {
                    inStream = null;
                }
                return null;
            }
        
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
        
        if (outStream!=null)
            try {
                outStream.writeObject(sentencePair);
                if (sentencePair.id%1000==999)
                {
                    outStream.reset();
                    System.gc();
                }
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    outStream.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                } finally {
                    outStream = null;
                }
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

        if (inStream != null)
            try {
                inStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                inStream = null;
            }
        
        if (outStream != null)
            try {
                outStream.close();
                objStreamAvailable = true;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                outStream = null;
            }
              
        srcSentenceReader = null;
        dstSentenceReader = null;
        
        srcAlignmentScanner = null;
        dstAlignmentScanner = null;
        
        srcTokenIndexScanner = null;
        dstTokenIndexScanner = null;

    }
}
