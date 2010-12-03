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
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeMap;

import clearsrl.SRLModel.Feature;
import clearsrl.SRLModel.PredicateFeature;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.morph.WordnetStemmer;

public class TrainSRL {
	static final float THRESHOLD=0.90f;
	
	static SRInstance addTrainingSample(SRLModel model, SRInstance instance, TBTree parsedTree, String[] namedEntities, float threshold, boolean buildDictionary)
	{	
		ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
		ArrayList<TObjectFloatHashMap<String>> labels = new ArrayList<TObjectFloatHashMap<String>>();

		if (instance.tree.getTokenCount() == parsedTree.getTokenCount())
		{
		    SRLUtil.getSamplesFromParse(instance, parsedTree, threshold, argNodes, labels);
		    SRInstance trainInstance = new SRInstance(parsedTree.getRootNode().getTokenNodes().get(instance.predicateNode.tokenIndex), parsedTree);
		    for (int i=0; i<labels.size(); ++i)
                trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(i)), argNodes.get(i)));
            trainInstance.addArg(new SRArg("rel", trainInstance.predicateNode));
		    
			model.addTrainingSamples(trainInstance, namedEntities, buildDictionary);
		}
			
		if (!buildDictionary)
		{	
			SRInstance trainInstance = new SRInstance(instance.predicateNode, parsedTree);
			for (int i=0; i<labels.size(); ++i)
			{
				if (SRLUtil.getMaxLabel(labels.get(i)).equals(SRLModel.NOT_ARG)) continue;
				trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(i)), argNodes.get(i)));
			}
			trainInstance.addArg(new SRArg("rel", instance.predicateNode));
			return trainInstance;
		}
		return null;
	}
	
	static List<SRInstance> addTrainingSentence(SRLModel model, SRInstance[] instances, TBTree parsedTree, String[] namedEntities, float threshold, boolean buildDictionary)
	{
	    List<SRInstance> trainInstances = new ArrayList<SRInstance>();

        for (SRInstance instance:instances)
        {
            if (instance.tree.getTokenCount() == parsedTree.getTokenCount())
            {
                ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
                ArrayList<TObjectFloatHashMap<String>> labels = new ArrayList<TObjectFloatHashMap<String>>();
                SRLUtil.getSamplesFromParse(instance, parsedTree, threshold, argNodes, labels);
                SRInstance trainInstance = new SRInstance(parsedTree.getRootNode().getTokenNodes().get(instance.predicateNode.tokenIndex), parsedTree);
                for (int i=0; i<labels.size(); ++i)
                    trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(i)), argNodes.get(i)));
                trainInstance.addArg(new SRArg("rel", trainInstance.predicateNode));
                trainInstances.add(trainInstance);
            }
	    }
        model.addTrainingSentence(parsedTree, trainInstances, namedEntities, buildDictionary);
        return trainInstances;
	}
	
	public static void main(String[] args) throws Exception
	{	
		Properties props = new Properties();
		FileInputStream in = new FileInputStream(args[0]);
		props.load(in);
		in.close();
		
		URL url = new URL("file", null, props.getProperty("wordnet_dic"));
		// construct the dictionary object and open it
		Dictionary dict = new Dictionary(url);
		dict.getCache().setMaximumCapacity(5000);
		dict.open();
		WordnetStemmer stemmer = new WordnetStemmer(dict);
		
		ArrayList<Feature> features = new ArrayList<Feature>();
		{
    		StringTokenizer tokenizer = new StringTokenizer(props.getProperty("feature"),",");
    		while (tokenizer.hasMoreTokens())
    		{
    			try {
    				features.add(Feature.valueOf(tokenizer.nextToken().trim()));
    			} catch (IllegalArgumentException e) {
    				System.err.println(e);
    			}
    		}
		}
		System.out.println(EnumSet.copyOf(features));
		
        ArrayList<PredicateFeature> predicateFeatures = new ArrayList<PredicateFeature>();
        {
            StringTokenizer tokenizer = new StringTokenizer(props.getProperty("predicateFeature"),",");
            while (tokenizer.hasMoreTokens())
            {
                try {
                    predicateFeatures.add(PredicateFeature.valueOf(tokenizer.nextToken().trim()));
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
            }
        }
        System.out.println(EnumSet.copyOf(predicateFeatures));

		SRLModel model = new SRLModel(SRLModel.Language.ENGLISH, EnumSet.copyOf(features), predicateFeatures.isEmpty()?null:EnumSet.copyOf(predicateFeatures));
		//model.setLabeled(false);
		model.setWordNetStemmer(stemmer);
		
		TBHeadRules headrules = new TBHeadRules(props.getProperty("headrules"));
		
		String dataFormat = props.getProperty("format", "default");
		
		int tCount = 0;
		int gCount = 0;

		int hCnt = 0;
		int tCnt = 0;
		
		if (dataFormat.equals("default"))
		{
			String trainRegex = props.getProperty("train.regex");
			Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), trainRegex);
			Map<String, TIntObjectHashMap<List<PBInstance>>>  propBank = PBUtil.readPBDir(props.getProperty("pbdir"), trainRegex, treeBank, false);
			Map<String, TBTree[]> parsedTreeBank = TBUtil.readTBDir(props.getProperty("parsedir"), trainRegex);
			
			for (Map.Entry<String, TBTree[]> entry: parsedTreeBank.entrySet())
				for (TBTree tree: entry.getValue())
					TBUtil.findHeads(tree.getRootNode(), headrules);
			
			model.initDictionary();
			for (Map.Entry<String, TIntObjectHashMap<List<PBInstance>>> entry:propBank.entrySet())
				for(TIntObjectIterator<List<PBInstance>> iter = entry.getValue().iterator(); iter.hasNext();)
				{
					iter.advance();
					for (PBInstance pbInstance:iter.value())
					{
					    SRInstance instance = new SRInstance(pbInstance);
					    addTrainingSample(model, instance, parsedTreeBank.get(entry.getKey())[pbInstance.treeIndex], null, THRESHOLD, true);
					}
				}
			model.finalizeDictionary(Integer.parseInt(props.getProperty("train.dictionary.cutoff", "2")));
			
			model.initScore();
			for (Map.Entry<String, TIntObjectHashMap<List<PBInstance>>> entry:propBank.entrySet())
                for(TIntObjectIterator<List<PBInstance>> iter = entry.getValue().iterator(); iter.hasNext();)
				{
					iter.advance();
					for (PBInstance pbInstance:iter.value())
                    {
    					SRInstance instance = new SRInstance(pbInstance);
    					
    					SRInstance trainInstance = addTrainingSample(model, instance, parsedTreeBank.get(entry.getKey())[pbInstance.treeIndex], null, THRESHOLD, false);
    					tCount += trainInstance.args.size()-1;
    					gCount += instance.args.size()-1;
    				
    					for (SRArg arg:instance.args)
    					{
    					    if (arg.isPredicate()) continue;
    					    if (arg.node.getParent()==null) continue;
    					    
    					    if (arg.node.head != arg.node.getParent().head)
    					        hCnt++;
    					    tCnt++;
    					}
                    
    					model.score.addResult(SRLUtil.convertSRInstanceToTokenMap(trainInstance), SRLUtil.convertSRInstanceToTokenMap(instance));
                    }
				}
			model.score.printResults(System.out);
		}
		/*
		else if (dataFormat.equals("conll"))
		{
			ArrayList<CoNLLSentence> training = CoNLLSentence.read(new FileReader(props.getProperty("train.input")), true);
			model.initDictionary();
			for (CoNLLSentence sentence:training)
			{
				TBUtil.findHeads(sentence.parse.getRootNode(), headrules);
				for (SRInstance instance:sentence.srls)
					addTrainingSample(model, instance, instance.tree, sentence.namedEntities, THRESHOLD, true);
			}
			model.finalizeDictionary(Integer.parseInt(props.getProperty("train.dictionary.cutoff", "2")));
			
			model.initScore();

			for (CoNLLSentence sentence:training)
			{
				for (SRInstance instance:sentence.srls)
				{
					SRInstance trainInstance = addTrainingSample(model, instance, instance.tree, sentence.namedEntities, THRESHOLD, false);
					
					tCount += trainInstance.args.size()-1;
					gCount += instance.args.size()-1;
				
                    for (SRArg arg:trainInstance.args)
                    {
                        if (arg.isPredicate()) continue;
                        if (arg.node==null) continue;
                        if (arg.node.getParent()==null) continue;
                        
                        if (arg.node.head != arg.node.getParent().head)
                            hCnt++;
                        tCnt++;
                    }
					
					model.score.addResult(SRLUtil.convertSRInstanceToTokenMap(trainInstance), SRLUtil.convertSRInstanceToTokenMap(instance));
				}
			}
			model.score.printResults(System.out);
		}*/
        else if (dataFormat.equals("conll"))
        {
            ArrayList<CoNLLSentence> training = CoNLLSentence.read(new FileReader(props.getProperty("train.input")), true);
            model.initDictionary();
            for (CoNLLSentence sentence:training)
            {
                TBUtil.findHeads(sentence.parse.getRootNode(), headrules);
                addTrainingSentence(model, sentence.srls, sentence.parse, sentence.namedEntities, THRESHOLD, true);
            }
            model.finalizeDictionary(Integer.parseInt(props.getProperty("train.dictionary.cutoff", "2")));

            for (CoNLLSentence sentence:training)
                addTrainingSentence(model, sentence.srls, sentence.parse, sentence.namedEntities, THRESHOLD, false);
        }		
		System.out.println("Reference arg instance count: "+gCount);
		System.out.println("Training arg instance count: "+tCount);
		System.out.printf("head word unique: %d/%d\n", hCnt, tCnt);
		System.gc();
		model.train(props);
		
		ObjectOutputStream mOut = new ObjectOutputStream(new FileOutputStream(props.getProperty("model_file")));
		mOut.writeObject(model);
		mOut.close();
		
		System.out.println("Model saved to "+props.getProperty("model_file"));	
	}

}
