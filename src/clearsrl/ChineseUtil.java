package clearsrl;

import java.util.List;
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
        TBNode root = predicateNode.getRoot();
        List<TBNode> nodes = root.getTokenNodes();

        for (TBNode node:nodes)
        {
            if (node.pos.matches("(SB|LB).*"))
            {
                TBNode parent = node.getParent();
                if (parent==null || !parent.pos.matches("VP.*"))
                    continue;
                if (predicateNode.isDecendentOf(parent))
                {
                }
            }
            
        }
        
        return 0;
    }

    public TBHeadRules getHeadRules() {
        return headRules;
    }

}
