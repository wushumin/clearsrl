package clearsrl;

import clearcommon.alg.FeatureSet;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import clearsrl.SRLModel.Feature;
import clearsrl.Sentence.Source;

public class TrainSRL {
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
        
        int tCount = 0;
        int gCount = 0;

        int hCnt = 0;
        int tCnt = 0;
        
        boolean modelPredicate = !props.getProperty("model_predicate", "false").equals("false");
    
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
                
                Map<String, Sentence[]> corpusMap = Sentence.readCorpus(srcProps, srcSet.contains(Source.PARSE)?Source.PARSE:Source.TREEBANK, srcSet);
                
                int weight = Integer.parseInt(srcProps.getProperty("weight", "1"));
                for (String key:corpusMap.keySet())
                    trainWeights.put(key, weight);
                
                if (sentenceMap==null) sentenceMap = corpusMap;
                else sentenceMap.putAll(corpusMap);
            }

            System.out.printf("%d training files read\n",sentenceMap.size());

            if (!srcSet.contains(Source.PARSE))
                model.setTrainGoldParse(true);

            model.initialize(props);
            for (Map.Entry<String, Sentence[]> entry: sentenceMap.entrySet()) {
                int weight = trainWeights.get(entry.getKey());
                weight = weight==0?1:weight;
                
                System.out.println("Processing "+entry.getKey());
                for (Sentence sent:entry.getValue()) {
                    ArrayList<SRInstance> srls = new ArrayList<SRInstance>();
                        
                    
                    for (int w=0; w<weight; ++w)
                        model.addTrainingSentence(sent, THRESHOLD);
                }
            }
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
        System.gc();
        model.train(props);
        
        ObjectOutputStream mOut = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(props.getProperty("model_file"))));
        mOut.writeObject(model);
        mOut.close();
        
        System.out.println("Model saved to "+props.getProperty("model_file"));  
    }

}
