package edu.colorado.clear.common.propbank;

public class OntoNotesTokenizer implements PBTokenizer {

    @Override
    public String[] tokenize(String line) {
        String[] tokens = line.split("[ \t]+");
        if (tokens.length<4)
            return null;
        tokens[0] = tokens[0].substring(0,tokens[0].indexOf("@"))+".parse";
        return tokens;
    }

}
