package clearsrl;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Stack;

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
        
        int retCode = 0;
        
        TBNode root = predicateNode.getRoot();
        List<TBNode> nodes = root.getTokenNodes();

        for (TBNode node:nodes)
        {
            if (node.getPOS().matches("(SB|LB).*"))
            {
                TBNode beiParent = node.getParent();
                if (beiParent==null || !beiParent.getPOS().matches("VP.*")|| predicateNode.getTokenIndex()<node.getTokenIndex())
                    continue;
                if (predicateNode.isDecendentOf(beiParent))
                {
                    TBNode predicateParent = predicateNode.getParent();
                    //System.out.println(predicateParent.getPathToAncestor(beiParent));
                    if (predicateParent==beiParent) // short bei?
                        return 1;
                    int count = countConstituents("VP", new LinkedList<TBNode>(predicateParent.getPathToAncestor(beiParent)), false, 0);
                    if (count <= 2)
                        return 2;
                    else
                        retCode = 1-count;
                }
            }
        }
        
        return retCode;
    }
    
    int countConstituents(String pos, Deque<TBNode> nodes, boolean left, int depth)
    {   
        if (nodes.isEmpty())
            return 0;
        
        TBNode node = nodes.pop();
        int count = node.getPOS().startsWith(pos)?1:0;
        
        ++depth;
        
        if (left)
            for (int i=node.getChildIndex()+1; i<node.getParent().getChildren().size();++i)
                count += countConstituents(pos, node.getParent().getChildren().get(i), depth);
        else
            for (int i=0; i<node.getChildIndex()-1;++i)
                count += countConstituents(pos, node.getParent().getChildren().get(i), depth);
        
        return count + countConstituents(pos, nodes, left, depth);
    }
     
    int countConstituents(String pos, TBNode node, int depth)
    {   
        int count = node.getPOS().startsWith(pos)?1:0;
        
        if (node.isTerminal() || depth == 0)
            return count;
        
        for (TBNode cNode:node.getChildren())
            count += countConstituents(pos, cNode, depth-1);
        
        return count;
    }

    

    public TBHeadRules getHeadRules() {
        return headRules;
    }

}
