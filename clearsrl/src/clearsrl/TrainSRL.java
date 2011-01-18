package clearsrl;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TObjectFloatHashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.OntoNoteTreeFileResolver;
import clearcommon.treebank.TBHeadRules;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.treebank.ParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
		    SRInstance trainInstance = new SRInstance(parsedTree.getRootNode().getTokenNodes().get(instance.predicateNode.getTokenIndex()), parsedTree);
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
                SRInstance trainInstance = new SRInstance(parsedTree.getRootNode().getTokenNodes().get(instance.predicateNode.getTokenIndex()), parsedTree);
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
		
		LanguageUtil langUtil = (LanguageUtil) Class.forName(props.getProperty("language.util-class")).newInstance();
        if (!langUtil.init(props))
            System.exit(-1);
		
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

		SRLModel model = new SRLModel(EnumSet.copyOf(features), predicateFeatures.isEmpty()?null:EnumSet.copyOf(predicateFeatures));
		//model.setLabeled(false);
		model.setLanguageUtil(langUtil);
		
		String dataFormat = props.getProperty("data.format", "default");
		
		int tCount = 0;
		int gCount = 0;

		int hCnt = 0;
		int tCnt = 0;
		
        boolean modelPredicate = !props.getProperty("model_predicate", "false").equals("false");
	
        TObjectIntHashMap<String> rolesetEmpty = new TObjectIntHashMap<String>();
        TObjectIntHashMap<String> rolesetArg = new TObjectIntHashMap<String>();
		if (!dataFormat.equals("conll"))
		{
			String trainRegex = props.getProperty("train.regex");
			//Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), trainRegex);
			Map<String, TIntObjectHashMap<List<PBInstance>>>  propBank = 
			    PBUtil.readPBDir(props.getProperty("pbdir"), 
			                     trainRegex, 
			                     props.getProperty("tbdir"), 
			                     dataFormat.equals("ontonotes")?new OntoNoteTreeFileResolver():null);
			
			System.out.println(propBank.size());
			
			Map<String, TBTree[]> parsedTreeBank = new TreeMap<String, TBTree[]>();
			for (Map.Entry<String, TIntObjectHashMap<List<PBInstance>>> entry:propBank.entrySet())
			{
			    try {
	                System.out.println("Reading "+props.getProperty("parsedir")+File.separatorChar+entry.getKey());
	                TBReader tbreader    = new TBReader(props.getProperty("parsedir")+File.separatorChar+entry.getKey());
	                ArrayList<TBTree> a_tree = new ArrayList<TBTree>();
	                TBTree tree;
	                while ((tree = tbreader.nextTree()) != null)
	                    a_tree.add(tree);
	                parsedTreeBank.put(entry.getKey(), a_tree.toArray(new TBTree[a_tree.size()]));
	            } catch (FileNotFoundException e) {
	                e.printStackTrace();
	            } catch (ParseException e) {
	                e.printStackTrace();
	            }
			}
			
            model.initDictionary();
			for (Map.Entry<String, TBTree[]> entry: parsedTreeBank.entrySet())
			{
			    TIntObjectHashMap<List<PBInstance>> pbFileMap = propBank.get(entry.getKey());
			    TBTree[] trees = entry.getValue(); 
			    for (int i=0; i<trees.length; ++i)
			    {
			        TBUtil.findHeads(trees[i].getRootNode(), langUtil.getHeadRules());
			        List<PBInstance> pbInstances = pbFileMap.get(i);
			        if (modelPredicate)
			        {
			            ArrayList<SRInstance> srls = new ArrayList<SRInstance>();
			            if (pbInstances!=null)
			            {
    			            for (PBInstance instance:pbInstances)
    			            {
    			                if (instance.getArgs().length>1)
    			                {
    			                    srls.add(new SRInstance(instance));
    			                    rolesetArg.put(instance.getRoleset(), rolesetArg.get(instance.getRoleset())+1);
    			                }
    			                else
    			                {
    			                    rolesetEmpty.put(instance.getRoleset(), rolesetEmpty.get(instance.getRoleset())+1);
    			                }
    			            }
			            }
                        addTrainingSentence(model, srls.toArray(new SRInstance[srls.size()]), trees[i], null, THRESHOLD, true);
                    }
			        else if (pbInstances!=null)
			        {
			            for (PBInstance pbInstance:pbInstances)
                        {
                            //System.out.println(pbInstance.rolesetId+" "+pbInstance.predicateNode.tokenIndex+" "+pbInstance.tree.getTreeIndex());
                            SRInstance instance = new SRInstance(pbInstance);
                            addTrainingSample(model, instance, parsedTreeBank.get(entry.getKey())[pbInstance.getTree().getIndex()], null, THRESHOLD, true);
                        }
			        }
			    }
			}
            model.finalizeDictionary(Integer.parseInt(props.getProperty("train.dictionary.cutoff", "2")));
			
            System.out.println("***************************************************");
            for (TObjectIntIterator<String> iter = rolesetEmpty.iterator(); iter.hasNext(); )
            {
                iter.advance();
                if (iter.value()<rolesetArg.get(iter.key())||iter.value()<2)
                    iter.remove();
                else
                    System.out.println(iter.key()+": "+iter.value()+"/"+rolesetArg.get(iter.key()));
            }
            System.out.println("***************************************************");
            
            for (Map.Entry<String, TBTree[]> entry: parsedTreeBank.entrySet())
            {
                TIntObjectHashMap<List<PBInstance>> pbFileMap = propBank.get(entry.getKey());
                TBTree[] trees = entry.getValue(); 
                for (int i=0; i<trees.length; ++i)
                {
                    List<PBInstance> pbInstances = pbFileMap.get(i);
                    if (modelPredicate)
                    {
                        ArrayList<SRInstance> srls = new ArrayList<SRInstance>();
                        if (pbInstances!=null)
                        {
                            for (PBInstance instance:pbInstances)
                            {
                                if (!rolesetEmpty.containsKey(instance.getRoleset()))
                                    srls.add(new SRInstance(instance));
                            }
                        }
                        
                        addTrainingSentence(model, srls.toArray(new SRInstance[srls.size()]), trees[i], null, THRESHOLD, false);
                    }
                    else if (pbInstances!=null)
                    {
                        for (PBInstance pbInstance:pbInstances)
                        {
                            //System.out.println(pbInstance.rolesetId+" "+pbInstance.predicateNode.tokenIndex+" "+pbInstance.tree.getTreeIndex());
                            SRInstance instance = new SRInstance(pbInstance);
                            addTrainingSample(model, instance, parsedTreeBank.get(entry.getKey())[pbInstance.getTree().getIndex()], null, THRESHOLD, false);
                        }
                    }
                }
            }
            /*
			model.initScore();
			for (Map.Entry<String, TIntObjectHashMap<List<PBInstance>>> entry:propBank.entrySet())
                for(TIntObjectIterator<List<PBInstance>> iter = entry.getValue().iterator(); iter.hasNext();)
				{
					iter.advance();
					for (PBInstance pbInstance:iter.value())
                    {
    					SRInstance instance = new SRInstance(pbInstance);
    					
    					SRInstance trainInstance = addTrainingSample(model, instance, parsedTreeBank.get(entry.getKey())[pbInstance.tree.getTreeIndex()], null, THRESHOLD, false);
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
			*/
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
                TBUtil.findHeads(sentence.parse.getRootNode(), langUtil.getHeadRules());
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
