package clearsrl;

import gnu.trove.TIntArrayList;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import clearcommon.alg.Classifier;
import clearcommon.alg.CrossValidator;
import clearcommon.alg.FeatureSet;
import clearcommon.alg.LinearClassifier;
import clearcommon.alg.PairWiseClassifier;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.util.EnglishUtil;
import clearcommon.util.LanguageUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
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
		PREDICATE(false),
		PREDICATEPOS(false),
		VOICE(false),
		SUBCATEGORIZATION(false),

		// Constituent dependent features
		PATH(false),
		PATHG1(false),           
		PATHG2(false),
		PATHG3(false),
		PATHG4(false),
		PATHDEP(false),
		PHRASETYPE(false),
		POSITION(false),
		CONSTITUENTDIST(false),
		FIRSTCONSTITUENTREl(false),
		FIRSTCONSTITUENTABS(false),
		HEADWORD(false),
		HEADWORDPOS(false),
		HEADWORDDUPE(false),
		FIRSTWORD(false),
		FIRSTWORDPOS(false),
		LASTWORD(false),
		LASTWORDPOS(false),
		SYNTACTICFRAME(false),
		NAMEDENTITIES(false),

        // sequence features
		SUPPORT(true),
		SUPPORTPATH(true),
		SUPPORTARG(true),
		ARGPREVIOUS(true),
		ARGLISTDIRECTIONAL(true),
		ARGLISTALL(true);
		
		boolean sequence;

		Feature(boolean sequence) {
			this.sequence = sequence;
		}
		
		public boolean isSequence() {
			return sequence;
		}
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
	
	class SRLSample{
		public SRLSample(TBNode predicate, TBTree tree, SRLSample support, ArgSample[] args) {
			this.predicate = predicate;
			this.tree = tree;
			this.support = support;
			this.args = args;
		}
		TBNode predicate;
		TBTree tree;
		SRLSample support;
		ArgSample[] args;
	}
	
	class ArgSample{ 
	    public ArgSample(TBNode node, TBNode predicate, String label, EnumMap<Feature,List<String>> features) {
	    	this.node = node;
	    	this.predicate = predicate;
	    	this.label = label;
	        this.features = features;
	        if (this.label==null) this.label = NOT_ARG;
	    }
	    TBNode node;
	    TBNode predicate;
	    String label;
	    EnumMap<Feature,List<String>> features;
	}
	
	class TokenDistanceComparator implements Comparator<ArgSample>, Serializable {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(ArgSample lhs, ArgSample rhs) {
			BitSet lhsSet = lhs.node.getTokenSet();
			BitSet rhsSet = rhs.node.getTokenSet();
			
			int lhsStart = lhsSet.nextSetBit(0);
			int rhsStart = rhsSet.nextSetBit(0);
			
			if ((lhs.predicate.getTokenIndex()<lhsStart)!=(lhs.predicate.getTokenIndex()<rhsStart))
				return lhs.predicate.getTokenIndex()-lhsStart;
				
			if (lhsStart!=rhsStart)
				return lhs.predicate.getTokenIndex()<lhsStart?lhsStart-rhsStart:rhsStart-lhsStart;

			return rhsSet.cardinality()-lhsSet.cardinality();
		}
		
	}
	
	class HeadDistanceComparator implements Comparator<ArgSample>, Serializable  {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(ArgSample o1, ArgSample o2) {
			// TODO Auto-generated method stub
			return 0;
		}
		
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
	
	Comparator<ArgSample>								 sampleComparator;
	
	boolean                                              trainGoldParse;
	
    transient LanguageUtil                               langUtil;
	
	transient double[]                                   labelValues;
	
	transient SortedMap<String, List<SRLSample>>         trainingSamples;
	
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
		
		trainingSamples = new TreeMap<String, List<SRLSample>>();
		
		predicateTrainingFeatures = new ArrayList<int[]>();
		predicateTrainingLabels = new TIntArrayList();
		
		sampleComparator = new TokenDistanceComparator();
		
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

    	SRLSample[] srlSamples = new SRLSample[trainInstances.size()]; 
    	
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
                    srlSamples[i] = addTrainingSamples(trainInstances.get(i), supportIds[i]<0?null:trainInstances.get(supportIds[i]), supportIds[i]<0?null:srlSamples[supportIds[i]], namedEntities, buildDictionary);

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
    
    public SRLSample addTrainingSamples(SRInstance sampleInstance, SRInstance supportInstance, SRLSample supportSample, String[] namedEntities, boolean buildDictionary)    
	{
        ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
        ArrayList<String> labels = new ArrayList<String>();
        
        for (SRArg arg:sampleInstance.args) {
            if (arg.isPredicate()) continue;
            argNodes.add(arg.node);
            labels.add(arg.label);
        }
        
		List<EnumMap<Feature,List<String>>> featureMapList = extractSampleFeature(sampleInstance.predicateNode, argNodes, namedEntities);
		List<ArgSample> sampleList = new ArrayList<ArgSample>();
        for (int i=0; i<featureMapList.size();++i)
            if (labelStringMap.containsKey(labels.get(i)))
                sampleList.add(new ArgSample(argNodes.get(i), sampleInstance.getPredicateNode(), labels.get(i), featureMapList.get(i)));
        
        ArgSample[] argSamsples = sampleList.toArray(new ArgSample[sampleList.size()]);
        Arrays.sort(argSamsples, sampleComparator);
				
		if (buildDictionary) {
			int c=0;
			List<ArgSample> predictedList = new ArrayList<ArgSample>();
			for (int i=0; i<featureMapList.size();++i) {
				boolean isNoArg = NOT_ARG.equals(labels.get(c));
				featureMapList.get(i).putAll(extractSequenceFeatures(sampleInstance.predicateNode, sampleList.get(i), sampleInstance, predictedList));
				if (!isNoArg)
					predictedList.add(argSamsples[i]);
				for(Map.Entry<EnumSet<Feature>,List<String>> entry:features.convertFlatSample(featureMapList.get(i)).entrySet())
					features.addToDictionary(entry.getKey(), entry.getValue(), isNoArg);
				
				//if (!NOT_ARG.equals(SRLUtil.getMaxLabel(labels.get(c))))
				//	System.out.println(sample.get(Feature.PATH));
				labelStringMap.put(labels.get(c), labelStringMap.get(labels.get(c))+1);
				++c;
			}
			return null;
		} else {              
            SRLSample srlSample = new SRLSample(sampleInstance.getPredicateNode(), sampleInstance.getTree(), supportSample, argSamsples);
            
            List<SRLSample> tSampleList = trainingSamples.get(sampleInstance.tree.getFilename());
            if (tSampleList == null) {
            	tSampleList = new ArrayList<SRLSample>();
                trainingSamples.put(sampleInstance.tree.getFilename(), tSampleList);
            }                
            tSampleList.add(srlSample);
            
            return srlSample;
		}
	}


	public List<EnumMap<Feature,List<String>>> extractSampleFeature(TBNode predicateNode, List<TBNode> argNodes, String[] namedEntities) {	
		List<EnumMap<Feature,List<String>>> featureMapList = new ArrayList<EnumMap<Feature,List<String>>>();
		
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
			default:
				break;
			}
		}
				
		for (TBNode argNode:argNodes)
		{
			
			EnumMap<Feature,List<String>> featureMap = new EnumMap<Feature,List<String>>(defaultMap);
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
					featureMap.put(feature, Arrays.asList(pathStr));
					break;
				case PATHG1:
				{
					StringBuilder buffer = new StringBuilder();
					for (String node:path) 
					{
						if (node.equals(DOWN_CHAR)) break;
						buffer.append(node);
					}
					featureMap.put(feature, Arrays.asList(buffer.toString()));
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
					featureMap.put(feature, values);
					break;
				}
				case PATHG3:
				{
					ArrayList<String> trigram = new ArrayList<String>();
					for (int i=0; i<path.size()-4; i+=2)
						trigram.add(path.get(i)+path.get(i+1)+path.get(i+2)+path.get(i+3)+path.get(i+4));
						
					featureMap.put(feature, trigram);
					break;
				}
				case PATHG4:
				{
					StringBuilder buffer = new StringBuilder();
					for (String node:path) buffer.append(node.charAt(0));
					featureMap.put(feature, Arrays.asList(buffer.toString()));
					break;
				}
				case PATHDEP:
				{
		            StringBuilder buffer = new StringBuilder();
                    for (String node:depPath) buffer.append(node);
                    featureMap.put(feature, Arrays.asList(buffer.toString()));
                    
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
						featureMap.put(feature, list);
					}
					else
						featureMap.put(feature, trainGoldParse?argNode.getFunctionTaggedPOS():Arrays.asList(argNode.getPOS()));
					break;
				case POSITION:
					//if (isBefore) sample.put(feature, Arrays.asList("before"));
				    featureMap.put(feature, Arrays.asList(isBefore?"before":"after"));
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
				    featureMap.put(feature, list);
                    break;
				}
				case HEADWORD:
					featureMap.put(feature, Arrays.asList(head.getWord()));
					break;
				case HEADWORDPOS:
					featureMap.put(feature, trainGoldParse?head.getFunctionTaggedPOS():Arrays.asList(head.getPOS()));
					break;
				case HEADWORDDUPE:
				    //if (argNode.getParent()!=null && argNode.getHead()==argNode.getParent().getHead())
				    featureMap.put(feature, Arrays.asList(Boolean.toString(argNode.getParent()!=null && argNode.getHead()==argNode.getParent().getHead())));
				    //else
				    //   sample.put(feature, Arrays.asList("HeadUnique"));
                    break;
				case FIRSTWORD:
					featureMap.put(feature, Arrays.asList(tnodes.get(0).getWord()));
					break;
				case FIRSTWORDPOS:
					featureMap.put(feature, trainGoldParse?tnodes.get(0).getFunctionTaggedPOS():Arrays.asList(tnodes.get(0).getPOS()));
					break;
				case LASTWORD:
					featureMap.put(feature, Arrays.asList(tnodes.get(tnodes.size()-1).getWord()));
					break;
				case LASTWORDPOS:
					featureMap.put(feature, trainGoldParse?tnodes.get(tnodes.size()-1).getFunctionTaggedPOS():Arrays.asList(tnodes.get(tnodes.size()-1).getPOS()));
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
					featureMap.put(feature, Arrays.asList(builder.toString()));
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
						featureMap.put(feature, Arrays.asList(neSet.toArray(new String[neSet.size()])));
					}
					break;
				}
				default:
					break;
				}
			}
			
			featureMapList.add(featureMap);
		}
		
		
		
		
		return featureMapList;
	}
	
	public EnumMap<Feature,List<String>> extractSequenceFeatures(TBNode predicate, ArgSample sample, SRInstance support, List<ArgSample> predictedSamples) {
		EnumMap<Feature,List<String>> featureMap = new EnumMap<Feature,List<String>>(Feature.class);
		
		for (Feature feature:features.getFeaturesFlat()) {
		switch (feature) {
			case SUPPORT:
				if (support!=null) {
					String lemma = langUtil.findStems(support.getPredicateNode()).get(0);
					String position = support.getPredicateNode().getTokenIndex()<predicate.getTokenIndex()?"left":"right";
					String level = support.getPredicateNode().getLevelToRoot()<predicate.getLevelToRoot()?"above":"sibling";
					featureMap.put(feature, Arrays.asList(lemma, position, level, lemma+" "+level, support.getPredicateNode().getWord()));
				}
				break;
			case SUPPORTPATH:
				if (support!=null) {
					List<TBNode> supportToTopNodes = support.getPredicateNode().getPathToRoot();
				    List<TBNode> predToTopNodes = predicate.getPathToRoot();
				    TBNode joinNode = trimPathNodes(supportToTopNodes, predToTopNodes);
					List<String> path = getPath(supportToTopNodes, predToTopNodes, joinNode);
					StringBuilder buffer = new StringBuilder();
					for (String node:path) buffer.append(node);
					featureMap.put(feature, Arrays.asList(buffer.toString()));
				}
				break;
			case SUPPORTARG:
				if (support!=null) {
					SRArg sArg = null;
					for (SRArg arg:support.getArgs()) {
						if (arg.getLabel().equals(NOT_ARG)) continue;
						if (arg.node == sample.node || sample.node.isDecendentOf(arg.node)) {
							sArg=arg;
							break;
						}							
					}
					if (sArg!=null)
						featureMap.put(feature, Arrays.asList((sArg.node==sample.node?"":"in ")+sArg.getLabel()));
				}
				break;
			case ARGLISTDIRECTIONAL:
				// list of all found args in the same direction
				if (!predictedSamples.isEmpty()) {
					List<String> labels = new ArrayList<String>(predictedSamples.size());
					boolean left = sample.node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex();
					for (ArgSample predictedSample:predictedSamples)
						if (left == predictedSample.node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex())
							labels.add(predictedSample.label);
					if (!labels.isEmpty())
						featureMap.put(feature, labels);
				}
				break;
			case ARGLISTALL:
				// list of all found args
				if (!predictedSamples.isEmpty()) {
					List<String> labels = new ArrayList<String>(predictedSamples.size());
					for (ArgSample predictedSample:predictedSamples)
						labels.add(predictedSample.label);
					featureMap.put(feature, labels);
				}
				break;
			case ARGPREVIOUS:
				if (!predictedSamples.isEmpty() && sample.node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex()==
							predictedSamples.get(predictedSamples.size()-1).node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex())
					featureMap.put(feature, Arrays.asList(predictedSamples.get(predictedSamples.size()-1).label));
				break;
			default:
				break;
			}
		}
		
		return featureMap;
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
            switch (feature) {
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

    static List<String> permute(List<String> lhs, List<String> rhs) {   
        if (lhs==null || rhs==null) return new ArrayList<String>(0);

    	ArrayList<String> ret = new ArrayList<String>(lhs.size()*rhs.size());
    	for (String a2:rhs)
    	    for (String a1:lhs)
    			ret.add(a1+" "+a2);
    	return ret;
    }
    
    private int buildMapIndex(TObjectIntHashMap<String> mapObj, int startIdx) {
        String[] keys = mapObj.keys(new String[mapObj.size()]);
        mapObj.clear();
        for (String key:keys)
            mapObj.put(key, ++startIdx);
        return startIdx;
    }
    
    public void train(Properties prop) {
    	// train predicate classifier
        if (predFeatures!=null && !predicateTrainingFeatures.isEmpty()) {
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
        
        classifier = new PairWiseClassifier(labelStringMap, prop);
        
        int folds = Integer.parseInt(prop.getProperty("crossvalidation.folds","5"));
        int threads = Integer.parseInt(prop.getProperty("crossvalidation.threads","1"));
        
        boolean hasSequenceFeature = false;
        for (Feature feature:features.getFeaturesFlat())
        	if (feature.sequence) {
        		hasSequenceFeature = true;
        		break;
        	}
        
        int rounds = hasSequenceFeature?5:1;
        double threshold = 0.001;
        
        String[] goldLabels = null;
        List<String> labelList = new ArrayList<String>();
        for (Map.Entry<String, List<SRLSample>> entry:trainingSamples.entrySet())
            for (SRLSample sample:entry.getValue())
                for (ArgSample argSample:sample.args)
                	labelList.add(argSample.label);
        goldLabels = labelList.toArray(new String[labelList.size()]);

        String[] labels = goldLabels;
        for (int r=0; r<rounds; ++r) {
        	String[] newLabels = train(hasSequenceFeature?folds:1, threads, labels);
        	int cnt=0;
        	for (int i=0; i<labels.length; ++i) {
        		//if (i<50) System.out.println(i+" "+labels[i]+" "+newLabels[i]);
        		if (labels[i].equals(newLabels[i])) ++cnt;
        	}
        	double agreement = cnt*1.0/labels.length;
        	System.out.printf("Round %d: %f\n", r, agreement);
        	labels = newLabels;
        
        	SRLScore score = new SRLScore(new TreeSet<String>(Arrays.asList(labelStringMap.keys(new String[labelStringMap.size()]))));
        	for (int i=0; i<labels.length; ++i)
        		score.addResult(labels[i], goldLabels[i]);
        
        	System.out.println(score.toString());
        	
        	if (1-agreement<=threshold) break;
        }
    }

    String[] train(int folds, int threads, String[] labels) {   			
        int[][] X = null;
        int[] y = null;
        int[] seed = null;

        List<int[]> xList = new ArrayList<int[]>();
        TIntArrayList yList = new TIntArrayList();
        TIntArrayList seedList = new TIntArrayList();
        int treeCnt = 0;
        for (Map.Entry<String, List<SRLSample>> entry:trainingSamples.entrySet()) {
            for (SRLSample srlSample:entry.getValue()) {
            	List<ArgSample> predictedArgs = new ArrayList<ArgSample>();
                for (ArgSample argSample:srlSample.args) {
                	if (!argSample.label.equals(NOT_ARG))
                		predictedArgs.add(argSample);
                	SRInstance support = null;
                	if (srlSample.support!=null) {
                		support = new SRInstance(srlSample.support.predicate, srlSample.support.tree);
                		for (ArgSample supportArg:srlSample.support.args)
                			if (!supportArg.label.equals(NOT_ARG))
                				support.addArg(new SRArg(supportArg.label, supportArg.node));
                	}
                	xList.add(getFeatureVector(srlSample.predicate, argSample, support, predictedArgs));
                    yList.add(labelStringMap.get(argSample.label));
                    seedList.add(treeCnt);
                }
            }
            ++treeCnt;
        }
        X = xList.toArray(new int[xList.size()][]);
        y = yList.toNativeArray();
        seed = seedList.toNativeArray();

        double[] weights = new double[labelStringMap.size()];
            
        String[] labelTypes = labelStringMap.keys(new String[labelStringMap.size()]);
        Arrays.sort(labelTypes);
 
        int idx=0;
        for (String label:labelTypes) {
            if (label.equals(NOT_ARG))
                weights[idx] = 1;
            else
                weights[idx] = 1;
            ++idx;
        }

        int[] yV;
        if (folds>1) {
            CrossValidator validator = new CrossValidator(classifier, threads);
            yV =  validator.validate(folds, X, y, seed, true);
        } else {
            classifier.train(X, y);
            yV = new int[y.length];
            for (int i=0; i<y.length; ++i)
                yV[i] = classifier.predict(X[i]);
        }
        
        String[] newLabels = new String[yV.length];
        for (int i=0; i<yV.length; ++i)
        	newLabels[i] = labelIndexMap.get(yV[i]);
        
        return newLabels;
    }

    
    int[] getFeatureVector(TBNode predicate, ArgSample sample, SRInstance support, List<ArgSample> predictedSamples) {
    	EnumMap<Feature,List<String>> sampleFeatures = extractSequenceFeatures(predicate, sample, support, predictedSamples);
    	sampleFeatures.putAll(sample.features);
    	return features.getFeatureVector(features.convertFlatSample(sampleFeatures));
    }
 
    public List<SRInstance> predict(TBTree parseTree, SRInstance[] goldSRLs, String[] namedEntities) {   
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
        List<EnumMap<Feature,List<String>>> featureMapList = extractSampleFeature(prediction.predicateNode, argNodes, namedEntities);
        
        ArgSample[] fsamples = new ArgSample[featureMapList.size()];
        
        for (int i=0; i<featureMapList.size(); ++i)
        	fsamples[i] = new ArgSample(argNodes.get(i), prediction.predicateNode, null, featureMapList.get(i));
        
        Arrays.sort(fsamples, sampleComparator);
        
        List<ArgSample> predictedList = new ArrayList<ArgSample>();
        
        for (int i=0; i<fsamples.length; ++i) {
        	if (labelValues==null) labelValues = new double[labelIndexMap.size()];
        	int[] x = getFeatureVector(prediction.predicateNode, fsamples[i], support, predictedList);
        	int labelIndex = classifier.predictValues(x, labelValues);
            double value = labelValues[classifier.getLabelIdxMap().get(labelIndex)];
            String label = labelIndexMap.get(labelIndex);
            if (labeled && !label.equals(NOT_ARG)) {
                prediction.addArg(new SRArg(label, argNodes.get(i), value));
                predictedList.add(fsamples[i]);
            }
        }
        
        prediction.cleanUpArgs();
        
        return featureMapList.size();
    }
	
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
    
    TBNode trimPathNodes(List<TBNode> argNodes, List<TBNode> predNodes) {
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
    
    List<String> getPath(TBNode argNode, TBNode predNode) {
        List<TBNode> argNodes = argNode.getPathToRoot();
        List<TBNode> predNodes = predNode.getPathToRoot();
        
        TBNode joinNode = trimPathNodes(argNodes, predNodes);
    
        return getPath(argNodes, predNodes, joinNode);
    }
    
    List<String> getPath(List<TBNode> argNodes, List<TBNode> predNodes, TBNode joinNode) {
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
