package clearsrl.ec;

import clearcommon.alg.Classifier;
import clearcommon.alg.CrossValidator;
import clearcommon.alg.FeatureSet;
import clearcommon.alg.PairWiseClassifier;
import clearcommon.alg.Classifier.InstanceFormat;
import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.util.ChineseUtil;

import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.TShortList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
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

import clearsrl.ec.ECCommon.Feature;
import clearsrl.ec.ECCommon.LabelType;

public class ECModel implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    TObjectIntMap<String> labelStringMap;
    TIntObjectMap<String> labelIndexMap;
    
    FeatureSet<Feature>       features;
    
    Classifier                classifier;
    
    LabelType                 labelType;
    
    float                     notECFeatureWeight = 0.2f;
    
    // must be a sorted map
    transient SortedMap<String, List<ECTreeSample>> trainingSamples;
    transient double[]                          labelValues;
    transient Logger                            logger;

    transient ChineseUtil chLangUtil = null;

    transient int tCount = 0;
    transient int count = 0;
    transient int hCount = 0;
    transient int thCount = 0;
    
    public LabelType getLabelType() {
        return labelType;
    }
    
    public ECModel (Set<EnumSet<Feature>> featureSet) {
        this(featureSet, LabelType.ALL);
    }
    
    public ECModel (Set<EnumSet<Feature>> featureSet, LabelType labelType) {
        logger = Logger.getLogger("clearsrl.ec");
        
        features = new FeatureSet<Feature>(featureSet);
        features.initialize();
        labelStringMap = new TObjectIntHashMap<String>();
        trainingSamples = new TreeMap<String, List<ECTreeSample>>();
        
        this.labelType = labelType;
    }
    
    public void setLangUtil(ChineseUtil chLangUtil) {
        this.chLangUtil = chLangUtil;
    }
    
    public TObjectIntMap<String> getLabelMap() {
        return labelStringMap;
    }
    
    public List<EnumMap<Feature,Collection<String>>> extractSampleFeature(TBTree tree, List<PBInstance> props, String[] labels, boolean buildDictionary) {
        
        List<EnumMap<Feature,Collection<String>>> samples = new ArrayList<EnumMap<Feature,Collection<String>>>();

        TBNode[] tokens = tree.getTokenNodes();

        String[] lemmas = new String[tokens.length];
        String[] poses = new String[tokens.length];
        for (int i=0; i<tokens.length;++i) {
            lemmas[i] = tokens[i].getWord();
            poses[i] = tokens[i].getPOS();
        }
    
        PBInstance[] rightPred = new PBInstance[tokens.length];
        
        if (props!=null) {
            PBInstance[] preds = new PBInstance[tokens.length];
            for (PBInstance prop:props)
                preds[prop.getPredicate().getTokenIndex()] = rightPred[prop.getPredicate().getTokenIndex()] = prop;

            for (int i=0; i<tokens.length; ++i) {
                TBNode ancestor = tokens[i];
                while (!ancestor.getPOS().matches("(IP|FRAG).*") && ancestor.getParent()!=null && ancestor.getParent().getTokenSet().nextSetBit(0)==i)
                    ancestor = ancestor.getParent();
                if (ancestor.getPOS().matches("(IP|FRAG).*")) {
                    for (int c=0; c<ancestor.getChildren().length; ++c)
                        if (preds[ancestor.getChildren()[c].getHead().getTokenIndex()]!=null) {
                            rightPred[i] = rightPred[i]==null?preds[ancestor.getChildren()[c].getHead().getTokenIndex()]:rightPred[i];
                            break;
                        }
                } else if (ancestor.getParent()!=null) {
                    for (int c=ancestor.getChildIndex(); c<ancestor.getParent().getChildren().length; ++c)
                        if (preds[ancestor.getParent().getChildren()[c].getHead().getTokenIndex()]!=null) {
                            rightPred[i] = rightPred[i]==null?preds[ancestor.getParent().getChildren()[c].getHead().getTokenIndex()]:rightPred[i];
                            break;
                        }               
                }
            }
            
            String[] predLemmas = new String[tokens.length];
            for (int i=0; i<predLemmas.length; ++i) {
                if (labels!=null && !labels[i].equals(ECCommon.NOT_EC))
                    predLemmas[i] = labels[i]+"/"+(rightPred[i]==null?"null":rightPred[i].getPredicate().getWord());
                else
                    predLemmas[i] = rightPred[i]==null?"null":rightPred[i].getPredicate().getWord();
            }
            preds=null;
        }

        for (int i=0; i<=tokens.length; ++i) {
            EnumMap<Feature,Collection<String>> sample = new EnumMap<Feature,Collection<String>>(Feature.class);
            for (Feature feature:features.getFeaturesFlat()) {
                switch (feature) {
                case T_L_LEMMA:
                    if (i>0) sample.put(feature, ECCommon.getPartial(lemmas[i-1]));
                    break;
                case T_R_LEMMA:
                    if (i<tokens.length) sample.put(feature, ECCommon.getPartial(lemmas[i]));
                    break;
                case T_L_POS:
                    if (i>0) sample.put(feature, Arrays.asList(poses[i-1]));
                    break;
                case T_R_POS:
                    if (i<tokens.length) sample.put(feature, Arrays.asList(poses[i]));
                    break;  
                case PARSE_FRAME:
                    {
                        TBNode ancestor = tokens[i];
                        while (!ancestor.getPOS().matches("(IP|FRAG)") && ancestor.getParent()!=null && ancestor.getParent().getTokenSet().nextSetBit(0)==i)
                            ancestor = ancestor.getParent();
                        if (ancestor.getPOS().matches("(IP|FRAG)")) {
                            StringBuilder pathStr = new StringBuilder(ancestor.getPOS());
                            for (int c=0; c<ancestor.getChildren().length; ++c) {
                                if (c>0 && ancestor.getChildren()[c].getPOS().equals(ancestor.getChildren()[c-1].getPOS()))
                                    continue;
                                pathStr.append("-"+ancestor.getChildren()[c].getPOS());
                                
                                if (ancestor.getHead()==ancestor.getChildren()[c].getHead()) break;
                            }
                            sample.put(feature, Arrays.asList(pathStr.toString()));
                        } else if (ancestor.getParent()!=null) {
                            StringBuilder pathStr = new StringBuilder(ancestor.getParent().getPOS());
                            for (int c=0; c<ancestor.getParent().getChildren().length; ++c) {
                                if (c==ancestor.getChildIndex())
                                    pathStr.append("-ec");
                                else if (c>0 && ancestor.getParent().getChildren()[c].getPOS().equals(ancestor.getParent().getChildren()[c-1].getPOS()))
                                    continue;
                                pathStr.append("-"+ancestor.getParent().getChildren()[c].getPOS());
                                
                                if (ancestor.getParent().getChildren()[c].getHead()==ancestor.getParent().getHead()) break;
                            }
                            if (pathStr.toString().indexOf("-VP")>0)
                                sample.put(feature, Arrays.asList(pathStr.toString(), ancestor.getParent().getPOS()+"-ec-"+ancestor.getPOS()+"-2"));                            
                        }
                    }   
                    break;
                case SRL_LEMMA:             // lemma of the closest predicate to the right
                    if (rightPred[i]!= null) {
                        List<String> list = ECCommon.getPartial(lemmas[rightPred[i].getPredicate().getTokenIndex()]);
                        list.add(Boolean.toString(true));
                        sample.put(feature,list);
                    }
                    break;
                case SRL_POS:             // POS of the closest predicate to the right
                    if (rightPred[i]!= null)
                        sample.put(feature, Arrays.asList(poses[rightPred[i].getPredicate().getTokenIndex()]));
                    break;
                case SRL_ANCESTOR:      
                    if (rightPred[i]!= null) {
                        List<String> before = new LinkedList<String>();
                        List<String> after = new LinkedList<String>();
                        
                        TBNode node = rightPred[i].getPredicate();
                        while (node.getParent()!=null) {
                            node = node.getParent();
                            if (node.getHead()!=rightPred[i].getPredicate()) break;
                            before.add(node.getPOS());
                        }
                        after.add(node.getPOS());

                        if (node.getPOS().matches("LCP|CP")) {
                            if (node.getPOS().equals("CP") && node.getParent()!=null) {
                                node = node.getParent();
                                after.add(node.getPOS());
                            }
                            if (node.getParent()!=null) {
                                node = node.getParent();
                                after.add(node.getPOS());
                            }
                        } 
                        
                        sample.put(feature, Arrays.asList(before.toString(), "a-"+after.toString(), 
                                "al-"+after.get(after.size()-1), "al-"+after.get(after.size()-1)+"-"+node.getHeadword()));
                    }
                    break;
                case SRL_NOARG0:             // predicate has no ARG0
                    if (rightPred[i]!= null) {
                        for (PBArg arg:rightPred[i].getAllArgs())
                            if (arg.getLabel().equals("ARG0")) {
                                sample.put(feature, Arrays.asList(Boolean.toString(false), arg.getNode().getPOS()));
                                break;
                            }
                        if (sample.get(feature)==null)
                            sample.put(feature, Arrays.asList(Boolean.toString(true)));
                    }
                    break;
                case SRL_NOLEFTCOREARG:      // predicate has no core argument to its left
                    if (rightPred[i]!= null) {
                        for (PBArg arg:rightPred[i].getArgs())
                            if (arg.getTerminalSet().nextSetBit(0)<rightPred[i].getPredicate().getTerminalIndex() 
                                    && arg.getLabel().matches("(R-)*ARG\\d")) {
                                sample.put(feature, Arrays.asList(Boolean.toString(false), arg.getLabel()));
                                break;
                            }
                        if (sample.get(feature)==null)
                            sample.put(feature, Arrays.asList(Boolean.toString(true)));
                    }
                    break;
                case SRL_NOLEFTNPARG:      // predicate has no NP argument to its left
                    if (rightPred[i]!= null) {
                        for (PBArg arg:rightPred[i].getArgs())
                            if (arg.getTerminalSet().nextSetBit(0)<rightPred[i].getPredicate().getTerminalIndex() 
                                    && arg.getNode().getPOS().equals("NP")) {
                                sample.put(feature, Arrays.asList(Boolean.toString(false), arg.getLabel()));
                                break;
                            }
                        if (sample.get(feature)==null)
                            sample.put(feature, Arrays.asList(Boolean.toString(true)));
                    }
                    break;
                case SRL_PATH:               // tree path from position to predicate
                    if (rightPred[i]!= null) {
                        List<String> paths = new ArrayList<String>();
                        StringBuilder pathStr = new StringBuilder();
                        
                        TBNode ancestor = rightPred[i].getPredicate().getParent();
                        while (ancestor.getNodeByTokenIndex(i)==null) {
                            pathStr.append(ancestor.getPOS()+'-');
                            ancestor = ancestor.getParent();
                        }
                        while (ancestor!=null && ancestor.getTokenSet().nextSetBit(0)==i){
                            pathStr.append(ancestor.getPOS()+'-');
                            paths.add(pathStr.toString());
                            ancestor = ancestor.getParent();
                        }
                        sample.put(feature, paths);
                    }
                    break;
                case SRL_FRAME:
                    if (rightPred[i]!= null) {
                        boolean addedEC = false;
                        ArrayList<String> pathList = new ArrayList<String>();
                        
                        List<PBArg> frontList = new ArrayList<PBArg>();
                        List<PBArg> backList = new ArrayList<PBArg>();
                        
                        StringBuilder pathStr = new StringBuilder();
                        for (PBArg arg:rightPred[i].getArgs()) {
                            if (arg.getLabel().equals("rel")) {
                                if (!addedEC) {
                                    pathStr.append("_EC");
                                    addedEC = true;
                                }
                                break;
                            }
                            BitSet tokenSet = arg.getTokenSet();
                            if (tokenSet.nextSetBit(0)!=i && tokenSet.get(i)) {
                                addedEC = false;
                                break;
                            }
                            if (tokenSet.nextSetBit(0)>=i && addedEC==false) {
                                pathStr.append("_EC");
                                addedEC = true;
                                
                            }
                            if (addedEC)backList.add(arg);
                            else frontList.add(arg);
                            pathStr.append("_"+arg.getLabel());
                        }
                        if (addedEC){
                            for (PBArg arg:frontList) {
                                pathList.add("f-"+arg.getLabel());
                                pathList.add("f-"+arg.getNode().getPOS());
                                pathList.add("f-"+arg.getLabel()+"-"+arg.getNode().getPOS());
                                pathList.add("f-"+arg.getNode().getHeadword());
                            }
                            for (PBArg arg:backList) {
                                pathList.add("b-"+arg.getLabel());
                                pathList.add("b-"+arg.getNode().getPOS());
                                pathList.add("b-"+arg.getLabel()+"-"+arg.getNode().getPOS());
                                pathList.add("b-"+arg.getNode().getHeadword());
                            }
                            pathList.add(pathStr.toString());
                        }
                        
                        TBNode ancestor = tokens[i];
                        while (!ancestor.getPOS().matches("(IP|FRAG)") && ancestor.getParent()!=null && ancestor.getParent().getTokenSet().nextSetBit(0)==i)
                            ancestor = ancestor.getParent();
                        
                        if (ancestor.getPOS().matches("(IP|FRAG)")) {
                            if (ancestor.getHead()==rightPred[i].getPredicate()) {
                                pathStr = new StringBuilder(ancestor.getPOS()+"-ec");
                                StringBuilder pathStr2 = new StringBuilder(ancestor.getPOS()+"-ec");
                                for (int c=0; c<ancestor.getChildren().length; ++c) {
                                    PBArg foundArg = null;
                                    for (PBArg arg:rightPred[i].getArgs())
                                        if (arg.getNode()==ancestor.getChildren()[c]) {
                                            foundArg = arg;
                                            break;
                                        }
                                    String label = foundArg==null?ancestor.getChildren()[c].getPOS():foundArg.getLabel();
                                    if (!pathStr.toString().endsWith(label)) pathStr.append("-"+label);
                                    if (foundArg!=null && !pathStr2.toString().endsWith(label) || ancestor.getHead()==ancestor.getChildren()[c].getHead()) pathStr2.append("-"+label);
                                    
                                    if (ancestor.getHead()==ancestor.getChildren()[c].getHead()) break;
                                }
                                pathList.add(pathStr.toString()); pathList.add(pathStr2.toString()+"-2"); 
                            }   
                        } else if (ancestor.getParent()!=null) {
                            pathStr = new StringBuilder(ancestor.getParent().getPOS());
                            for (int c=0; c<ancestor.getParent().getChildren().length; ++c) {
                                if (c==ancestor.getChildIndex())
                                    pathStr.append("-ec");

                                PBArg foundArg = null;
                                for (PBArg arg:rightPred[i].getArgs())
                                    if (arg.getNode()==ancestor.getParent().getChildren()[c]) {
                                        foundArg = arg;
                                        break;
                                    }
                                String label = foundArg==null?ancestor.getParent().getChildren()[c].getPOS():foundArg.getLabel();
                                if (!pathStr.toString().endsWith(label)) pathStr.append("-"+label);
                                if (ancestor.getParent().getChildren()[c].getHead()==rightPred[i].getPredicate()) break;
                            }
                            pathList.add(pathStr.toString());           
                        }
                    
                        pathList.trimToSize();
                        if (!pathList.isEmpty()) sample.put(feature, pathList);
                    }
                case D_SRL_INFRONTARG:
                    if (rightPred[i]!= null) {
                        for (PBArg arg:rightPred[i].getArgs())
                            if (arg.getTokenSet().nextSetBit(0)==i) {
                                sample.put(feature, Arrays.asList(arg.getLabel(), Boolean.toString(true)));
                                break;
                            }
                    }
                    break;
                case D_SRL_BEHINDARG:
                    if (rightPred[i]!= null) {
                        for (PBArg arg:rightPred[i].getArgs())
                            if (arg.getTokenSet().length()==i) {
                                sample.put(feature, Arrays.asList(arg.getLabel(), Boolean.toString(true)));
                                break;
                            }
                    }
                    break;
                case SRL_FIRSTARGPOS:
                    if (rightPred[i]!= null) {
                        if (rightPred[i].getArgs()[0].getTokenSet().nextSetBit(0)==i)
                            sample.put(feature, Arrays.asList(Boolean.toString(true)));
                    }
                    break;
                case SRL_LEFTARGTYPE:
                    if (rightPred[i]!= null) {
                        List<String> argTypes = new ArrayList<String>();
                        for (PBArg arg:rightPred[i].getArgs())
                            if (i>0 && arg.getTokenSet().get(i-1) && arg.getTokenSet().get(i)) {
                                argTypes.clear();
                                break;
                            } else if (arg.getTokenSet().length()<=i)
                                argTypes.add(arg.getLabel());
                        if (argTypes.isEmpty()) sample.put(feature, argTypes);
                    }
                case D_SRL_INARG:              // positioned inside an argument of the predicate
                    if (rightPred[i]!= null) {
                        for (PBArg arg:rightPred[i].getArgs())
                            if (i>0 && arg.getTokenSet().get(i-1) && arg.getTokenSet().get(i)) {
                                sample.put(feature, Arrays.asList(Boolean.toString(true)));
                                break;
                            }
                    }
                    break;
                case ECP1:
                    if (buildDictionary && i-1>=0) sample.put(feature, Arrays.asList(labels[i-1]));
                    break;
                case ECN1:
                    if (buildDictionary && i+1<tokens.length) sample.put(feature, Arrays.asList(labels[i+1]));
                    break;  
                default:
                    break;
                }
            }
            samples.add(sample);
        }

        return samples;
    }

    
    public void addTrainingSentence(TBTree goldTree, TBTree parsedTree, List<PBInstance> props, boolean buildDictionary) {
        String[] labels = ECCommon.getECLabels(goldTree, labelType);

        List<EnumMap<Feature,Collection<String>>> samples = extractSampleFeature(parsedTree==null?goldTree:parsedTree, props, labels, buildDictionary);
        
        if (buildDictionary) {
            int c=0;
            for (EnumMap<Feature,Collection<String>>sample:samples) {
                boolean notEC = ECCommon.NOT_EC.equals(labels[c]);
                features.addToDictionary(sample, notEC?notECFeatureWeight:1f);
                //if (!NOT_ARG.equals(SRLUtil.getMaxLabel(labels.get(c))))
                //  System.out.println(sample.get(Feature.PATH));
                labelStringMap.put(labels[c], labelStringMap.get(labels[c])+1);
                ++c;
            }
        } else {  
            List<ECSample> sampleList = new ArrayList<ECSample>();
            
            for (int i=0; i<samples.size();++i)
                if (labelStringMap.containsKey(labels[i]))
                    sampleList.add(new ECSample(samples.get(i), labels[i]));
            if (!sampleList.isEmpty()) {
                List<ECTreeSample> tSamples = trainingSamples.get(parsedTree.getFilename());
                if (tSamples == null) {
                    tSamples = new ArrayList<ECTreeSample>();
                    trainingSamples.put(parsedTree.getFilename(), tSamples);
                }
                tSamples.add(new ECTreeSample(goldTree, parsedTree, sampleList.toArray(new ECSample[sampleList.size()])));
            }
        }
    }
    
    public void finalizeDictionary(float cutoff)
    {
        FeatureSet.trimMap(labelStringMap,20);
        
        logger.info("Labels: ");
        String[] labels = labelStringMap.keys(new String[labelStringMap.size()]);
        Arrays.sort(labels);
        for (String label:labels) {
            logger.info("  "+label+" "+labelStringMap.get(label));
        }   
        
        //features.addFeatures(EnumSet.of(Feature.ECP1), (TObjectIntHashMap<String>)(labelStringMap.clone()), false);
        //features.addFeatures(EnumSet.of(Feature.ECN1), (TObjectIntHashMap<String>)(labelStringMap.clone()), false);
        
        features.rebuildMap(cutoff);

        FeatureSet.buildMapIndex(labelStringMap, 0, true);
        
        labelIndexMap = new TIntObjectHashMap<String>();
        for (TObjectIntIterator<String> iter=labelStringMap.iterator();iter.hasNext();) {
            iter.advance();
            labelIndexMap.put(iter.value(),iter.key());
        }
        labelValues = new double[labelIndexMap.size()]; 
    }

    String[] train(int folds, int threads, String[] labels) {               
        int[][] X = null;
        int[] y = null;
        int[] seed = null;
        
        boolean hasSequenceFeature = features.getFeaturesFlat().contains(Feature.ECP1) || features.getFeaturesFlat().contains(Feature.ECN1);
        List<int[]> xList = new ArrayList<int[]>();
        TIntList yList = new TIntArrayList();
        TIntList seedList = new TIntArrayList();
        int lCnt = 0;
        int treeCnt = 0;
        for (Map.Entry<String, List<ECTreeSample>> entry:trainingSamples.entrySet()) {
            for (ECTreeSample treeSample:entry.getValue()) {
                ECSample[] samples = treeSample.getECSamples();
                for (int i=0; i<samples.length;++i) {
                    if (hasSequenceFeature) {
                        if (features.getFeaturesFlat().contains(Feature.ECN1)) {
                            if (i+1<samples.length) {
                                String label = labels==null?samples[i+1].label:labels[lCnt+1];
                                samples[i].features.put(Feature.ECN1, Arrays.asList(label));
                            }
                        } else if (features.getFeaturesFlat().contains(Feature.ECP1)) {
                            if (i>0) {  
                                String label = labels==null?samples[i-1].label:labels[lCnt-1];
                                samples[i].features.put(Feature.ECP1, Arrays.asList(label));
                            }
                        }
                    }
                    
                    xList.add(features.getFeatureVector(samples[i].features));
                    yList.add(labelStringMap.get(samples[i].label));
                    seedList.add(treeCnt);
                    ++lCnt;
                }
            }
            ++treeCnt;
        }
        X = xList.toArray(new int[xList.size()][]);
        y = yList.toArray();
        seed = seedList.toArray();

        double[] weights = new double[labelStringMap.size()];
            
        String[] labelTypes = labelStringMap.keys(new String[labelStringMap.size()]);
        Arrays.sort(labelTypes);
 
        int idx=0;
        for (String label:labelTypes) {
            if (label.equals(ECCommon.NOT_EC))
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
    
    public String[] predict(TBTree tree, List<PBInstance> props) {
        List<EnumMap<Feature,Collection<String>>> samples = extractSampleFeature(tree, props, null, false);
        
        boolean hasSequenceFeature = features.getFeaturesFlat().contains(Feature.ECP1) || features.getFeaturesFlat().contains(Feature.ECN1);
        
        String[] labels = new String[samples.size()+1];
        for (int i=0; i<samples.size(); ++i) {
            if (hasSequenceFeature && i>0) samples.get(i).put(Feature.ECP1, Arrays.asList(labels[i-1]));
            labels[i]  = labelIndexMap.get(classifier.predict(features.getFeatureVector(samples.get(i))));
        }
        labels[samples.size()] = ECCommon.NOT_EC;
        return labels;
    }

    public void train(Properties prop) {            
        int folds = Integer.parseInt(prop.getProperty("crossvalidation.folds","5"));
        int threads = Integer.parseInt(prop.getProperty("crossvalidation.threads","2"));

        boolean hasSequenceFeature = features.getFeaturesFlat().contains(Feature.ECP1) || features.getFeaturesFlat().contains(Feature.ECN1);
        
        classifier = new PairWiseClassifier();
        classifier.initialize(labelStringMap, prop);
        
        int rounds = hasSequenceFeature?5:1;
        double threshold = 0.001;
        
        String[] goldLabels = null;

        List<String> labelList = new ArrayList<String>();
        for (Map.Entry<String, List<ECTreeSample>> entry:trainingSamples.entrySet())
            for (ECTreeSample treeSample:entry.getValue())
                for (ECSample sample:treeSample.getECSamples())
                    labelList.add(sample.label);
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
        
            ECScore score = new ECScore(new TreeSet<String>(Arrays.asList(labelStringMap.keys(new String[labelStringMap.size()]))));
            for (int i=0; i<labels.length; ++i)
                score.addResult(labels[i], goldLabels[i]);
        
            System.out.println(score.toString());
            
            if (1-agreement<=threshold) break;
        }
    }
}
