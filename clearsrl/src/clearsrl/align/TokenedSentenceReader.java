package clearsrl.align;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.SortedMap;

import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.OntoNoteTreeFileResolver;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;

public class TokenedSentenceReader extends SentenceReader {

    Map<String, TBTree[]> treeBank;
    Map<String, SortedMap<Integer, List<PBInstance>>>  propBank;
    Scanner tokenScanner;
    
    public TokenedSentenceReader(Properties props)
    {
       super(props);
    }
    
    @Override
    public void close() {
        if (tokenScanner!=null) {
            tokenScanner.close();
            tokenScanner=null;
        }
    }

    @Override
    public void initialize() throws FileNotFoundException {
        close();
        
        // TODO Auto-generated method stub
        if (treeBank==null)
            treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), props.getProperty("tb.regex"));
        if (propBank==null)
            propBank = PBUtil.readPBDir(new TBReader(treeBank), props.getProperty("pbdir"), props.getProperty("pb.regex"), new OntoNoteTreeFileResolver());
        
        tokenScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("token_idx")))).useDelimiter("[\n\r]");
    }

    @Override
    public Sentence nextSentence() {
        if (!tokenScanner.hasNext()) return null;
        return Sentence.parseSentence(tokenScanner.nextLine(), treeBank, propBank);
    }

}
