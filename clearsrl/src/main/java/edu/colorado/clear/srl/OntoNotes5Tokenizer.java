package clearsrl;

import java.util.Arrays;

import clearcommon.propbank.PBTokenizer;

public class OntoNotes5Tokenizer implements PBTokenizer {

    @Override
    public String[] tokenize(String line) {
        String[] tokens = line.split("[ \t]+");
        
        if (tokens.length<7 || !tokens[6].equals("gold"))
            return null;
        
        String[] tRet = Arrays.copyOfRange(tokens, 3, tokens.length);
        
        tRet[0] = tRet[0].substring(tRet[0].indexOf("/parse/")+7);
        
        return tRet;
    }

}
