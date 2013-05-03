package clearsrl;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import clearcommon.alg.Classifier;
import clearcommon.alg.CrossValidator;
import clearcommon.alg.FeatureSet;
import clearcommon.alg.LinearClassifier;
import clearcommon.alg.PairWiseClassifier;
import clearcommon.alg.Classifier.InstanceFormat;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.util.EnglishUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.LanguageUtil.POS;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

public class SRLModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public static final String NOT_ARG="!ARG";
	public static final String IS_ARG="ARG";
	
	public enum Feature
	{
		// Constituent independent features
		PREDICATE,
		PREDICATEPOS,
		VOICE,
		SUBCATEGORIZATION,
		SUPPORT,
		SUPPORTPATH,
		
		// Constituent dependent features
		PATH,
		PATHG1,           
		PATHG2,
		PATHG3,
		PATHG4,
		PATHDEP,
		PHRASETYPE,
		POSITION,
		CONSTITUENTDIST,
		FIRSTCONSTITUENTREl,
		FIRSTCONSTITUENTABS,
		HEADWORD,
		HEADWORDPOS,
		HEADWORDDUPE,
		FIRSTWORD,
		FIRSTWORDPOS,
		LASTWORD,
		LASTWORDPOS,
		SYNTACTICFRAME,
		NAMEDENTITIES,
		SUPPORTARG,
		
        // Round 2+ label feature
		ARGLIST,
		ARGLISTPREVIOUS,
		ARGTYPE,
		ARGTYPEPREVIOUS,
	};
	
	public enum PredFeature
	{
	    PREDICATE,
	    PREDICATEPOS,
	    PREDICATEHEAD,
	    PREDICATEHEADPOS,
	    PARENTPOS,
	    HEADOFVP,
	    VPSIBLING,
	    LEFTWORD,
	    LEFTWORDPOS,
	    RIGHTHEADWORD,
	    RIGHTHEADWORDPOS,
	    RIGHTPHASETYPE,
	};
	
	public class Sample {
	    public Sample(int[] features, String label, int terminalIndex, boolean isBeforePredicate)
	    {
	        this.features = features;
	        this.label = label;
	        this.terminalIndex = terminalIndex;
	        this.isBeforePredicate = isBeforePredicate;
	        
	        if (this.label==null) this.label = NOT_ARG;
	    }
	    int[] features;
	    String label;
	    int terminalIndex;
	    boolean isBeforePredicate;
	}
	
	public static String UP_CHAR = "^";
	public static String DOWN_CHAR = "v";
	public static String RIGHT_ARROW = "->";

	boolean                                           labeled;
	public boolean isLabeled() {
		return labeled;
	}

	public void setLabeled(boolean labeled) {
		this.labeled = labeled;
	}
	
	FeatureSet<Feature>                                  features;
	FeatureSet<PredFeature>                              predFeatures;

	Classifier                                           classifier;
	Classifier                                           classifier2;
	Classifier                                           predicateClassifier;
    
	TObjectIntHashMap<String>                            labelStringMap;
	TIntObjectHashMap<String>                            labelIndexMap;
	
	boolean                                              trainGoldParse;
	
    transient LanguageUtil                               langUtil;
	
	transient double[]                                   labelValues;
	
	transient SortedMap<String, List<Sample[]>>          trainingSamples;
	
	transient ArrayList<int[]>                           predicateTrainingFeatures;
	transient TIntArrayList                              predicateTrainingLabels;

	transient int                                        argsTrained = 0;
	transient int                                        argsTotal = 0;

	
	
	transient Logger                                     logger;
	
//	transient private SRLScore                        score;
	
	public SRLModel (Set<EnumSet<Feature>> featureSet, Set<EnumSet<PredFeature>> predicateFeatureSet)
	{
		logger = Logger.getLogger("clearsrl");
		
		labeled       = true;

		features = new FeatureSet<Feature>(featureSet);
		if (predicateFeatureSet!=null)
			predFeatures = new FeatureSet<PredFeature>(predicateFeatureSet);
		
		trainingSamples = new TreeMap<String, List<Sample[]>>();
		
		predicateTrainingFeatures = new ArrayList<int[]>();
		predicateTrainingLabels = new TIntArrayList();
		
		trainGoldParse =false;
	}
	
	public void setLanguageUtil(LanguageUtil langUtil) {
	    this.langUtil = langUtil;
	}
	
    public void setTrainGoldParse(boolean trainGoldParse) {
        this.trainGoldParse = trainGoldParse;
    }
	
	public void initDictionary() {
		labelStringMap     = new TObjectIntHashMap<String>();
	}
	
	public TObjectIntHashMap<String> getLabelValueMap() {
		return labelStringMap;
	}

	public void finalizeDictionary(int cutoff) {
	    FeatureSet.trimMap(labelStringMap,20);
	    
	    logger.info("Labels: ");
        String[] labels = labelStringMap.keys(new String[labelStringMap.size()]);
        Arrays.sort(labels);
        for (String label:labels)
            logger.info("  "+label+" "+labelStringMap.get(label));
        
        features.addFeatures(EnumSet.of(Feature.ARGLIST), (TObjectIntHashMap<String>)(labelStringMap.clone()), false);
        features.addFeatures(EnumSet.of(Feature.ARGLISTPREVIOUS), (TObjectIntHashMap<String>)(labelStringMap.clone()), false);
        features.addFeatures(EnumSet.of(Feature.ARGTYPE), (TObjectIntHashMap<String>)(labelStringMap.clone()), false);
        features.addFeatures(EnumSet.of(Feature.ARGTYPEPREVIOUS), (TObjectIntHashMap<String>)(labelStringMap.clone()), false);
        features.rebuildMap(cutoff, cutoff*3);
        
        
       /* 
		for (EnumSet<Feature> feature:featureSet)
		{
			StringBuilder builder = new StringBuilder(feature.toString()+": "+featureStringMap.get(feature).size()+"/"+noargFeatureStringMap.get(feature).size());
			FeatureSet.trimMap(featureStringMap.get(feature),cutoff);
			FeatureSet.trimMap(noargFeatureStringMap.get(feature),cutoff*10);
			builder.append(" "+featureStringMap.get(feature).size()+"/"+noargFeatureStringMap.get(feature).size());
			featureStringMap.get(feature).putAll(noargFeatureStringMap.get(feature));
			builder.append(" "+featureStringMap.get(feature).size());
			builder.append(featureStringMap.get(feature).size()<=250?" "+Arrays.asList(featureStringMap.get(feature).keys()):"");
			logger.info(builder.toString());
		}*/
        
        
		
		if (predFeatures!=null) {
			predFeatures.rebuildMap(cutoff, cutoff*3);
			
            //for (EnumSet<PredFeature> predFeature:predicateFeatureSet)
            //	FeatureSet.trimMap(predicateFeatureStringMap.get(predFeature),cutoff);
		}
		/*
		
		labelStringSet = new TreeSet<String>();
		for (String str:labelStringMap.keys(new String[labelStringMap.size()]))
			labelStringSet.add(str);
		*/
		
		
        buildMapIndex(labelStringMap, 0);
        
        labelIndexMap = new TIntObjectHashMap<String>();
        for (TObjectIntIterator<String> iter=labelStringMap.iterator();iter.hasNext();) {
            iter.advance();
            labelIndexMap.put(iter.value(),iter.key());
        }
        labelValues = new double[labelIndexMap.size()];
		
		
        System.err.printf("ARGS trained %d/%d\n", argsTrained, argsTotal);
        
		//rebuildMaps();
	}
    /*
    void rebuildMaps()
    {   
        featureStringMap.put(EnumSet.of(Feature.ARGLIST), (TObjectIntHashMap<String>)(labelStringMap.clone()));
        featureStringMap.put(EnumSet.of(Feature.ARGLISTPREVIOUS), (TObjectIntHashMap<String>)(labelStringMap.clone()));
        featureStringMap.put(EnumSet.of(Feature.ARGTYPE), (TObjectIntHashMap<String>)(labelStringMap.clone()));
        featureStringMap.put(EnumSet.of(Feature.ARGTYPEPREVIOUS), (TObjectIntHashMap<String>)(labelStringMap.clone()));
        
        int startIdx=0;
        for (Map.Entry<EnumSet<Feature>, TObjectIntHashMap<String>> entry: featureStringMap.entrySet())
            startIdx = buildMapIndex(entry.getValue(), startIdx);
 
        buildMapIndex(labelStringMap, 0);
        
        labelIndexMap = new TIntObjectHashMap<String>();
        for (TObjectIntIterator<String> iter=labelStringMap.iterator();iter.hasNext();)
        {
            iter.advance();
            labelIndexMap.put(iter.value(),iter.key());
        }
        labelValues = new double[labelIndexMap.size()];
        
        if (predicateFeatureSet!=null)
        {
            startIdx=0;
            for (Map.Entry<EnumSet<PredFeature>, TObjectIntHashMap<String>> entry: predicateFeatureStringMap.entrySet())
                startIdx = buildMapIndex(entry.getValue(), startIdx);      
        }
    }
	*/
	
	
    public void addTrainingSentence(TBTree tree, List<SRInstance> instances, String[] namedEntities, float threshold, boolean buildDictionary) {
    	
    	int[] supportIds = SRLUtil.findSupportPredicates(instances, langUtil, SRLUtil.SupportType.ALL, true);
    	
    	List<SRInstance> trainInstances = new ArrayList<SRInstance>(instances.size());
    	for (SRInstance instance:instances)
    		trainInstances.add(new SRInstance(instance.predicateNode, tree, instance.getRolesetId(), 1.0));

    	BitSet processedSet = new BitSet(supportIds.length);
        // classify verb predicates first
        int cardinality = 0;        
        do {
        	cardinality = processedSet.cardinality();
        	for (int i=processedSet.nextClearBit(0); i<supportIds.length; i=processedSet.nextClearBit(i+1))
        		if (supportIds[i]<0 || processedSet.get(supportIds[i])) {
        			ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
                    ArrayList<Map<String, Float>> labels = new ArrayList<Map<String, Float>>();
                    SRLUtil.getSamplesFromParse(instances.get(i), supportIds[i]<0?null:trainInstances.get(supportIds[i]), tree, langUtil, 1, threshold, argNodes, labels);
                    for (int l=0; l<labels.size(); ++l)
                        trainInstances.get(i).addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(l)), argNodes.get(l)));
                    addTrainingSamples(trainInstances.get(i), supportIds[i]<0?null:trainInstances.get(supportIds[i]), namedEntities, buildDictionary);

                    if (buildDictionary) {
                    	
                    	int args = 0;
	                    for (SRArg arg:trainInstances.get(i).getArgs())
	                    	if (!arg.getLabel().equals(NOT_ARG))
	                            args++;
	                    if (args!=instances.get(i).getArgs().size()) {
	                    	System.err.println("\n"+trainInstances.get(i));
	                    	System.err.println(instances.get(i));
	                    	if (supportIds[i]>=0)
	                    		System.err.println(trainInstances.get(supportIds[i]));
	                    	System.err.println(tree+"\n");
	                    }
	                    argsTrained+=args-1;
	                    argsTotal+=instances.get(i).getArgs().size()-1;
                    }
                    
        			processedSet.set(i);
        		}
        } while (processedSet.cardinality()>cardinality);
        
        TBNode[] nodes = tree.getTokenNodes();
        
        ArrayList<TBNode> predicateCandidates = new ArrayList<TBNode>();
        for (TBNode node: nodes)
            if (langUtil.isPredicateCandidate(node.getPOS()))
                predicateCandidates.add(node);
        
        // add predicate training samples
        if (buildDictionary) {
            for (TBNode predicateCandidate:predicateCandidates)            	
                for(Map.Entry<EnumSet<PredFeature>,List<String>> entry:extractPredicateFeature(predicateCandidate, nodes).entrySet())
                	predFeatures.addToDictionary(entry.getKey(), entry.getValue(), false);
        } else {
        	BitSet instanceMask = new BitSet(tree.getTokenCount());
    		for (SRInstance instance:trainInstances)
    			instanceMask.set(instance.getPredicateNode().getTokenIndex());
        	
            for (TBNode predicateCandidate:predicateCandidates) {
                predicateTrainingFeatures.add(predFeatures.getFeatureVector(extractPredicateFeature(predicateCandidate, nodes)));
                predicateTrainingLabels.add(instanceMask.get(predicateCandidate.getTokenIndex())?1:2);
            }
        }
    }
    
    public void addTrainingSamples(SRInstance sampleInstance, SRInstance supportInstance, String[] namedEntities, boolean buildDictionary)    
	{
        if (sampleInstance.args.size()<=1) return;
        
        ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
        ArrayList<String> labels = new ArrayList<String>();
        
        for (SRArg arg:sampleInstance.args)
        {
            if (arg.isPredicate()) continue;
            argNodes.add(arg.node);
            labels.add(arg.label);
        }
        if (argNodes.isEmpty()) return;
        
		List<Map<EnumSet<Feature>,List<String>>> samples = extractSampleFeature(sampleInstance.predicateNode, argNodes, supportInstance, namedEntities);
		if (buildDictionary)
		{
			int c=0;
			for (Map<EnumSet<Feature>,List<String>>sample:samples)
			{
				boolean isNoArg = NOT_ARG.equals(labels.get(c));
				for(Map.Entry<EnumSet<Feature>,List<String>> entry:sample.entrySet())
					features.addToDictionary(entry.getKey(), entry.getValue(), isNoArg);
				
				//if (!NOT_ARG.equals(SRLUtil.getMaxLabel(labels.get(c))))
				//	System.out.println(sample.get(Feature.PATH));
				labelStringMap.put(labels.get(c), labelStringMap.get(labels.get(c))+1);
				++c;
			}
		}
		else
		{  
			List<Sample> sampleList = new ArrayList<Sample>();
			int predTerminalIndex = sampleInstance.predicateNode.getTerminalIndex();
			
            for (int i=0; i<samples.size();++i)
                if (labelStringMap.containsKey(labels.get(i)))
                {
                    int sampleTerminalIndex = argNodes.get(i).getHead().getTerminalIndex();
                    sampleList.add(new Sample(features.getFeatureVector(samples.get(i)), labels.get(i), 
                            sampleTerminalIndex, predTerminalIndex>sampleTerminalIndex));
                }
            if (!sampleList.isEmpty())
            {
                List<Sample[]> tSamples = trainingSamples.get(sampleInstance.tree.getFilename());
                if (tSamples == null)
                {
                    tSamples = new ArrayList<Sample[]>();
                    trainingSamples.put(sampleInstance.tree.getFilename(), tSamples);
                }
                tSamples.add(sampleList.toArray(new Sample[sampleList.size()]));
            }
		}
	}


	public List<Map<EnumSet<Feature>,List<String>>> extractSampleFeature(TBNode predicateNode, List<TBNode> argNodes, SRInstance support, String[] namedEntities)
	{
		List<Map<EnumSet<Feature>,List<String>>> samples = new ArrayList<Map<EnumSet<Feature>,List<String>>>();
		
		//EnumMap<Feature,List<String>> defaultMap = new EnumMap<Feature,List<String>>(Feature.class);
		
		// find predicate lemma
		String predicateLemma = predicateNode.getWord();
		{
			List<String> stems = langUtil.findStems(predicateLemma, LanguageUtil.POS.VERB);
			if (!stems.isEmpty()) predicateLemma = stems.get(0);
		}

		String relatedVerb = null;
		if ((langUtil instanceof EnglishUtil) && !langUtil.isVerb(predicateNode.getPOS()))
			relatedVerb = ((EnglishUtil)langUtil).findDerivedVerb(predicateNode);

        List<String> predicateAlternatives = langUtil.getPredicateAlternatives(predicateLemma, features.getFeatureStrMap()==null?null:features.getFeatureStrMap().get(EnumSet.of(Feature.PREDICATE)));
		
		// build subcategorization feature
		String subCat = null;
		{
			StringBuilder builder = new StringBuilder();
			TBNode predicateParentNode = predicateNode.getParent();
			builder.append(predicateParentNode.getPOS()+RIGHT_ARROW);
			for (TBNode node:predicateParentNode.getChildren())
				builder.append(node.getPOS()+"-");
			subCat = builder.toString();
		}
		
		// figure out whether predicate is passive or not
		boolean isPassive = langUtil.getPassive(predicateNode)>0;

		EnumMap<Feature,List<String>> defaultMap = new EnumMap<Feature,List<String>>(Feature.class);
		for (Feature feature:features.getFeaturesFlat())
		{
			switch (feature) {
			case PREDICATE:
				if (relatedVerb!=null)
					defaultMap.put(feature, Arrays.asList(relatedVerb, predicateLemma+"-n"));
				else
					defaultMap.put(feature, predicateAlternatives);
				break;
			case PREDICATEPOS:
				if (predicateNode.getPOS().matches("V.*"))
					defaultMap.put(feature, trainGoldParse?predicateNode.getFunctionTaggedPOS():Arrays.asList(predicateNode.getPOS()));
				break;
			case VOICE:
				if (langUtil.isVerb(predicateNode.getPOS()))
					 defaultMap.put(feature, Arrays.asList(isPassive?"passive":"active"));
				break;
			case SUBCATEGORIZATION:
				defaultMap.put(feature, Arrays.asList(subCat.toString()));
				break;
			case SUPPORT:
				if (support!=null) {
					String lemma = langUtil.findStems(support.getPredicateNode()).get(0);
					String position = support.getPredicateNode().getTokenIndex()<predicateNode.getTokenIndex()?"left":"right";
					String level = support.getPredicateNode().getLevelToRoot()<predicateNode.getLevelToRoot()?"above":"sibling";
					defaultMap.put(feature, Arrays.asList(lemma, position, level, lemma+" "+level, support.getPredicateNode().getWord()));
				}
				break;
			case SUPPORTPATH:
				if (support!=null) {
					List<TBNode> supportToTopNodes = support.getPredicateNode().getPathToRoot();
				    List<TBNode> predToTopNodes = predicateNode.getPathToRoot();
				    TBNode joinNode = trimPathNodes(supportToTopNodes, predToTopNodes);
					List<String> path = getPath(supportToTopNodes, predToTopNodes, joinNode);
					StringBuilder buffer = new StringBuilder();
					for (String node:path) buffer.append(node);
					defaultMap.put(feature, Arrays.asList(buffer.toString()));
				}
				break;
			default:
				break;
			}
		}
				
		for (TBNode argNode:argNodes)
		{
			
			EnumMap<Feature,List<String>> sample = new EnumMap<Feature,List<String>>(defaultMap);
			List<TBNode> tnodes = argNode.getTokenNodes();
			
			List<TBNode> argToTopNodes = argNode.getPathToRoot();
		    List<TBNode> predToTopNodes = predicateNode.getPathToRoot();
		    TBNode joinNode = trimPathNodes(argToTopNodes, predToTopNodes);
			List<String> path = getPath(argToTopNodes, predToTopNodes, joinNode);
		
			
			
            List<TBNode> argDepToTopNodes = new ArrayList<TBNode>();
            
            if (!argToTopNodes.isEmpty()) argDepToTopNodes.add(argToTopNodes.get(0));
            
            for (int i=1; i<argToTopNodes.size()-1; ++i)
                if (argToTopNodes.get(i).getHead()!=argToTopNodes.get(i+1).getHead())
                    argDepToTopNodes.add(argToTopNodes.get(i));
            
            if (argDepToTopNodes.size()>1 && argDepToTopNodes.get(argDepToTopNodes.size()-1).getHead() == joinNode.getHead())
                argDepToTopNodes.remove(argDepToTopNodes.size()-1);
            
            List<TBNode> predDepToTopNodes = new ArrayList<TBNode>();
            
            if (!predToTopNodes.isEmpty()) predDepToTopNodes.add(predToTopNodes.get(0));
            
            for (int i=1; i<predToTopNodes.size()-1; ++i)
                if (predToTopNodes.get(i).getHead()!=predToTopNodes.get(i+1).getHead())
                    predDepToTopNodes.add(predToTopNodes.get(i));
            
            if (predDepToTopNodes.size()>1 && predDepToTopNodes.get(predDepToTopNodes.size()-1).getHead() == joinNode.getHead())
                predDepToTopNodes.remove(predDepToTopNodes.size()-1);

            List<String> depPath = getPath(argDepToTopNodes, predDepToTopNodes, joinNode);
			
			// compute head
			TBNode head = argNode.getHead();
			if (argNode.getPOS().matches("PP.*"))
			{
			    int i = argNode.getChildren().length-1;
				for (; i>=0; --i)
				{
					if (argNode.getChildren()[i].getPOS().matches("NP.*"))
					{
						if (argNode.getChildren()[i].getHead()!=null && argNode.getChildren()[i].getHeadword()!=null)
							head = argNode.getChildren()[i].getHead();
						break;
					}
				}
				if (i<0 && argNode.getChildren()[argNode.getChildren().length-1].getHead()!=null && 
				        argNode.getChildren()[argNode.getChildren().length-1].getHeadword()!=null)
				    head = argNode.getChildren()[argNode.getChildren().length-1].getHead();
			}
			
			boolean isBefore = tnodes.get(0).getTokenIndex() < predicateNode.getTokenIndex();
			//System.out.println(predicateNode+" "+predicateNode.tokenIndex+": "+argNode.getParent()+" "+argToTopNodes.size());

			int cstDst = countConstituents(argToTopNodes.get(0).getPOS(), isBefore?argToTopNodes:predToTopNodes, isBefore?predToTopNodes:argToTopNodes, joinNode);
			//System.out.println(cstDst+" "+path);
			
			for (Feature feature:features.getFeaturesFlat())
			{
				String pathStr=null;
				{
					StringBuilder buffer = new StringBuilder();
					for (String node:path) buffer.append(node);
					pathStr = buffer.toString();
				}
				switch (feature) {
				case PATH:
					sample.put(feature, Arrays.asList(pathStr));
					break;
				case PATHG1:
				{
					StringBuilder buffer = new StringBuilder();
					for (String node:path) 
					{
						if (node.equals(DOWN_CHAR)) break;
						buffer.append(node);
					}
					sample.put(feature, Arrays.asList(buffer.toString()));
					break;
				}
				case PATHG2:
				{
					boolean inSameClause = true;
					
					StringBuilder buffer = new StringBuilder();
					for (int i=1; i<path.size()-2; i++) 
					{
						if (path.get(i).startsWith("S") || path.get(i).equals("IP"))
						{
							buffer.append("S");
							if (!path.get(i-1).equals(UP_CHAR)||!path.get(i+1).equals(DOWN_CHAR))
								inSameClause = false;
						}
						else
							buffer.append(path.get(i));
					}

					ArrayList<String> values = new ArrayList<String>();
					if (inSameClause)
						values.add("SameClause");
					if (path.size()>2)
					{
						values.add(path.get(0)+buffer.toString().replaceAll("[^S\\^v][A-Z]*","*")+path.get(path.size()-2)+path.get(path.size()-1));
						values.add(path.get(0)+buffer.toString().replaceAll("[\\^v][^S][A-Z]*", "")+path.get(path.size()-2)+path.get(path.size()-1));
						values.add(path.get(0)+buffer.toString().replaceAll("S([\\^v][^S][A-Z]*)+\\^S", "S^S").replaceAll("S([\\^v][^S][A-Z]*)+vS", "SvS")+path.get(path.size()-2)+path.get(path.size()-1));
					}
					sample.put(feature, values);
					break;
				}
				case PATHG3:
				{
					ArrayList<String> trigram = new ArrayList<String>();
					for (int i=0; i<path.size()-4; i+=2)
						trigram.add(path.get(i)+path.get(i+1)+path.get(i+2)+path.get(i+3)+path.get(i+4));
						
					sample.put(feature, trigram);
					break;
				}
				case PATHG4:
				{
					StringBuilder buffer = new StringBuilder();
					for (String node:path) buffer.append(node.charAt(0));
					sample.put(feature, Arrays.asList(buffer.toString()));
					break;
				}
				case PATHDEP:
				{
		            StringBuilder buffer = new StringBuilder();
                    for (String node:depPath) buffer.append(node);
                    sample.put(feature, Arrays.asList(buffer.toString()));
                    
                    //System.out.println(pathStr+" "+buffer.toString());
                    
				    break;
				}
				case PHRASETYPE:
					if (argNode.getPOS().equals("PP") && argNode.getHead()!=null && argNode.getHeadword()!=null)
					{
						ArrayList<String> list = new ArrayList<String>();
						if (trainGoldParse) list.addAll(argNode.getFunctionTaggedPOS());
						else list.add("PP"); 
						list.add("PP-"+argNode.getHeadword().toLowerCase()); 
						sample.put(feature, list);
					}
					else
						sample.put(feature, trainGoldParse?argNode.getFunctionTaggedPOS():Arrays.asList(argNode.getPOS()));
					break;
				case POSITION:
					//if (isBefore) sample.put(feature, Arrays.asList("before"));
				    sample.put(feature, Arrays.asList(isBefore?"before":"after"));
					break;
				case CONSTITUENTDIST:
				{
				    ArrayList<String> list = new ArrayList<String>();
				    //if (cstDst!=1) list.add("notclosest");
				    //list.add(cstDst==1?"closest":"notclosest");
				    list.add(Integer.toString(cstDst>5?5:cstDst));
				    for (int i=1; i<=5; ++i)
				        if (cstDst<=i)
				            list.add("<="+Integer.toString(i));
				    sample.put(feature, list);
                    break;
				}
				case HEADWORD:
					sample.put(feature, Arrays.asList(head.getWord()));
					break;
				case HEADWORDPOS:
					sample.put(feature, trainGoldParse?head.getFunctionTaggedPOS():Arrays.asList(head.getPOS()));
					break;
				case HEADWORDDUPE:
				    //if (argNode.getParent()!=null && argNode.getHead()==argNode.getParent().getHead())
				    sample.put(feature, Arrays.asList(Boolean.toString(argNode.getParent()!=null && argNode.getHead()==argNode.getParent().getHead())));
				    //else
				    //   sample.put(feature, Arrays.asList("HeadUnique"));
                    break;
				case FIRSTWORD:
					sample.put(feature, Arrays.asList(tnodes.get(0).getWord()));
					break;
				case FIRSTWORDPOS:
					sample.put(feature, trainGoldParse?tnodes.get(0).getFunctionTaggedPOS():Arrays.asList(tnodes.get(0).getPOS()));
					break;
				case LASTWORD:
					sample.put(feature, Arrays.asList(tnodes.get(tnodes.size()-1).getWord()));
					break;
				case LASTWORDPOS:
					sample.put(feature, trainGoldParse?tnodes.get(tnodes.size()-1).getFunctionTaggedPOS():Arrays.asList(tnodes.get(tnodes.size()-1).getPOS()));
					break;
				case SYNTACTICFRAME:
				{
					StringBuilder builder = new StringBuilder();
					for (TBNode node:argNode.getParent().getChildren())
					{
						if (node != argNode)
							builder.append(node.getPOS().toLowerCase()+"-");
						else
							builder.append(node.getPOS().toUpperCase()+"-");
					}
					sample.put(feature, Arrays.asList(builder.toString()));
					break;
				}
				case NAMEDENTITIES:
				{
				    if (namedEntities==null) break;
					Set<String> neSet = new TreeSet<String>();
					for (TBNode node:tnodes)
					{
						if (namedEntities[node.getTokenIndex()]!=null)
							neSet.add(namedEntities[node.getTokenIndex()]);
					}
					if (!neSet.isEmpty())
					{
						StringBuilder buffer = new StringBuilder();
						for (String ne:neSet) buffer.append(ne+" ");
						neSet.add(buffer.toString().trim());
						sample.put(feature, Arrays.asList(neSet.toArray(new String[neSet.size()])));
					}
					break;
				}
				case SUPPORTARG:
					if (support!=null) {
						SRArg sArg = null;
						for (SRArg arg:support.getArgs()) {
							if (arg.getLabel().equals(NOT_ARG)) continue;
							if (arg.node == argNode || argNode.isDecendentOf(arg.node)) {
								sArg=arg;
								break;
							}							
						}
						if (sArg!=null)
							sample.put(feature, Arrays.asList((sArg.node==argNode?"":"in ")+sArg.getLabel()));
					}
					break;
				default:
					break;
				}
			}
			
			samples.add(features.convertFlatSample(sample));
		}
		
		return samples;
	}
/*
	<T extends Enum<T>> int[] getFeatureVector(Map<EnumSet<T>,List<String>> sample, Map<EnumSet<T>, TObjectIntHashMap<String>> fStringMap)
    {
        TIntHashSet featureSet = new TIntHashSet();
        
        for(Map.Entry<EnumSet<T>,List<String>> entry:sample.entrySet())
        {
            TObjectIntHashMap<String> fMap = fStringMap.get(entry.getKey());
            for (String fVal:entry.getValue())
            {
                int mapIdx = fMap.get(fVal);
                if (mapIdx>0) featureSet.add(mapIdx-1);
            }
        }
        int [] features = featureSet.toArray();
        Arrays.sort(features);
        return features;
    }
    */
    public Map<EnumSet<PredFeature>,List<String>> extractPredicateFeature(TBNode predicateNode, TBNode[] nodes)
    {
        EnumMap<PredFeature,List<String>> sampleFlat = new EnumMap<PredFeature,List<String>>(PredFeature.class);
        
        // find predicate lemma
        String predicateLemma = predicateNode.getWord(); {
            List<String> stems = langUtil.findStems(predicateLemma, LanguageUtil.POS.VERB);
            if (!stems.isEmpty()) predicateLemma = stems.get(0);
        }
        TBNode head = predicateNode.getHeadOfHead();
        TBNode parent = predicateNode.getParent();
        TBNode leftNode = predicateNode.getTokenIndex()>0? nodes[predicateNode.getTokenIndex()-1]:null;
        TBNode rightSibling = (predicateNode.getParent()!=null&&predicateNode.getChildIndex()<predicateNode.getParent().getChildren().length-1)?
                predicateNode.getParent().getChildren()[predicateNode.getChildIndex()+1]:null;
        TBNode rightHeadnode = (rightSibling==null||rightSibling.getHead()==null)?null:rightSibling.getHead();

        
        for (PredFeature feature:predFeatures.getFeaturesFlat()) {
            switch (feature)
            {
            case PREDICATE:
                sampleFlat.put(feature, Arrays.asList(predicateLemma));
                break;
            case PREDICATEPOS:
                sampleFlat.put(feature, trainGoldParse?predicateNode.getFunctionTaggedPOS():Arrays.asList(predicateNode.getPOS()));
                break;
            case PARENTPOS:
                if (parent!=null)
                    sampleFlat.put(feature, trainGoldParse?parent.getFunctionTaggedPOS():Arrays.asList(parent.getPOS()));
                break;
            case PREDICATEHEAD:
            	if (head!=null) 
            		sampleFlat.put(feature, Arrays.asList(langUtil.findStems(head).get(0)));
            	break;
            case PREDICATEHEADPOS:
            	if (head!=null) 
            		sampleFlat.put(feature, Arrays.asList(head.getPOS()));
            	break;
            case HEADOFVP:
            	if (langUtil.isVerb(predicateNode.getPOS())) {
            		TBNode vpAncestor = predicateNode;
                    while (vpAncestor != null && !vpAncestor.getPOS().equals("VP"))
                    	vpAncestor = vpAncestor.getParent();
                    if (vpAncestor!=null && vpAncestor.getPOS().equals("VP"))
                    	sampleFlat.put(feature, Arrays.asList(Boolean.toString(vpAncestor.getHead()==predicateNode)));	
            	}
            	break;
            case VPSIBLING:
            	if (langUtil.isVerb(predicateNode.getPOS())) {
            		for (int i=predicateNode.getChildIndex()+1; i<predicateNode.getParent().getChildren().length;++i)
            			if (predicateNode.getParent().getChildren()[i].getPOS().equals("VP")) {
            				sampleFlat.put(feature, Arrays.asList(Boolean.toString(true)));
            				break;
            			}
            		if (sampleFlat.get(feature)==null)
            			sampleFlat.put(feature, Arrays.asList(Boolean.toString(false)));
            	}
            	break;
            case LEFTWORD:
                if (leftNode!=null) sampleFlat.put(feature, Arrays.asList(leftNode.getWord()));   
                break;
            case LEFTWORDPOS:
                if (leftNode!=null)
                    sampleFlat.put(feature, trainGoldParse?leftNode.getFunctionTaggedPOS():Arrays.asList(leftNode.getPOS()));
                break;
            case RIGHTHEADWORD:
                if (rightHeadnode!=null) sampleFlat.put(feature, Arrays.asList(rightHeadnode.getWord()));   
                break;
            case RIGHTHEADWORDPOS:
                if (rightHeadnode!=null)
                    sampleFlat.put(feature, trainGoldParse?rightHeadnode.getFunctionTaggedPOS():Arrays.asList(rightHeadnode.getPOS()));
                break;
            case RIGHTPHASETYPE:
                if (rightSibling!=null)
                    sampleFlat.put(feature, Arrays.asList(rightSibling.getPOS()));
                break;
            }
        }        
        return predFeatures.convertFlatSample(sampleFlat);
    }
    
    static <T extends Enum<T>> Map<EnumSet<T>,List<String>> convertFlatSample(EnumMap<T,List<String>> sampleFlat, Set<EnumSet<T>> fSet)
    { 
        //System.out.println(sampleFlat);
        Map<EnumSet<T>,List<String>> sample = new HashMap<EnumSet<T>,List<String>>();
        
        for (EnumSet<T> feature:fSet)
        {   
            Iterator<T> iter = feature.iterator();
            List<String> sList = sampleFlat.get(iter.next());
            for (;iter.hasNext() && sList!=null && !sList.isEmpty();)
                sList = permute(sList, sampleFlat.get(iter.next()));
            
            if (sList!=null && !sList.isEmpty())
            {
                //if (feature.size()>1) System.out.println(toString(feature)+": "+sList);
                sample.put(feature, sList);
            }
        }
        
        return sample;   
    }
    
    static List<String> permute(List<String> lhs, List<String> rhs)
    {   
        if (lhs==null || rhs==null) return new ArrayList<String>(0);

    	ArrayList<String> ret = new ArrayList<String>(lhs.size()*rhs.size());
    	for (String a2:rhs)
    	    for (String a1:lhs)
    			ret.add(a1+" "+a2);
    	return ret;
    }
    
    private int buildMapIndex(TObjectIntHashMap<String> mapObj, int startIdx)
    {
        String[] keys = mapObj.keys(new String[mapObj.size()]);
        mapObj.clear();
        for (String key:keys)
            mapObj.put(key, ++startIdx);
        return startIdx;
    }
    
    public void train(Properties prop)
    {   
        if (predFeatures!=null && !predicateTrainingFeatures.isEmpty())
        {
            TObjectIntHashMap<String> predicateLabelMap = new TObjectIntHashMap<String>();
            predicateLabelMap.put("predicate", 1);
            predicateLabelMap.put("not_predicate", 2);
            
            int dist = 0;
            
            for (int i=0; i<predicateTrainingLabels.size(); ++i)
                dist += (predicateTrainingLabels.get(i)==1)?1:0;
            logger.info(String.format("Training predicates: %d/%d/%d\n", dist, predicateTrainingLabels.size()-dist, predicateTrainingLabels.size()));
                
            predicateClassifier = new LinearClassifier(predicateLabelMap, prop);
            predicateClassifier.train(predicateTrainingFeatures.toArray(new int[predicateTrainingFeatures.size()][]),
                    predicateTrainingLabels.toNativeArray());
            
            double score = 0;
            for (int i=0; i<predicateTrainingFeatures.size(); ++i)
                score += (predicateClassifier.predict(predicateTrainingFeatures.get(i))==predicateTrainingLabels.get(i))?1:0;
            logger.info(String.format("Predicate training accuracy: %f\n", score/predicateTrainingLabels.size()));
        }
        
        int[][] X = null;
        int[] y = null;
        int[] seed = null;
        {
            List<int[]> xList = new ArrayList<int[]>();
            TIntArrayList yList = new TIntArrayList();
            TIntArrayList seedList = new TIntArrayList();
            int treeCnt = 0;
            for (Map.Entry<String, List<Sample[]>> entry:trainingSamples.entrySet())
            {
                for (Sample[] samples:entry.getValue())
                    for (Sample sample:samples)
                    {
                        xList.add(sample.features);
                        yList.add(labelStringMap.get(sample.label));
                        seedList.add(treeCnt);
                    }
                ++treeCnt;
            }
            X = xList.toArray(new int[xList.size()][]);
            y = yList.toNativeArray();
            seed = seedList.toNativeArray();
        }

        double[] weights = null;
        if (!labeled)
        {
            int notArgIndex = labelStringMap.get(NOT_ARG);
            for (int i=0; i<y.length; ++i)
                y[i] = y[i]==notArgIndex?notArgIndex:notArgIndex+1;
        }
        else
        {
            weights = new double[labelStringMap.size()];
            
            String[] labels = labelStringMap.keys(new String[labelStringMap.size()]);
            Arrays.sort(labels);
 
            int idx=0;
            for (String label:labels)
            {
                if (label.equals(SRLModel.NOT_ARG))
                    weights[idx] = 1;
                else
                    weights[idx] = 1;
                ++idx;
            }
        }
        
        //liblinearbinary.Problem problem = LinearClassifier.convertToProblem(X, y, Double.parseDouble(prop.getProperty("liblinear.bias", "-1")));
        //classifier = new LinearClassifier(labelStringMap, prop);
        classifier = new PairWiseClassifier(labelStringMap, prop);
        //classifier = new TwoStageClassifier(labelStringMap, 0.95, prop);
        
        SRLScore scorer = new SRLScore(new TreeSet<String>(Arrays.asList(labelStringMap.keys(new String[labelStringMap.size()]))));
        
        int folds = Integer.parseInt(prop.getProperty("crossvalidation.folds","5"));
        
        int[] yV;
        if (folds>1)
        {
            int threads = Integer.parseInt(prop.getProperty("crossvalidation.threads","2"));
            CrossValidator validator = new CrossValidator(classifier, threads);
            yV =  validator.validate(folds, X, y, seed, true);
        }
        else
        {
            classifier.train(X, y);
            yV = new int[y.length];
            for (int i=0; i<y.length; ++i)
                yV[i] = classifier.predict(X[i]);
        }
        
        //System.err.println("labels: ");
        //for (TObjectIntIterator<String> iter=labelStringMap.iterator();iter.hasNext();)
        //{
        //    iter.advance();
        //    System.err.println(""+iter.value()+" "+labelIndexMap.get(iter.value()));
        //}
        for (int i=0; i<y.length; ++i)
        {
            if (labelIndexMap.get(yV[i])==null)
                logger.warning("unknown label encountered: "+yV[i]+"training label is "+labelIndexMap.get(y[i])+"/"+y[i]);
            scorer.addResult(labelIndexMap.get(yV[i]),labelIndexMap.get(y[i]));
        }
        logger.info("Cross validation score: \n"+scorer.toString());
        
        {
            List<int[]> xList = new ArrayList<int[]>();
            int iCnt = 0;
            for (Map.Entry<String, List<Sample[]>> entry:trainingSamples.entrySet())
            {
                for (Sample[] samples:entry.getValue())
                {
                    Sample[] samplesClone = new Sample[samples.length];
                    for (int i=0; i<samples.length; ++i)
                        samplesClone[i] = new Sample(samples[i].features, labelIndexMap.get(yV[iCnt++]), samples[i].terminalIndex, samples[i].isBeforePredicate);
                    
                    for (int[] x:getFeatures(samplesClone)) xList.add(x);
                }
            }
            X = xList.toArray(new int[xList.size()][]);
        }
        
        classifier2 = new PairWiseClassifier(labelStringMap, prop);
        classifier2.train(X, y);
    }

    int[][] getFeatures(Sample[] samples)
    {
        TObjectIntHashMap<String> argListMap = features.getFeatureStrMap().get(EnumSet.of(Feature.ARGLIST));
        TObjectIntHashMap<String> argListPreviousMap = features.getFeatureStrMap().get(EnumSet.of(Feature.ARGLISTPREVIOUS));
        TObjectIntHashMap<String> argTypeMap = features.getFeatureStrMap().get(EnumSet.of(Feature.ARGTYPE));
        TObjectIntHashMap<String> argTypePreviousMap = features.getFeatureStrMap().get(EnumSet.of(Feature.ARGTYPE));
        
        int[][] X = new int[samples.length][];
        
        int mapIdx;
        
        for (int i=0; i<samples.length; ++i) {
            TIntHashSet featureSet = new TIntHashSet(samples[i].features);
            
            mapIdx = argTypeMap.get(samples[i].label);
            if (mapIdx!=0) featureSet.add(mapIdx);
            
            for (int a=0; a<samples.length; ++a) {
                if (a==i) continue;
                mapIdx = argListMap.get(samples[a].label);
                if (mapIdx!=0) featureSet.add(mapIdx);
                
                if (samples[i].terminalIndex!=samples[a].terminalIndex &&
                    samples[i].isBeforePredicate^samples[i].terminalIndex>samples[a].terminalIndex) {
                    mapIdx = argListPreviousMap.get(samples[a].label);
                    if (mapIdx!=0) featureSet.add(mapIdx);
                }
            }
            
            if (samples[i].isBeforePredicate) {
                for (int a=samples.length-1;a>i; --a)
                    if (samples[i].terminalIndex<samples[a].terminalIndex && samples[a].label.matches("ARG\\d")) {
                        mapIdx = argTypePreviousMap.get(samples[a].label);
                        if (mapIdx!=0) featureSet.add(mapIdx);
                        break;
                    }
            } else {
                for (int a=i+1;a<samples.length; ++a) {
                    if (samples[i].terminalIndex>samples[a].terminalIndex && samples[a].label.matches("ARG\\d")) {
                        mapIdx = argTypePreviousMap.get(samples[a].label);
                        if (mapIdx!=0) featureSet.add(mapIdx);
                        break;
                    }
                }
            }
            
            X[i] = featureSet.toArray();
            Arrays.sort(X[i]);
        }
        return X;
    }

    public List<SRInstance> predict(TBTree parseTree, SRInstance[] goldSRLs, String[] namedEntities)
    {   
    	ArrayList<SRInstance> predictions = new ArrayList<SRInstance>();
        
        TBNode[] nodes = parseTree.getTokenNodes();
        
        //debug block
        for (int i=0; i<nodes.length;++i)
        	if (nodes[i].getTokenIndex()!=i) {
        		System.err.println(parseTree.getFilename()+" "+parseTree.getIndex()+": "+parseTree.toString());
        		for (int j=0; j<nodes.length;++j)
        			System.err.print(nodes[j].getWord()+"/"+nodes[j].getTokenIndex()+" ");
        		System.err.print("\n");
        		System.err.flush();
        		System.exit(1);
        	}

        if (goldSRLs==null) {
        	double[] vals = new double[2];
            for (TBNode node: nodes)
                if (langUtil.isPredicateCandidate(node.getPOS()) &&
                    predicateClassifier.predictValues(predFeatures.getFeatureVector(extractPredicateFeature(node, nodes)), vals)==1)
                	predictions.add(new SRInstance(node, parseTree, langUtil.findStems(node).get(0)+".XX", vals[1]-vals[0]));
        } else {
        	for (SRInstance goldSRL:goldSRLs)
        		predictions.add(new SRInstance(parseTree.getNodeByTokenIndex(goldSRL.getPredicateNode().getTokenIndex()), parseTree, goldSRL.getRolesetId(), 1.0));
        }

        int[] supportIds = SRLUtil.findSupportPredicates(predictions, langUtil, SRLUtil.SupportType.VERB, false);
        BitSet predicted = new BitSet(supportIds.length);
        
        // classify verb predicates first
        int cardinality = 0;        
        do {
        	cardinality = predicted.cardinality();
        	for (int i=predicted.nextClearBit(0); i<supportIds.length; i=predicted.nextClearBit(i+1))
        		if (!langUtil.isVerb(predictions.get(i).getPredicateNode().getPOS()))
        			continue;
        		else if (supportIds[i]<0 || predicted.get(supportIds[i])) {
        			predict(predictions.get(i), supportIds[i]<0?null:predictions.get(supportIds[i]), namedEntities);
        			predicted.set(i);
        		}
        } while (predicted.cardinality()>cardinality);
        
        // then classify nominal predicates, by now we'll have the verb arguments to help find support verbs
        supportIds = SRLUtil.findSupportPredicates(predictions, langUtil, SRLUtil.SupportType.NOMINAL, false);
        do {
        	cardinality = predicted.cardinality();
        	for (int i=predicted.nextClearBit(0); i<supportIds.length; i=predicted.nextClearBit(i+1))
        		if (langUtil.isVerb(predictions.get(i).getPredicateNode().getPOS()))
        			continue;
        		else if (supportIds[i]<0 || predicted.get(supportIds[i])) {
        			predict(predictions.get(i), supportIds[i]<0?null:predictions.get(supportIds[i]), namedEntities);
        			predicted.set(i);
        		}
        } while (predicted.cardinality()>cardinality);
        
        return predictions;
    }

    public int predict(SRInstance prediction, SRInstance support, String[] namedEntities) {
        List<TBNode> argNodes = SRLUtil.getArgumentCandidates(prediction.predicateNode, support, langUtil, 1);
        List<Map<EnumSet<Feature>,List<String>>> samples = extractSampleFeature(prediction.predicateNode, argNodes, support, namedEntities);

        //boolean doStage2 = false;
        boolean doStage2 = classifier2!=null;
        
        int predTerminalIndex = prediction.predicateNode.getTerminalIndex();
        
        Sample[] fsamples = new Sample[samples.size()];
        
        for (int i=0; i<samples.size(); ++i)
        {
            //for (Map.Entry<EnumSet<Feature>,List<String>> entry:samples.get(i).entrySet())
            //    System.out.println(toString(entry.getKey())+": "+entry.getValue());
            //System.out.println(Arrays.toString(getFeatureVector(samples.get(i), featureStringMap)));
            
            if (labelValues==null) labelValues = new double[labelIndexMap.size()];
            
            int[] x = features.getFeatureVector(samples.get(i));
            int labelIndex = classifier.predictValues(x, labelValues);
            double value = labelValues[classifier.getLabelIdxMap().get(labelIndex)];
            String label = labelIndexMap.get(labelIndex);
            
            if (doStage2)
            {
                int terminalIndex = argNodes.get(i).getHead().getTerminalIndex();
                fsamples[i] = new Sample(x, label, terminalIndex, predTerminalIndex>terminalIndex);
            }
            else if (labeled && !label.equals(NOT_ARG))
                prediction.addArg(new SRArg(label, argNodes.get(i), value));
        }

        if (doStage2)
        {
            int[][] X = getFeatures(fsamples);
            for (int i=0; i<samples.size(); ++i)
            {
                int labelIndex = classifier2.predictValues(X[i], labelValues);
                double value = labelValues[classifier.getLabelIdxMap().get(labelIndex)];
                String label = labelIndexMap.get(labelIndex);
                if (labeled && !label.equals(NOT_ARG))
                    prediction.addArg(new SRArg(label, argNodes.get(i), value));
            }
        }

        prediction.cleanUpArgs();
        
        /*
        if (goldSRL!=null)
        {
            if (labeled)
                score.addResult(SRLUtil.convertSRInstanceToTokenMap(prediction), SRLUtil.convertSRInstanceToTokenMap(goldSRL));
            else
            {
                Map<String, BitSet> goldMap = SRLUtil.convertSRInstanceToTokenMap(goldSRL);
                Map<String, BitSet> newGoldMap = new TreeMap<String, BitSet>();
                newGoldMap.put(IS_ARG, new BitSet());
                for (Map.Entry<String, BitSet> entry: goldMap.entrySet())
                    newGoldMap.get(IS_ARG).or(entry.getValue());
                score.addResult(SRLUtil.convertSRInstanceToTokenMap(prediction), newGoldMap);
            }
        }*/
        
        return samples.size();
    }
/*
    private void initScore()
    {
        score = new SRLScore(labelStringSet);
    }
*/
    void writeData(PrintStream out, int[] featureVec, int label, InstanceFormat format)
    {
        if (format==InstanceFormat.DEFAULT)
        {
            out.print(label-1);
            for (int f : featureVec)
                out.print(" "+f);
        }
        else if (format==InstanceFormat.SVM)
        {
            out.print(label);
            for (int f : featureVec)
                out.print(" "+(f+1)+":1");
        }
        out.print("\n");
    }
    
    public void writeTrainingData(String filename, InstanceFormat format) throws FileNotFoundException
    {
        PrintStream fout = new PrintStream(new FileOutputStream(filename));
        
        for (Map.Entry<String, List<Sample[]>> entry:trainingSamples.entrySet())
            for (Sample[] samples:entry.getValue())
                for (Sample sample:samples)
                    writeData(fout, sample.features, labelStringMap.get(sample.label), format);
                
        fout.close();
    }
    
    /*
    public void writeSample(PrintStream out, TBNode predicateNode, ArrayList<TBNode> argNodes, ArrayList<String> labels, String[] namedEntities, InstanceFormat format)
    {
        List<Map<EnumSet<Feature>,List<String>>> samples = extractSampleFeature(predicateNode, argNodes, namedEntities);
        
        for (int i=0; i<samples.size(); ++i)
        {
            int lIdx = labelStringMap.get(labels.get(i));
            if (lIdx==0)
            {
                logger.severe("Unknown label: "+labels.get(i)+", converting to "+NOT_ARG);
                lIdx = labelStringMap.get(NOT_ARG);
            }
            writeData(out, features.getFeatureVector(samples.get(i)), lIdx, format);
        }
    }   
*/
	
	int countConstituents(String label, List<TBNode> lnodes, List<TBNode> rnodes, TBNode joinNode)
	{   
	    int count = 0;
	    
	    count += countConstituents(label, new LinkedList<TBNode>(lnodes), true, 100);
        count += countConstituents(label, new LinkedList<TBNode>(rnodes), false, 100);
        
        for (int i=lnodes.get(lnodes.size()-1).getChildIndex()+1; i<rnodes.get(rnodes.size()-1).getChildIndex(); ++i)
            count += countConstituents(label, joinNode.getChildren()[i], lnodes.size()>rnodes.size()?lnodes.size():rnodes.size());
        
        count += joinNode.getPOS().startsWith(label)?1:0;
        
	    return count;
	}
	
    int countConstituents(String label, Deque<TBNode> nodes, boolean left, int depth)
    {   
        TBNode node = nodes.pop();
        int count = node.getPOS().startsWith(label)?1:0;

        if (nodes.isEmpty())
            return count;
        
        ++depth;
        
        if (left)
            for (int i=node.getChildIndex()+1; i<node.getParent().getChildren().length;++i)
                count += countConstituents(label, node.getParent().getChildren()[i], depth);
        else
            for (int i=0; i<node.getChildIndex()-1;++i)
                count += countConstituents(label, node.getParent().getChildren()[i], depth);
        
        return count + countConstituents(label, nodes, left, depth);
    }
	 
    int countConstituents(String label, TBNode node, int depth)
    {   
        int count = node.getPOS().startsWith(label)?1:0;
        
        if (node.isTerminal() || depth == 0)
            return count;
        
        for (TBNode cNode:node.getChildren())
            count += countConstituents(label, cNode, depth-1);
        
        return count;
    }
    
    protected TBNode trimPathNodes(List<TBNode> argNodes, List<TBNode> predNodes)
    {
        TBNode joinNode = null;
        do
        {
            joinNode = argNodes.get(argNodes.size()-1);
            argNodes.remove(argNodes.size()-1);
            predNodes.remove(predNodes.size()-1);
        } while (!argNodes.isEmpty() && !predNodes.isEmpty() && 
                argNodes.get(argNodes.size()-1).getChildIndex()==predNodes.get(predNodes.size()-1).getChildIndex());
                //argNodes.get(argNodes.size()-1).terminalIndex==predNodes.get(predNodes.size()-1).terminalIndex);
        return joinNode;
    }
    
    protected List<String> getPath(TBNode argNode, TBNode predNode)
    {
        List<TBNode> argNodes = argNode.getPathToRoot();
        List<TBNode> predNodes = predNode.getPathToRoot();
        
        TBNode joinNode = trimPathNodes(argNodes, predNodes);
    
        return getPath(argNodes, predNodes, joinNode);
    }
    
    protected List<String> getPath(List<TBNode> argNodes, List<TBNode> predNodes, TBNode joinNode)
    {
        ArrayList<String> path = new ArrayList<String>();
        
        for (TBNode node:argNodes)
        {
            path.add(node.getPOS());
            path.add(UP_CHAR);
        }
        path.add(joinNode.getPOS());
        for (int i=predNodes.size()-1; i>=0; --i)
        {
            path.add(DOWN_CHAR);
            path.add(predNodes.get(i).getPOS());
        }
        return path;
    }       
}
