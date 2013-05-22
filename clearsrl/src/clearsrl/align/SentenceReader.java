package clearsrl.align;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public abstract class SentenceReader {
    Properties props;
	
    public SentenceReader(Properties props) {
        this.props = props;
    }
    
	public abstract void initialize() throws FileNotFoundException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException;
	public abstract Sentence nextSentence();
	public abstract void close();
}
