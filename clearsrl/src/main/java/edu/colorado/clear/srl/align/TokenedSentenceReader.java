package edu.colorado.clear.srl.align;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.SortedMap;

import edu.colorado.clear.common.propbank.DefaultPBTokenizer;
import edu.colorado.clear.common.propbank.OntoNotesTokenizer;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.propbank.PBTokenizer;
import edu.colorado.clear.common.propbank.PBUtil;
import edu.colorado.clear.common.treebank.TBReader;
import edu.colorado.clear.common.treebank.TBTree;
import edu.colorado.clear.common.treebank.TBUtil;

public class TokenedSentenceReader extends SentenceReader {

    Map<String, TBTree[]> treeBank;
    Map<String, SortedMap<Integer, List<PBInstance>>>  propBank;
    Scanner tokenScanner;
    boolean tokenIndexed;
    
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

    @SuppressWarnings("resource")
    @Override
    public void initialize() throws FileNotFoundException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        close();

        if (treeBank==null)
            treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), props.getProperty("tb.regex"));
        if (propBank==null) {
            PBTokenizer tokenzier = props.getProperty("pb.tokenizer")==null?(props.getProperty("data.format", "default").equals("ontonotes")?new OntoNotesTokenizer():new DefaultPBTokenizer()):(PBTokenizer)Class.forName(props.getProperty("pb.tokenizer")).newInstance();
            propBank = PBUtil.readPBDir(props.getProperty("pbdir"), props.getProperty("pb.regex"), new TBReader(treeBank), tokenzier);
        }
        tokenScanner = new Scanner(new BufferedReader(new FileReader(props.getProperty("token_idx")))).useDelimiter("[\n\r]");
        
        tokenIndexed = !props.getProperty("tokenIndexed","false").equals("false");
    }

    @Override
    public Sentence nextSentence() {
        if (!tokenScanner.hasNext()) return null;
        return Sentence.parseSentence(tokenScanner.nextLine(), treeBank, propBank, tokenIndexed);
    }

}
