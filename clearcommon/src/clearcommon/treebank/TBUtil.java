package clearcommon.treebank;

import clearcommon.util.JIO;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TBUtil {

	public static TBNode findHeads(TBNode node, TBHeadRules headrules)
    {
		if (node.isTerminal()) return node.head=node;
		
        TBHeadRule headrule = headrules.getHeadRule(node.pos);
        if (headrule==null)
        {
            System.err.println(node.toParse());
            headrule = TBHeadRule.DEFAULT;
        }
        ArrayList<TBNode> decendants = node.getTokenNodes();
       
        for (TBNode childNode: node.getChildren())
        	findHeads(childNode, headrules);

        for (int r=0; r<headrule.rules.length; ++r)
        {
            if (headrule.dirs[r] == TBHeadRule.Direction.LEFT)
            {
                for (int i=0; i<node.getChildren().size(); i++)
                    if ((node.head = findHeadsAux(node, node.getChildren().get(i), headrule.rules[r]))!=null)
                        return node.head;
            }
            else
            {
                for (int i=node.getChildren().size()-1; i>=0; i--)
                	if ((node.head = findHeadsAux(node, node.getChildren().get(i), headrule.rules[r]))!=null)
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
	
	public static Map<String, TBTree[]> readTBDir(String dirName, String regex)
	{
		File dir = new File(dirName);
		
		ArrayList<String> files = JIO.getFiles(dir, regex);
		if (!dir.isDirectory() && Pattern.matches(regex, dir.getName()))
			files.add(dir.getName());
		
		Map<String, TBTree[]> tbMap = new TreeMap<String, TBTree[]>();
		
		TBReader          tbreader = null;
		ArrayList<TBTree> a_tree   = null;
		TBTree            tree     = null;
		for (String treeFile: files)
		{
			System.out.println("Reading "+dirName+File.separatorChar+treeFile);
			try {
				tbreader    = new TBReader(dirName, treeFile);
			
				a_tree = new ArrayList<TBTree>();
				
				while ((tree = tbreader.nextTree()) != null)
					a_tree.add(tree);
				tbMap.put(treeFile, a_tree.toArray(new TBTree[a_tree.size()]));
			} catch(Exception e)
			{
				System.err.println(e);
			}
		}
		
		return tbMap;
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
				for (TBTree tree:entry.getValue())
				{
					ArrayList<TBNode> nodes = tree.getRootNode().getTokenNodes();
					for (TBNode node: nodes)
						pStream.print(node.word+" ");
					pStream.print('\n');
				}
				pStream.close();
			} catch (Exception e)
			{
				System.err.print(e);
			}
		}
	}
}
