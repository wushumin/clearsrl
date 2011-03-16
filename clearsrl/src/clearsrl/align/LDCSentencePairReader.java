package clearsrl.align;

import java.io.FileNotFoundException;
import java.util.Properties;

public class LDCSentencePairReader extends SentencePairReader {

	
	
	public LDCSentencePairReader(Properties props) {
		this(props, true);
	}

	public LDCSentencePairReader(Properties props, boolean reWriteObjStream) {
		super(props, reWriteObjStream);
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
        //TODO: the rest
    }
	
	@Override
	public SentencePair nextPair() {
    	if (objStreamAvailable) return readSentencePair();
    	
    	int id=0;
    	SentencePair sentencePair = new SentencePair(id);
    	
		// TODO the rest
    	
    	writeSentencePair(sentencePair);
        
        return sentencePair;
	}
	
	@Override
    void close()
    {
		// TODO the rest
		
        super.close();
    }

}
