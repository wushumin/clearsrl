package clearcommon.treebank;

import clearcommon.util.FileUtil;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class TBUtil {

	public static TBNode findHeads(TBNode node, TBHeadRules headrules)
    {
		if (node.isTerminal()) return node.head=node;
		
        TBHeadRule headrule = headrules.getHeadRule(node.pos);
        if (headrule==null)
        {
            if (node.pos.equals("NML"))
                node.pos = "NP";
            else if (node.pos.equals("SG"))
                node.pos = "S";
            headrule = headrules.getHeadRule(node.pos);
            if (headrule==null)
            {
                //System.err.println(node.pos+": "+node.toParse());
                headrule = TBHeadRule.DEFAULT;
            }
        }
        List<TBNode> decendants = node.getTokenNodes();
        if (decendants.isEmpty()) decendants = node.getTerminalNodes();
       
        for (TBNode childNode: node.getChildren())
        	findHeads(childNode, headrules);

        for (int r=0; r<headrule.rules.length; ++r)
        {
            if (headrule.dirs[r] == TBHeadRule.Direction.LEFT)
            {
                for (int i=0; i<node.children.length; i++)
                    if ((node.head = findHeadsAux(node, node.children[i], headrule.rules[r]))!=null)
                        return node.head;
            }
            else
            {
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
                {
                    System.out.println(tree.index);
                    System.out.flush();
                }
            }
            return a_tree.toArray(new TBTree[a_tree.size()]);
        } catch(Exception e)
        {
            System.err.println(e);
            return null;
        }
    }
    
	public static Map<String, TBTree[]> readTBDir(String dirName, String regex)
	{
		File dir = new File(dirName);
		
		List<String> files = FileUtil.getFiles(dir, regex);
		if (!dir.isDirectory() && Pattern.matches(regex, dir.getName()))
			files.add(dir.getName());
		
		Map<String, TBTree[]> tbMap = new TreeMap<String, TBTree[]>();
		for (String treeFile: files)
		{
			System.out.println("Reading "+dirName+File.separatorChar+treeFile);
			
			TBTree[] trees = readTBFile(dirName, treeFile);
			
			if (trees!=null) tbMap.put(treeFile, trees);
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
					for (TBNode node: tree.getRootNode().getTokenNodes())
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
