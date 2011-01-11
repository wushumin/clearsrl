package clearsrl;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TObjectFloatHashMap;
import harvest.propbank.PBInstance;
import harvest.propbank.PBUtil;
import harvest.treebank.TBHeadRules;
import harvest.treebank.TBNode;
import harvest.treebank.TBTree;
import harvest.treebank.TBUtil;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import clearsrl.SRLModel.Feature;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.morph.WordnetStemmer;

public class TestPassive {
	static final float THRESHOLD=0.8f;
	
	public static void main(String[] args) throws Exception
	{	
		Properties props = new Properties();
		FileInputStream in = new FileInputStream(args[0]);
		props.load(in);
		in.close();
		
		LanguageUtil langUtil = (LanguageUtil) Class.forName("clearsrl.EnglishUtil").newInstance();
        if (!langUtil.init(props))
            System.exit(-1);
		
		
		ArrayList<Feature> features = new ArrayList<Feature>();
		StringTokenizer tokenizer = new StringTokenizer(props.getProperty("feature"),",");
		while (tokenizer.hasMoreTokens())
		{
			try {
				features.add(Feature.valueOf(tokenizer.nextToken().trim()));
			} catch (IllegalArgumentException e) {
				System.err.println(e);
			}
		}
		System.out.println(EnumSet.copyOf(features));
		
		TBHeadRules headrules = new TBHeadRules(props.getProperty("headrules"));
		
		String dataFormat = props.getProperty("format", "default");
		
		int tCount = 0;
		int gCount = 0;
		
		if (!dataFormat.equals("conll"))
			return;
		
		ArrayList<CoNLLSentence> training = CoNLLSentence.read(new FileReader(props.getProperty("train.input")), true);
		
		for (CoNLLSentence sentence:training)
		{
			TBUtil.findHeads(sentence.parse.getRootNode(), headrules);
			for (SRInstance instance:sentence.srls)
			{
				if (instance.getPredicateNode().pos.matches("V.*"))
				{
					System.out.println(langUtil.getPassive(instance.getPredicateNode())+": "+" "+instance);
					System.out.println("   "+instance.tree.toString()+"\n");
				}
			}
		}
	}

}
