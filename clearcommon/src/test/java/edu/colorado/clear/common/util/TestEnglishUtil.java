package edu.colorado.clear.common.util;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class TestEnglishUtil {
	public static void main(String[] args) throws Exception {   
		String home = System.getenv("HOME");
		Properties props = new Properties();
		props.put("wordnet_dic", home+"/WordNet-3.0/dict");
		props.put("headrules", home+"/stages/shumin/srl/english.headrules");
		props.put("frame_dir", home+"/stages/shumin/srl/english.frames.unified/frames");
		
		EnglishUtil util = new EnglishUtil();
		util.init(props);
		
		Set<String> frameIdSet = new HashSet<String>();
		for (PBFrame frame:util.frameMap.values())
			frameIdSet.add(frame.id);
		
		System.out.println(util.frameMap.size()+" "+frameIdSet.size());
		
    }
}
