package clearsrl.align;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public abstract class SentencePairReader {
    
	static final int GZIP_BUFFER = 0x40000;
	
    Properties props;
    
    ObjectInputStream inStream;
    ObjectOutputStream outStream;
    
    boolean objStreamAvailable;
    
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

    public void initialize() throws IOException
    {
        close();

        if (objStreamAvailable)
        {
            try {
                inStream = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(props.getProperty("sentencePair_file", "")),GZIP_BUFFER),GZIP_BUFFER*4));
                return;
            } catch (FileNotFoundException e) {
                objStreamAvailable = false;
            } catch (Exception e) {
                e.printStackTrace();
                objStreamAvailable = false;
            }
        }
        
		try {
            outStream = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(props.getProperty("sentencePair_file", "")),GZIP_BUFFER),GZIP_BUFFER*4));
        } catch (IOException e) {
            //e.printStackTrace();
        }
		
    }
    
    public abstract SentencePair nextPair();
    
    SentencePair readSentencePair()
    {
        if (inStream!=null)
            try {
                return (SentencePair) inStream.readObject();
            } catch (Exception e) {
            	if (!(e instanceof EOFException))
            	{
            		e.printStackTrace();
            		objStreamAvailable = false;
            	}
            	try {
                    inStream.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                    objStreamAvailable = false;
                } finally {
                    inStream = null;
                }
                return null;
            }
        return null;
    }
    
    void writeSentencePair(SentencePair sentencePair) 
    {
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
    }
    
    public void close()
    {
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
    }
}
