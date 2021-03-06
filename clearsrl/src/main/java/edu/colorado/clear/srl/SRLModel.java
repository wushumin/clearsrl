package edu.colorado.clear.srl;

import edu.colorado.clear.common.alg.Classifier;
import edu.colorado.clear.common.alg.CrossValidator;
import edu.colorado.clear.common.alg.FeatureSet;
import edu.colorado.clear.common.alg.LinearClassifier;
import edu.colorado.clear.common.alg.SimpleModel;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.treebank.TBTree;
import edu.colorado.clear.common.util.EnglishUtil;
import edu.colorado.clear.common.util.LanguageUtil;
import edu.colorado.clear.common.util.PBFrame;
import edu.colorado.clear.common.util.PropertyUtil;
import edu.colorado.clear.common.util.PBFrame.Roleset;
import edu.colorado.clear.srl.ec.ECCommon;
import edu.colorado.clear.srl.util.LDAModel;
import edu.colorado.clear.srl.util.Topics;
import edu.colorado.clear.srl.util.LDAModel.SparseCount;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.TObjectFloatMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import gnu.trove.map.hash.TObjectFloatHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author shumin
 *
 */
public class SRLModel implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
   

    public static final String NOT_PRED="!PRED";
    public static final String IS_PRED="PRED";
    
    private static final int GZIP_BUFFER = 0x40000;
    
    public enum Feature {
        // Constituent independent features
        PREDICATE,
        PREDICATETYPE,
        PREDICATEPOS,
        VOICE,
        SUBCATEGORIZATION,
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
        ROLESET,
        ROLESETCLASS,
        ECSET,
        
        // Constituent dependent features
        PATH,
        PATHG1,           
        PATHG2,
        PATHG3,
        PATHG4,
        PATHDEP,
        PHRASETYPE,
        POSITION,
        COMPLEMENTIZERARG, // identifies the NP head (usually an argument) of a complementizer (Chinese)
        CONSTITUENTDIST,
        FIRSTCONSTITUENTREl,
        FIRSTCONSTITUENTABS,
        HEADWORD,
        HEADWORDPOS,
        HEADWORDTOPICS,
        HEADWORDTOPICNUM,
        HEADWORDSP,
        
        HEADWORDDUPE,
        FIRSTWORD,
        FIRSTWORDPOS,
        LASTWORD,
        LASTWORDPOS,
        SYNTACTICFRAME,
        NAMEDENTITIES,
        ECPOSITION,

        // sequence features
        SUPPORT(true),
        SUPPORTPATH(true),
        SUPPORTARG(true),

        // stage 2 classification features
        ARGTYPE(false, true),
        ARGREL(false, true),
        ARGTOPICTYPE(false, true),
        ARGROLEMISS(false, true),
        ARGLISTDIRECTIONAL(false, true),
        ARGLISTALL(false, true);
        
        boolean sequence;
        boolean stage2;

        Feature() {
            this.sequence = false;
            this.stage2 = false;
        }
        
        Feature(boolean sequence) {
            this.sequence = sequence;
        }
        
        Feature(boolean sequence, boolean stage2) {
            this.sequence = sequence;
            this.stage2 = stage2;
        }
        
        public boolean isSequence() {
            return sequence;
        }
        public boolean isStage2() {
            return stage2;
        }

        static boolean hasSequenceFeature(EnumSet<Feature> features) {
            for (Feature feature:features)
                if (feature.isSequence())
                    return true;
            return false;
        }
        
        static boolean hasStage2Feature(EnumSet<Feature> features) {
            for (Feature feature:features)
                if (feature.isStage2())
                    return true;
            return false;
        } 
    };
    
    class SRLSample implements Serializable{
        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        public SRLSample(TBNode predicate, String roleset, TBTree tree, SRLSample support, ArgSample[] args, boolean isGoldNominal, boolean isTrainNominal) {
            this.predicate = predicate;
            this.roleset = roleset;
            this.tree = tree;
            this.support = support;
            this.args = args;
            this.isGoldNominal = isGoldNominal;
            this.isTrainNominal = isTrainNominal;
        }
        TBNode predicate;
        String roleset;
        TBTree tree;
        SRLSample support;
        ArgSample[] args;
        boolean isGoldNominal;
        boolean isTrainNominal;
    }
    
    class ArgSample implements Serializable{ 
        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        public ArgSample(TBNode node, TBNode predicate, String label, EnumMap<Feature,Collection<String>> features) {
            this.node = node;
            this.predicate = predicate;
            this.label = label;
            this.features = features;
            if (this.label==null) this.label = SRArg.NOT_ARG;
            this.labelProb = null;
        }
        
        @Override
		public String toString() {
            if (label.equals(SRArg.NOT_ARG))
                return node.toText();
            return "["+label+" "+node.toText()+"]";
        }
        TBNode node;
        TBNode predicate;
        String label;
        double[] labelProb;
        EnumMap<Feature,Collection<String>> features;
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
                
            int lhsEnd = lhsSet.nextClearBit(lhsStart)-1;
            int rhsEnd = rhsSet.nextClearBit(rhsStart)-1;
            
            if (lhsStart!=rhsStart)
                return lhs.predicate.getTokenIndex()<lhsStart?lhsStart-rhsStart:rhsEnd-lhsEnd;

            return rhsSet.cardinality()-lhsSet.cardinality();
            
            //if (lhs.node.getHead().getTokenIndex()!=rhs.node.getHead().getTokenIndex())
            //  return lhs.predicate.getTokenIndex()<lhsStart?lhs.node.getHead().getTokenIndex()-rhs.node.getHead().getTokenIndex():rhs.node.getHead().getTokenIndex()-lhs.node.getHead().getTokenIndex();
            
            //return lhs.node.isDecendentOf(rhs.node)?1:-1;
        }
        
    }
    
    public static String UP_CHAR = "^";
    public static String DOWN_CHAR = "v";
    public static String RIGHT_ARROW = "->";

    boolean                                 labeled = true;
    public boolean isLabeled() {
        return labeled;
    }

    public void setLabeled(boolean labeled) {
        this.labeled = labeled;
    }
    
    Set<EnumSet<Feature>>                   predicateFeatureSet;
    Set<EnumSet<Feature>>                   argFeatureSet;
    
    int                                     predicateMinCount = 2;

    SimpleModel<Feature>                    predicateModel;
    Map<String, SimpleModel<Feature>>       rolesetModelMap;
    transient Map<String, String[]>         rolesetValidatedLabelMap;
    
    FeatureSet<Feature>                     argLabelFeatures;
    TObjectIntMap<String>                   argLabelStringMap;
    public TObjectIntMap<String> getArgLabelStringMap() {
		return argLabelStringMap;
	}

	TIntObjectMap<String>                   argLabelIndexMap;
    
    // 2 stage classifier training:
    // train argLabel, argLabelStage2, then retrain both stages a few rounds (to propagate SRL output of support predicate)
    
    // main classifier
    // can use output of currently found arguments as well as parent SRL 
    Classifier                              argLabelClassifier;
    
    // classifier that looks at all found arguments of the previous classifier 
    // should only have a pared down list of likely argument constituents
    Classifier                              argLabelStage2Classifier;
    double                                  argLabelStage2Threshold;
    
    int                                     argLabelMinCount = 20;
    
    Comparator<ArgSample>                   sampleComparator = new TokenDistanceComparator();
    
    boolean                                 trainGoldParse = false;
    
    int                                     argCandidateLevelDown = 2;
    boolean                                 argCandidateAllHeadPhrases = true;
    float                                   noArgWeight = 0.2f;
    float                                   nominalWeight = 2;
    
    boolean                                 filterArgs = false;
    
    boolean                                 trainNominal = true;
    
    Map<String, List<String>>               argTopicMap = null;
    
    FeatureSet<Feature>                     nominalArgLabelFeatures = null;
    Classifier                              nominalArgLabelClassifier = null;
    Classifier                              nominalArgLabelStage2Classifier = null;
    double                                  nominalArgLabelStage2Threshold = 0;
    
    boolean                                 useGoldPredicateSeparation = false;
    
    Set<String>                             argPrimaryLabelSet = null;
    
    LDAModel                                topicModel = null;
    
    boolean                                 lemmatizeTopicWord = false;

    SRLVerbNetSP                            verbNetSP = null;
    
    transient LanguageUtil                  langUtil;
    
//  transient ArrayList<int[]>              predicateTrainingFeatures;
//  transient TIntArrayList                 predicateTrainingLabels;

    transient int                           argsTrained = 0;
    transient int                           argsTotal = 0;

    transient Logger                        logger = null;
    
    transient File                          trainingSampleFile;
    transient File                          extractedSampleFile;
    
    transient ObjectOutputStream            trainingSampleOutStream;
    
    transient int                           trainingTreeCnt;
    transient Set<String>                   predicateOverrideKeySet = null;


    /**
     * Constructor 
     * @param featureSet set of features used for argument identification/labeling
     * @param predicateFeatureSet set of features used for predicate identification
     */
    public SRLModel (Set<EnumSet<Feature>> argFeatureSet, Set<EnumSet<Feature>> predicateFeatureSet) {
        logger = Logger.getLogger("clearsrl");

        this.argFeatureSet = argFeatureSet;
        this.predicateFeatureSet = predicateFeatureSet;
        
        argLabelFeatures = new FeatureSet<Feature>(argFeatureSet);
        predicateModel = new SimpleModel<Feature>(predicateFeatureSet);
    }

    private void readObject(java.io.ObjectInputStream in)
    	     throws IOException, ClassNotFoundException {
    	in.defaultReadObject();
    	if (nominalArgLabelFeatures == null) {
    		nominalArgLabelFeatures = argLabelFeatures;
    		nominalArgLabelClassifier = argLabelClassifier;
    		nominalArgLabelStage2Classifier = argLabelStage2Classifier;
    		nominalArgLabelStage2Threshold = argLabelStage2Threshold;
    	}
    	logger = Logger.getLogger("clearsrl");
    }
    
    public void initialize(Properties props) throws IOException {
    	filterArgs = !props.getProperty("filterArguments", "false").equals("false");
    	trainNominal = !props.getProperty("trainNominal", "true").equals("false");
    	boolean separateNominalClassifier = !props.getProperty("separateNominalClassifier", "false").equals("false");
    	
    	useGoldPredicateSeparation = !props.getProperty("goldPredicateSeparation", "false").equals("false");
    	
        predicateModel.initialize();
        rolesetModelMap = new HashMap<String, SimpleModel<Feature>>();

        argLabelFeatures.initialize(); 
        if (trainNominal && separateNominalClassifier) {
        	nominalArgLabelFeatures = new FeatureSet<Feature>(argFeatureSet);
        	nominalArgLabelFeatures.initialize();
        } else 
        	nominalArgLabelFeatures = argLabelFeatures;
        
        // process name/id + object name/id means this should be unique, right?
        String uniqueExtension = ManagementFactory.getRuntimeMXBean().getName()+'.'+Integer.toHexString(System.identityHashCode(this));

        trainingTreeCnt = 0;
        trainingSampleFile = new File(props.getProperty("tmpdir", "/tmp"), "clearsrl.trainTree."+uniqueExtension);
        trainingSampleFile.deleteOnExit();
        
        extractedSampleFile = new File(props.getProperty("tmpdir", "/tmp"), "clearsrl.extractedTree."+uniqueExtension);
        extractedSampleFile.deleteOnExit();
        
        // might as well make sure both can be open for write
        trainingSampleOutStream = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(trainingSampleFile),GZIP_BUFFER),GZIP_BUFFER*4));
        //extractedSampleOut = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(extractedSampleFile),GZIP_BUFFER),GZIP_BUFFER*4));
        
        argLabelStringMap = new TObjectIntHashMap<String>();

        String topicModelFile = props.getProperty("topic.model");
        if (topicModelFile!=null) {
        	topicModel = LDAModel.readLDAModel(new File(topicModelFile));
        	
        	lemmatizeTopicWord = topicModel.getLemmatize();
        	Map<String, int[]> topicMap = topicModel.getWordTopics();
        	argTopicMap = new HashMap<String, List<String>>();
        	for (Map.Entry<String, int[]> entry:topicMap.entrySet()) {
        		String word = entry.getKey().substring(0,entry.getKey().lastIndexOf(':'));
        		String label = entry.getKey().substring(word.length()+1); 
        		
        		List<String> topics = argTopicMap.get(word);
        		if (topics==null)
        			argTopicMap.put(word, topics=new ArrayList<String>());
        		
        		for (int topicId:entry.getValue())
        			topics.add(label+':'+topicId);
        	}
        } else
        	argTopicMap = Topics.readAllTopics(props);
       
        if (argTopicMap!=null) {
        	int sum=0;
        	for (Map.Entry<String, List<String>> entry:argTopicMap.entrySet())
        		sum += entry.getValue().size();
        	logger.info(String.format("topic words: %d, total topic count: %d", argTopicMap.size(), sum));
        }
        
        if (argLabelFeatures.getFeaturesFlat().contains(Feature.HEADWORDSP)) {
        	logger.info("Initializing VerbNet SP");
        	verbNetSP = new SRLVerbNetSP();
        	verbNetSP.setLangUtil(langUtil);
        	verbNetSP.initialize(PropertyUtil.filterProperties(props, "selpref.", true));
        	logger.info("Reading count Map");
        	Map<String, Map<String, TObjectFloatMap<String>>> countDB = SRLSelPref.readTrainingCount(new File(props.getProperty("headwordDB")));
        	verbNetSP.makeSP(countDB);
        }
    }
    
    /**
     * Must be manually set as LanguageUtil is not serializable
     * @param langUtil
     */
    public void setLanguageUtil(LanguageUtil langUtil) {
        this.langUtil = langUtil;
        if (verbNetSP!=null)
        	verbNetSP.setLangUtil(langUtil);
    }
    
    public void setTrainGoldParse(boolean trainGoldParse) {
        this.trainGoldParse = trainGoldParse;
    }
    
    public void setPredicateOverride(Set<String> keySet) {
    	predicateOverrideKeySet = keySet;
    }

    @Override
	protected void finalize() {
        if (trainingSampleFile!=null)
            trainingSampleFile.delete();
        trainingSampleFile = null;

        if (extractedSampleFile!=null)
            extractedSampleFile.delete();
        extractedSampleFile = null;
    }
    
    /**
     * 
     * 
     * @param cutoff
     */
    void makeTrainingPredicates(int cutoff) throws IOException {  
        predicateModel.finalizeDictionary(predicateMinCount, 0);
        for (Iterator<Map.Entry<String, SimpleModel<Feature>>> iter=rolesetModelMap.entrySet().iterator() ; iter.hasNext();) {
            SimpleModel<Feature> rolesetModel = iter.next().getValue();
            rolesetModel.finalizeDictionary(predicateMinCount, 5);
            if (rolesetModel.getLabelSet().size()<2)
                iter.remove();
        }

        logger.info("Second pass processing of predicate training samples.");
        try (ObjectInputStream cachedInStream = 
        		new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(trainingSampleFile),GZIP_BUFFER),GZIP_BUFFER*4))) {
            for (;;) {
            	Sentence sentence = (Sentence)cachedInStream.readObject();
                float threshold = cachedInStream.readFloat();
                addTrainingPredicates(sentence, threshold, false);
            }
        } catch (EOFException e) {
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    Map<String, List<String>> makeArgTopicMap(ArgSample[] samples) {
    	if (topicModel==null || !argLabelFeatures.getFeaturesFlat().contains(Feature.ARGTOPICTYPE))
    		return null;
    	
    	int notArgIdx = argLabelStringMap.get(SRArg.NOT_ARG)-1;
    	
    	TObjectDoubleMap<String> wordMap = new TObjectDoubleHashMap<String>();
    	for (ArgSample sample:samples) {
    		String topicWord = Topics.getTopicHeadword(sample.node, lemmatizeTopicWord?langUtil:null);
    		if (topicWord==null)
    			continue;
    		topicWord+=':';
    		if (sample.labelProb!=null) {
    			for (int i=0; i<sample.labelProb.length; ++i)
    				if (i!=notArgIdx && sample.labelProb[i]>=0.05)
    					wordMap.adjustOrPutValue(topicWord+argLabelIndexMap.get(i+1), sample.labelProb[i], sample.labelProb[i]);
    		} else if (!SRArg.NOT_ARG.equals(sample.label))
    			wordMap.adjustOrPutValue(topicWord+sample.label, 1, 1);
    	}
    	if (wordMap.size()<2)
    		return null;
    	
    	Map<String, double[]> distMap = topicModel.infer(wordMap, 30, 20);
    	
    	if (distMap==null)
    		return null;
    	
    	Map<String, List<String>> topicMap = new HashMap<String, List<String>>();
    	for (Map.Entry<String, double[]> entry:distMap.entrySet()) {
    		int[] topics = LDAModel.getTopTopics(new SparseCount(entry.getValue()), 3, 0.02, 0.02);
    		if (topics==null || entry.getKey()==null)
    			continue;
    		
    		String word=entry.getKey().substring(0,entry.getKey().lastIndexOf(':'));
    		String label=entry.getKey().substring(word.length()+1);
    		
    		List<String> topicVals = topicMap.get(word);
    		if (topicVals==null) {
    			topicVals = new ArrayList<String>();
    			topicMap.put(word, topicVals);
    		}
    		for (int topic:topics)
    			topicVals.add(label+':'+topic);
		}
    	
    	return topicMap; 
    }
    
    /**
     * 
     * 
     * @param cutoff
     */
    void makeTrainingArguments(int cutoff) throws IOException {
        
        // Probably should hard code minimum number of instances
        FeatureSet.trimMap(argLabelStringMap,argLabelMinCount);
        
        logger.info("Labels: ");
        String[] labels = argLabelStringMap.keys(new String[argLabelStringMap.size()]);
        Arrays.sort(labels);
        for (String label:labels)
            logger.info("  "+label+" "+argLabelStringMap.get(label));
        
        argLabelFeatures.rebuildMap(cutoff);
        
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<EnumSet<Feature>, TObjectIntMap<String>> entry: argLabelFeatures.getFeatureStrMap().entrySet())
        	builder.append(FeatureSet.toString(entry.getKey())+": "+entry.getValue().size()+"\n");
        logger.info("number of verb predicate argument features \n"+builder.toString());
        	
        if (nominalArgLabelFeatures!=argLabelFeatures)
        	nominalArgLabelFeatures.rebuildMap(cutoff);
        
        FeatureSet.buildMapIndex(argLabelStringMap, 0, true);
        argLabelIndexMap = new TIntObjectHashMap<String>();
        for (TObjectIntIterator<String> iter=argLabelStringMap.iterator(); iter.hasNext();) {
            iter.advance();
            argLabelIndexMap.put(iter.value(),iter.key());
        }

        argPrimaryLabelSet = new HashSet<String>();
        for (String label:argLabelStringMap.keySet())
        	if (label.startsWith("C-") && argLabelStringMap.keySet().contains(label.substring(2)))
        		argPrimaryLabelSet.add(label.substring(2));
        	else if (label.matches("ARG\\d-.*") && argLabelStringMap.keySet().contains(label.substring(0,4)))
        		argPrimaryLabelSet.add(label.substring(0,4));

        logger.info(String.format("ARGS trained %d/%d", argsTrained, argsTotal));
        
        logger.info("Second pass processing of training samples.");
        TObjectIntMap<String> rolesetCntMap = new TObjectIntHashMap<String>();
        try (ObjectOutputStream cachedOutStream = 
        		new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(extractedSampleFile),GZIP_BUFFER),GZIP_BUFFER*4));
        		ObjectInputStream cachedInStream = 
        				new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(trainingSampleFile),GZIP_BUFFER),GZIP_BUFFER*4))) {
        	int trainingTreeCnt =0;
        	String trainFile = null;
            for (;;) {
            	Sentence sentence = (Sentence)cachedInStream.readObject();
                float threshold = cachedInStream.readFloat();
                
                TBTree tree = sentence.parse==null?sentence.treeTB:sentence.parse;
                if (!tree.getFilename().equals(trainFile)) {
                	trainFile = tree.getFilename();
                	logger.info("Processing (P2.2) "+trainFile);
                }
                SRLSample[] srlSamples = addTrainingArguments(sentence, threshold, rolesetCntMap, false);
                cachedOutStream.writeObject(tree);
                cachedOutStream.writeObject(srlSamples);
                if (++trainingTreeCnt%500==0)
                	cachedOutStream.reset();
            }
        } catch (EOFException e) {
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        for (Map.Entry<String, String[]> entry:rolesetValidatedLabelMap.entrySet())
        	if (entry.getValue().length!=rolesetCntMap.get(entry.getKey())+1)
        		logger.warning(String.format("roleset count mismatch: %s: %d/%d\n",entry.getKey(), entry.getValue().length, rolesetCntMap.get(entry.getKey())+1));
        trainingSampleFile.delete();
        logger.info("Second pass argument processing completed.");
    }
    

    
    /**
     * add training sentences to the model
     * @param tree input parse tree
     * @param goldInstances annotated SRL instances for the sentence captured by the parse tree (the instances themselves can be based off a different parse tree, but must have same tokenization)
     * @param namedEntities named entity features, if available
     * @param threshold argument matching threshold (for converting one parse to another)
     * @throws IOException 
     */
    public void addTrainingSentence(Sentence sentence, float threshold) throws IOException {
        trainingSampleOutStream.writeObject(sentence);
        trainingSampleOutStream.writeFloat(threshold);
        if (++trainingTreeCnt%5000==0)
            trainingSampleOutStream.reset();
    	addTrainingPredicates(sentence, threshold, true);
        addTrainingArguments(sentence, threshold, null, true);
    }
    // debug
    transient int nomPositiveCnt = 0;
    transient int nomNegativeCnt = 0;
    
    void addTrainingPredicates(Sentence sent, float threshold, boolean buildDictionary) {
    	TBTree tree = sent.parse==null?sent.treeTB:sent.parse;

    	TBNode[] nodes = tree.getTokenNodes();
    	/*
        ArrayList<TBNode> predicateCandidates = new ArrayList<TBNode>();
        for (TBNode node: nodes)
            if (langUtil.isPredicateCandidate(node.getPOS()) && 
            		(trainNominal || langUtil.isVerb(node.getPOS())))
                predicateCandidates.add(node);
 */
        PBInstance[] goldSRLs = new PBInstance[nodes.length];
        for (PBInstance goldInstance: sent.propPB)
        	if (trainNominal || langUtil.isVerb(goldInstance.getPredicate().getPOS()))
        		goldSRLs[goldInstance.getPredicate().getTokenIndex()] = goldInstance;
        
        // add predicate training samples
        for (TBNode node: nodes) {
        	boolean trainPredicate = langUtil.isPredicateCandidate(node.getPOS()) && 
        			(langUtil.isVerb(node.getPOS()) || 
        					(trainNominal && 
        							(sent.annotatedNominals==null || 
        							sent.annotatedNominals.contains(langUtil.makePBFrameKey(node)))));
        	
            if (goldSRLs[node.getTokenIndex()]==null && !trainPredicate)
                continue;
           
            EnumMap<Feature,Collection<String>> sample = extractFeaturePredicate(predicateModel.getFeaturesFlat(), node, null, null);
           
            if (trainPredicate)
            	predicateModel.addTrainingSample(sample, goldSRLs[node.getTokenIndex()]!=null?IS_PRED:NOT_PRED, buildDictionary);

            // debug
            if (trainPredicate && !langUtil.isVerb(node.getPOS()))
            	if (goldSRLs[node.getTokenIndex()]!=null)
            		nomPositiveCnt++;
            	else
            		nomNegativeCnt++;
            	
            
            if (goldSRLs[node.getTokenIndex()]==null)
                continue;
            
            String key = langUtil.makePBFrameKey(node);
            PBFrame frame = langUtil.getFrame(key);
            if (frame==null)
                continue;
            
            SimpleModel<Feature> rolesetModel = rolesetModelMap.get(key);
            
            //FeatureSet<Feature> rolesetFeature = rolesetFeatureMap.get(key);
            //TObjectIntMap<String> rolesetLabelString = rolesetLabelStringMap.get(key);
            if (buildDictionary) {
            	if (frame.getRolesets().size()>1) {
	                if (rolesetModel==null) {
	                    rolesetModel = new SimpleModel<Feature>(predicateFeatureSet);
	                    rolesetModel.initialize();
	                    rolesetModelMap.put(key, rolesetModel);
	                }
	                rolesetModel.addTrainingSample(sample, goldSRLs[node.getTokenIndex()].getRoleset(), true);
            	}
            } else if (rolesetModel!=null) {
            	rolesetModel.addTrainingSample(sample, goldSRLs[node.getTokenIndex()].getRoleset(), false);
            }
        }
    }
    
    SRLSample[] addTrainingArguments(Sentence sent, float threshold, TObjectIntMap<String> rolesetCntMap, boolean buildDictionary) {
    	TBTree tree = sent.parse==null?sent.treeTB:sent.parse;
    	List<SRInstance> goldInstances = SRLUtil.convertToSRInstance(sent.propPB);
    	for (Iterator<SRInstance> iter=goldInstances.iterator(); iter.hasNext();) {
    		SRInstance instance = iter.next();
    		if (!trainNominal && !langUtil.isVerb(instance.getPredicateNode().getPOS()))
    			iter.remove();
    	}
    	Collections.sort(goldInstances);
        List<SRInstance> trainInstances = new ArrayList<SRInstance>(goldInstances.size());
        for (SRInstance goldInstance:goldInstances) {
        	SRInstance trainInstance = new SRInstance(goldInstance.predicateNode, tree, null, goldInstance.getRolesetId(), 1.0);
            trainInstances.add(trainInstance);
            
            if (buildDictionary) continue;
            
            // get the roleset
            String key = langUtil.makePBFrameKey(trainInstance.predicateNode);
            PBFrame frame = langUtil.getFrame(key);
            if (frame==null)
            	continue;
            String[] rolesetLabels = rolesetValidatedLabelMap.get(key);
            
            //if (rolesetLabels!=null)
            //	System.out.printf("%s gold:%s label:%s %s\n", key, goldInstance.getRolesetId(), rolesetLabels[rolesetCntMap.get(key)], rolesetModelMap.get(key).getLabelSet());
            
            if (rolesetLabels==null || !rolesetModelMap.get(key).getLabelSet().contains(goldInstance.getRolesetId()))
            	trainInstance.rolesetId = frame.getRolesets().firstKey();
            else {
            	int idx = rolesetCntMap.adjustOrPutValue(key, 1, 0);
            	if (idx<rolesetLabels.length)
            		trainInstance.rolesetId = rolesetLabels[idx];
            	else
            		logger.severe("Error assigning role: "+tree.getFilename()+":"+tree.getIndex()+" "+goldInstance.getPredicateNode().getTerminalIndex()+" "+goldInstance.getRolesetId()+"\n"+
            				goldInstances);
            }
        }
        SRLSample[] srlSamples = new SRLSample[trainInstances.size()]; 
        int[] supportIds = SRLUtil.findSupportPredicates(trainInstances, useGoldPredicateSeparation?goldInstances:null, langUtil, SRLUtil.SupportType.ALL, true);        
        BitSet processedSet = new BitSet(supportIds.length);
        // classify verb predicates first
        int cardinality = 0;        
        do {
            cardinality = processedSet.cardinality();
            for (int i=processedSet.nextClearBit(0); i<supportIds.length; i=processedSet.nextClearBit(i+1))
                if (supportIds[i]<0 || processedSet.get(supportIds[i])) {                   
                    
                    List<TBNode> candidateNodes = 
                            SRLUtil.getArgumentCandidates(tree.getNodeByTokenIndex(goldInstances.get(i).getPredicateNode().getTokenIndex()), 
                            							  filterArgs,
                                                          supportIds[i]<0?null:trainInstances.get(supportIds[i]), 
                                                          langUtil, argCandidateLevelDown, argCandidateAllHeadPhrases);
                    Map<TBNode, SRArg> candidateMap = SRLUtil.mapArguments(goldInstances.get(i), tree, candidateNodes);
                    srlSamples[i] = addTrainingSRL(trainInstances.get(i), candidateMap, 
                                                      supportIds[i]<0?null:trainInstances.get(supportIds[i]), 
                                                      supportIds[i]<0?null:srlSamples[supportIds[i]], 
                                                      goldInstances.get(i), 
                                                      sent.depEC==null?null:sent.depEC[trainInstances.get(i).predicateNode.getTokenIndex()],
                                                      sent.namedEntities,
                                                      buildDictionary);

                    if (buildDictionary) {
                        int args = 0;
                        for (SRArg arg:trainInstances.get(i).getArgs())
                            if (!arg.getLabel().equals(SRArg.NOT_ARG))
                                args++;
                        
                        /*if (args!=goldInstances.get(i).getArgs().size()) {
                            System.err.println("\n"+trainInstances.get(i));
                            System.err.println(goldInstances.get(i));
                            if (supportIds[i]>=0)
                                System.err.println(trainInstances.get(supportIds[i]));
                            System.err.println(tree+"\n");
                        }*/
                        argsTrained+=args-1;
                        argsTotal+=goldInstances.get(i).getArgs().size()-1;
                    }
                    
                    processedSet.set(i);
                }
        } while (processedSet.cardinality()>cardinality);
        return srlSamples;
    }
    
    SRLSample addTrainingSRL(SRInstance sampleInstance, Map<TBNode, SRArg> candidateMap, 
            SRInstance supportInstance, SRLSample supportSample, SRInstance goldInstance, 
            String[] depEC, String[] namedEntities, boolean buildDictionary) {
        
        List<TBNode> argNodes = new ArrayList<TBNode>(candidateMap.keySet());
        List<String> labels = new ArrayList<String>();
        
        boolean isNominal = !langUtil.isVerb(useGoldPredicateSeparation?goldInstance.getPredicateNode().getPOS():sampleInstance.getPredicateNode().getPOS());
        
        List<EnumMap<Feature,Collection<String>>> featureMapList = extractFeatureSRL(isNominal?nominalArgLabelFeatures:argLabelFeatures, sampleInstance.predicateNode, sampleInstance.getRolesetId(), argNodes, sampleInstance.getRolesetId(), depEC, namedEntities, true);
        
        for (TBNode node:argNodes) {
            if (candidateMap.get(node)==null)
                labels.add(SRArg.NOT_ARG);
            else
                labels.add(langUtil.convertPBLabelTrain(candidateMap.get(node).label));
            sampleInstance.addArg(new SRArg(labels.get(labels.size()-1), node));
        }
        
        List<ArgSample> sampleList = new ArrayList<ArgSample>();
        for (int i=0; i<featureMapList.size();++i) {
            SRArg goldArg = candidateMap.get(argNodes.get(i));
            if (goldArg!=null) {
                BitSet goldTokenSet = goldArg.getTokenSet();
                BitSet argTokenSet = argNodes.get(i).getTokenSet();
                if (!argTokenSet.equals(goldTokenSet)) {
                    if (argTokenSet.nextSetBit(0)!=goldTokenSet.nextSetBit(0)) {
                        featureMapList.get(i).remove(Feature.FIRSTWORD);
                        featureMapList.get(i).remove(Feature.FIRSTWORDPOS);
                        if (argTokenSet.nextSetBit(0)>sampleInstance.getPredicateNode().getTokenIndex())
                            featureMapList.get(i).remove(Feature.CONSTITUENTDIST);
                    } else {
                        featureMapList.get(i).remove(Feature.LASTWORD);
                        featureMapList.get(i).remove(Feature.LASTWORDPOS);
                        if (argTokenSet.nextSetBit(0)<sampleInstance.getPredicateNode().getTokenIndex())
                            featureMapList.get(i).remove(Feature.CONSTITUENTDIST);
                    }   
                }   
            }
            
            if (buildDictionary || argLabelStringMap.containsKey(labels.get(i)))
                sampleList.add(new ArgSample(argNodes.get(i), sampleInstance.getPredicateNode(), labels.get(i), featureMapList.get(i)));
        }
        
        ArgSample[] argSamples = sampleList.toArray(new ArgSample[sampleList.size()]);
        Arrays.sort(argSamples, sampleComparator);
                
        if (buildDictionary) {
            List<SRArg> predictedList = new ArrayList<SRArg>();
            for (ArgSample argSample:argSamples)
            	if (!SRArg.NOT_ARG.equals(argSample.label))
            		predictedList.add(new SRArg(argSample.label, argSample.node));
            
            Map<String, List<String>> argTopicMap = makeArgTopicMap(argSamples);
            
            for (int i=0; i<featureMapList.size();++i) {
                boolean isNoArg = SRArg.NOT_ARG.equals(argSamples[i].label);
                featureMapList.get(i).putAll(extractFeatureSequence(isNominal?nominalArgLabelFeatures:argLabelFeatures, sampleInstance.predicateNode, sampleInstance.getRolesetId(), sampleList.get(i), sampleInstance, predictedList, argTopicMap, buildDictionary));
                for(Map.Entry<EnumSet<Feature>,Collection<String>> entry:argLabelFeatures.convertFlatSample(featureMapList.get(i)).entrySet())
                	if (isNominal)
                		nominalArgLabelFeatures.addToDictionary(entry.getKey(), entry.getValue(), isNoArg?noArgWeight:nominalWeight);
                	else
                		argLabelFeatures.addToDictionary(entry.getKey(), entry.getValue(), isNoArg?noArgWeight:1);
                
                //if (!NOT_ARG.equals(SRLUtil.getMaxLabel(labels.get(c))))
                //  System.out.println(sample.get(Feature.PATH));
                argLabelStringMap.put(argSamples[i].label, argLabelStringMap.get(argSamples[i].label)+1);
            }
            return null;
        } else {
            return new SRLSample(sampleInstance.getPredicateNode(), sampleInstance.rolesetId, sampleInstance.getTree(), supportSample, argSamples, !langUtil.isVerb(goldInstance.getPredicateNode().getPOS()), isNominal);
        }
    }

    public EnumMap<Feature,Collection<String>> extractFeatureArgument(FeatureSet<Feature> featureSet, TBNode predicateNode, TBNode argNode, String[] namedEntities, List<String> headwordSP) {
        EnumMap<Feature,Collection<String>> featureMap = new EnumMap<Feature,Collection<String>>(Feature.class);
        List<TBNode> tnodes = argNode.getTokenNodes();
        
        List<TBNode> argToTopNodes = argNode.getPathToRoot();
        List<TBNode> predToTopNodes = predicateNode.getPathToRoot();
        TBNode joinNode = trimPathNodes(argToTopNodes, predToTopNodes);
        List<String> path = getPath(argToTopNodes, predToTopNodes, joinNode);

        List<TBNode> argDepToTopNodes = new ArrayList<TBNode>();
        
        if (!argToTopNodes.isEmpty())
            argDepToTopNodes.add(argToTopNodes.get(0));
        else
            System.err.println("not good...");
        
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
        if (argNode.getPOS().matches("PP.*")) {
        	/*
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
                head = argNode.getChildren()[argNode.getChildren().length-1].getHead();*/
            for (TBNode child:argNode.getChildren())
            	if (child.getHead()!=head) {
            		head = child.getHead();
            		break;
            	}
        }
        
        boolean isBefore = tnodes.get(0).getTokenIndex() < predicateNode.getTokenIndex();
        //System.out.println(predicateNode+" "+predicateNode.tokenIndex+": "+argNode.getParent()+" "+argToTopNodes.size());

        int cstDst = countConstituents(argToTopNodes.get(0).getPOS(), isBefore?argToTopNodes:predToTopNodes, isBefore?predToTopNodes:argToTopNodes, joinNode);
        //System.out.println(cstDst+" "+path);
        
        for (Feature feature:featureSet.getFeaturesFlat()) {
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
            case COMPLEMENTIZERARG:
            	if ((predicateNode.getConstituentByHead().getPOS().equals("CP") && predicateNode.getHeadOfHead()==argNode.getHead() || 
						predicateNode.getHeadOfHead()!=null && predicateNode.getHeadOfHead().getConstituentByHead().getPOS().equals("CP")) &&
						predicateNode.getHeadOfHead().getHeadOfHead()==argNode.getHead())
            		featureMap.put(feature,Arrays.asList(Boolean.toString(true)));
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
            	featureMap.put(feature, Arrays.asList(head.getWord().toLowerCase()));
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
            case HEADWORDTOPICS:
            	if (argTopicMap!=null) {
            		List<String> topics = argTopicMap.get(Topics.getTopicHeadword(argNode, lemmatizeTopicWord?langUtil:null));
            		if (topics!=null)
            			featureMap.put(feature, topics);
            	}
            	break;
            case HEADWORDTOPICNUM:
            	if (argTopicMap!=null) {
            		List<String> topics = argTopicMap.get(Topics.getTopicHeadword(argNode, lemmatizeTopicWord?langUtil:null));
            		if (topics!=null) {
            			Set<String> topicNum = new HashSet<String>();
            			for (String topic:topics)
            				topicNum.add(topic.substring(topic.indexOf(':')+1));
            			//if (topics.size()!=topicNum.size())
            			//	logger.fine(Topics.getTopicHeadword(argNode)+": "+topics);
            			featureMap.put(feature, topicNum);
            		}
            	}
            	break;
            case HEADWORDSP:
            	if (headwordSP!=null) {
            		featureMap.put(feature, headwordSP);
            	}
            	break;
            case FIRSTWORD:
                featureMap.put(feature, Arrays.asList(tnodes.get(0).getWord().toLowerCase()));
                break;
            case FIRSTWORDPOS:
                featureMap.put(feature, trainGoldParse?tnodes.get(0).getFunctionTaggedPOS():Arrays.asList(tnodes.get(0).getPOS()));
                break;
            case LASTWORD:
                featureMap.put(feature, Arrays.asList(tnodes.get(tnodes.size()-1).getWord().toLowerCase()));
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
            	if (head.isToken() && namedEntities[head.getTokenIndex()]!=null)
            		featureMap.put(feature, Arrays.asList(namedEntities[head.getTokenIndex()]));
            	break;
            	/*
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
            }*/
            default:
                break;
            }
        }
        return featureMap;
    }
    
    List<List<String>> getSPLabels(TBNode predicateNode, String rolesetId, List<TBNode> argNodes, boolean training) {
	    String[] headwords = new String[argNodes.size()];
	    Set<String> headwordSet = new HashSet<String>();
	    for (int i=0; i<argNodes.size(); ++i) {
	    	headwords[i] = verbNetSP.getSPHeadword(argNodes.get(i));
	    	if (headwords[i]!=null)
	    		headwordSet.add(headwords[i]);
	    }
	    if (headwordSet.isEmpty())
	    	return null;
	    
	    String predKey = verbNetSP.getPredicateKey(predicateNode, rolesetId);
	    
    	List<String> uniqueHeadwords = new ArrayList<String>(headwordSet);
    	List<TObjectFloatMap<String>> spList = verbNetSP.getSP(predKey, uniqueHeadwords, training);
    	TObjectFloatMap<String> topVal = new TObjectFloatHashMap<String>();
    	Map<String, List<String>> spMap = new HashMap<String, List<String>>();
    	for (int i=0; i<spList.size(); ++i) {
			if (spList.get(i)==null)
				continue;
			String label = SRLSelPref.getHighLabel(spList.get(i));
			List<String> spLabels = new ArrayList<String>(2);
			spLabels.add(label);
			spMap.put(uniqueHeadwords.get(i), spLabels);
			if (!topVal.containsKey(label) ||
					topVal.get(label) < spList.get(i).get(label))
				topVal.put(label, spList.get(i).get(label));
		}
    	for (int i=0; i<spList.size(); ++i) {
    		List<String> labelList = spMap.get(uniqueHeadwords.get(i));
    		if (labelList==null) 
    			continue;

    		if (spList.get(i).get(labelList.get(0))!=topVal.get(labelList.get(0)) && labelList.get(0).matches("ARG\\d"))
    			spMap.remove(uniqueHeadwords.get(i));
    	}
    	
    	List<List<String>> retList = new ArrayList<List<String>>();
    	for (int i=0; i<headwords.length; ++i)
    		retList.add(spMap.get(headwords[i]));
    	
    	return retList;
    }
    
    List<EnumMap<Feature,Collection<String>>> extractFeatureSRL(FeatureSet<Feature> featureSet, TBNode predicateNode, String rolesetId, List<TBNode> argNodes, String frame, String[] depEC, String[] namedEntities, boolean training) {   
        List<EnumMap<Feature,Collection<String>>> featureMapList = new ArrayList<EnumMap<Feature,Collection<String>>>();
        
        EnumMap<Feature,Collection<String>> defaultMap = extractFeaturePredicate(featureSet.getFeaturesFlat(), predicateNode, frame, depEC);

        List<List<String>> spLabels = null;
        if (featureSet.getFeaturesFlat().contains(Feature.HEADWORDSP) && verbNetSP!=null)
        	spLabels = getSPLabels(predicateNode, rolesetId, argNodes, training);

        
        for (int i=0; i<argNodes.size(); ++i) {
            EnumMap<Feature,Collection<String>> featureMap = extractFeatureArgument(featureSet, predicateNode, argNodes.get(i), namedEntities, spLabels==null?null:spLabels.get(i));
            featureMap.putAll(defaultMap);
            featureMapList.add(featureMap);
        }
        return featureMapList;
    }
    
    EnumMap<Feature,Collection<String>> extractFeatureSequence(FeatureSet<Feature> featureSet, TBNode predicate, String rolesetId, ArgSample sample, SRInstance support, List<SRArg> predictedArgs, Map<String, List<String>> topicMap, boolean buildDictionary) {
        EnumMap<Feature,Collection<String>> featureMap = new EnumMap<Feature,Collection<String>>(Feature.class);
        
        boolean hasExplicitSupport = false;
        for (SRArg arg:predictedArgs)
        	if (langUtil.isExplicitSupport(arg.label)) {
        		hasExplicitSupport = true;
        		break;
        	}
        
        for (Feature feature:featureSet.getFeaturesFlat()) {
        switch (feature) {
            case SUPPORT:
                if (support!=null) {
                    String lemma = langUtil.findStems(support.getPredicateNode()).get(0);
                    String position = support.getPredicateNode().getTokenIndex()<predicate.getTokenIndex()?"left":"right";
                    String level = support.getPredicateNode().getLevelToRoot()<predicate.getLevelToRoot()?"above":"sibling";
                    featureMap.put(feature, Arrays.asList(lemma, position, level, Boolean.toString(hasExplicitSupport),
                    		lemma+" "+Boolean.toString(hasExplicitSupport), lemma+" "+level, support.getPredicateNode().getWord()));
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
                        if (arg.getLabel().equals(SRArg.NOT_ARG)) continue;
                        if (arg.node == sample.node || sample.node.isDecendentOf(arg.node)) {
                            sArg=arg;
                            break;
                        }                           
                    }
                    if (sArg!=null) {
                    	if (sample.node==sArg.node) {
                    		if (hasExplicitSupport)
                    			featureMap.put(feature, Arrays.asList(sArg.getLabel(),"sup-"+sArg.getLabel()));
                    		else 
                    			featureMap.put(feature, Arrays.asList(sArg.getLabel()));
                    	} else 
                    		featureMap.put(feature, Arrays.asList("nest-"+sArg.getLabel()));
                    }
                }
                break;
            case ARGLISTDIRECTIONAL:
                // list of all found args in the same direction
                if (!predictedArgs.isEmpty()) {
                    List<String> labels = new ArrayList<String>(predictedArgs.size());
                    boolean left = sample.node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex();
                    for (SRArg predictedArg:predictedArgs)
                        if (sample.node!= predictedArg.node && left == predictedArg.node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex())
                            labels.add(predictedArg.label);
                    if (!labels.isEmpty())
                        featureMap.put(feature, labels);
                }
                break;
            case ARGLISTALL:
                // list of all found args
                if (!predictedArgs.isEmpty()) {
                    List<String> labels = new ArrayList<String>(predictedArgs.size());
                    for (SRArg predictedArg:predictedArgs)
                    	if (sample.node!= predictedArg.node)
                    		labels.add(predictedArg.label);
                    featureMap.put(feature, labels);
                }
                break;
            case ARGROLEMISS:
        		Roleset role = langUtil.getRoleSet(predicate, rolesetId);
        	    if (role!=null) {
        	    	Set<String> roles = new HashSet<String>(role.getRoles());
        	    	for (SRArg predictedArg:predictedArgs)
                    	if (sample.node!= predictedArg.node)
                    		roles.remove(predictedArg.label.toLowerCase());
        	    	if (!roles.isEmpty())
        	    		featureMap.put(feature, roles);
        	    }
            	break;
            case ARGTOPICTYPE:
            	if (topicMap!=null) {
            		List<String> topics = topicMap.get(Topics.getTopicHeadword(sample.node, lemmatizeTopicWord?langUtil:null));
            		if (topics!=null)
            			featureMap.put(feature, topics);
            	}
            	
            	/*
            	if (argTopicMap!=null && !predictedArgs.isEmpty())
	            	for (SRArg predictedArg:predictedArgs)
	            		if (sample.node==predictedArg.node) {
	            			List<String> topics = argTopicMap.get(Topics.getTopicHeadword(predictedArg.node));
	                    	if (topics==null) break;
	                    	List<String> filteredTopics = new ArrayList<String>();
	                    	for (String topic:topics)
	                    		if (topic.startsWith(predictedArg.label))
	                    			filteredTopics.add(topic);
	                    	if (!filteredTopics.isEmpty())
	                        	featureMap.put(feature, filteredTopics);
	            			break;	
	            		}
	            		*/
            	break;
            case ARGREL: 
            	if (!predictedArgs.isEmpty() && argPrimaryLabelSet!=null ) {
            		List<String> fVals = new ArrayList<String>();
            		for (SRArg predictedArg:predictedArgs) {
            			if (sample.node==predictedArg.node || !argPrimaryLabelSet.contains(predictedArg.label) 
            					|| sample.node.isDecendentOf(predictedArg.node) || predictedArg.node.isDecendentOf(sample.node))
            				continue;
            			
            			boolean isLeft=sample.node.getTerminalSet().nextSetBit(0)<predictedArg.node.getTerminalSet().nextSetBit(0);
            			
            			if (sample.node.getParent()==predictedArg.node.getParent())
            				fVals.add(predictedArg.label+Boolean.toString(isLeft)+"==sibling ");
            			fVals.add(predictedArg.label+Boolean.toString(isLeft));
            			fVals.add(predictedArg.label+Boolean.toString(isLeft)+' '+predictedArg.node.getHeadword());
            		}
            		if (!fVals.isEmpty())
            			featureMap.put(feature, fVals);
            	}
            	break;
            case ARGTYPE:
                /*if (!predictedArgs.isEmpty() && sample.node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex()==
                        predictedArgs.get(predictedArgs.size()-1).node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex())
                    featureMap.put(feature, Arrays.asList(predictedArgs.get(predictedArgs.size()-1).label));*/
            	if (!predictedArgs.isEmpty()) {
            		String label = null;
            		List<String> fVals = new ArrayList<String>();
	            	for (SRArg predictedArg:predictedArgs)
	            		if (sample.node==predictedArg.node) {
	            			label = predictedArg.label;
	            			/*
	            			if (argLabelFeatures.getFeaturesFlat().contains(Feature.HEADWORDTOPICS)) {
		            			List<String> topics = argTopicMap.get(Topics.getTopicHeadword(sample.node));
		                		if (topics!=null) {
		                			for (String topic:topics)
		                				if (topic.indexOf(label)>=0)
		                					fVals.add(topic);
		                		}
	            			}*/
	            			break;
	            		}
	            	
	            	if (label!=null && !label.equals(SRArg.NOT_ARG)) {
	            		boolean unique = true;
	            		for (SRArg predictedArg:predictedArgs)
		            		if (sample.node!=predictedArg.node&&predictedArg.label.equals(label)) {
		            			unique = false;
		            			break;
		            		}
	            		fVals.add(label);
	            		fVals.add(Boolean.toString(unique));
	            		fVals.add(Boolean.toString(unique)+' '+label);
	            		featureMap.put(feature, fVals);		
	            	}
            	}
                break;
            default:
                break;
            }
        }
        
        return featureMap;
    }

    EnumMap<Feature,Collection<String>> extractFeaturePredicate(EnumSet<Feature> featureSet, TBNode predicateNode, String rolesetId, String[] depEC) {
        EnumMap<Feature,Collection<String>> sampleFlat = new EnumMap<Feature,Collection<String>>(Feature.class);
        
        // find predicate lemma
        String predicateLemma = predicateNode.getWord();
        {
            List<String> stems = langUtil.findStems(predicateNode);
            if (!stems.isEmpty()) predicateLemma = stems.get(0);
        }
        String predicateType = predicateNode.getPOS().substring(0,1);

        String relatedVerb = null;
        if ((langUtil instanceof EnglishUtil) && !langUtil.isVerb(predicateNode.getPOS()))
            relatedVerb = ((EnglishUtil)langUtil).findDerivedVerb(predicateNode);

        List<String> predicateAlternatives = langUtil.getPredicateAlternatives(predicateLemma, argLabelFeatures.getFeatureStrMap()==null?null:argLabelFeatures.getFeatureStrMap().get(EnumSet.of(Feature.PREDICATE)));

        TBNode head = predicateNode.getHeadOfHead();
        TBNode parent = predicateNode.getParent();
        TBNode leftNode = predicateNode.getTokenIndex()>0? predicateNode.getRoot().getNodeByTokenIndex(predicateNode.getTokenIndex()-1):null;
        TBNode rightSibling = (predicateNode.getParent()!=null&&predicateNode.getChildIndex()<predicateNode.getParent().getChildren().length-1)?
                predicateNode.getParent().getChildren()[predicateNode.getChildIndex()+1]:null;
        TBNode rightHeadnode = (rightSibling==null||rightSibling.getHead()==null)?null:rightSibling.getHead();

        for (Feature feature:featureSet) {
            switch (feature) {
            case PREDICATE:
                if (relatedVerb!=null)
                    sampleFlat.put(feature, Arrays.asList(relatedVerb, predicateLemma+'-'+predicateType));
                else
                	sampleFlat.put(feature, predicateAlternatives);
                break;
            case PREDICATETYPE:
            	sampleFlat.put(feature, Arrays.asList(predicateType));
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
            case VOICE:
                if (langUtil.isVerb(predicateNode.getPOS()))
                    sampleFlat.put(feature, langUtil.getConstructionTypes(predicateNode));
                break;
            case SUBCATEGORIZATION: 
                if (parent!=null) {
                    StringBuilder builder = new StringBuilder();
                    builder.append(parent.getPOS()+RIGHT_ARROW);
                    for (TBNode node:parent.getChildren())
                        builder.append(node.getPOS()+"-");
                    sampleFlat.put(feature, Arrays.asList(builder.toString()));
                }
                break;
            case ROLESET:
            	if (rolesetId!=null) {
            		Roleset role = langUtil.getRoleSet(predicateNode, rolesetId);
            		// make a copy of the roles as it may not be serializable otherwise
            	    if (role!=null)
            	    	sampleFlat.put(feature, new ArrayList<String>(role.getRoles()));
            	}
            	break;
            case ROLESETCLASS:
            	if (rolesetId!=null) {
            		Roleset role = langUtil.getRoleSet(predicateNode, rolesetId);
            	    if (role!=null && !role.getClasses().isEmpty())
            	    	sampleFlat.put(feature, role.getClasses());
            	}
            case ECSET:
            	//TODO
            	if (depEC!=null) {
            		List<String> ecList = new ArrayList<String>();
            		for (int i=0; i<depEC.length; ++i) {
            			if (depEC[i]==null) continue;
            			for (String subLabel:depEC[i].trim().split("\\s+")) {
            				if (ECCommon.NOT_EC.equals(subLabel)) continue;
            				ecList.add(subLabel);
            				ecList.add((i<=predicateNode.getTokenIndex()?"l-":"r-")+subLabel);
            			}
            		}
            		if (!ecList.isEmpty())
            			sampleFlat.put(feature, ecList);
            	}
            	break;
            default:
                break;
            }
        }        
        return sampleFlat;
    }
    
    static void makeProb(double[] prob_estimates) {
    	
        if (prob_estimates.length == 2) {
        	// for binary classification
        	if (prob_estimates[0]==0) {
        		prob_estimates[1] = 1 / (1 + Math.exp(-prob_estimates[1]));
        		prob_estimates[0] = 1. - prob_estimates[1];
        	} else {
        		prob_estimates[0] = 1 / (1 + Math.exp(-prob_estimates[0]));
        		prob_estimates[1] = 1. - prob_estimates[0];
        	}
        } else {
        	for (int i = 0; i < prob_estimates.length; i++)
                prob_estimates[i] = 1 / (1 + Math.exp(-prob_estimates[i]));
        	
            double sum = 0;
            for (int i = 0; i < prob_estimates.length; i++)
                sum += prob_estimates[i];

            for (int i = 0; i < prob_estimates.length; i++)
                prob_estimates[i] = prob_estimates[i] / sum;
        }
    }
    
    double computeThreshold(BitSet mask, boolean setMask, String[] goldLabels, String[] labels, double[][] labelValues, double threshold) {
    	if (threshold>=1) return 0;
    	TDoubleArrayList rList = new TDoubleArrayList();
    	int rTotal = 0;
        for (int i=0; i<goldLabels.length; ++i) {
            if (setMask^mask.get(i)) continue;
            makeProb(labelValues[i]);

            if (SRArg.NOT_ARG.equals(goldLabels[i])) continue;
            
            if (SRArg.NOT_ARG.equals(labels[i]))
            	rList.add(labelValues[i][argLabelStringMap.get(SRArg.NOT_ARG)-1]);
            else // !NOT_ARG.equals(newLabels[i])
            	rTotal++;
        }
        
        double[] rVal = rList.toArray();
        Arrays.sort(rVal);
        
        double recall = 1.0*rTotal/(rVal.length+rTotal);
        logger.info(String.format("IS_ARG recall %f %d/%d\n", recall, rTotal, rVal.length+rTotal));
        if (recall<threshold)
        	return rVal[(int)Math.floor(rVal.length-(1-threshold)*(rVal.length+rTotal))];
        return 1;
    }
    
    void printScore(String[] sysLabels, String[] goldLabels, BitSet nominalMask, BitSet stage2Mask) {
    	SRLScore score = new SRLScore(argLabelStringMap.keySet());
    	SRLScore nomScore = nominalMask.isEmpty()?null:new SRLScore(argLabelStringMap.keySet());
    	SRLScore verbScore = nominalMask.isEmpty()?null:new SRLScore(argLabelStringMap.keySet());
    	
    	int cnt=0;
    	for (int i=0; i<goldLabels.length; ++i)
    		if (stage2Mask!=null && !stage2Mask.get(i)) {
    			score.addResult(SRArg.NOT_ARG, goldLabels[i]);
    			if (nomScore==null) continue;
    			if (nominalMask.get(i))
        			nomScore.addResult(SRArg.NOT_ARG, goldLabels[i]);
        		else
        			verbScore.addResult(SRArg.NOT_ARG, goldLabels[i]);
    		} else {
    			score.addResult(sysLabels[cnt], goldLabels[i]);
    			if (nomScore!=null) {
	    			if (nominalMask.get(i))
	        			nomScore.addResult(sysLabels[cnt], goldLabels[i]);
	        		else
	        			verbScore.addResult(sysLabels[cnt], goldLabels[i]);
    			}
    			++cnt;	
    		}
        System.out.println("Overall:");
        System.out.println(score.toString());
        if (nomScore!=null) {
        	 System.out.println("Verb:");
             System.out.println(verbScore.toString());
             System.out.println("Nominal:");
             System.out.println(nomScore.toString());
        }
    }
    
    public void train(Properties prop) throws IOException {
    	
    	// finalize dictionary & close the training sample cache stream
    	if (trainingSampleOutStream!=null) {
    		trainingSampleOutStream.close();
	    	try {
	            trainingSampleOutStream.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    	trainingSampleOutStream = null;
    	}
    	
    	int cutoff = Integer.parseInt(prop.getProperty("dictionary.cutoff", "5"));
    	
        makeTrainingPredicates(cutoff);

        // train predicate classifier
        logger.info(String.format("Training predicates"));
        predicateModel.train(prop);
        
        // train roleset classifiers
        rolesetValidatedLabelMap = new HashMap<String, String[]>();
        for (Map.Entry<String, SimpleModel<Feature>> entry:rolesetModelMap.entrySet()) {
            logger.info(String.format("Training %s\n", entry.getKey()));
            rolesetValidatedLabelMap.put(entry.getKey(),entry.getValue().train(prop, true));
        }
        
        makeTrainingArguments(cutoff);
        
        boolean finalCrossValidation = !prop.getProperty("crossvalidation.final","false").equals("false");
        int folds = Integer.parseInt(prop.getProperty("crossvalidation.folds","5"));
        int threads = Integer.parseInt(prop.getProperty("threads","1"));
        
        double stage2Threshold = 1.0;
        
        boolean hasSequenceFeature = Feature.hasSequenceFeature(argLabelFeatures.getFeaturesFlat());
        boolean hasStage2Feature = Feature.hasStage2Feature(argLabelFeatures.getFeaturesFlat());
        
        // initialize arg label classifier
        try {
        	Properties stage1Prop = PropertyUtil.filterProperties(prop, "stage1.",true);
            argLabelClassifier = (Classifier)Class.forName(stage1Prop.getProperty("classifier", LinearClassifier.class.getName())).newInstance();
            argLabelClassifier.setDimension(argLabelFeatures.getDimension());
            argLabelClassifier.initialize(argLabelStringMap, stage1Prop);
            
            if (argLabelFeatures!=nominalArgLabelFeatures) {
            	Properties stage1NomProp = PropertyUtil.filterProperties(PropertyUtil.filterProperties(prop, "nominal.",true), "stage1.",true);
            	nominalArgLabelClassifier=(Classifier)Class.forName(stage1NomProp.getProperty("classifier", LinearClassifier.class.getName())).newInstance();
            	nominalArgLabelClassifier.setDimension(nominalArgLabelFeatures.getDimension());
            	nominalArgLabelClassifier.initialize(argLabelStringMap, stage1NomProp);
            } else
            	nominalArgLabelClassifier = argLabelClassifier;
            
            if (hasStage2Feature) {
            	Properties stage2Prop = PropertyUtil.filterProperties(prop, "stage2.",true);
            	stage2Threshold = Double.parseDouble(stage2Prop.getProperty("threshold","0.98"));
            	argLabelStage2Classifier = (Classifier)Class.forName(stage2Prop.getProperty("classifier", LinearClassifier.class.getName())).newInstance();
            	argLabelStage2Classifier.setDimension(argLabelFeatures.getDimension());
            	argLabelStage2Classifier.initialize(argLabelStringMap, stage2Prop);
            	
            	if (argLabelFeatures!=nominalArgLabelFeatures) {
            		Properties stage2NomProp = PropertyUtil.filterProperties(PropertyUtil.filterProperties(prop, "nominal.",true), "stage2.",true);
                	nominalArgLabelStage2Classifier=(Classifier)Class.forName(stage2NomProp.getProperty("classifier", LinearClassifier.class.getName())).newInstance();
                	nominalArgLabelStage2Classifier.setDimension(nominalArgLabelFeatures.getDimension());
                	nominalArgLabelStage2Classifier.initialize(argLabelStringMap, stage2NomProp);
                } else
                	nominalArgLabelStage2Classifier = argLabelStage2Classifier;
            }
            
        } catch (InstantiationException e2) {
            e2.printStackTrace();
            return;
        } catch (IllegalAccessException e2) {
            e2.printStackTrace();
            return;
        } catch (ClassNotFoundException e2) {
            e2.printStackTrace();
            return;
        }
        
        int maxIter = Integer.parseInt(prop.getProperty("sequence.iterations","3"));
        //if (hasStage2Feature)
        //	rounds = hasSequenceFeature?(int)(Math.ceil(rounds/2.0)):1;
        //else 
        if (!hasSequenceFeature)
        	maxIter = hasStage2Feature?1:0;

        double threshold = Double.parseDouble(prop.getProperty("sequence.threshold", "0.001"));
        
        String[] goldLabels = null;
        int[] y = null;
        BitSet goldNominalMask = new BitSet();
        BitSet trainNominalMask = new BitSet();
        {
        	List<String> labelList = new ArrayList<String>();
        	readTrainingArguments(extractedSampleFile, null, null, null, null, null, null, labelList, goldNominalMask, trainNominalMask, null, true, true);
        	goldLabels = labelList.toArray(new String[labelList.size()]);
        	y = new int[goldLabels.length];
        	for (int i=0; i<goldLabels.length; ++i)
        		y[i] = argLabelStringMap.get(goldLabels[i]);
        }
        
        boolean trainFinalClassifier = true;
        BitSet stage2Mask = null;
        String[] labels = Arrays.copyOf(goldLabels, goldLabels.length);
        double[][] labelValues = null;
        for (int iterCount=0; iterCount<maxIter; ++iterCount) {
        	//if entering the final round, train the final classifier as well
        	if (iterCount+1==maxIter)
        		trainFinalClassifier = false;
        	double[][] newLabelValues = new double[labels.length][argLabelStringMap.size()];
            String[] newLabels = trainArguments(argLabelClassifier, nominalArgLabelClassifier, iterCount==0?null:labels, labelValues, null, folds, !trainFinalClassifier, threads, newLabelValues, y, iterCount>0, false);
            if (hasStage2Feature) {
	            if (argLabelClassifier==nominalArgLabelClassifier) {
	            	argLabelStage2Threshold = computeThreshold(new BitSet(), false, goldLabels, newLabels, newLabelValues, stage2Threshold);
	            	nominalArgLabelStage2Threshold = argLabelStage2Threshold;
	            } else {
		            argLabelStage2Threshold = computeThreshold(trainNominalMask, false, goldLabels, newLabels, newLabelValues, stage2Threshold);
		            nominalArgLabelStage2Threshold = computeThreshold(trainNominalMask, true, goldLabels, newLabels, newLabelValues, stage2Threshold);
	            }
	            stage2Mask = new BitSet();
	            for (int i=0; i<newLabels.length; ++i)
	            	if (!SRArg.NOT_ARG.equals(newLabels[i]) || 
	            			!trainNominalMask.get(i) && (argLabelStage2Threshold==0 || newLabelValues[i][argLabelStringMap.get(SRArg.NOT_ARG)-1]<=argLabelStage2Threshold) ||
	            					trainNominalMask.get(i) && (nominalArgLabelStage2Threshold==0 || newLabelValues[i][argLabelStringMap.get(SRArg.NOT_ARG)-1]<=nominalArgLabelStage2Threshold))
	            		stage2Mask.set(i);
	            
	            TDoubleArrayList pList = new TDoubleArrayList();
	            for (int i=0; i<labels.length; ++i)
	                if (SRArg.NOT_ARG.equals(goldLabels[i]))
	                	pList.add(newLabelValues[i][argLabelStringMap.get(SRArg.NOT_ARG)-1]);
	            double[] pVal = pList.toArray();
	            Arrays.sort(pVal);

	            logger.info(String.format("Threshold: %f, verb NO_ARG value: %f, nominal NO_ARG value: %f, %d/%d filtered, %d training arguments", 
	            		stage2Threshold, argLabelStage2Threshold, nominalArgLabelStage2Threshold, pVal.length-Math.abs(Arrays.binarySearch(pVal, argLabelStage2Threshold)), pVal.length, stage2Mask.cardinality()));	            
            }
            
            int cnt=0;
            for (int i=0; i<labels.length; ++i)
                if (labels[i].equals(newLabels[i])) ++cnt;    
            double agreement = cnt*1.0/labels.length;
            System.out.printf("Iteration %d stage 1: %f\n", iterCount, agreement);
            printScore(newLabels, goldLabels, goldNominalMask, null);
            
            labels = newLabels;
            labelValues = newLabelValues;
            
            if (1-agreement<=threshold)
                break;
        }
        
        if (trainFinalClassifier) {
	        if (hasStage2Feature||!finalCrossValidation) {
	        	// reset the threads allowed depending on whether cross-validation is required
		    	//Properties stage1Prop = PropertyUtil.filterProperties(prop, "stage1.",true);
		    	//int cvThreads = Integer.parseInt(stage1Prop.getProperty("crossvalidation.threads","1"));
		        //int pwThreads = Integer.parseInt(stage1Prop.getProperty("pairwise.threads","1"));
		        //stage1Prop.setProperty("pairwise.threads", Integer.toString(cvThreads*pwThreads));
		        //argLabelClassifier.initialize(argLabelStringMap, stage1Prop);
	        }
	        String[] predictions = trainArguments(argLabelClassifier, nominalArgLabelClassifier, maxIter>0?labels:null, labelValues, null, hasStage2Feature||!finalCrossValidation?1:folds, true, threads, null, y, true, false);
	        System.out.println("stage 1 training"+(hasStage2Feature||!finalCrossValidation?"":"(cross validated)")+":");
	        printScore(predictions, goldLabels, goldNominalMask, null);
        }
	        
        if (hasStage2Feature) {
        	if (!finalCrossValidation) {
        		// reset the threads allowed depending on whether cross-validation is required
	        	//Properties stage2Prop = PropertyUtil.filterProperties(prop, "stage2.",true);
	        	//int cvThreads = Integer.parseInt(stage2Prop.getProperty("crossvalidation.threads","1"));
	            //int pwThreads = Integer.parseInt(stage2Prop.getProperty("pairwise.threads","1"));
	            //stage2Prop.setProperty("pairwise.threads", Integer.toString(cvThreads*pwThreads));
	            //argLabelStage2Classifier.initialize(argLabelStringMap, stage2Prop);
	            if (argLabelStage2Classifier!=nominalArgLabelStage2Classifier) {
	            	Properties stage2NomProp = PropertyUtil.filterProperties(PropertyUtil.filterProperties(prop, "nominal.",true), "stage2.",true);
	            	nominalArgLabelStage2Classifier.initialize(argLabelStringMap, stage2NomProp);
	            }
        	}
        	String[] predictions = trainArguments(argLabelStage2Classifier, nominalArgLabelStage2Classifier, labels, labelValues, stage2Mask, finalCrossValidation?folds:1, true, threads, null, y, true, true);
        	System.out.println("stage 2 training"+(finalCrossValidation?"(cross validated)":"")+":");
            printScore(predictions, goldLabels, goldNominalMask, stage2Mask);
        }
    }

    
    /**
     * 
     * @param useSequence whether to use sequence features (may not be a good idea at the start)
     * @param folds folds of x-validation
     * @param threads number of threads used for x-validation
     * @param labels set of predicted labels from last round of training
     * @return new set of labels
     */
    String[] trainArguments(Classifier verbClassifier, Classifier nounClassifier, String[] predictedLabels, double[][] predictedLabelProb, BitSet stage2Mask, int folds, boolean trainAll, int threads, double[][] values, int[] y, boolean useSequence, boolean usePrediction) {             
        Object[] X = null;
        int[] seeds = null;
        BitSet nominalMask = new BitSet();
        {
	        List<Object> xList = new ArrayList<Object>();
	        TIntArrayList seedList = new TIntArrayList();
	        List<String> stage2GoldLabels = stage2Mask==null?null:new ArrayList<String>();
	
	        readTrainingArguments(extractedSampleFile, predictedLabels, predictedLabelProb, stage2Mask, verbClassifier, nounClassifier, xList, stage2GoldLabels, null, nominalMask, folds>1?seedList:null, useSequence, usePrediction);
	        
	        X = xList.toArray();
	
	        if (stage2GoldLabels!=null) {
	        	y= new int[stage2GoldLabels.size()];
	        	for (int i=0; i<y.length; ++i)
	        		y[i] = argLabelStringMap.get(stage2GoldLabels.get(i));
	        }
	        if (!seedList.isEmpty())
	        	seeds = seedList.toArray();
        }
        
        if (verbClassifier==nounClassifier)
        	return trainClassifier(verbClassifier, X, y, values, seeds, folds, trainAll, threads);
        
        int nCount = nominalMask.cardinality();
        int vCount = y.length-nCount;
        
        Object[] vX = new Object[vCount];
        Object[] nX = new Object[nCount];
        int[] vy = new int[vCount];
        int[] ny = new int[nCount];
        double[][] vValues = values==null?null:new double[vCount][];
        double[][] nValues = values==null?null:new double[nCount][];
        int[] vSeeds = seeds==null?null:new int[vCount];
        int[] nSeeds = seeds==null?null:new int[nCount];
        
        vCount = 0;
        nCount = 0;
        for (int i=0; i<y.length; ++i) {
        	if (nominalMask.get(i)) {
        		nX[nCount] = X[i];
        		ny[nCount] = y[i];
        		if (nValues!=null) nValues[nCount] = values[i];
        		if (nSeeds!=null) nSeeds[nCount] = seeds[i];
        		++nCount;
        	} else {
        		vX[vCount] = X[i];
        		vy[vCount] = y[i];
        		if (vValues!=null) vValues[vCount] = values[i];
        		if (vSeeds!=null) vSeeds[vCount] = seeds[i];
        		++vCount;
        	}
        }
        
        String[] vLabels = trainClassifier(verbClassifier, vX, vy, vValues, vSeeds, folds, trainAll, threads);
        String[] nLabels = trainClassifier(nounClassifier, nX, ny, nValues, nSeeds, folds, trainAll, threads);
        
        String[] newLabels = new String[y.length];
        
        vCount = 0;
        nCount = 0;
        for (int i=0; i<y.length; ++i) {
        	if (nominalMask.get(i)) {
        		newLabels[i] = nLabels[nCount];
        		if (values!=null) values[i] = nValues[nCount];
        		++nCount;
        	} else {
        		newLabels[i] = vLabels[vCount];
        		if (values!=null) values[i] = vValues[vCount];
        		++vCount;
        	}
        }
        return newLabels;
    }
    
    
    String[] trainClassifier(Classifier classifier, Object[] X, int[] y, double[][] values, int[] seeds, int folds, boolean trainAll, int threads) {
    	String[] newLabels = new String[y.length];
        if (folds>1) {
            CrossValidator validator = new CrossValidator(classifier, threads/classifier.setThreads(threads));
            int[] yV =  validator.validate(folds, X, y, values, seeds, trainAll);
            for (int i=0; i<yV.length; ++i)
                newLabels[i] = argLabelIndexMap.get(yV[i]);
        } else {
        	classifier.setThreads(threads);
        	classifier.trainNative(X, y);
 	        for (int i=0; i<y.length; ++i)
 	        	newLabels[i] = argLabelIndexMap.get(classifier.predictNative(X[i]));
        }
        System.gc();
        return newLabels;
    	
    }

    void readTrainingArguments(File sampleFile, String[] predictedLabels, double[][] predictedLabelProb, BitSet stage2Mask, 
    		Classifier verbClassifier, Classifier nounClassifier, 
    		List<Object> xList, List<String> labelList, BitSet goldNominalMask, BitSet trainNominalMask, TIntArrayList seedList, boolean useSequence, boolean usePrediction) {
    	
    	if (xList!=null) xList.clear();
    	if (labelList!=null) labelList.clear();
    	if (goldNominalMask!=null) goldNominalMask.clear();
    	if (trainNominalMask!=null) trainNominalMask.clear();
    	if (seedList!=null) seedList.clear();
    	
    	try (ObjectInputStream cachedInStream = 
    			new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(sampleFile),GZIP_BUFFER),GZIP_BUFFER*4))) {
            int predictedCnt=0;
            int stage2Cnt=0;
            int sampleCount=0;
            Set<String> treeNameSet = new HashSet<String>();
            for (;;) {
                TBTree tree = (TBTree)cachedInStream.readObject();
                treeNameSet.add(tree.getFilename());
                SRLSample[] samples = (SRLSample[]) cachedInStream.readObject();
                // relabel everything first
                if (labelList!=null || goldNominalMask!=null || trainNominalMask!=null) {
                	int stage2LocalCnt = stage2Cnt;
                	for (SRLSample srlSample:samples) {
               		 	for (ArgSample argSample:srlSample.args)
               		 		if (stage2Mask==null || stage2Mask.get(stage2LocalCnt++)) {
               		 			if (goldNominalMask!=null && srlSample.isGoldNominal)
               		 				goldNominalMask.set(sampleCount);
	               		 		if (trainNominalMask!=null && useGoldPredicateSeparation?srlSample.isGoldNominal:srlSample.isTrainNominal)
	           		 				trainNominalMask.set(sampleCount);
               		 			sampleCount++;
               		 			if (labelList!=null)
               		 				labelList.add(argSample.label);
               		 		}
                	}
                }
                if (predictedLabels!=null)
                	 for (SRLSample srlSample:samples) 
                		 for (ArgSample argSample:srlSample.args) {
                			 if (predictedLabelProb!=null)
                				 argSample.labelProb = predictedLabelProb[predictedCnt];
                			 argSample.label = predictedLabels[predictedCnt++];
                		 }
                
                for (SRLSample srlSample:samples) {
                    Map<String, List<String>> argTopicMap = null;
                    List<SRArg> predictedArgs = new ArrayList<SRArg>();
                    if (stage2Mask!=null && xList!=null && usePrediction) {
	                    for (ArgSample argSample:srlSample.args)
	                    	if (!argSample.label.equals(SRArg.NOT_ARG))
	                    		predictedArgs.add(new SRArg(argSample.label, argSample.node));
	                    	argTopicMap = makeArgTopicMap(srlSample.args);
                    }
                    for (ArgSample argSample:srlSample.args) {
                    	// only train arguments needed for stage2
                    	if (stage2Mask!=null && !stage2Mask.get(stage2Cnt++))
                    		continue;
                    	if (xList!=null) {
	                        SRInstance support = null;
	                        if (srlSample.support!=null) {
	                            support = new SRInstance(srlSample.support.predicate, srlSample.support.tree, null);
	                            for (ArgSample supportArg:srlSample.support.args)
	                                if (!supportArg.label.equals(SRArg.NOT_ARG))
	                                    support.addArg(new SRArg(supportArg.label, supportArg.node));
	                        }
	                        if (srlSample.isTrainNominal)
	                        	xList.add(nounClassifier.getNativeFormat(getFeatureVector(nominalArgLabelFeatures, srlSample.predicate, srlSample.roleset, argSample, useSequence?support:null, predictedArgs, argTopicMap)));
	                        else
	                        	xList.add(verbClassifier.getNativeFormat(getFeatureVector(argLabelFeatures, srlSample.predicate, srlSample.roleset, argSample, useSequence?support:null, predictedArgs, argTopicMap)));
                    	}
                        
                        if (seedList!=null)
                        	seedList.add(treeNameSet.size()-1);
                    }
                }
            }
        } catch (Exception e) {
            if (!(e instanceof EOFException))
                e.printStackTrace();
        }
    	System.gc();
    }

    /*
    SRInstance makeSRInstance(SRLSample sample) {
        SRInstance instance = new SRInstance(sample.predicate, sample.tree, argLabelStringMap);
        for (ArgSample argSample:sample.args)
            if (!argSample.label.equals(SRArg.NOT_ARG))
                instance.addArg(new SRArg(argSample.label, argSample.node));
        return instance;
    }*/
    
    int[] getFeatureVector(FeatureSet<Feature> featureSet, TBNode predicate, String rolesetId, ArgSample sample, SRInstance support, List<SRArg> predictedArgs, Map<String, List<String>> topicMap) {
        EnumMap<Feature,Collection<String>> sampleFeatures = extractFeatureSequence(featureSet, predicate, rolesetId, sample, support, predictedArgs, topicMap, false);
        sampleFeatures.putAll(sample.features);
        return featureSet.getFeatureVector(sampleFeatures);
    }
 
    String predictRoleSet(TBNode node, EnumMap<Feature,Collection<String>> features) {
        String key = langUtil.makePBFrameKey(node);
        PBFrame frame = langUtil.getFrame(key);
        if (frame==null)
            return langUtil.findStems(node).get(0)+".XX";
        SimpleModel<Feature> rolesetModel = rolesetModelMap.get(key);
        if (frame.getRolesets().size()==1 || rolesetModel==null)
            return frame.getRolesets().firstKey();
        return rolesetModel.predictLabel(features);
        /*Classifier classifier = rolesetClassifiers.get(key);
        if (frame.getRolesets().size()==1 || classifier==null)
            return frame.getRolesets().keySet().iterator().next();
        return rolesetLabelIndexMap.get(key).get(classifier.predict(rolesetFeatureMap.get(key).getFeatureVector(features)));*/
        //return langUtil.findStems(node).get(0)+".XX";
    }
    
    public List<SRInstance> predict(TBTree parseTree, List<PBInstance> propPB, BitSet predicates, String[][] depEC, String[] namedEntities) {
    	return predict(parseTree, propPB, predicates, depEC, namedEntities, false);
    }
    
    public List<SRInstance> predict(TBTree parseTree, List<PBInstance> propPB, BitSet predicates, String[][] depEC, String[] namedEntities, boolean includeAllArgs) {
    	
    	List<SRInstance> goldSRLs = propPB==null?null:SRLUtil.convertToSRInstance(propPB);
    	    	
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

        if (goldSRLs!=null) {
            for (SRInstance goldSRL:goldSRLs) {
            	if (!trainNominal && !langUtil.isVerb(goldSRL.getPredicateNode().getPOS())) continue;
                TBNode node = parseTree.getNodeByTokenIndex(goldSRL.getPredicateNode().getTokenIndex());
                predictions.add(new SRInstance(node, parseTree, argLabelStringMap, predictRoleSet(node, extractFeaturePredicate(predicateModel.getFeaturesFlat(), node, null, depEC==null?null:depEC[node.getTokenIndex()])), 1.0));
            }
        } else if (predicates!=null) {
        	System.err.println(parseTree.getFilename()+" "+parseTree.getIndex()+" "+predicates);
        	System.err.flush();
        	
        	try {
        	for (int i=predicates.nextSetBit(0); i>=0; i=predicates.nextSetBit(i+1)) {
        		TBNode node = parseTree.getNodeByTokenIndex(i);
        		predictions.add(new SRInstance(node, parseTree, argLabelStringMap, predictRoleSet(node, extractFeaturePredicate(predicateModel.getFeaturesFlat(), node, null, depEC==null?null:depEC[node.getTokenIndex()])), 1.0));	
        	}
        	} catch (Exception e) {
        		System.err.println(parseTree.getFilename()+" "+parseTree.getIndex()+" "+predicates);
        		System.err.println(parseTree);
        		System.err.println(parseTree.getTokenCount());
        		System.err.println(parseTree.getTokenNodes());
        		throw e;
        	}
        } else {
        	int isPredVal = predicateModel.getLabelStringMap().get(IS_PRED);
            double[] vals = new double[2];
            for (TBNode node: nodes) {
                if (!langUtil.isPredicateCandidate(node.getPOS()) || !langUtil.isVerb(node.getPOS()) && (!trainNominal || langUtil.getFrame(node)==null)) continue;
                
                EnumMap<Feature,Collection<String>> predFeatures = extractFeaturePredicate(predicateModel.getFeaturesFlat(), node, null, depEC==null?null:depEC[node.getTokenIndex()]);
                if (predicateOverrideKeySet!=null && predicateOverrideKeySet.contains(langUtil.makePBFrameKey(node))) {
                	logger.fine("overrode classifying predicate "+langUtil.makePBFrameKey(node));
                	predictions.add(new SRInstance(node, parseTree, argLabelStringMap, predictRoleSet(node, predFeatures), 1f)); 
                } else if (predicateModel.predictValues(predFeatures, vals)==isPredVal) {
                	makeProb(vals);
                    predictions.add(new SRInstance(node, parseTree, argLabelStringMap, predictRoleSet(node, predFeatures), vals[1])); 
                } /*else if (langUtil.isVerb(node.getPOS()) && node.getConstituentByHead()!=node && langUtil.getFrame(node)!=null) {
                	logger.fine("overrode classifying verb predicate "+node);
                	predictions.add(new SRInstance(node, parseTree, predictRoleSet(node, predFeatures), 1f)); 
                }*/
            }
        }
        
        if (predictions.isEmpty())
        	return predictions;
        
        int[] supportIds = SRLUtil.findSupportPredicates(predictions, useGoldPredicateSeparation?goldSRLs:null, langUtil, SRLUtil.SupportType.VERB, false);
        
        BitSet predicted = new BitSet(supportIds.length);
        
        // classify verb predicates first
        int cardinality = 0;        
        do {
            cardinality = predicted.cardinality();
            for (int i=predicted.nextClearBit(0); i<supportIds.length; i=predicted.nextClearBit(i+1))
                if (!langUtil.isVerb(predictions.get(i).getPredicateNode().getPOS()))
                    continue;
                else if (supportIds[i]<0 || predicted.get(supportIds[i])) {
                    predict(predictions.get(i), goldSRLs==null?null:goldSRLs.get(i), supportIds[i]<0?null:predictions.get(supportIds[i]), namedEntities, includeAllArgs);
                    predicted.set(i);
                }
        } while (predicted.cardinality()>cardinality);
        
        // then classify nominal predicates, by now we'll have the verb arguments to help find support verbs
        supportIds = SRLUtil.findSupportPredicates(predictions, useGoldPredicateSeparation?goldSRLs:null, langUtil, SRLUtil.SupportType.NOMINAL, false);
        do {
            cardinality = predicted.cardinality();
            for (int i=predicted.nextClearBit(0); i<supportIds.length; i=predicted.nextClearBit(i+1))
                if (langUtil.isVerb(predictions.get(i).getPredicateNode().getPOS()))
                    continue;
                else if (supportIds[i]<0 || predicted.get(supportIds[i])) {
                    predict(predictions.get(i), goldSRLs==null?null:goldSRLs.get(i), supportIds[i]<0?null:predictions.get(supportIds[i]), namedEntities, includeAllArgs);
                    predicted.set(i);
                }
        } while (predicted.cardinality()>cardinality);
        
        // debug
        for (int i=0; i<predictions.size(); ++i) {
            SRInstance instance = predictions.get(i);
            TBNode topVp = instance.predicateNode;
            List<TBNode> vpList = new ArrayList<TBNode>();
            
            while (topVp.getParent() != null && topVp.getParent().getHead()==instance.predicateNode && topVp.getParent().getPOS().equals("VP")) {
                topVp = topVp.getParent();
                vpList.add(topVp);
            }
            
            if (topVp.getPOS().equals("VP")) {
                for (SRArg arg:instance.getArgs())
                    if (arg.node.isDecendentOf(topVp)) {
                        boolean found = false;
                        for (TBNode node:vpList)
                            if (arg.node.getParent()==node) {
                                found = true;
                                break;
                            }
                        if (found) continue;
                    /*
                        System.err.println(parseTree.getFilename()+" "+parseTree.getIndex()+" "+parseTree);
                        System.err.println(arg);
                        System.err.println(instance);
                        if (goldSRLs!=null) {
                            System.err.println(goldSRLs.get(i).getTree().getFilename()+" "+goldSRLs.get(i).getTree().getIndex()+" "+goldSRLs.get(i).getTree());
                            System.err.println(goldSRLs.get(i));
                        }
                        System.err.print("\n");
                        */
                    }
            }
        }
        //System.out.printf("%d/%d, %d/%d\n", filteredArg, totalArg, filteredNoArg, totalNoArg);
        return predictions;
    }

    transient int filteredArg = 0;
    transient int totalArg = 0;
    transient int filteredNoArg = 0;
    transient int totalNoArg = 0;
    
    int predict(SRInstance prediction, SRInstance gold, SRInstance support, String[] namedEntities, boolean includeAllArgs) {
    	
    	List<TBNode> argNodes = SRLUtil.getArgumentCandidates(prediction.predicateNode, filterArgs, support, langUtil, argCandidateLevelDown, argCandidateAllHeadPhrases);
        List<SRArg> goldArgs=null;
        if (gold!=null) {
            Map<TBNode, SRArg> candidateMap = SRLUtil.mapArguments(gold, prediction.getTree(), argNodes);
            
            argNodes = new ArrayList<TBNode>(candidateMap.size());
            goldArgs = new ArrayList<SRArg>(candidateMap.size());
            
            for (Map.Entry<TBNode, SRArg> entry:candidateMap.entrySet()) {
            	argNodes.add(entry.getKey());
                goldArgs.add(entry.getValue());
            }
        }
    	
        //System.out.println("Predicting "+prediction.tree.getFilename()+" "+prediction.tree.getIndex()+" "+prediction.predicateNode.getTokenIndex());
    	
    	for (TBNode node:argNodes)
    		if (node.getHead()==null || node.getHead().getWord()==null) {
    			logger.severe(prediction.getTree().getFilename()+" "+prediction.getTree().getIndex()+": null head: "+node.toParse()+"\n"+prediction.getTree().toPrettyParse());
    			logger.severe(node.getHead().toParse());
    			logger.severe(node.getHead().getParent().toParse());
    			break;
    		}
    	
    	boolean isNominal = !langUtil.isVerb(gold!=null&&useGoldPredicateSeparation?gold.getPredicateNode().getPOS():prediction.getPredicateNode().getPOS());
    	//boolean isNominal = gold==null?!langUtil.isVerb(prediction.getPredicateNode().getPOS()):!langUtil.isVerb(gold.getPredicateNode().getPOS());
    	
        List<EnumMap<Feature,Collection<String>>> featureMapList = extractFeatureSRL(isNominal?nominalArgLabelFeatures:argLabelFeatures, prediction.predicateNode, prediction.rolesetId, argNodes, prediction.getRolesetId(), null, namedEntities, false);
        
        ArgSample[] fsamples = new ArgSample[featureMapList.size()];
        
        BitSet stage2Mask = argLabelStage2Classifier==null?null:new BitSet();
        
        for (int i=0; i<featureMapList.size(); ++i) {
            fsamples[i] = new ArgSample(argNodes.get(i), prediction.predicateNode, goldArgs==null||goldArgs.get(i)==null?null:goldArgs.get(i).getLabel(), featureMapList.get(i));
        }
        double threshold = argLabelClassifier.canPredictProb()?0.01:0.06;
        
        Arrays.sort(fsamples, sampleComparator);
        
        List<SRArg> emptyArgs = new ArrayList<SRArg>();
        for (int i=0; i<fsamples.length; ++i) {
            double[] labelValues = new double[argLabelIndexMap.size()];
            int labelIndex;
            if (isNominal) {
            	int[] x = getFeatureVector(nominalArgLabelFeatures, prediction.predicateNode, prediction.rolesetId, fsamples[i], support, emptyArgs, null);
                if (nominalArgLabelClassifier.canPredictProb())
	                labelIndex = nominalArgLabelClassifier.predictProb(x, labelValues);
	            else {
	                labelIndex = nominalArgLabelClassifier.predictValues(x, labelValues);
	                makeProb(labelValues);
	            }
            } else {
            	int[] x = getFeatureVector(argLabelFeatures, prediction.predicateNode, prediction.rolesetId, fsamples[i], support, emptyArgs, null);
                if (argLabelClassifier.canPredictProb())
	                labelIndex = argLabelClassifier.predictProb(x, labelValues);
	            else {
	                labelIndex = argLabelClassifier.predictValues(x, labelValues);
	                makeProb(labelValues);
	            }
            }
           
            String goldLabel = fsamples[i].label;
            fsamples[i].label = argLabelIndexMap.get(labelIndex);
            fsamples[i].labelProb = labelValues;

            if (labeled && !fsamples[i].label.equals(SRArg.NOT_ARG)) {
                prediction.addArg(new SRArg(fsamples[i].label, fsamples[i].node, labelValues[argLabelStringMap.get(fsamples[i].label)-1], labelValues));
                if (stage2Mask!=null)
                	stage2Mask.set(i);
            } else if (!isNominal && labelValues[argLabelStringMap.get(SRArg.NOT_ARG)-1]<=argLabelStage2Threshold ||
            		isNominal && labelValues[argLabelStringMap.get(SRArg.NOT_ARG)-1]<=nominalArgLabelStage2Threshold)
            	if (stage2Mask!=null)
            		stage2Mask.set(i);    
                
            if (!goldLabel.equals(SRArg.NOT_ARG))
                totalArg++;
            if (fsamples[i].label.equals(SRArg.NOT_ARG)) {
                totalNoArg++;
                /*
                if (value>1-threshold) {
                    if (!goldLabel.equals(NOT_ARG))
                        filteredArg++;
                    else
                        filteredNoArg++;
                }
                */
                Arrays.sort(labelValues);
                if (labelValues[labelValues.length-2]<threshold) {
                    if (!goldLabel.equals(SRArg.NOT_ARG))
                        filteredArg++;
                    else
                        filteredNoArg++;
                }
                    
            }
            
        }

        if (stage2Mask!=null) {
        	Map<String, List<String>> argTopicMap = makeArgTopicMap(fsamples);
        	List<SRArg> predictedArgs = new ArrayList<SRArg>(prediction.getArgs());
        	SRArg rel = prediction.args.get(0);
        	prediction.args.clear();
        	prediction.args.add(rel);
        	for (int i=0; i<fsamples.length; ++i) {
        		if (!stage2Mask.get(i)) continue;

                double[] labelValues = new double[argLabelIndexMap.size()];
                int labelIndex;
                
                if (isNominal) {
                	int[] x = getFeatureVector(nominalArgLabelFeatures, prediction.predicateNode, prediction.rolesetId, fsamples[i], support, predictedArgs, argTopicMap);
	                if (nominalArgLabelStage2Classifier.canPredictProb())
	                    labelIndex = nominalArgLabelStage2Classifier.predictProb(x, labelValues);
	                else {
	                    labelIndex = nominalArgLabelStage2Classifier.predictValues(x, labelValues);
	                    makeProb(labelValues);
	                }
                } else {
	                int[] x = getFeatureVector(argLabelFeatures, prediction.predicateNode, prediction.rolesetId, fsamples[i], support, predictedArgs, argTopicMap);
	                if (argLabelStage2Classifier.canPredictProb())
	                    labelIndex = argLabelStage2Classifier.predictProb(x, labelValues);
	                else {
	                    labelIndex = argLabelStage2Classifier.predictValues(x, labelValues);
	                    makeProb(labelValues);
	                }
                }
                
                fsamples[i].label = argLabelIndexMap.get(labelIndex);
                if (labeled && !fsamples[i].label.equals(SRArg.NOT_ARG))
                    prediction.addArg(new SRArg(fsamples[i].label, fsamples[i].node, labelValues[argLabelStringMap.get(fsamples[i].label)-1], labelValues));
        	}
        }
        
        prediction.cleanUpArgs();
        Roleset roleSet = langUtil.getRoleSet(prediction.predicateNode, prediction.rolesetId);
        
        if  (roleSet!=null)
        	for (SRArg arg:prediction.getArgs())
        		arg.auxLabel=roleSet.getAuxLabel(arg.label);
        
        //System.out.println(prediction);
        //System.out.println(fsamples);
        
        return featureMapList.size();
    }
    
    int countConstituents(String label, List<TBNode> lnodes, List<TBNode> rnodes, TBNode joinNode) {   
        int count = 0;
        
        count += countConstituents(label, new LinkedList<TBNode>(lnodes), true, 100);
        count += countConstituents(label, new LinkedList<TBNode>(rnodes), false, 100);
        
        for (int i=lnodes.get(lnodes.size()-1).getChildIndex()+1; i<rnodes.get(rnodes.size()-1).getChildIndex(); ++i)
            count += countConstituents(label, joinNode.getChildren()[i], lnodes.size()>rnodes.size()?lnodes.size():rnodes.size());
        
        count += joinNode.getPOS().startsWith(label)?1:0;
        
        return count;
    }
    
    int countConstituents(String label, Deque<TBNode> nodes, boolean left, int depth) {   
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
     
    int countConstituents(String label, TBNode node, int depth) {   
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
        
        for (TBNode node:argNodes) {
            path.add(node.getPOS());
            path.add(UP_CHAR);
        }
        path.add(joinNode.getPOS());
        for (int i=predNodes.size()-1; i>=0; --i) {
            path.add(DOWN_CHAR);
            path.add(predNodes.get(i).getPOS());
        }
        return path;
    }       
}
