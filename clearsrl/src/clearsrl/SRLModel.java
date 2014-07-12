package clearsrl;

import clearcommon.alg.Classifier;
import clearcommon.alg.CrossValidator;
import clearcommon.alg.FeatureSet;
import clearcommon.alg.LinearClassifier;
import clearcommon.alg.PairWiseClassifier;
import clearcommon.alg.SimpleModel;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.util.EnglishUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PBFrame;
import clearcommon.util.PBFrame.Roleset;
import clearcommon.util.PropertyUtil;
import clearsrl.ec.ECCommon;
import clearsrl.util.Topics;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntObjectHashMap;
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
import java.util.TreeSet;
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
    
    public static final String NOT_ARG="!ARG";
    public static final String IS_ARG="ARG";

    public static final String NOT_PRED="!PRED";
    public static final String IS_PRED="PRED";
    
    private static final int GZIP_BUFFER = 0x40000;
    
    public enum Feature {
        // Constituent independent features
        PREDICATE,
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
        public SRLSample(TBNode predicate, String roleset, TBTree tree, SRLSample support, ArgSample[] args, SRInstance gold) {
            this.predicate = predicate;
            this.roleset = roleset;
            this.tree = tree;
            this.support = support;
            this.args = args;
            this.gold = gold;
        }
        TBNode predicate;
        String roleset;
        TBTree tree;
        SRLSample support;
        ArgSample[] args;
        SRInstance gold;
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
            if (this.label==null) this.label = NOT_ARG;
        }
        
        public String toString() {
            if (label.equals(NOT_ARG))
                return node.toText();
            return "["+label+" "+node.toText()+"]";
        }
        TBNode node;
        TBNode predicate;
        String label;
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
    
    public void initialize(Properties props) throws IOException {
    	filterArgs = !props.getProperty("filterArguments", "false").equals("false");
    	trainNominal = !props.getProperty("trainNominal", "true").equals("false");
    	
        predicateModel.initialize();
        rolesetModelMap = new HashMap<String, SimpleModel<Feature>>();

        argLabelFeatures.initialize(); 
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
        
        argTopicMap = Topics.readAllTopics(props);
    }
    
    /**
     * Must be manually set as LanguageUtil is not serializable
     * @param langUtil
     */
    public void setLanguageUtil(LanguageUtil langUtil) {
        this.langUtil = langUtil;
    }
    
    public void setTrainGoldParse(boolean trainGoldParse) {
        this.trainGoldParse = trainGoldParse;
    }
    
    public void setPredicateOverride(Set<String> keySet) {
    	predicateOverrideKeySet = keySet;
    }
    
    public TObjectIntMap<String> getLabelValueMap() {
        return argLabelStringMap;
    }

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
        
        FeatureSet.buildMapIndex(argLabelStringMap, 0, true);
        argLabelIndexMap = new TIntObjectHashMap<String>();
        for (TObjectIntIterator<String> iter=argLabelStringMap.iterator(); iter.hasNext();) {
            iter.advance();
            argLabelIndexMap.put(iter.value(),iter.key());
        }

        System.err.printf("ARGS trained %d/%d\n", argsTrained, argsTotal);
        
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
        	System.out.printf("%s: %d/%d\n",entry.getKey(), entry.getValue().length, rolesetCntMap.get(entry.getKey()));
        trainingSampleFile.delete();
        logger.info("Second pass argument processing completed.");
    }
    
    /**
     * Mapping arguments annotated on one parse (typically gold) to ones using another parse tree (typically automatic)
     * @param instance src SRL instance
     * @param parsedTree the parsed tree to perform SRL off of 
     * @param candidateNodes argument candidate nodes    
     * @return map of nodes of parse tree to arguments from the src instance
     */
    Map<TBNode, SRArg> mapArguments(SRInstance instance, TBTree parsedTree, List<TBNode> candidateNodes) {
        Map<TBNode, SRArg> candidateMap = new HashMap<TBNode, SRArg>();
        
        Map<BitSet, TBNode> tokenSetMap = new HashMap<BitSet, TBNode>();
        for (TBNode node:candidateNodes) {
            BitSet tokenSet = node.getTokenSet();
            if (!tokenSetMap.containsKey(tokenSet) || tokenSetMap.get(tokenSet).isDecendentOf(node))
                tokenSetMap.put(tokenSet, node);
        }
        
        Set<TBNode> candidateNodeSet = new HashSet<TBNode>(tokenSetMap.values());
        
        // find a good match between parse candidates and argument boundary of src SRL
        for (SRArg arg:instance.getArgs()) {
            if (arg.isPredicate()) continue;
            BitSet argBitSet = arg.getTokenSet();
            if (argBitSet.isEmpty()) continue;
            
            if (tokenSetMap.containsKey(argBitSet))
                candidateMap.put(tokenSetMap.get(argBitSet), arg);
            else {
                TBNode constituent = parsedTree.getNodeByTokenIndex(arg.node.getHead().getTokenIndex()).getConstituentByHead();
                BitSet cSet = constituent.getTokenSet();
                
                if (!cSet.get(instance.predicateNode.getTokenIndex()) && candidateNodeSet.contains(constituent))
                    candidateMap.put(constituent, arg);
            }
        }
        
        for (TBNode node:candidateNodeSet)
            if (!candidateMap.containsKey(node))
                candidateMap.put(node, null);
        
        return candidateMap;
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
    
    static List<SRInstance> convertToSRInstance(List<PBInstance> props) {
    	List<SRInstance> srls = new ArrayList<SRInstance>();
    	if (props==null) return srls;
        for (PBInstance instance:props)
            srls.add(new SRInstance(instance));
    	return srls;
    }
    
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
            		(trainNominal || langUtil.isVerb(node.getPOS()));
        	
            if (goldSRLs[node.getTokenIndex()]==null && !trainPredicate)
                continue;
           
            EnumMap<Feature,Collection<String>> sample = extractFeaturePredicate(predicateModel.getFeaturesFlat(), node, null, null);
           
            if (trainPredicate)
            	predicateModel.addTrainingSample(sample, goldSRLs[node.getTokenIndex()]!=null?IS_PRED:NOT_PRED, buildDictionary);

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
    	List<SRInstance> goldInstances = convertToSRInstance(sent.propPB);
    	for (Iterator<SRInstance> iter=goldInstances.iterator(); iter.hasNext();) {
    		SRInstance instance = iter.next();
    		if (!trainNominal && !langUtil.isVerb(instance.getPredicateNode().getPOS()))
    			iter.remove();
    	}
    	Collections.sort(goldInstances);
        List<SRInstance> trainInstances = new ArrayList<SRInstance>(goldInstances.size());
        for (SRInstance goldInstance:goldInstances) {
        	SRInstance trainInstance = new SRInstance(goldInstance.predicateNode, tree, goldInstance.getRolesetId(), 1.0);
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
        int[] supportIds = SRLUtil.findSupportPredicates(goldInstances, langUtil, SRLUtil.SupportType.ALL, true);        
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
                    Map<TBNode, SRArg> candidateMap = mapArguments(goldInstances.get(i), tree, candidateNodes);
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
                            if (!arg.getLabel().equals(NOT_ARG))
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
        
        boolean isNominal = !langUtil.isVerb(sampleInstance.getPredicateNode().getPOS());
        
        List<EnumMap<Feature,Collection<String>>> featureMapList = extractFeatureSRL(sampleInstance.predicateNode, argNodes, sampleInstance.getRolesetId(), depEC, namedEntities);
        
        for (TBNode node:argNodes) {
            if (candidateMap.get(node)==null)
                labels.add(NOT_ARG);
            else
                labels.add(SRLUtil.removeArgModifier(candidateMap.get(node).label));
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
            	if (!NOT_ARG.equals(argSample.label))
            		predictedList.add(new SRArg(argSample.label, argSample.node));
            
            for (int i=0; i<featureMapList.size();++i) {
                boolean isNoArg = NOT_ARG.equals(argSamples[i].label);
                featureMapList.get(i).putAll(extractFeatureSequence(sampleInstance.predicateNode, sampleInstance.getRolesetId(), sampleList.get(i), sampleInstance, predictedList, buildDictionary));
                for(Map.Entry<EnumSet<Feature>,Collection<String>> entry:argLabelFeatures.convertFlatSample(featureMapList.get(i)).entrySet())
                    argLabelFeatures.addToDictionary(entry.getKey(), entry.getValue(), (isNominal?nominalWeight:1)*(isNoArg?noArgWeight:1));
                
                //if (!NOT_ARG.equals(SRLUtil.getMaxLabel(labels.get(c))))
                //  System.out.println(sample.get(Feature.PATH));
                argLabelStringMap.put(argSamples[i].label, argLabelStringMap.get(argSamples[i].label)+1);
            }
            return null;
        } else {
            return new SRLSample(sampleInstance.getPredicateNode(), sampleInstance.rolesetId, sampleInstance.getTree(), supportSample, argSamples, goldInstance);
        }
    }

    public EnumMap<Feature,Collection<String>> extractFeatureArgument(TBNode predicateNode, TBNode argNode, String[] namedEntities) {
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
        
        for (Feature feature:argLabelFeatures.getFeaturesFlat()) {
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
            		List<String> topics = argTopicMap.get(Topics.getTopicHeadword(argNode));
            		if (topics!=null)
            			featureMap.put(feature, topics);
            	}
            	break;
            case HEADWORDTOPICNUM:
            	if (argTopicMap!=null) {
            		List<String> topics = argTopicMap.get(Topics.getTopicHeadword(argNode));
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
        return featureMap;
    }

    List<EnumMap<Feature,Collection<String>>> extractFeatureSRL(TBNode predicateNode, List<TBNode> argNodes, String frame, String[] depEC, String[] namedEntities) {   
        List<EnumMap<Feature,Collection<String>>> featureMapList = new ArrayList<EnumMap<Feature,Collection<String>>>();
        
        EnumMap<Feature,Collection<String>> defaultMap = extractFeaturePredicate(argLabelFeatures.getFeaturesFlat(), predicateNode, frame, depEC);

        for (TBNode argNode:argNodes) {
            EnumMap<Feature,Collection<String>> featureMap = extractFeatureArgument(predicateNode, argNode, namedEntities);
            featureMap.putAll(defaultMap);
            featureMapList.add(featureMap);
        }
        return featureMapList;
    }
    
    EnumMap<Feature,Collection<String>> extractFeatureSequence(TBNode predicate, String rolesetId, ArgSample sample, SRInstance support, List<SRArg> predictedArgs, boolean buildDictionary) {
        EnumMap<Feature,Collection<String>> featureMap = new EnumMap<Feature,Collection<String>>(Feature.class);
        
        for (Feature feature:argLabelFeatures.getFeaturesFlat()) {
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
                    if (sArg!=null) {
                    	if (sample.node==sample.node)
                    		featureMap.put(feature, Arrays.asList("sup-"+sArg.getLabel()));
                    	else 
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
	            	
	            	if (label!=null && !label.equals(NOT_ARG)) {
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
                    sampleFlat.put(feature, Arrays.asList(relatedVerb, predicateLemma+"-n"));
                else
                    sampleFlat.put(feature, predicateAlternatives);
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
                {
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
            	    if (role!=null)
            	    	sampleFlat.put(feature, role.getRoles());
            	}
            	break;
            case ROLESETCLASS:
            	if (rolesetId!=null) {
            		Roleset role = langUtil.getRoleSet(predicateNode, rolesetId);
            	    if (role!=null && role.getClasses()!=null)
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
        makeTrainingPredicates(Integer.parseInt(prop.getProperty("dictionary.cutoff", "2")));

        // train predicate classifier
        logger.info(String.format("Training predicates"));
        predicateModel.train(prop);
        
        // train roleset classifiers
        rolesetValidatedLabelMap = new HashMap<String, String[]>();
        for (Map.Entry<String, SimpleModel<Feature>> entry:rolesetModelMap.entrySet()) {
            logger.info(String.format("Training %s\n", entry.getKey()));
            rolesetValidatedLabelMap.put(entry.getKey(),entry.getValue().train(prop, true));
        }
        
        makeTrainingArguments(Integer.parseInt(prop.getProperty("dictionary.cutoff", "2")));
        
        
        int folds = Integer.parseInt(prop.getProperty("crossvalidation.folds","5"));
        int threads = Integer.parseInt(prop.getProperty("crossvalidation.threads","1"));
        
        double stage2Threshold = 1.0;
        
        boolean hasSequenceFeature = Feature.hasSequenceFeature(argLabelFeatures.getFeaturesFlat());
        boolean hasStage2Feature = Feature.hasStage2Feature(argLabelFeatures.getFeaturesFlat());
        
        // initialize arg label classifier
        try {
        	Properties stage1Prop = PropertyUtil.filterProperties(prop, "stage1.",true);
            argLabelClassifier = (Classifier)Class.forName(stage1Prop.getProperty("classifier", "clearcommon.alg.LinearClassifier")).newInstance();
            argLabelClassifier.setDimension(argLabelFeatures.getDimension());
            argLabelClassifier.initialize(argLabelStringMap, stage1Prop);
            
            if (hasStage2Feature) {
            	Properties stage2Prop = PropertyUtil.filterProperties(prop, "stage2.",true);
            	stage2Threshold = Double.parseDouble(stage2Prop.getProperty("threshold","0.95"));
            	argLabelStage2Classifier = (Classifier)Class.forName(stage2Prop.getProperty("classifier", "clearcommon.alg.PairWiseClassifier")).newInstance();
            	argLabelStage2Classifier.setDimension(argLabelFeatures.getDimension());
            	argLabelStage2Classifier.initialize(argLabelStringMap, stage2Prop);
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
        
        int rounds = Integer.parseInt(prop.getProperty("sequence.rounds","3"));
        if (hasStage2Feature)
        	rounds = hasSequenceFeature?(int)(Math.ceil(rounds/2.0)):1;
        else if (!hasSequenceFeature)
        	rounds = 0;

        double threshold = 0.001;
        
        String[] goldLabels = null;
        int[] y = null;
        {
        	List<String> labelList = new ArrayList<String>();
        	readTrainingArguments(extractedSampleFile, null, null, null, null, labelList, null);
        	goldLabels = labelList.toArray(new String[labelList.size()]);
        	y = new int[goldLabels.length];
        	for (int i=0; i<goldLabels.length; ++i)
        		y[i] = argLabelStringMap.get(goldLabels[i]);
        }
        
        BitSet stage2Mask = null;
        
        String[] labels = Arrays.copyOf(goldLabels, goldLabels.length);
        for (int r=0; r<rounds; ++r) {
        	stage2Mask = new BitSet();
        	TDoubleArrayList pList = new TDoubleArrayList();
        	TDoubleArrayList rList = new TDoubleArrayList();
        	int rTotal = 0;
        	
        	double[][] labelValues = new double[labels.length][argLabelStringMap.size()];
            String[] newLabels = trainArguments(argLabelClassifier, r==0?null:labels, null, folds, threads, labelValues, y);
            
            int cnt=0;
            for (int i=0; i<labels.length; ++i) {
                //if (i<50) System.out.println(i+" "+labels[i]+" "+newLabels[i]);
                if (labels[i].equals(newLabels[i])) ++cnt;
                
                makeProb(labelValues[i]);
                
                if (NOT_ARG.equals(goldLabels[i]))
                	pList.add(labelValues[i][argLabelStringMap.get(NOT_ARG)-1]);
                else if (NOT_ARG.equals(newLabels[i]))
                	rList.add(labelValues[i][argLabelStringMap.get(NOT_ARG)-1]);
                else // !NOT_ARG.equals(newLabels[i])
                	rTotal++;
            }
            
            double[] rVal = rList.toArray();
            double[] pVal = pList.toArray();
            Arrays.sort(rVal);
            Arrays.sort(pVal);
            
            double recall = 1.0*rTotal/(rVal.length+rTotal);
            logger.info(String.format("IS_ARG recall %f %d/%d\n", recall, rTotal, rVal.length+rTotal));
            if (recall>stage2Threshold)
            	argLabelStage2Threshold = 1;
            else
            	argLabelStage2Threshold = rVal[(int)Math.floor(rVal.length-(1-stage2Threshold)*(rVal.length+rTotal))];
            
            for (int i=0; i<newLabels.length; ++i)
            	if (!NOT_ARG.equals(newLabels[i]) || labelValues[i][argLabelStringMap.get(NOT_ARG)-1]<=argLabelStage2Threshold)
            		stage2Mask.set(i);
            
            double agreement = cnt*1.0/labels.length;
            System.out.printf("Round %d stage 1: %f\n", r, agreement);
            SRLScore score = new SRLScore(argLabelStringMap.keySet());
            for (int i=0; i<newLabels.length; ++i)
                score.addResult(newLabels[i], goldLabels[i]);        
            System.out.println(score.toString());

            logger.info(String.format("Threshold: %f, NO_ARG value: %f, %d/%d filtered, %d training arguments", stage2Threshold, argLabelStage2Threshold, pVal.length-Math.abs(Arrays.binarySearch(pVal, argLabelStage2Threshold)), pVal.length, stage2Mask.cardinality()));
            
            if (hasSequenceFeature && hasStage2Feature) {
            	newLabels = trainArguments(argLabelStage2Classifier, newLabels, stage2Mask, folds, threads, null, y);
            	
            	cnt=0;
            	int nCnt=0;
                for (int i=0; i<labels.length; ++i)
                	if (stage2Mask.get(i)) {
                		if (labels[i].equals(newLabels[nCnt])) ++cnt;
                		labels[i] = newLabels[nCnt++];
                	} else {
                		if (labels[i].equals(NOT_ARG)) ++cnt;
                		labels[i] = NOT_ARG;
                	}
                		
                agreement = cnt*1.0/labels.length;
                System.out.printf("Round %d stage 2: %f\n", r, agreement);
                score = new SRLScore(argLabelStringMap.keySet());
                for (int i=0; i<labels.length; ++i)
                    score.addResult(labels[i], goldLabels[i]);
                System.out.println(score.toString());
            } else
            	labels = newLabels;
        
            /*
            int lcnt=0;
            SRLScore score2 = new SRLScore(new TreeSet<String>(Arrays.asList(labelStringMap.keys(new String[labelStringMap.size()]))));
            SRLScore score3 = new SRLScore(new TreeSet<String>(Arrays.asList(labelStringMap.keys(new String[labelStringMap.size()]))));
            for (Map.Entry<String, SortedMap<Integer, List<SRLSample>>> entry:trainingSamples.entrySet())
                for (Map.Entry<Integer, List<SRLSample>> entry2:entry.getValue().entrySet()) {
                    List<SRInstance> instances;
                    SRInstance[] goldInstances = new SRInstance[entry2.getValue().size()];
                    for (int i=0; i<entry2.getValue().size(); ++i) {
                        SRLSample sample = entry2.getValue().get(i);
                        goldInstances[i] = sample.gold;
                        SRInstance instance = new SRInstance(sample.predicate, sample.tree);
                        SRInstance goldInstance = new SRInstance(sample.predicate, sample.tree);
                        List<TBNode> nodes = new ArrayList<TBNode>(sample.args.length);
                        for (ArgSample argSample:sample.args) {
                            nodes.add(argSample.node);
                            if (!goldLabels[lcnt].equals(NOT_ARG))
                                goldInstance.addArg(new SRArg(goldLabels[lcnt], argSample.node));
                            ++lcnt;
                        }
                        predict(instance, nodes, sample.support==null?null:makeSRInstance(sample.support), null);
                        score2.addResult(instance, sample.gold);
                    }
                    //if (goldInstances.length>0)
                        //System.out.println("Predicting: "+entry2.getValue().get(0).tree.getFilename()+" "+entry2.getValue().get(0).tree.getIndex());
                    instances = predict(entry2.getValue().get(0).tree, goldInstances, null);
                    for (int i=0; i<instances.size(); ++i)
                        score3.addResult(instances.get(i), goldInstances[i]);
                }
            System.out.println(score2.toString());
            System.out.println(score3.toString());
            */
            if (1-agreement<=threshold) {
                /*if (r>0) {
                    // change the training sample labels back
                    int labelCnt=0;
                    for (Map.Entry<String, SortedMap<Integer, List<SRLSample>>> entry:trainingSamples.entrySet())
                        for (Map.Entry<Integer, List<SRLSample>> entry2:entry.getValue().entrySet())
                            for (SRLSample sample:entry2.getValue()) {
                                for (ArgSample argSample:sample.args)
                                    argSample.label=goldLabels[labelCnt++];        
                            }
                }*/
                break;
            }
        }

        String[] predictions = trainArguments(argLabelClassifier, rounds>0?labels:null, null, 1, threads, null, y);
        SRLScore score = new SRLScore(argLabelStringMap.keySet());
        for (int i=0; i<predictions.length; ++i)
            score.addResult(predictions[i], goldLabels[i]);
        System.out.println("stage 1 training:");
        System.out.println(score.toString());
        
        if (hasStage2Feature) {
        	predictions = trainArguments(argLabelStage2Classifier, labels, stage2Mask, 1, threads, null, y);
        	int cnt = 0;
        	SRLScore score2 = new SRLScore(argLabelStringMap.keySet());
        	for (int i=0; i<goldLabels.length; ++i)
        		if (!stage2Mask.get(i))
        			score2.addResult(NOT_ARG, goldLabels[i]);
        		else
        			score2.addResult(predictions[cnt++], goldLabels[i]);
        	System.out.println("stage 2 training:");
            System.out.println(score2.toString());
        }
    }

    
    /**
     * 
     * @param useSequence whether to use sequence features (may not be a good idea at the start)
     * @param folds folds of x-validataion
     * @param threads number of threads used for x-validataion
     * @param labels set of predicted labels from last round of training
     * @return new set of labels
     */
    String[] trainArguments(Classifier classifier, String[] predictedLabels, BitSet stage2Mask, int folds, int threads, double[][] values, int[] y) {             
        Object[] X = null;

        List<Object> xList = new ArrayList<Object>();
        TIntArrayList seedList = new TIntArrayList();
        List<String> stage2GoldLabels = stage2Mask==null?null:new ArrayList<String>();

        readTrainingArguments(extractedSampleFile, predictedLabels, stage2Mask, classifier, xList, stage2GoldLabels, folds>1?seedList:null);
        
        X = xList.toArray();

        if (stage2GoldLabels!=null) {
        	y= new int[stage2GoldLabels.size()];
        	for (int i=0; i<y.length; ++i)
        		y[i] = argLabelStringMap.get(stage2GoldLabels.get(i));
        }
        
        /*
        double[] weights = new double[argLabelStringMap.size()];
        String[] labelTypes = argLabelStringMap.keys(new String[argLabelStringMap.size()]);
        Arrays.sort(labelTypes);
        int idx=0;
        for (String label:labelTypes) {
            if (label.equals(NOT_ARG))
                weights[idx] = 1;
            else
                weights[idx] = 1;
            ++idx;
        }*/

        String[] newLabels = new String[y.length];
        if (folds>1) {
            CrossValidator validator = new CrossValidator(classifier, threads);
            int[] yV =  validator.validate(folds, X, y, values, seedList.toArray(), false);
            for (int i=0; i<yV.length; ++i)
                newLabels[i] = argLabelIndexMap.get(yV[i]);

        } else {
        	classifier.trainNative(X, y);
 	        for (int i=0; i<y.length; ++i)
 	        	newLabels[i] = argLabelIndexMap.get(classifier.predictNative(X[i]));
        }
        System.gc();
        return newLabels;
    }

    void readTrainingArguments(File sampleFile, String[] predictedLabels, BitSet stage2Mask, Classifier classifier, List<Object> xList, List<String> labelList, TIntArrayList seedList) {
    	
    	if (xList!=null) xList.clear();
    	if (labelList!=null) labelList.clear();
    	if (seedList!=null) seedList.clear();
    	
    	try (ObjectInputStream cachedInStream = 
    			new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(sampleFile),GZIP_BUFFER),GZIP_BUFFER*4))) {
            int predictedCnt=0;
            int stage2Cnt=0;
            Set<String> treeNameSet = new HashSet<String>();
            for (;;) {
                TBTree tree = (TBTree)cachedInStream.readObject();
                treeNameSet.add(tree.getFilename());
                SRLSample[] samples = (SRLSample[]) cachedInStream.readObject();
                // relabel everything first
                if (labelList!=null) {
                	int stage2LocalCnt = stage2Cnt;
                	for (SRLSample srlSample:samples) 
               		 	for (ArgSample argSample:srlSample.args)
               		 		if (stage2Mask==null || stage2Mask.get(stage2LocalCnt++))
               		 			labelList.add(argSample.label);
                }
                if (predictedLabels!=null)
                	 for (SRLSample srlSample:samples) 
                		 for (ArgSample argSample:srlSample.args)
                			 argSample.label = predictedLabels[predictedCnt++];

                for (SRLSample srlSample:samples) {
                    List<SRArg> predictedArgs = new ArrayList<SRArg>();
                    if (stage2Mask!=null)
	                    for (ArgSample argSample:srlSample.args)
	                    	if (xList!=null && !argSample.label.equals(NOT_ARG))
	                    		predictedArgs.add(new SRArg(argSample.label, argSample.node));
                    
                    for (ArgSample argSample:srlSample.args) {
                    	// only train arguments needed for stage2
                    	if (stage2Mask!=null && !stage2Mask.get(stage2Cnt++))
                    		continue;
                    	if (xList!=null) {
	                        SRInstance support = null;
	                        if (srlSample.support!=null) {
	                            support = new SRInstance(srlSample.support.predicate, srlSample.support.tree);
	                            for (ArgSample supportArg:srlSample.support.args)
	                                if (!supportArg.label.equals(NOT_ARG))
	                                    support.addArg(new SRArg(supportArg.label, supportArg.node));
	                        }
	                
	                        xList.add(classifier.getNativeFormat(getFeatureVector(srlSample.predicate, srlSample.roleset, argSample, support, predictedArgs)));
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

    
    SRInstance makeSRInstance(SRLSample sample) {
        SRInstance instance = new SRInstance(sample.predicate, sample.tree);
        for (ArgSample argSample:sample.args)
            if (!argSample.label.equals(NOT_ARG))
                instance.addArg(new SRArg(argSample.label, argSample.node));
        return instance;
    }
    
    
    int[] getFeatureVector(TBNode predicate, String rolesetId, ArgSample sample, SRInstance support, List<SRArg> predictedArgs) {
        EnumMap<Feature,Collection<String>> sampleFeatures = extractFeatureSequence(predicate, rolesetId, sample, support, predictedArgs, false);
        sampleFeatures.putAll(sample.features);
        return argLabelFeatures.getFeatureVector(sampleFeatures);
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
    
    public List<SRInstance> predict(TBTree parseTree, List<PBInstance> propPB, String[][] depEC, String[] namedEntities) {
    	
    	List<SRInstance> goldSRLs = propPB==null?null:convertToSRInstance(propPB);
    	    	
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
        	int isPredVal = predicateModel.getLabelStringMap().get(IS_PRED);
            double[] vals = new double[2];
            for (TBNode node: nodes) {
                if (!(langUtil.isPredicateCandidate(node.getPOS())&&(trainNominal || langUtil.isVerb(node.getPOS())))) continue;
                EnumMap<Feature,Collection<String>> predFeatures = extractFeaturePredicate(predicateModel.getFeaturesFlat(), node, null, depEC==null?null:depEC[node.getTokenIndex()]);
                if (predicateOverrideKeySet!=null && predicateOverrideKeySet.contains(langUtil.makePBFrameKey(node))) {
                	if (langUtil.makePBFrameKey(node)==null)
                		logger.severe("null frame key encountered for "+node.toParse());
                	else
                		logger.info(langUtil.makePBFrameKey(node));
                	logger.info(node.getWord());
                	logger.info(node.toParse());
                	logger.info("overrode classifying predicate "+langUtil.makePBFrameKey(node));
                	predictions.add(new SRInstance(node, parseTree, predictRoleSet(node, predFeatures), 1f)); 
                } else if (predicateModel.predictValues(predFeatures, vals)==isPredVal) {
                	makeProb(vals);
                    predictions.add(new SRInstance(node, parseTree, predictRoleSet(node, predFeatures), vals[1])); 
                }
            }
        } else
            for (SRInstance goldSRL:goldSRLs) {
            	if (!trainNominal && !langUtil.isVerb(goldSRL.getPredicateNode().getPOS())) continue;
                TBNode node = parseTree.getNodeByTokenIndex(goldSRL.getPredicateNode().getTokenIndex());
                predictions.add(new SRInstance(node, parseTree, predictRoleSet(node, extractFeaturePredicate(predicateModel.getFeaturesFlat(), node, null, depEC==null?null:depEC[node.getTokenIndex()])), 1.0));
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
                    predict(predictions.get(i), goldSRLs==null?null:goldSRLs.get(i), supportIds[i]<0?null:predictions.get(supportIds[i]), namedEntities);
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
                    predict(predictions.get(i), goldSRLs==null?null:goldSRLs.get(i), supportIds[i]<0?null:predictions.get(supportIds[i]), namedEntities);
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

    public int predict(SRInstance prediction, SRInstance gold, SRInstance support, String[] namedEntities) {
        List<TBNode> candidateNodes = SRLUtil.getArgumentCandidates(prediction.predicateNode, filterArgs, support, langUtil, argCandidateLevelDown, argCandidateAllHeadPhrases);
        List<SRArg> goldArgs=null;
        if (gold!=null) {
            Map<TBNode, SRArg> candidateMap = mapArguments(gold, prediction.getTree(), candidateNodes);
            
            candidateNodes = new ArrayList<TBNode>(candidateMap.size());
            goldArgs = new ArrayList<SRArg>(candidateMap.size());
            
            for (Map.Entry<TBNode, SRArg> entry:candidateMap.entrySet()) {
                candidateNodes.add(entry.getKey());
                goldArgs.add(entry.getValue());
            }
        }
        return predict(prediction, candidateNodes, goldArgs, support, namedEntities);
    } 

    transient int filteredArg = 0;
    transient int totalArg = 0;
    transient int filteredNoArg = 0;
    transient int totalNoArg = 0;
    
    int predict(SRInstance prediction, List<TBNode> argNodes, List<SRArg> goldArgs, SRInstance support, String[] namedEntities) {
        //System.out.println("Predicting "+prediction.tree.getFilename()+" "+prediction.tree.getIndex()+" "+prediction.predicateNode.getTokenIndex());
        List<EnumMap<Feature,Collection<String>>> featureMapList = extractFeatureSRL(prediction.predicateNode, argNodes, prediction.getRolesetId(), null, namedEntities);
        
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
            // TODO: needs to be changed
            int[] x = getFeatureVector(prediction.predicateNode, prediction.rolesetId, fsamples[i], support, emptyArgs);
            int labelIndex;
            if (argLabelClassifier.canPredictProb())
                labelIndex = argLabelClassifier.predictProb(x, labelValues);
            else {
                labelIndex = argLabelClassifier.predictValues(x, labelValues);
                makeProb(labelValues);
            }
            String goldLabel = fsamples[i].label;
            fsamples[i].label = argLabelIndexMap.get(labelIndex);

            if (labeled && !fsamples[i].label.equals(NOT_ARG)) {
                prediction.addArg(new SRArg(fsamples[i].label, fsamples[i].node, labelValues, argLabelStringMap));
                if (stage2Mask!=null)
                	stage2Mask.set(i);
            } else if (labelValues[argLabelStringMap.get(NOT_ARG)-1]<=argLabelStage2Threshold)
            	if (stage2Mask!=null)
            		stage2Mask.set(i);    
                
            if (!goldLabel.equals(NOT_ARG))
                totalArg++;
            if (fsamples[i].label.equals(NOT_ARG)) {
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
                    if (!goldLabel.equals(NOT_ARG))
                        filteredArg++;
                    else
                        filteredNoArg++;
                }
                    
            }
            
        }

        if (stage2Mask!=null) {
        	List<SRArg> predictedArgs = new ArrayList<SRArg>(prediction.getArgs());
        	SRArg rel = prediction.args.get(0);
        	prediction.args.clear();
        	prediction.args.add(rel);
        	for (int i=0; i<fsamples.length; ++i) {
        		if (!stage2Mask.get(i)) continue;
        	
                double[] labelValues = new double[argLabelIndexMap.size()];
                int[] x = getFeatureVector(prediction.predicateNode, prediction.rolesetId, fsamples[i], support, predictedArgs);
                int labelIndex;
                if (argLabelStage2Classifier.canPredictProb())
                    labelIndex = argLabelStage2Classifier.predictProb(x, labelValues);
                else {
                    labelIndex = argLabelStage2Classifier.predictValues(x, labelValues);
                    makeProb(labelValues);
                }
                
                fsamples[i].label = argLabelIndexMap.get(labelIndex);
                if (labeled && !fsamples[i].label.equals(NOT_ARG))
                    prediction.addArg(new SRArg(fsamples[i].label, fsamples[i].node, labelValues, argLabelStringMap));
        	}
        }
        
        prediction.cleanUpArgs();
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
