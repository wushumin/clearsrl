package clearcommon.propbank;

public class DefaultPBTokenizer implements PBTokenizer {

    @Override
    public String[] tokenize(String line) {
        return line.split("[ \t]+");
    }

}
