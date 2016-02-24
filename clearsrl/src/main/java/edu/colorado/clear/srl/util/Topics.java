package clearsrl.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import clearcommon.treebank.TBNode;
import clearcommon.util.LanguageUtil;

public class Topics {
    public static String getTopicHeadword(TBNode node, LanguageUtil langUtil) {
    	TBNode head = getTopicHeadNode(node);
    	if (head==null || head.getWord()==null)
    		return null;
    	
		return langUtil==null?head.getWord().toLowerCase():langUtil.findStems(head).get(0).toLowerCase();
    }
    
    public static TBNode getTopicHeadNode(TBNode node) {
    	TBNode head = node.getHead();
		if (head.getPOS().equals("PU"))
			return null;
		if (node.getPOS().equals("PP")) {
			for (TBNode child:node.getChildren())
            	if (child.getHead()!=node.getHead()) {
            		if (child.getPOS().equals("LCP")) {
            			for (TBNode grandChild:child.getChildren())
            				if (grandChild.getHead()!=child.getHead()) {
            					head = grandChild.getHead();
            					break;
            				}
            		} else
            			head = child.getHead();
            		break;
            	}
		}
		return head;
    }

	public static Map<String, int[]> readTopics(File file){
		Map<String, int[]> topicMap = new HashMap<String, int[]>();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line=reader.readLine())!=null) {
				String[] tokens = line.split("\\s+");
				if (tokens.length<=1)
					continue;
				int[] topicIds = new int[tokens.length-1];
				for (int i=1;i<tokens.length;++i)
					topicIds[i-1] = Integer.parseInt(tokens[i].substring(tokens[i].indexOf(':')+1));
				topicMap.put(tokens[0], topicIds);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return topicMap;
	}
	
	public static Map<String, List<String>> readAllTopics(Properties prop) {
		Map<String, List<String>> argTopicMap = new HashMap<String, List<String>>();
		String tlist = prop.getProperty("topics");
		if (tlist==null) return argTopicMap;
		
		Set<String> topics = new TreeSet<String>(Arrays.asList(tlist.trim().split("\\s*,\\s*")));
		if (topics.contains("ALLARG")) {
			topics.clear();
			topics.add("ALLARG");
		}
		for (String topic:topics) {
			File file = new File(prop.getProperty("topic."+topic+".fname"));
			Map<String, int[]> topicMap = readTopics(file);
			for (Map.Entry<String, int[]> entry:topicMap.entrySet()) {
				String word = entry.getKey();
				String localTopic = topic;
				
				if (localTopic.equals("ALLARG")) {
					String[] tokens = entry.getKey().split(":");
					if (tokens.length!=2)
						continue;
					word = tokens[0];
					localTopic = tokens[1];
				}
				
				List<String> values = argTopicMap.get(word);
				if (values==null) {
					values = new ArrayList<String>();
					argTopicMap.put(word, values);
				}
				for (int topicId:entry.getValue())
					values.add(localTopic+':'+topicId);
				//values.add(topic);
			}
		}
		for (Map.Entry<String, List<String>> entry:argTopicMap.entrySet())
			((ArrayList<String>)entry.getValue()).trimToSize();
		
		return argTopicMap;
	}
	
}
