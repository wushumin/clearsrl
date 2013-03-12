package clearcommon.treebank;

import clearcommon.propbank.PBFileReader;
import clearcommon.util.FileUtil;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author shumin
 *
 */
public final class TBUtil {

	private static Logger logger = Logger.getLogger(PBFileReader.class.getPackage().getName());
	
	/**
	 * Finds subject, direct object and indirect object of predicate node if present
	 * @param predicateNode
	 * @param langUtil
	 * @return
	 */
	public static TBNode[] findConstituents(TBNode predicateNode)
	{
		TBNode[] nodes = new TBNode[3];

		TBNode vpNode = predicateNode;
		
		while (vpNode.parent!=null && vpNode.parent.pos.equals("VP"))
			vpNode = vpNode.parent;
		
		TBNode searchNode = vpNode.pos.equals("VP")?vpNode:vpNode.parent;
		
		TBNode firstPP = null;
		TBNode firstNP = null;
		TBNode secondNP = null;
		
		for (int i=0; i<searchNode.children.length; ++i)
		{
			if (firstPP==null && searchNode.children[i].pos.equals("PP"))
				firstPP = searchNode.children[i];
			if ((firstNP==null || secondNP==null) && searchNode.children[i].pos.equals("NP"))
				if (firstNP==null)
					firstNP = searchNode.children[i];
				else
					secondNP = searchNode.children[i];		
		}
		
		if (vpNode.parent != null)
		{
			if (vpNode.parent.getPOS().startsWith("SQ"))
			{
				nodes[0] = firstNP;
				return nodes;
			}
			if (vpNode.childIndex!=0 && vpNode.parent.children[vpNode.childIndex-1].getPOS().equals("NP"))
				nodes[0] = vpNode.parent.children[vpNode.childIndex-1];
		}
		if (vpNode.pos.equals("VP"))
		{	
			if (secondNP==null || (firstPP!=null && secondNP.childIndex<firstPP.childIndex))
			{
				nodes[1] = firstNP==null?null:firstNP;
			}
			else
			{
				nodes[1] = secondNP==null?null:secondNP;
				nodes[2] = firstNP==null?null:firstNP;
			}
		}
		
		return nodes;
	}
	
	public static TBNode getPPHead(TBNode node)
	{
		TBNode head = null;
	    int i = node.getChildren().length-1;
		for (; i>=0; --i)
		{
			if (node.getChildren()[i].getPOS().matches("NP.*"))
			{
				if (node.getChildren()[i].getHead()!=null && node.getChildren()[i].getHeadword()!=null)
					head = node.getChildren()[i].getHead();
				break;
			}
		}
		if (i<0 && node.getChildren()[node.getChildren().length-1].getHead()!=null && 
				node.getChildren()[node.getChildren().length-1].getHeadword()!=null)
		    head = node.getChildren()[node.getChildren().length-1].getHead();
		
		return head;
	}
	
	public static void linkHeads(TBTree tree, TBHeadRules headrules) {
		findHeads(tree.getRootNode(), headrules);
		for (TBNode aNode : tree.getRootNode().getTerminalNodes())
			aNode.linkDependency();
	}

	static TBNode findHeads(TBNode node, TBHeadRules headrules) {
		if (node.isTerminal()) return node.head=node;
		
        TBHeadRule headrule = headrules.getHeadRule(node.pos);
        if (headrule==null) {
            if (node.pos.equals("NML"))
                node.pos = "NP";
            else if (node.pos.equals("SG"))
                node.pos = "S";
            headrule = headrules.getHeadRule(node.pos);
            if (headrule==null)
                headrule = TBHeadRule.DEFAULT;
        }
        List<TBNode> decendants = node.getTokenNodes();
        if (decendants.isEmpty()) decendants = node.getTerminalNodes();
       
        for (TBNode childNode: node.getChildren())
        	findHeads(childNode, headrules);

        for (int r=0; r<headrule.rules.length; ++r) {
            if (headrule.dirs[r] == TBHeadRule.Direction.LEFT) {
                for (int i=0; i<node.children.length; i++)
                    if ((node.head = findHeadsAux(node, node.children[i], headrule.rules[r]))!=null)
                        return node.head;
            } else {
                for (int i=node.children.length-1; i>=0; i--)
                	if ((node.head = findHeadsAux(node, node.children[i], headrule.rules[r]))!=null)
                        return node.head;
            }
        }
       
        // head not found (because all children are either empty-category or punctuation
        if (headrule.dirs[0] == TBHeadRule.Direction.LEFT)
            return node.head=decendants.get(0).head;
        else
            return node.head=decendants.get(decendants.size()-1).head;
    }
	
    protected static TBNode findHeadsAux(TBNode curr, TBNode child, String rule)
    {  
        if (!child.isEC() && !child.pos.equals(":") && !child.pos.equals(",") && child.pos.matches(rule))
            return child.head;
        return null;
    }

    public static TBTree[] readTBFile(String dirName, String treeFile)
    {
        ArrayList<TBTree>  a_tree = new ArrayList<TBTree>();
        try {
            TBFileReader tbreader     = new SerialTBFileReader(dirName, treeFile);
            TBTree       tree         = null;
            
            while ((tree = tbreader.nextTree()) != null)
            {
                a_tree.add(tree);
                if (tree.index!=0 && tree.index%10000==0)
                	logger.info("reading tree "+tree.index);
            }
            return a_tree.toArray(new TBTree[a_tree.size()]);
        } catch(Exception e) {
        	logger.severe(e.getMessage());
        	e.printStackTrace();
            return null;
        }
    }
    
	public static Map<String, TBTree[]> readTBDir(String dirName, String regex)
	{
		File dir = new File(dirName);
		
		List<String> files = FileUtil.getFiles(dir, regex);
		if (!dir.isDirectory() && Pattern.matches(regex, dir.getName()))
			files.add(dir.getName());
		
		return readTBDir(dirName, files);
	}
	
    public static Map<String, TBTree[]> readTBDir(String dirName, List<String> files)
	{
    	Map<String, TBTree[]> tbMap = new TreeMap<String, TBTree[]>();
		for (String treeFile: files)
		{
			logger.info("Reading "+dirName+File.separatorChar+treeFile);
			
			TBTree[] trees = readTBFile(dirName, treeFile);
			
			if (trees!=null) tbMap.put(treeFile, trees);
		}
		
		return tbMap;
	}
    
    public static void extractText(PrintStream out, TBTree[] trees) {
    	for (TBTree tree:trees) {
			for (TBNode node: tree.getRootNode().getTokenNodes())
				out.print(node.word+" ");
			out.print('\n');
		}
    }

	public static void extractText(String outputDir, Map<String, TBTree[]> trees)
	{
		File dir = new File(outputDir);
		if (!dir.isDirectory())
			return;
		
		for (Map.Entry<String, TBTree[]> entry: trees.entrySet())
		{
			try {
				File file = new File(dir, entry.getKey());
				file.getParentFile().mkdirs();
				PrintStream pStream = new PrintStream(file, "UTF-8");
				extractText(pStream, entry.getValue());
				pStream.close();
			} catch (Exception e)
			{
				logger.severe(e.getMessage());
			}
		}
	}
}
