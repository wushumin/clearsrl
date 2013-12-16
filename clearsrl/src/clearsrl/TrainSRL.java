package clearsrl;

import clearcommon.alg.FeatureSet;
import clearcommon.propbank.DefaultPBTokenizer;
import clearcommon.propbank.OntoNotesTokenizer;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBTokenizer;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.SerialTBFileReader;
import clearcommon.treebank.TBFileReader;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import clearcommon.treebank.ParseException;
import clearcommon.util.FileUtil;
import clearcommon.util.LanguageUtil;
import clearcommon.util.PropertyUtil;

import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
            String sourceList = props.getProperty("sources","");
            String[] sources = sourceList.trim().split("\\s*,\\s*");
            
            Map<String, TBTree[]> treeBank = null;
            Map<String, SortedMap<Integer, List<PBInstance>>>  propBank = null;
            Map<String, TBTree[]> parsedTreeBank = props.getProperty("goldparse", "false").equals("true")?null:new TreeMap<String, TBTree[]>();
            Map<String, Integer> trainWeights =new TreeMap<String, Integer>();

            for (String source:sources) {
                System.out.println("Processing source "+source);
                Properties srcProps = source.isEmpty()?props:PropertyUtil.filterProperties(props, source+".", true);
                System.out.println(PropertyUtil.toString(srcProps));
                
                String treeRegex = srcProps.getProperty("tb.regex");
                Map<String, TBTree[]> srcTreeBank = TBUtil.readTBDir(srcProps.getProperty("tbdir"), treeRegex);
                
                String filename = srcProps.getProperty("pb.filelist");
                List<String> fileList = filename==null?FileUtil.getFiles(new File(srcProps.getProperty("pbdir")), srcProps.getProperty("pb.regex"), true)
                        :FileUtil.getFileList(new File(srcProps.getProperty("pbdir")), new File(filename), true);
                
                PBTokenizer tokenzier = srcProps.getProperty("pb.tokenizer")==null?(srcProps.getProperty("data.format", "default").equals("ontonotes")?new OntoNotesTokenizer():new DefaultPBTokenizer()):(PBTokenizer)Class.forName(props.getProperty("pb.tokenizer")).newInstance();
                
                Map<String, SortedMap<Integer, List<PBInstance>>> srcPropBank = PBUtil.readPBDir(fileList, new TBReader(srcTreeBank), tokenzier);

                int weight = Integer.parseInt(srcProps.getProperty("weight", "1"));
                
                if (parsedTreeBank!=null)
                    for (Map.Entry<String, SortedMap<Integer, List<PBInstance>>> entry:srcPropBank.entrySet())
                    {
                        try {
                            TBFileReader tbreader    = new SerialTBFileReader(srcProps.getProperty("parsedir"), entry.getKey());
                            System.out.println("Reading "+srcProps.getProperty("parsedir")+File.separatorChar+entry.getKey());
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
                
                if (treeBank==null) treeBank = srcTreeBank;
                else treeBank.putAll(srcTreeBank);
                
                for (String key:srcPropBank.keySet())
                    trainWeights.put(key, weight);
                
                if (propBank==null) propBank = srcPropBank;
                else propBank.putAll(srcPropBank);
            }

            System.out.println(propBank.size());

            if (parsedTreeBank==null) {
                parsedTreeBank = treeBank;
                model.setTrainGoldParse(true);
            }

            model.initialize(props);
            for (Map.Entry<String, TBTree[]> entry: parsedTreeBank.entrySet())
            {
                SortedMap<Integer, List<PBInstance>> pbFileMap = propBank.get(entry.getKey());
                if (pbFileMap==null) continue;
                int weight = trainWeights.get(entry.getKey());
                
                System.out.println("Processing "+entry.getKey());
                TBTree[] trees = entry.getValue();
                
                for (int i=0; i<trees.length; ++i)
                {
                    TBUtil.linkHeads(trees[i], langUtil.getHeadRules());
                    List<PBInstance> pbInstances = pbFileMap.get(i);

                    ArrayList<SRInstance> srls = new ArrayList<SRInstance>();
                        
                    if (pbInstances!=null)  {
                        if (!pbInstances.isEmpty())
                            TBUtil.linkHeads(pbInstances.get(0).getTree(), langUtil.getHeadRules());
                        for (PBInstance instance:pbInstances) {
                            srls.add(new SRInstance(instance));
                            if (instance.getArgs().length>1)
                                rolesetArg.put(instance.getRoleset(), rolesetArg.get(instance.getRoleset())+1);
                            else
                                rolesetEmpty.put(instance.getRoleset(), rolesetEmpty.get(instance.getRoleset())+1);
                        }
                    }
                    for (int w=0; w<weight; ++w)
                        model.addTrainingSentence(trees[i], srls, null, THRESHOLD);
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
            ArrayList<CoNLLSentence> training = CoNLLSentence.read(new FileReader(props.getProperty("input")), true);
            model.initialize(props);
            for (CoNLLSentence sentence:training) {
                TBUtil.linkHeads(sentence.parse, langUtil.getHeadRules());
                model.addTrainingSentence(sentence.parse, Arrays.asList(sentence.srls), sentence.namedEntities, THRESHOLD);
            }
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
