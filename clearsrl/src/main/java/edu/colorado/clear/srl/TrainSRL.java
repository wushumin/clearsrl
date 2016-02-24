package edu.colorado.clear.srl;

import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import edu.colorado.clear.common.alg.FeatureSet;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.util.LanguageUtil;
import edu.colorado.clear.common.util.PropertyUtil;
import edu.colorado.clear.srl.SRLModel.Feature;
import edu.colorado.clear.srl.Sentence.Source;

public class TrainSRL {
	static Logger logger = Logger.getLogger("clearsrl");
	
    static final float THRESHOLD=0.90f;
    /*
    static List<SRInstance> addTrainingSentence(SRLModel model, SRInstance[] instances, TBTree parsedTree, String[] namedEntities, float threshold, boolean buildDictionary) {
        List<SRInstance> trainInstances = new ArrayList<SRInstance>();

        for (SRInstance instance:instances) {
            if (instance.tree.getTokenCount() == parsedTree.getTokenCount()) {
                ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
                ArrayList<Map<String, Float>> labels = new ArrayList<Map<String, Float>>();
                SRLUtil.getSamplesFromParse(instance, parsedTree, model.langUtil, threshold, argNodes, labels);
                SRInstance trainInstance = new SRInstance(instance.predicateNode, parsedTree, instance.getRolesetId(), 1.0);
                for (int i=0; i<labels.size(); ++i)
                    trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(i)), argNodes.get(i)));
                trainInstances.add(trainInstance);
                //System.out.println(trainInstance);
            }
        }
        model.addTrainingSentence(parsedTree, trainInstances, namedEntities, buildDictionary);
        return trainInstances;
    }*/
    
    public static void main(String[] args) throws Exception {   
        Properties props = new Properties();
        FileInputStream in = new FileInputStream(args[0]);
        props.load(in);
        in.close();
        props = PropertyUtil.resolveEnvironmentVariables(props);
        
        props = PropertyUtil.filterProperties(props, "srl.", true);
        props = PropertyUtil.filterProperties(props, "train.", true);

        {
	        String logLevel = props.getProperty("logger.level");
	        if (logLevel!=null) {
		        ConsoleHandler ch = new ConsoleHandler();
		        ch.setLevel(Level.parse(logLevel));
		        logger.addHandler(ch);
		        logger.setLevel(Level.parse(logLevel));
	        }
        }
        
        System.out.print(PropertyUtil.toString(props));
        
        Properties langProps = PropertyUtil.filterProperties(props, props.getProperty("language").trim()+'.');
        LanguageUtil langUtil = (LanguageUtil) Class.forName(langProps.getProperty("util-class")).newInstance();
        if (!langUtil.init(langProps))
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
        
        Set<EnumSet<Feature>> predicateFeatures = new HashSet<EnumSet<Feature>>();
        {
            String[] tokens = props.getProperty("predicateFeature").trim().split(",");
            for (String token:tokens)
                try {
                    predicateFeatures.add(FeatureSet.toEnumSet(Feature.class, token));
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }  
        }
        
        EnumSet<Source> srcSet = Sentence.readSources(props.getProperty("corpus.source"));
        Source srcTreeType = srcSet.contains(Source.PARSE)?Source.PARSE:Source.TREEBANK;
        srcTreeType = Source.valueOf(props.getProperty("corpus.tree", srcTreeType.toString()));
        
        SRLModel model = new SRLModel(features, predicateFeatures.isEmpty()?null:predicateFeatures);
        
        System.out.println("Argument Features:");
        for (EnumSet<SRLModel.Feature> feature:model.argLabelFeatures.getFeatures())
            System.out.println(feature.toString());
        
        if (model.predicateModel!=null) {
            System.out.println("\nPredicate features:");
            for (EnumSet<SRLModel.Feature> feature:model.predicateModel.getFeatures())
                System.out.println(feature.toString());
        }
        
        //model.setLabeled(false);
        model.setLanguageUtil(langUtil);
        
        String dataFormat = props.getProperty("data.format", "default");
        
        Map<String, Map<String, TObjectIntMap<String>>> trainPredCntMap = null;
        Map<String, Map<String, TObjectIntMap<String>>> trainPPCntMap = null;
        
        File headwordOutFile = null;
        {
        	String fName = props.getProperty("headwordDB");
        	if (fName!=null)
        		headwordOutFile = new File(fName);
        }
        if (headwordOutFile!=null && !headwordOutFile.exists()) {
        	trainPredCntMap = new TreeMap<String, Map<String, TObjectIntMap<String>>>();
        	trainPPCntMap = new TreeMap<String, Map<String, TObjectIntMap<String>>>();
        }

        int tCount = 0;
        int gCount = 0;

        int hCnt = 0;
        int tCnt = 0;
        
        boolean partialNominalAnnotation = !props.getProperty("partialNominalAnnotation", "false").equals("false");
    
        TObjectIntMap<String> rolesetEmpty = new TObjectIntHashMap<String>();
        TObjectIntMap<String> rolesetArg = new TObjectIntHashMap<String>();
        if (!dataFormat.equals("conll"))
        {
            String sourceList = props.getProperty("corpus","");
            String[] sources = sourceList.trim().split("\\s*,\\s*");

            Map<String, Sentence[]> sentenceMap = null;
            TObjectIntMap<String> trainWeights =new TObjectIntHashMap<String>();

            for (String source:sources) {
                System.out.println("Processing corpus "+source);
                Properties srcProps = source.isEmpty()?props:PropertyUtil.filterProperties(props, source+".", true);
                System.out.println(PropertyUtil.toString(srcProps));
                
                Map<String, Sentence[]> corpusMap = Sentence.readCorpus(srcProps, srcTreeType, srcSet, langUtil);
                
                int weight = Integer.parseInt(srcProps.getProperty("weight", "1"));
                for (String key:corpusMap.keySet())
                    trainWeights.put(key, weight);
                
                if (sentenceMap==null) sentenceMap = corpusMap;
                else sentenceMap.putAll(corpusMap);
            }

            System.out.printf("%d training files read\n",sentenceMap.size());

            if (srcTreeType.equals(Source.TREEBANK)||srcTreeType.equals(Source.TB_HEAD))
                model.setTrainGoldParse(true);

            model.initialize(props);
            for (Map.Entry<String, Sentence[]> entry: sentenceMap.entrySet()) {
                int weight = trainWeights.get(entry.getKey());
                weight = weight==0?1:weight;
                
                logger.info("Processing "+entry.getKey());
                
                Set<String> annotatedNominals = null;
                if (partialNominalAnnotation) {
	                annotatedNominals = new HashSet<String>();
	                for (Sentence sent:entry.getValue()) 
	                	 if (sent.propPB!=null)
	                		 for (PBInstance instance:sent.propPB)
	                			 if (!langUtil.isVerb(instance.getPredicate().getPOS()))
	                				annotatedNominals.add(langUtil.makePBFrameKey(instance.getPredicate()));
                }
                
                for (Sentence sent:entry.getValue()) {   
                	sent.annotatedNominals = annotatedNominals;
                    logger.fine("Processing tree "+(sent.parse==null?sent.treeTB.getIndex():sent.parse.getIndex()));
                    if (sent.parse!=null && sent.treeTB!=null && sent.parse.getTokenCount()!=sent.treeTB.getTokenCount()) {
                    	logger.warning("tree "+entry.getKey()+":"+sent.parse.getIndex()+" inconsistent, skipping");
                    	continue;
                    }
                    if (sent.propPB!=null) {
                    	Collections.sort(sent.propPB);
                    	BitSet predMask = new BitSet();
                    	for (Iterator<PBInstance> iter=sent.propPB.iterator();iter.hasNext();) {
                    		PBInstance instance = iter.next();
                    		if (predMask.get(instance.getPredicate().getTokenIndex())) {
                    			logger.warning("deleting duplicate props: "+sent.propPB);
                    			iter.remove();
                    			continue;
                    		}
                    		predMask.set(instance.getPredicate().getTokenIndex());
                    	}                    	
                    }
                    for (int w=0; w<weight; ++w) {
                        model.addTrainingSentence(sent, THRESHOLD);
                        if (trainPredCntMap!=null)
                        	for (PBInstance pb:sent.propPB) {
                        		SRInstance instance = new SRInstance(pb);                        		
                        		String predKey = instance.getRolesetId();
                        		if (langUtil.isVerb(instance.getPredicateNode().getPOS()))
                        			predKey+="-v";
                        		else if (langUtil.isNoun(instance.getPredicateNode().getPOS()))
                        			predKey+="-n";
                        		else if (langUtil.isAdjective(instance.getPredicateNode().getPOS()))
                        			predKey+="-j";
                        		Map<String, TObjectIntMap<String>> labelMap = trainPredCntMap.get(predKey);
                        		if (labelMap==null)
                        			trainPredCntMap.put(predKey, labelMap=new TreeMap<String, TObjectIntMap<String>>());
                        		for (SRArg arg:instance.getScoringArgs()) {
                        			if (arg.getLabel().equals("rel"))
                        				continue;                        			
                        			TBNode headNode = SRLSelPref.getHeadNode(arg.node);
                        			if (!langUtil.isNoun(headNode.getPOS()))
                        				continue;
                        			
                        			Map<String, TObjectIntMap<String>> currLabelMap = labelMap;
                        			
                        			if (arg.node.getPOS().equals("PP")) {
                        				TBNode pNode = headNode.getHeadOfHead();
                        				if (pNode.getConstituentByHead()!=arg.node || pNode.getWord()==null)
                        					continue;
                        				String preposition = "PP-"+pNode.getWord().toLowerCase();
                        				currLabelMap = trainPPCntMap.get(preposition);
                        				if (currLabelMap==null)
                        					trainPPCntMap.put(preposition, currLabelMap = new TreeMap<String, TObjectIntMap<String>>());
                        			} 
                        			TObjectIntMap<String> wordMap = currLabelMap.get(langUtil.convertPBLabelTrain(arg.label));
                        			if (wordMap==null)
                        				currLabelMap.put(langUtil.convertPBLabelTrain(arg.label), wordMap=new TObjectIntHashMap<String>());
                        			wordMap.adjustOrPutValue(SRLVerbNetSP.getArgHeadword(headNode, langUtil), 1, 1);
                        		}
                        	}
                    }
                }
            }
            System.out.println("Nominal predicate training counts: "+model.nomPositiveCnt+"/"+model.nomNegativeCnt);
            System.out.println("***************************************************");
            for (TObjectIntIterator<String> iter=rolesetEmpty.iterator();iter.hasNext();) {
                iter.advance();
                if (iter.value()<rolesetArg.get(iter.key())||iter.value()<2)
                    iter.remove();
                else
                    System.out.println(iter.key()+": "+iter.value()+"/"+rolesetArg.get(iter.key()));
            }
            System.out.println("***************************************************");            
        }
        else if (dataFormat.equals("conll")) {
        	/*
            ArrayList<CoNLLSentence> training = CoNLLSentence.read(new FileReader(props.getProperty("input")), true);
            model.initialize(props);
            for (CoNLLSentence sentence:training) {
                TBUtil.linkHeads(sentence.parse, langUtil.getHeadRules());
                model.addTrainingSentence(sentence.parse, Arrays.asList(sentence.srls), sentence.namedEntities, THRESHOLD);
            }*/
        }       
        System.out.println("Reference arg instance count: "+gCount);
        System.out.println("Training arg instance count: "+tCount);
        System.out.printf("head word unique: %d/%d\n", hCnt, tCnt);
        if (trainPredCntMap!=null)
	        try (PrintStream writer = new PrintStream(new FileOutputStream(headwordOutFile), false, "UTF-8")){
	        	for (Map.Entry<String, Map<String, TObjectIntMap<String>>> e1:trainPredCntMap.entrySet())
	        		for (Map.Entry<String, TObjectIntMap<String>> e2:e1.getValue().entrySet()) {
	        			writer.printf("%s %s", e1.getKey(), e2.getKey());
	        			String[] headwords = e2.getValue().keys(new String[e2.getValue().size()]);
	        			Arrays.sort(headwords);
	        			for (String label:headwords)
	        				writer.printf(" %s:%d", label, e2.getValue().get(label));
	        			writer.print('\n');
	        		}
	        	for (Map.Entry<String, Map<String, TObjectIntMap<String>>> e1:trainPPCntMap.entrySet())
	        		for (Map.Entry<String, TObjectIntMap<String>> e2:e1.getValue().entrySet()) {
	        			writer.printf("%s %s", e1.getKey(), e2.getKey());
	        			String[] headwords = e2.getValue().keys(new String[e2.getValue().size()]);
	        			Arrays.sort(headwords);
	        			for (String label:headwords)
	        				writer.printf(" %s:%d", label, e2.getValue().get(label));
	        			writer.print('\n');
	        		}
	        	System.out.println("wrote headword db to "+headwordOutFile.getPath());
	        } catch (IOException e ) {
	        	e.printStackTrace();
	        	headwordOutFile.delete();
	        }
        
        System.gc();
        model.train(props);
        
        ObjectOutputStream mOut = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(props.getProperty("model_file"))));
        mOut.writeObject(model);
        mOut.close();
        
        System.out.println("Model saved to "+props.getProperty("model_file"));  
    }

}
