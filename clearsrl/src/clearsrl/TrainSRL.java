package clearsrl;

import clearcommon.alg.FeatureSet;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.OntoNoteTreeFileResolver;
import clearcommon.treebank.SerialTBFileReader;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBFileReader;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.treebank.ParseException;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;

import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

import clearsrl.SRLModel.Feature;
import clearsrl.SRLModel.PredFeature;

public class TrainSRL {
	static final float THRESHOLD=0.90f;
	
	static SRInstance addTrainingSample(SRLModel model, SRInstance instance, TBTree parsedTree, String[] namedEntities, float threshold, boolean buildDictionary)
	{	
		ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
		ArrayList<Map<String, Float>> labels = new ArrayList<Map<String, Float>>();

		if (instance.tree.getTokenCount() == parsedTree.getTokenCount())
		{
		    SRLUtil.getSamplesFromParse(instance, parsedTree, threshold, argNodes, labels);
		    SRInstance trainInstance = new SRInstance(instance.predicateNode, parsedTree, instance.getRolesetId(), 1.0);
		    for (int i=0; i<labels.size(); ++i)
                trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(i)), argNodes.get(i)));
		    
			model.addTrainingSamples(trainInstance, namedEntities, buildDictionary);
		}
			
		if (!buildDictionary)
		{	
			SRInstance trainInstance = new SRInstance(instance.predicateNode, parsedTree, instance.getRolesetId(), 1.0);
			for (int i=0; i<labels.size(); ++i)
			{
				if (SRLUtil.getMaxLabel(labels.get(i)).equals(SRLModel.NOT_ARG)) continue;
				trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(i)), argNodes.get(i)));
			}
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
                ArrayList<Map<String, Float>> labels = new ArrayList<Map<String, Float>>();
                SRLUtil.getSamplesFromParse(instance, parsedTree, threshold, argNodes, labels);
                SRInstance trainInstance = new SRInstance(instance.predicateNode, parsedTree, instance.getRolesetId(), 1.0);
                for (int i=0; i<labels.size(); ++i)
                    trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(i)), argNodes.get(i)));
                trainInstances.add(trainInstance);
                //System.out.println(trainInstance);
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
		
		props = PropertyUtil.filterProperties(props, "srl.");
		props = PropertyUtil.filterProperties(props, "train.", true);
		
		System.out.print(PropertyUtil.toString(props));
		
		LanguageUtil langUtil = (LanguageUtil) Class.forName(props.getProperty("language.util-class")).newInstance();
        if (!langUtil.init(props))
            System.exit(-1);
		
		Set<EnumSet<Feature>> features = new HashSet<EnumSet<Feature>>();
		{
		    String[] tokens = props.getProperty("feature").trim().split(",");
		    for (String token:tokens)
		        try {
		            features.add(FeatureSet.toEnumSet(Feature.class, token));
		        } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }  
		}
		
		Set<EnumSet<PredFeature>> predicateFeatures = new HashSet<EnumSet<PredFeature>>();
        {
            String[] tokens = props.getProperty("predicateFeature").trim().split(",");
            for (String token:tokens)
                try {
                    predicateFeatures.add(FeatureSet.toEnumSet(PredFeature.class, token));
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }  
        }

		SRLModel model = new SRLModel(features, predicateFeatures.isEmpty()?null:predicateFeatures);
		
		System.out.println("Features:");
        for (EnumSet<SRLModel.Feature> feature:model.features.getFeatures())
            System.out.println(feature.toString());
        
        if (model.predFeatures!=null)
        {
            System.out.println("\nPredicate features:");
            for (EnumSet<SRLModel.PredFeature> feature:model.predFeatures.getFeatures())
                System.out.println(feature.toString());
        }
		
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
			String treeRegex = props.getProperty("tb.regex");
			String propRegex = props.getProperty("pb.regex");
			Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), treeRegex);
			Map<String, SortedMap<Integer, List<PBInstance>>>  propBank = 
			    PBUtil.readPBDir(props.getProperty("pbdir"), 
			                     propRegex, 
			                     new TBReader(treeBank),
			                     dataFormat.equals("ontonotes")?new OntoNoteTreeFileResolver():null);
			
			System.out.println(propBank.size());
			
			Map<String, TBTree[]> parsedTreeBank = null;
			
			if (!props.getProperty("goldparse", "false").equals("false"))
			{
			    parsedTreeBank = treeBank;
			    model.setTrainGoldParse(true);
			}
			else
			{
    			parsedTreeBank = new TreeMap<String, TBTree[]>();
    			for (Map.Entry<String, SortedMap<Integer, List<PBInstance>>> entry:propBank.entrySet())
    			{
    			    try {
    	                TBFileReader tbreader    = new SerialTBFileReader(props.getProperty("parsedir"), entry.getKey());
    	                System.out.println("Reading "+props.getProperty("parsedir")+File.separatorChar+entry.getKey());
    	                ArrayList<TBTree> a_tree = new ArrayList<TBTree>();
    	                TBTree tree;
    	                while ((tree = tbreader.nextTree()) != null)
    	                    a_tree.add(tree);
    	                parsedTreeBank.put(entry.getKey(), a_tree.toArray(new TBTree[a_tree.size()]));
    	            } catch (FileNotFoundException e) {
    	            } catch (ParseException e) {
    	                e.printStackTrace();
    	            }
    			}
			}

			model.initDictionary();
			for (Map.Entry<String, TBTree[]> entry: parsedTreeBank.entrySet())
			{
				SortedMap<Integer, List<PBInstance>> pbFileMap = propBank.get(entry.getKey());
				if (pbFileMap==null) continue;
				
				System.out.println("Processing (p1) "+entry.getKey());
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
            for (TObjectIntIterator<String> iter = rolesetEmpty.iterator(); iter.hasNext();)
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
            	SortedMap<Integer, List<PBInstance>> pbFileMap = propBank.get(entry.getKey());
            	if (pbFileMap==null) continue;
            	
            	System.out.println("Processing (p2) "+entry.getKey());
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
                                //if (!rolesetEmpty.containsKey(instance.getRoleset()))
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
		
		ObjectOutputStream mOut = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(props.getProperty("model_file"))));
		mOut.writeObject(model);
		mOut.close();
		
		System.out.println("Model saved to "+props.getProperty("model_file"));	
	}

}
