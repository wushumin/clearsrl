package clearsrl.align;

import java.io.FileNotFoundException;
import java.util.Properties;

public abstract class SentenceReader {
    Properties props;
	
    public SentenceReader(String propRoot, Properties props)
    {
        this.props = new Properties();
        for (String propName:props.stringPropertyNames())
        {
        	if (propName.startsWith(propRoot))
        		this.props.setProperty(propName.substring(propRoot.length()), props.getProperty(propName));
        }
    }
    
	public abstract void initialize() throws FileNotFoundException;
	public abstract Sentence nextSentence();
	public abstract void close();
}
