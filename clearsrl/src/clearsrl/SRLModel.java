package clearsrl;

import clearcommon.alg.Classifier;
import clearcommon.alg.CrossValidator;
import clearcommon.alg.FeatureSet;
import clearcommon.alg.LinearClassifier;
import clearcommon.alg.SimpleModel;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.util.EnglishUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PBFrame;
import clearcommon.util.PBFrame.Roleset;
import gnu.trove.iterator.TObjectIntIterator;
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
		COMPLEMENTIZER,  // used mostly for Chinese
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

        // sequence features
        SUPPORT(true),
        SUPPORTPATH(true),
        SUPPORTARG(true),
        ARGPREVIOUS(true),
        ARGLISTDIRECTIONAL(true),
        ARGLISTALL(true);
        
        boolean sequence;

        Feature() {
            this.sequence = false;
        }
        
        Feature(boolean sequence) {
            this.sequence = sequence;
        }
        
        public boolean isSequence() {
            return sequence;
        }
        
        static boolean hasSequenceFeature(EnumSet<Feature> features) {
            for (Feature feature:features)
                if (feature.isSequence())
                    return true;
            return false;
        }
    };
    
    class SRLSample implements Serializable{
        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        public SRLSample(TBNode predicate, TBTree tree, SRLSample support, ArgSample[] args, SRInstance gold) {
            this.predicate = predicate;
            this.tree = tree;
            this.support = support;
            this.args = args;
            this.gold = gold;
        }
        TBNode predicate;
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
    
    int                                     argLabelMinCount = 20;
    
    Comparator<ArgSample>                   sampleComparator = new TokenDistanceComparator();
    
    boolean                                 trainGoldParse = false;
    
    int                                     argCandidateLevelDown = 2;
    boolean                                 argCandidateAllHeadPhrases = true;
    float                                   noArgWeight = 0.2f;
    float                                   nominalWeight = 2;
    
    transient LanguageUtil                  langUtil;
    
    transient double[]                      labelValues;
    
//  transient ArrayList<int[]>              predicateTrainingFeatures;
//  transient TIntArrayList                 predicateTrainingLabels;

    transient int                           argsTrained = 0;
    transient int                           argsTotal = 0;

    transient Logger                        logger;
    
    transient File                          trainingSampleFile;
    transient File                          extractedSampleFile;
    
    transient ObjectOutputStream            cachedOutStream;
    transient ObjectInputStream             cachedInStream;
    
    transient int                           trainingTreeCnt;
    
    
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
        if (predicateFeatureSet!=null)
            predicateModel = new SimpleModel<Feature>(predicateFeatureSet);
    }
    
    public void initialize(Properties props) throws IOException {
        if (predicateModel!=null) {
            predicateModel.initialize();
            rolesetModelMap = new HashMap<String, SimpleModel<Feature>>();
        }
        argLabelFeatures.initialize();
        // process name/id + object name/id means this should be unique, right?
        String uniqueExtension = ManagementFactory.getRuntimeMXBean().getName()+'.'+Integer.toHexString(System.identityHashCode(this));

        trainingTreeCnt = 0;
        trainingSampleFile = new File(props.getProperty("tmpdir", "/tmp"), "trainingSamples."+uniqueExtension);
        trainingSampleFile.deleteOnExit();
        
        extractedSampleFile = new File(props.getProperty("tmpdir", "/tmp"), "extractedSamples."+uniqueExtension);
        extractedSampleFile.deleteOnExit();
        
        // might as well make sure both can be open for write
        cachedOutStream = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(trainingSampleFile),GZIP_BUFFER),GZIP_BUFFER*4));
        //extractedSampleOut = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(extractedSampleFile),GZIP_BUFFER),GZIP_BUFFER*4));
        
        argLabelStringMap = new TObjectIntHashMap<String>();
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
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    private void finalizeDictionary(int cutoff) throws IOException {
        
        // Probably should hard code minimum number of instances
        FeatureSet.trimMap(argLabelStringMap,argLabelMinCount);
        
        logger.info("Labels: ");
        String[] labels = argLabelStringMap.keys(new String[argLabelStringMap.size()]);
        Arrays.sort(labels);
        for (String label:labels)
            logger.info("  "+label+" "+argLabelStringMap.get(label));
        
        argLabelFeatures.rebuildMap(cutoff);
        
        FeatureSet.buildMapIndex(argLabelStringMap, 0);
        argLabelIndexMap = new TIntObjectHashMap<String>();
        for (TObjectIntIterator<String> iter=argLabelStringMap.iterator(); iter.hasNext();) {
            iter.advance();
            argLabelIndexMap.put(iter.value(),iter.key());
        }
        
        if (predicateModel!=null) {
            predicateModel.finalizeDictionary(predicateMinCount, 0);
            
            for (Iterator<Map.Entry<String, SimpleModel<Feature>>> iter=rolesetModelMap.entrySet().iterator() ; iter.hasNext();) {
                SimpleModel<Feature> rolesetModel = iter.next().getValue();
                rolesetModel.finalizeDictionary(predicateMinCount, 5);
                if (rolesetModel.getLabels().size()<2)
                    iter.remove();
            }
        }
        
        System.err.printf("ARGS trained %d/%d\n", argsTrained, argsTotal);

        cachedOutStream.close();
        cachedOutStream = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(extractedSampleFile),GZIP_BUFFER),GZIP_BUFFER*4));
        cachedInStream = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(trainingSampleFile),GZIP_BUFFER),GZIP_BUFFER*4));
        
        logger.info("Second pass processing of training samples.");
        try {
            for (;;) {
                TBTree tree = (TBTree)cachedInStream.readObject();
                List<SRInstance> goldInstances = (List<SRInstance>)cachedInStream.readObject();
                String[] namedEntities = (String[])cachedInStream.readObject();
                float threshold = cachedInStream.readFloat();
                addTrainingSentence(tree, goldInstances, namedEntities, threshold, false);
            }
        } catch (EOFException e) {
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            cachedInStream.close();
            cachedInStream = null;
        }
        trainingSampleFile.delete();
        logger.info("Second pass processing completed.");
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
    public void addTrainingSentence(TBTree tree, List<SRInstance> goldInstances, String[] namedEntities, float threshold) throws IOException {
        addTrainingSentence(tree, goldInstances, namedEntities, threshold, true);
        cachedOutStream.writeObject(tree);
        cachedOutStream.writeObject(goldInstances);
        cachedOutStream.writeObject(namedEntities);
        cachedOutStream.writeFloat(threshold);
        if (++trainingTreeCnt%5000==0)
            cachedOutStream.reset();
    }
    
    void addTrainingSentence(TBTree tree, List<SRInstance> goldInstances, String[] namedEntities, float threshold, boolean buildDictionary) {
        
        int[] supportIds = SRLUtil.findSupportPredicates(goldInstances, langUtil, SRLUtil.SupportType.ALL, true);
        
        List<SRInstance> trainInstances = new ArrayList<SRInstance>(goldInstances.size());
        for (SRInstance instance:goldInstances)
            trainInstances.add(new SRInstance(instance.predicateNode, tree, instance.getRolesetId(), 1.0));

        SRLSample[] srlSamples = new SRLSample[trainInstances.size()]; 
        
        BitSet processedSet = new BitSet(supportIds.length);
        // classify verb predicates first
        int cardinality = 0;        
        do {
            cardinality = processedSet.cardinality();
            for (int i=processedSet.nextClearBit(0); i<supportIds.length; i=processedSet.nextClearBit(i+1))
                if (supportIds[i]<0 || processedSet.get(supportIds[i])) {                   
                    
                    List<TBNode> candidateNodes = 
                            SRLUtil.getArgumentCandidates(tree.getNodeByTokenIndex(goldInstances.get(i).getPredicateNode().getTokenIndex()), 
                                                          supportIds[i]<0?null:trainInstances.get(supportIds[i]), 
                                                          langUtil, argCandidateLevelDown, argCandidateAllHeadPhrases);
                    Map<TBNode, SRArg> candidateMap = mapArguments(goldInstances.get(i), tree, candidateNodes);
                    srlSamples[i] = addTrainingSample(trainInstances.get(i), candidateMap, 
                                                       supportIds[i]<0?null:trainInstances.get(supportIds[i]), 
                                                       supportIds[i]<0?null:srlSamples[supportIds[i]], 
                                                       goldInstances.get(i), namedEntities, buildDictionary);

                    if (buildDictionary) {
                        int args = 0;
                        for (SRArg arg:trainInstances.get(i).getArgs())
                            if (!arg.getLabel().equals(NOT_ARG))
                                args++;
                        if (args!=goldInstances.get(i).getArgs().size()) {
                            System.err.println("\n"+trainInstances.get(i));
                            System.err.println(goldInstances.get(i));
                            if (supportIds[i]>=0)
                                System.err.println(trainInstances.get(supportIds[i]));
                            System.err.println(tree+"\n");
                        }
                        argsTrained+=args-1;
                        argsTotal+=goldInstances.get(i).getArgs().size()-1;
                    }
                    
                    processedSet.set(i);
                }
        } while (processedSet.cardinality()>cardinality);

        TBNode[] nodes = tree.getTokenNodes();
        ArrayList<TBNode> predicateCandidates = new ArrayList<TBNode>();
        for (TBNode node: nodes)
            if (langUtil.isPredicateCandidate(node.getPOS()))
                predicateCandidates.add(node);
 
        SRInstance[] goldInstanceArray = new SRInstance[tree.getTokenCount()];
        for (SRInstance goldInstance: goldInstances)
            goldInstanceArray[goldInstance.getPredicateNode().getTokenIndex()] = goldInstance;
        
        SRInstance[] trainInstanceArray = new SRInstance[tree.getTokenCount()];
        for (SRInstance trainInstance: trainInstances)
            trainInstanceArray[trainInstance.getPredicateNode().getTokenIndex()] = trainInstance;
        
        // add predicate training samples
        for (TBNode node: nodes) {
            if (!langUtil.isPredicateCandidate(node.getPOS()))
                continue;
            EnumMap<Feature,Collection<String>> sample = extractPredicateFeature(predicateModel.getFeaturesFlat(), node, null);
            
            predicateModel.addTrainingSample(sample, goldInstanceArray[node.getTokenIndex()]!=null?IS_PRED:NOT_PRED, buildDictionary);

            if (goldInstanceArray[node.getTokenIndex()]==null)
                continue;
            
            String key = PBFrame.makeKey(node, langUtil);
            PBFrame frame = langUtil.getFrame(key);
            if (frame==null || frame.getRolesets().size()<=1)
                continue;
            
            SimpleModel<Feature> rolesetModel = rolesetModelMap.get(key);
            
            //FeatureSet<Feature> rolesetFeature = rolesetFeatureMap.get(key);
            //TObjectIntMap<String> rolesetLabelString = rolesetLabelStringMap.get(key);
            if (buildDictionary) {
                /*
                if (rolesetFeature == null) {
                    rolesetFeature = new FeatureSet<Feature>(predicateFeatureSet);
                    rolesetFeature.initialize();
                    rolesetFeatureMap.put(key, rolesetFeature);
                }
                if (rolesetLabelString == null) {
                    rolesetLabelString = new TObjectIntHashMap<String>();
                    rolesetLabelStringMap.put(key, rolesetLabelString);
                }
                for(Map.Entry<EnumSet<Feature>,List<String>> entry:sample.entrySet())
                    rolesetFeature.addToDictionary(entry.getKey(), entry.getValue(), 1);
                rolesetLabelString.put(goldInstanceArray[node.getTokenIndex()].rolesetId, rolesetLabelString.get(goldInstanceArray[node.getTokenIndex()].rolesetId)+1);
                */
                if (rolesetModel==null) {
                    rolesetModel = new SimpleModel<Feature>(predicateFeatureSet);
                    rolesetModel.initialize();
                    rolesetModelMap.put(key, rolesetModel);
                }
                rolesetModel.addTrainingSample(sample, goldInstanceArray[node.getTokenIndex()].rolesetId, true);
                
            } else if (rolesetModel!=null) {
                rolesetModel.addTrainingSample(sample, goldInstanceArray[node.getTokenIndex()].rolesetId, false);
                /*ArrayList<int[]> examples = rolesetTrainingFeatures.get(key);
                if (examples==null) {
                    examples = new ArrayList<int[]>();
                    rolesetTrainingFeatures.put(key, examples);
                }
                examples.add(rolesetFeature.getFeatureVector(sample));
                
                TIntArrayList labels = rolesetTrainingLabels.get(key);
                if (labels==null) {
                    labels = new TIntArrayList();
                    rolesetTrainingLabels.put(key, labels);
                }
                labels.add(rolesetLabelString.get(goldInstanceArray[node.getTokenIndex()].rolesetId));
                */
            }
        }

        /*
        // add predicate training samples
        if (buildDictionary) {
            SRInstance[] goldInstanceIndex = new SRInstance[tree.getTokenCount()];
            for (SRInstance goldInstance: goldInstances)
                goldInstanceIndex[goldInstance.getPredicateNode().getTokenIndex()] = goldInstance;
            
            for (TBNode node: nodes)
                if (langUtil.isPredicateCandidate(node.getPOS())) {
                    Map<EnumSet<Feature>,List<String>> sample = predicateFeatures.convertFlatSample(extractPredicateFeature(node));
                    for(Map.Entry<EnumSet<Feature>,List<String>> entry:sample.entrySet())
                        predicateFeatures.addToDictionary(entry.getKey(), entry.getValue(), 1);
                    if (goldInstanceIndex[node.getTokenIndex()]!=null) {
                        PBFrame frame = langUtil.getFrame(node);
                        if (frame!=null && frame.getRolesets().size()>1) {
                            String key = PBFrame.makeKey(node, langUtil);
                            FeatureSet<Feature> rolesetFeature = rolesetFeatureMap.get(key);
                            if (rolesetFeature == null) {
                                rolesetFeature = new FeatureSet<Feature>(predicateFeatureSet);
                                rolesetFeatureMap.put(key, rolesetFeature);
                            }
                            TObjectIntMap<String>  rolesetLabelString = rolesetLabelStringMap.get(key);
                            if (rolesetLabelString == null) {
                                rolesetLabelString = new ObjectIntOpenHashMap<String>();
                                rolesetLabelStringMap.put(key, rolesetLabelString);
                            }
                            for(Map.Entry<EnumSet<Feature>,List<String>> entry:sample.entrySet())
                                rolesetFeature.addToDictionary(entry.getKey(), entry.getValue(), 1);
                            rolesetLabelString.put(goldInstanceIndex[node.getTokenIndex()].rolesetId, rolesetLabelString.get(goldInstanceIndex[node.getTokenIndex()].rolesetId)+1);
                        }
                        
                    }
                }
        } else {
            BitSet instanceMask = new BitSet(tree.getTokenCount());
            for (SRInstance instance:trainInstances)
                instanceMask.set(instance.getPredicateNode().getTokenIndex());
            
            
            
            for (TBNode predicateCandidate:predicateCandidates) {
                predicateTrainingFeatures.add(predicateFeatures.getFeatureVector(predicateFeatures.convertFlatSample(extractPredicateFeature(predicateCandidate))));
                predicateTrainingLabels.add(instanceMask.get(predicateCandidate.getTokenIndex())?1:2);
            }
            */
            
        if (!buildDictionary) {
            try {
                cachedOutStream.writeObject(tree);
                cachedOutStream.writeObject(srlSamples);
                if (++trainingTreeCnt%5000==0)
                    cachedOutStream.reset();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    SRLSample addTrainingSample(SRInstance sampleInstance, Map<TBNode, SRArg> candidateMap, 
            SRInstance supportInstance, SRLSample supportSample, SRInstance goldInstance, 
            String[] namedEntities, boolean buildDictionary) {
        
        List<TBNode> argNodes = new ArrayList<TBNode>(candidateMap.keySet());
        List<String> labels = new ArrayList<String>();
        
        boolean isNominal = !langUtil.isVerb(sampleInstance.getPredicateNode().getPOS());
        
        List<EnumMap<Feature,Collection<String>>> featureMapList = extractSampleFeature(sampleInstance.predicateNode, argNodes, sampleInstance.getRolesetId(), namedEntities);
        
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
            int c=0;
            List<SRArg> predictedList = new ArrayList<SRArg>();
            for (int i=0; i<featureMapList.size();++i) {
                boolean isNoArg = NOT_ARG.equals(labels.get(c));
                featureMapList.get(i).putAll(extractSequenceFeatures(sampleInstance.predicateNode, sampleList.get(i), sampleInstance, predictedList, buildDictionary));
                if (!isNoArg)
                    predictedList.add(new SRArg(argSamples[i].label, argSamples[i].node));
                for(Map.Entry<EnumSet<Feature>,Collection<String>> entry:argLabelFeatures.convertFlatSample(featureMapList.get(i)).entrySet())
                    argLabelFeatures.addToDictionary(entry.getKey(), entry.getValue(), (isNominal?nominalWeight:1)*(isNoArg?noArgWeight:1));
                
                //if (!NOT_ARG.equals(SRLUtil.getMaxLabel(labels.get(c))))
                //  System.out.println(sample.get(Feature.PATH));
                argLabelStringMap.put(labels.get(c), argLabelStringMap.get(labels.get(c))+1);
                ++c;
            }
            return null;
        } else {
            return new SRLSample(sampleInstance.getPredicateNode(), sampleInstance.getTree(), supportSample, argSamples, goldInstance);
        }
    }

    public EnumMap<Feature,Collection<String>> extractArgumentFeature(TBNode predicateNode, TBNode argNode, String[] namedEntities) {
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
        return featureMap;
    }

    List<EnumMap<Feature,Collection<String>>> extractSampleFeature(TBNode predicateNode, List<TBNode> argNodes, String frame, String[] namedEntities) {   
        List<EnumMap<Feature,Collection<String>>> featureMapList = new ArrayList<EnumMap<Feature,Collection<String>>>();
        
        EnumMap<Feature,Collection<String>> defaultMap = extractPredicateFeature(argLabelFeatures.getFeaturesFlat(), predicateNode, frame);

        for (TBNode argNode:argNodes) {
            EnumMap<Feature,Collection<String>> featureMap = extractArgumentFeature(predicateNode, argNode, namedEntities);
            featureMap.putAll(defaultMap);
            featureMapList.add(featureMap);
        }
        return featureMapList;
    }
    
    EnumMap<Feature,Collection<String>> extractSequenceFeatures(TBNode predicate, ArgSample sample, SRInstance support, List<SRArg> predictedArgs, boolean buildDictionary) {
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
                    if (sArg!=null)
                        featureMap.put(feature, Arrays.asList("nested", sArg.getLabel()));
                }
                break;
            case ARGLISTDIRECTIONAL:
                // list of all found args in the same direction
                if (!predictedArgs.isEmpty()) {
                    List<String> labels = new ArrayList<String>(predictedArgs.size());
                    boolean left = sample.node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex();
                    for (SRArg predictedArg:predictedArgs)
                        if (left == predictedArg.node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex())
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
                        labels.add(predictedArg.label);
                    featureMap.put(feature, labels);
                }
                break;
            case ARGPREVIOUS:
                if (!predictedArgs.isEmpty() && sample.node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex()==
                        predictedArgs.get(predictedArgs.size()-1).node.getTokenSet().nextSetBit(0)<predicate.getTokenIndex())
                    featureMap.put(feature, Arrays.asList(predictedArgs.get(predictedArgs.size()-1).label));
                break;
            default:
                break;
            }
        }
        
        return featureMap;
    }

    EnumMap<Feature,Collection<String>> extractPredicateFeature(EnumSet<Feature> featureSet, TBNode predicateNode, String rolesetId) {
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
			case COMPLEMENTIZER:
				if (predicateNode.getHeadOfHead()!=null && predicateNode.getHeadOfHead().getConstituentByHead().getPOS().equals("CP"))
					sampleFlat.put(feature,Arrays.asList(Boolean.toString(true)));
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
                    sampleFlat.put(feature, Arrays.asList(langUtil.getPassive(predicateNode)>0?"passive":"active"));
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
            default:
                break;
            }
        }        
        return sampleFlat;
    }
    
    public void train(Properties prop) throws IOException {
        finalizeDictionary(Integer.parseInt(prop.getProperty("dictionary.cutoff", "2")));
        
        // train predicate classifier
        if (predicateModel!=null) {
            logger.info(String.format("Training predicates"));
            predicateModel.train(prop);
            /*
            TObjectIntMap<String> predicateLabelMap = new TObjectIntHashMap<String>();
            predicateLabelMap.put("predicate", 1);
            predicateLabelMap.put("not_predicate", 2);
            
            int dist = 0;
            
            for (int i=0; i<predicateTrainingLabels.size(); ++i)
                dist += (predicateTrainingLabels.get(i)==1)?1:0;
            logger.info(String.format("Training predicates: %d/%d/%d\n", dist, predicateTrainingLabels.size()-dist, predicateTrainingLabels.size()));
            
            predicateClassifier = new LinearClassifier();
            predicateClassifier.initialize(predicateLabelMap, prop);
            predicateClassifier.train(predicateTrainingFeatures.toArray(new int[predicateTrainingFeatures.size()][]),
                    predicateTrainingLabels.toArray());
            
            double score = 0;
            for (int i=0; i<predicateTrainingFeatures.size(); ++i)
                score += (predicateClassifier.predict(predicateTrainingFeatures.get(i))==predicateTrainingLabels.get(i))?1:0;
            logger.info(String.format("Predicate training accuracy: %f\n", score/predicateTrainingLabels.size()));
            */
            for (Map.Entry<String, SimpleModel<Feature>> entry:rolesetModelMap.entrySet()) {
                logger.info(String.format("Training %s\n", entry.getKey()));
                entry.getValue().train(prop);
                
            }
            
            /*
            rolesetClassifiers = new HashMap<String, Classifier>();
            for (Map.Entry<String, ArrayList<int[]>> entry:rolesetTrainingFeatures.entrySet()) {
                logger.info(String.format("Training %s(%d)\n", entry.getKey(), entry.getValue().size()));
                TIntArrayList rolesetTrainingLabel = rolesetTrainingLabels.get(entry.getKey());
                Classifier rolesetClassifier = new LinearClassifier();
                rolesetClassifier.initialize(rolesetLabelStringMap.get(entry.getKey()), prop);
                rolesetClassifier.train(entry.getValue().toArray(new int[entry.getValue().size()][]), rolesetTrainingLabel.toArray());

                score = 0;
                for (int i=0; i<entry.getValue().size(); ++i)
                    score += (rolesetClassifier.predict(entry.getValue().get(i))==rolesetTrainingLabel.get(i))?1:0;
                logger.info(String.format("%s training accuracy: %f\n", entry.getKey(), score/rolesetTrainingLabel.size()));
                rolesetClassifiers.put(entry.getKey(), rolesetClassifier);
            }*/
            
        }
        
        try {
            argLabelClassifier = (Classifier)Class.forName(prop.getProperty("classifier", "clearcommon.alg.LinearClassifier")).newInstance();
            argLabelClassifier.initialize(argLabelStringMap, prop);
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
        
        int folds = Integer.parseInt(prop.getProperty("crossvalidation.folds","5"));
        int threads = Integer.parseInt(prop.getProperty("crossvalidation.threads","1"));
        
        boolean hasSequenceFeature = false;
        for (Feature feature:argLabelFeatures.getFeaturesFlat())
            if (feature.sequence) {
                hasSequenceFeature = true;
                break;
            }
        
        int rounds = hasSequenceFeature?2:1;
        double threshold = 0.001;
        
        String[] goldLabels = null;
        List<String> labelList = new ArrayList<String>();
        
        if (cachedOutStream!=null) {
            try {
                cachedOutStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            cachedOutStream = null;
        }

        try {
            cachedInStream = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(extractedSampleFile),GZIP_BUFFER),GZIP_BUFFER*4));
            for (;;) {
                cachedInStream.readObject();
                SRLSample[] samples = (SRLSample[]) cachedInStream.readObject();
                for (SRLSample sample:samples)
                    for (ArgSample argSample:sample.args)
                        labelList.add(argSample.label);
                goldLabels = labelList.toArray(new String[labelList.size()]);
            }
        } catch (Exception e) {
            if (!(e instanceof EOFException))
                e.printStackTrace();
            try {
                cachedInStream.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            } finally {
                cachedInStream = null;
            }
        }

        String[] labels = goldLabels;
        for (int r=0; r<rounds-1; ++r) {
            String[] newLabels = trainArgLabel(r!=0, hasSequenceFeature?folds:1, threads, labels, goldLabels);
            int cnt=0;
            for (int i=0; i<labels.length; ++i) {
                //if (i<50) System.out.println(i+" "+labels[i]+" "+newLabels[i]);
                if (labels[i].equals(newLabels[i])) ++cnt;
            }
            double agreement = cnt*1.0/labels.length;
            System.out.printf("Round %d: %f\n", r, agreement);
            labels = newLabels;
        
            SRLScore score = new SRLScore(argLabelStringMap.keySet());
            for (int i=0; i<labels.length; ++i)
                score.addResult(labels[i], goldLabels[i]);
        
            System.out.println(score.toString());
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
        if (hasSequenceFeature && folds>1)
        	trainArgLabel(true, 1, threads, labels, goldLabels);
    }

    /**
     * 
     * @param useSequence whether to use sequence features (may not be a good idea at the start)
     * @param folds folds of x-validataion
     * @param threads number of threads used for x-validataion
     * @param labels set of predicted labels from last round of training
     * @return new set of labels
     */
    String[] trainArgLabel(boolean useSequence, int folds, int threads, String[] labels, String[] goldLabels) {             
        int[][] X = null;
        int[] y = new int[goldLabels.length];
        int[] seed = null;

        List<int[]> xList = new ArrayList<int[]>();
        TIntArrayList seedList = new TIntArrayList();
        
        try {
            int labelCnt=0;
            Set<String> treeNameSet = new HashSet<String>();
            cachedInStream = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(extractedSampleFile),GZIP_BUFFER),GZIP_BUFFER*4));
            for (;;) {
                TBTree tree = (TBTree)cachedInStream.readObject();
                treeNameSet.add(tree.getFilename());
                SRLSample[] samples = (SRLSample[]) cachedInStream.readObject();
                for (SRLSample srlSample:samples) {
                    List<SRArg> predictedArgs = new ArrayList<SRArg>();
                    for (ArgSample argSample:srlSample.args) {
                        argSample.label = labels[labelCnt++];
                        if (!argSample.label.equals(NOT_ARG))
                            predictedArgs.add(new SRArg(argSample.label, argSample.node));
                        SRInstance support = null;
                        if (srlSample.support!=null) {
                            support = new SRInstance(srlSample.support.predicate, srlSample.support.tree);
                            for (ArgSample supportArg:srlSample.support.args)
                                if (!supportArg.label.equals(NOT_ARG))
                                    support.addArg(new SRArg(supportArg.label, supportArg.node));
                        }
                        if (useSequence)
                            xList.add(getFeatureVector(srlSample.predicate, argSample, support, predictedArgs));
                        else
                            xList.add(argLabelFeatures.getFeatureVector(argSample.features));
                        
                        seedList.add(treeNameSet.size()-1);
                    }
                }
            }
        } catch (Exception e) {
            if (!(e instanceof EOFException))
                e.printStackTrace();
            try {
                cachedInStream.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            } finally {
                cachedInStream = null;
            }
        }
        
        for (int i=0; i<goldLabels.length; ++i)
            y[i] = argLabelStringMap.get(goldLabels[i]);
        
        X = xList.toArray(new int[xList.size()][]);
        seed = seedList.toArray();

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
        }

        int[] yV;
        if (folds>1) {
            CrossValidator validator = new CrossValidator(argLabelClassifier, threads);
            yV =  validator.validate(folds, X, y, null, seed, false);
        } else {
            argLabelClassifier.train(X, y);
            yV = new int[y.length];
            //for (int i=0; i<y.length; ++i)
            //    yV[i] = classifier.predict(X[i]);
        }
        
        String[] newLabels = new String[yV.length];
        for (int i=0; i<yV.length; ++i)
            newLabels[i] = argLabelIndexMap.get(yV[i]);
        
        return newLabels;
    }

    SRInstance makeSRInstance(SRLSample sample) {
        SRInstance instance = new SRInstance(sample.predicate, sample.tree);
        for (ArgSample argSample:sample.args)
            if (!argSample.label.equals(NOT_ARG))
                instance.addArg(new SRArg(argSample.label, argSample.node));
        return instance;
    }
    
    
    int[] getFeatureVector(TBNode predicate, ArgSample sample, SRInstance support, List<SRArg> predictedArgs) {
        EnumMap<Feature,Collection<String>> sampleFeatures = extractSequenceFeatures(predicate, sample, support, predictedArgs, false);
        sampleFeatures.putAll(sample.features);
        return argLabelFeatures.getFeatureVector(sampleFeatures);
    }
 
    String predictRoleSet(TBNode node, EnumMap<Feature,Collection<String>> features) {
        String key = PBFrame.makeKey(node, langUtil);
        PBFrame frame = langUtil.getFrame(key);
        if (frame==null)
            return langUtil.findStems(node).get(0)+".XX";
        SimpleModel<Feature> rolesetModel = rolesetModelMap.get(key);
        if (frame.getRolesets().size()==1 || rolesetModel==null)
            return frame.getRolesets().keySet().iterator().next();
        return rolesetModel.predictLabel(features);
        /*Classifier classifier = rolesetClassifiers.get(key);
        if (frame.getRolesets().size()==1 || classifier==null)
            return frame.getRolesets().keySet().iterator().next();
        return rolesetLabelIndexMap.get(key).get(classifier.predict(rolesetFeatureMap.get(key).getFeatureVector(features)));*/
        //return langUtil.findStems(node).get(0)+".XX";
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
            for (TBNode node: nodes) {
                if (!langUtil.isPredicateCandidate(node.getPOS())) continue;
                EnumMap<Feature,Collection<String>> predFeatures = extractPredicateFeature(predicateModel.getFeaturesFlat(), node, null);
                if (predicateModel.predictValues(predFeatures, vals)==1)
                    predictions.add(new SRInstance(node, parseTree, predictRoleSet(node, predFeatures), vals[1]-vals[0])); 
                    //if (!langUtil.isVerb(node.getPOS()))
                    //  System.out.println(node);
            }
        } else
            for (SRInstance goldSRL:goldSRLs) {
                TBNode node = parseTree.getNodeByTokenIndex(goldSRL.getPredicateNode().getTokenIndex());
                predictions.add(new SRInstance(node, parseTree, predictRoleSet(node, extractPredicateFeature(predicateModel.getFeaturesFlat(), node, null)), 1.0));
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
                    predict(predictions.get(i), goldSRLs==null?null:goldSRLs[i], supportIds[i]<0?null:predictions.get(supportIds[i]), namedEntities);
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
                    predict(predictions.get(i), goldSRLs==null?null:goldSRLs[i], supportIds[i]<0?null:predictions.get(supportIds[i]), namedEntities);
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
                    
                        System.err.println(parseTree.getFilename()+" "+parseTree.getIndex()+" "+parseTree);
                        System.err.println(arg);
                        System.err.println(instance);
                        if (goldSRLs!=null) {
                            System.err.println(goldSRLs[i].getTree().getFilename()+" "+goldSRLs[i].getTree().getIndex()+" "+goldSRLs[i].getTree());
                            System.err.println(goldSRLs[i]);
                        }
                        System.err.print("\n");
                    }
            }
        }
        //System.out.printf("%d/%d, %d/%d\n", filteredArg, totalArg, filteredNoArg, totalNoArg);
        return predictions;
    }

    public int predict(SRInstance prediction, SRInstance gold, SRInstance support, String[] namedEntities) {
        List<TBNode> candidateNodes = SRLUtil.getArgumentCandidates(prediction.predicateNode, support, langUtil, argCandidateLevelDown, argCandidateAllHeadPhrases);
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
        List<EnumMap<Feature,Collection<String>>> featureMapList = extractSampleFeature(prediction.predicateNode, argNodes, prediction.getRolesetId(), namedEntities);
        
        ArgSample[] fsamples = new ArgSample[featureMapList.size()];
        
        for (int i=0; i<featureMapList.size(); ++i)
            fsamples[i] = new ArgSample(argNodes.get(i), prediction.predicateNode, goldArgs.get(i)==null?null:goldArgs.get(i).getLabel(), featureMapList.get(i));
        
        double threshold = argLabelClassifier.canPredictProb()?0.01:0.06;
        
        Arrays.sort(fsamples, sampleComparator);
        
        for (int i=0; i<fsamples.length; ++i) {
            if (labelValues==null) labelValues = new double[argLabelIndexMap.size()];
            int[] x = getFeatureVector(prediction.predicateNode, fsamples[i], support, prediction.getArgs());
            int labelIndex;
            if (argLabelClassifier.canPredictProb())
                labelIndex = argLabelClassifier.predictProb(x, labelValues);
            else {
                labelIndex = argLabelClassifier.predictValues(x, labelValues);
                double denom = 0;
                for (int l=0; l<labelValues.length; ++l) {
                    labelValues[l] = 1 / (1 + Math.exp(-labelValues[l]));
                    denom += labelValues[l];
                }
                for (int l=0; l<labelValues.length; ++l)
                    labelValues[l] /= denom;
            }
            double value = labelValues[argLabelClassifier.getLabelIdxMap().get(labelIndex)];
            String goldLabel = fsamples[i].label;
            fsamples[i].label = argLabelIndexMap.get(labelIndex);
            if (labeled && !fsamples[i].label.equals(NOT_ARG))
                prediction.addArg(new SRArg(fsamples[i].label, fsamples[i].node, value));
            
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
