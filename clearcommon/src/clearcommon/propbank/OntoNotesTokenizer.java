package clearcommon.propbank;

public class OntoNotesTokenizer implements PBTokenizer {

    @Override
    public String[] tokenize(String line) {
        String[] tokens = line.split("[ \t]+");
        if (tokens.length<4 || tokens[4].endsWith("-n"))  //skip roleset-v for ontonotes
            return null;
        tokens[0] = tokens[0].substring(0,tokens[0].indexOf("@"))+".parse";
        return tokens;
    }

}
