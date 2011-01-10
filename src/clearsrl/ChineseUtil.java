package clearsrl;

import java.util.Properties;

import harvest.treebank.TBHeadRules;
import harvest.treebank.TBNode;

public class ChineseUtil extends LanguageUtil {

    TBHeadRules headRules;
    
    @Override
    public boolean init(Properties props) {
        headRules = new TBHeadRules(props.getProperty("headrules"));
        return true;
    }
    
    @Override
    public int getPassive(TBNode predicateNode) {
        return 0;
    }

    public TBHeadRules getHeadRules() {
        return headRules;
    }

}
