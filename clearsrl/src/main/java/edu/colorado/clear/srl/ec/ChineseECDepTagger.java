package edu.colorado.clear.srl.ec;

import edu.colorado.clear.common.alg.FeatureSet;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.propbank.PBUtil;
import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.treebank.TBReader;
import edu.colorado.clear.common.treebank.TBTree;
import edu.colorado.clear.common.treebank.TBUtil;
import edu.colorado.clear.common.util.ChineseUtil;
import edu.colorado.clear.common.util.PropertyUtil;
import edu.colorado.clear.srl.ec.ECCommon.Feature;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectIntMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class ChineseECDepTagger {

    enum Task {
        EXTRACT,
        TRAIN,
        VALIDATE,
        DECODE
    }
    
    private static Logger logger = Logger.getLogger("ectagger");

    @Option(name="-prop",usage="properties file")
    private File propFile = null; 
    
    @Option(name="-t",usage="task: write/train/decode")
    private Task task = Task.DECODE; 
    
    @Option(name="-c",usage="corpus name")
    private String corpus = null;
    
    @Option(name="-m",usage="model file")
    private String modelName = null;   
    
    @Option(name="-v",usage="verbose")
    private boolean verbose = false;
    
    @Option(name="-h",usage="help message")
    private boolean help = false;
    
    
    /*
    static double getFMeasure(int[] yRef, int[]ySys)
    {
        double p=0;
        int pd=0;
        double r=0;
        int rd=0;
        double f=0;
        
        for (int i=0; i<yRef.length; ++i)
        {
            p += (ySys[i]==1 && yRef[i]==1)?1:0;
            r += (ySys[i]==1 && yRef[i]==1)?1:0;
            pd += ySys[i]==1?1:0;
            rd += yRef[i]==1?1:0;
        }
        p /=pd;
        r /=rd;
        f =  2*p*r/(p+r);
            
        System.out.printf("precision: %f recall: %f, f-measure: %f\n", p, r,f);
        return f;
    }
    */
    static double getFMeasure(TObjectIntMap<String> labelMap, int[] yRef, int[]ySys)
    {
        double pLabeled = 0;
        double pUnlabeled = 0;
        
        double rLabeled = 0;
        double rUnlabeled = 0;
        
        int pdLabeled = 0;
        int pdUnlabeled = 0;
        
        int rdLabeled = 0;
        int rdUnlabeled = 0;
        
        double fLabeled=0;
        double fUnlabeled=0;
        
        int notECIdx = labelMap.get(ECCommon.NOT_EC);
        
        for (TObjectIntIterator<String> iter = labelMap.iterator(); iter.hasNext();)
        {
            iter.advance();
            if (iter.value()==notECIdx) continue;
        
            double p=0;
            double r=0;
            int pd=0;
            int rd=0;
            double f=0;
            
            for (int i=0; i<yRef.length; ++i)
            {
                int labeled = (ySys[i]==iter.value() && yRef[i]==iter.value())?1:0;
                int unlabeled = (ySys[i]!=notECIdx && yRef[i]!=notECIdx)?1:0;
                
                p += labeled;
                r += labeled;
                pd += ySys[i]==iter.value()?1:0;
                rd += yRef[i]==iter.value()?1:0;
                
                pLabeled  += labeled;
                rLabeled  += labeled;
                pdLabeled += ySys[i]==iter.value()?1:0;
                rdLabeled += yRef[i]==iter.value()?1:0;
                
                pUnlabeled  += unlabeled;
                rUnlabeled  += unlabeled;
                pdUnlabeled += ySys[i]!=notECIdx?1:0;
                rdUnlabeled += yRef[i]!=notECIdx?1:0;
            }
            p /=pd;
            r /=rd;
            f =  2*p*r/(p+r);
                
            System.out.printf("%s(%d): precision: %f recall: %f, f-measure: %f\n", iter.key(), iter.value(), p, r,f);
        }
        pLabeled /= pdLabeled;
        rLabeled /= rdLabeled;
        fLabeled =  2*pLabeled*rLabeled/(pLabeled+rLabeled);
        
        System.out.printf("labeled: precision: %f recall: %f, f-measure: %f\n", pLabeled, rLabeled, fLabeled);
        
        pUnlabeled /= pdUnlabeled;
        rUnlabeled /= rdUnlabeled;
        fUnlabeled =  2*pUnlabeled*rUnlabeled/(pUnlabeled+rUnlabeled);
        
        System.out.printf("unlabeled: precision: %f recall: %f, f-measure: %f\n", pUnlabeled, rUnlabeled, fUnlabeled);
        
        return fLabeled;
    }
/*
    static double getFMeasure(double[] yRef, double[]ySys)
    {
        double p=0;
        int pd=0;
        double r=0;
        int rd=0;
        double f=0;
        
        for (int i=0; i<yRef.length; ++i)
        {
            p += (ySys[i]==1 && yRef[i]==1)?1:0;
            r += (ySys[i]==1 && yRef[i]==1)?1:0;
            pd += ySys[i]==1?1:0;
            rd += yRef[i]==1?1:0;
        }
        p /=pd;
        r /=rd;
        f =  2*p*r/(p+r);
            
        System.out.printf("precision: %f recall: %f, f-measure: %f\n", p, r,f);
        return f;
    }
*/
    
    static String removeTraceIndex(String trace)
    {
        return TBNode.WORD_PATTERN.matcher(trace).group(1);
        
        /*
        Matcher matcher = TBTree.TRACE_PATTERN.matcher(trace);
        if (matcher.matches())
            return matcher.group(1);
        
        return trace;
        */
    }
    
    static String findLittlePro(TBNode node, String cLabel)
    {
        if (node.getWord().equals("*pro*"))
            return node.getWord();
        return ECCommon.NOT_EC;
    }
    
    static String findAllTraces(TBNode node, String cLabel, boolean uniTrace)
    {
        /*
        if (node.word.equals("*OP*"))
            return ECDepModel.NOT_EC;
        if (node.trace!=null && (node.trace.pos.equals("WHNP") || node.trace.pos.equals("WHPP")))
            return cLabel;
        */
        if (cLabel.equals(ECCommon.NOT_EC) || uniTrace)
        {
            return removeTraceIndex(node.getWord());
        }
        return cLabel+"-"+removeTraceIndex(node.getWord());
    }
    
    static String getFeatures(TBNode node, ArrayList<String> tokens, ArrayList<String> poses, ArrayList<String> labels, String cLabel)
    {
        if (node.isTerminal())
        {
            if (!node.isEC())
            {
                tokens.add(node.getWord());
                poses.add(node.getPOS());
                labels.add(cLabel);
                return ECCommon.NOT_EC;
            }
            //return ECDepModel.IS_EC;
            //return findLittlePro(node, cLabel);
            return findAllTraces(node, cLabel, true);
        }
        for (TBNode child:node.getChildren())
            cLabel = getFeatures(child, tokens, poses, labels, cLabel);
        return cLabel;
    }
    
    static void extract(Properties props) throws FileNotFoundException, IOException
    {
        /*
        Set<EnumSet<Feature>> features = new HashSet<EnumSet<Feature>>();
        {
            String[] tokens = props.getProperty("feature.value").trim().split(",");
            for (String token:tokens)
                try {
                    features.add(FeatureSet.toEnumSet(Feature.class, token));
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
        }

        ECDepModel model = new ECDepModel(features);
        
        Map<String, TBTree[]> tbMapTrain = TBUtil.readTBDir(props.getProperty("corpus"), props.getProperty("train.regex"));
        System.out.println(props.getProperty("corpus"));
        System.out.println(props.getProperty("train.regex"));
        
        
        Map<String, TBTree[]> tbMapTest = TBUtil.readTBDir(props.getProperty("corpus"), props.getProperty("test.regex"));
        
        for (Map.Entry<String, TBTree[]> entry : tbMapTrain.entrySet())
            for (TBTree tree:entry.getValue())
                model.addTrainingSentence(tree, true);
        
        model.features.rebuildMap(5, 5);
        
        for (Map.Entry<String, TBTree[]> entry : tbMapTrain.entrySet())
            for (TBTree tree:entry.getValue())
                model.addTrainingSentence(tree, false);
        
        //System.out.printf("hit: %d, total: %d\n", model.hit, model.total);
    */
        
        /*
        PrintStream tout = new PrintStream(new FileOutputStream(props.getProperty("test.file")));
        
        for (Map.Entry<String, TBTree[]> entry : tbMapTest.entrySet())
            for (TBTree tree:entry.getValue())
            {
                tokens.clear(); poses.clear(); labels.clear();
                getFeatures(tree.getRootNode(), tokens, poses, labels, ECDepModel.NOT_EC);                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    
                
                model.writeSample(tout, tokens.toArray(new String[tokens.size()]), poses.toArray(new String[poses.size()]), labels.toArray(new String[labels.size()]), InstanceFormat.valueOf(props.getProperty("file_format")));               
                //model.addTrainingSentence(tokens.toArray(new String[tokens.size()]), poses.toArray(new String[poses.size()]), labels.toArray(new String[labels.size()]));
            }
    
        tout.close();
        */
        /*
        ObjectOutputStream mOut = new ObjectOutputStream(new FileOutputStream(props.getProperty("model_file")));
        mOut.writeObject(model);
        mOut.close();
        */
        //model.writeTrainingData(props.getProperty("train.file"), InstanceFormat.valueOf(props.getProperty("file_format")));
    }
    
    
    static void validate(ECDepModel model, Properties props, ChineseUtil langUtil) throws IOException, ClassNotFoundException
    {
        Properties validateProps = PropertyUtil.filterProperties(props, "validate.", true);
        
        if (model == null)
        {
            ObjectInputStream mIn = new ObjectInputStream(new GZIPInputStream(new FileInputStream(validateProps.getProperty("model_file"))));
            model = (ECDepModel)mIn.readObject();
            mIn.close();
            model.setLangUtil(langUtil);
        }

        System.err.println(model.labelStringMap);
        
        ECScore score = new ECScore(new TreeSet<String>(Arrays.asList(model.labelStringMap.keys(new String[model.labelStringMap.size()]))));
        
        String corpus = validateProps.getProperty("corpus");
        corpus=corpus==null?"":corpus+"."; 
        
        Map<String, TBTree[]> tbMapValidate = null;
        if (validateProps.getProperty(corpus+"tbdepdir")!=null)
        	tbMapValidate = TBUtil.readTBDir(validateProps.getProperty(corpus+"tbdir"), validateProps.getProperty(corpus+"tb.regex"), validateProps.getProperty(corpus+"tbdepdir"), 8, 10);
        else 
        	tbMapValidate = TBUtil.readTBDir(validateProps.getProperty(corpus+"tbdir"), validateProps.getProperty(corpus+"tb.regex"), langUtil.getHeadRules());
        
        Map<String, TBTree[]> parseValidate = null;
        if (validateProps.getProperty(corpus+"parsedepdir")!=null)
        	parseValidate = TBUtil.readTBDir(validateProps.getProperty(corpus+"parsedir"), validateProps.getProperty(corpus+"tb.regex"), validateProps.getProperty(corpus+"parsedepdir"), 8, 10);
        else
        	parseValidate = TBUtil.readTBDir(validateProps.getProperty(corpus+"parsedir"), validateProps.getProperty(corpus+"tb.regex"), langUtil.getHeadRules());
        
        Map<String, SortedMap<Integer, List<PBInstance>>>  propValidate = 
                PBUtil.readPBDir(validateProps.getProperty(corpus+"propdir"), validateProps.getProperty(corpus+"pb.regex"), new TBReader(parseValidate));
        
        for (Map.Entry<String, TBTree[]> entry : parseValidate.entrySet())
        {
            TBTree[] tbTrees = tbMapValidate.get(entry.getKey());
            TBTree[] parseTrees = entry.getValue();
            SortedMap<Integer, List<PBInstance>> pbInstances = propValidate.get(entry.getKey());

            for (int i=0; i<parseTrees.length; ++i)
            {
                List<PBInstance> propList = pbInstances==null?null:pbInstances.get(i);
                String[] goldLabels = ECCommon.getECLabels(tbTrees[i], model.labelType);
                String[] labels = model.predict(parseTrees[i], propList);
                for (int l=0; l<labels.length; ++l)
                    score.addResult(labels[l], goldLabels[l]);
                /*
                Map<Integer, String> labels = model.predict(parseTrees[i], propList);
                Map<Integer, String> goldLabels = new TreeMap<Integer, String>();
                for (ECDependent dependent:ECDependent.getDependents(tbTrees[i]))
                    goldLabels.put(dependent.getPredicate().getTokenIndex(), ECCommon.convertLabel(dependent.getSubject(), model.labelType));
                
                for (Map.Entry<Integer, String> e2:labels.entrySet())
                    if (!goldLabels.containsKey(e2.getKey()))
                        goldLabels.put(e2.getKey(), ECCommon.NOT_EC);
                
                for (Map.Entry<Integer, String> e2:goldLabels.entrySet()) {
                    if (!labels.containsKey(e2.getKey()))
                        labels.put(e2.getKey(), ECCommon.NOT_EC);
                    score.addResult(labels.get(e2.getKey()), e2.getValue());
                }
                */
                /*
                {
                    boolean hasPro = false;
                    boolean correctPro = true;
                    
                    for (Map.Entry<Integer, String> e2:goldLabels.entrySet()) {
                        if (e2.getValue().toLowerCase().equals("*pro*") || labels.get(e2.getKey()).toLowerCase().equals("*pro*"))
                            if (e2.getValue().toLowerCase().equals("*pro*"))
                                hasPro = true;
                            if (!e2.getValue().equals(labels.get(e2.getKey())))
                                correctPro = false;
                    }

                    if (hasPro && !correctPro) {
                        StringBuilder builder = new StringBuilder(tbTrees[i].getFilename()+" "+tbTrees[i].getIndex()+"\n");
                        builder.append(tbTrees[i].toText(true)); builder.append("\n");
                        
                        TBNode[] nodes = parseTrees[i].getTokenNodes();
                        
                        String label;
                        for (TBNode node:nodes) {
                            if ((label=labels.get(node.getTokenIndex()))!=null && !label.equals(ECCommon.NOT_EC))
                                builder.append(label+' ');
                            builder.append(node.getWord()+' ');
                        }
                        builder.append("\n");
                        
                        if (propList!=null)
                            for (PBInstance prop:propList)
                                builder.append(prop.toText()+'\n');
                        builder.append(parseTrees[i]); builder.append("\n");
                        builder.append(tbTrees[i]); builder.append("\n");
                        
                        System.err.println(builder.toString());
                    }
                }*/
            }
        }
        System.out.println(score.toString());
    }
    
    static void train(Properties props, ChineseUtil langUtil) throws IOException, ClassNotFoundException
    {   
        Properties trainProps = PropertyUtil.filterProperties(props, "train.", true);
        Set<EnumSet<Feature>> features = new HashSet<EnumSet<Feature>>();
        {
            String[] tokens = trainProps.getProperty("feature").trim().split(",");
            for (String token:tokens)
                try {
                    features.add(FeatureSet.toEnumSet(Feature.class, token));
                } catch (IllegalArgumentException e) {
                    System.err.println(e);
                }
        }
        features = FeatureSet.getBigramSet(features);
        
        logger.info("features: "+features);

        ECDepModel model = new ECDepModel(features);
        model.setLangUtil(langUtil);
        
        String corpus = trainProps.getProperty("corpus");
        corpus=corpus==null?"":corpus+"."; 

        Map<String, TBTree[]> tbMapTrain = null;
        if (trainProps.getProperty(corpus+"tbdepdir")!=null)
        	tbMapTrain = TBUtil.readTBDir(trainProps.getProperty(corpus+"tbdir"), trainProps.getProperty(corpus+"tb.regex"), trainProps.getProperty(corpus+"tbdepdir"), 8, 10);
        else 
        	tbMapTrain = TBUtil.readTBDir(trainProps.getProperty(corpus+"tbdir"), trainProps.getProperty(corpus+"tb.regex"), langUtil.getHeadRules());
        
        Map<String, TBTree[]> parseTrain = null;
        if (trainProps.getProperty(corpus+"parsedepdir")!=null)
        	parseTrain = TBUtil.readTBDir(trainProps.getProperty(corpus+"parsedir"), trainProps.getProperty(corpus+"tb.regex"), trainProps.getProperty(corpus+"parsedepdir"), 8, 10);
        else
        	parseTrain = TBUtil.readTBDir(trainProps.getProperty(corpus+"parsedir"), trainProps.getProperty(corpus+"tb.regex"), langUtil.getHeadRules());
        
        Map<String, SortedMap<Integer, List<PBInstance>>>  propTrain = 
                PBUtil.readPBDir(trainProps.getProperty(corpus+"propdir"), trainProps.getProperty(corpus+"pb.regex"), new TBReader(parseTrain));

        for (Map.Entry<String, TBTree[]> entry : parseTrain.entrySet())
        {
            TBTree[] tbTrees = tbMapTrain.get(entry.getKey());
            TBTree[] parseTrees = entry.getValue();
            SortedMap<Integer, List<PBInstance>> pbInstances = propTrain.get(entry.getKey());
            
            for (int i=0; i<parseTrees.length; ++i) {
                /*System.out.println(tbTrees[i]);
                for (TBNode node : tbTrees[i].getRootNode().getTerminalNodes()) {
                    if (node.isEC()|| node.getParent()!=null)
                        if (node.getParent().hasFunctionTag("SBJ")) {
                            System .out.print(node.getWord()+"-sbj-"+node.getHeadOfHead().getWord()+' ');
                            continue;
                        } else if (node.getParent().hasFunctionTag("OBJ")) {
                            System .out.print(node.getWord()+"-obj-"+node.getHeadOfHead().getWord()+' ');
                            continue;
                        }
                    System .out.print(node.getWord()+' ');
                }
                System .out.print("\n");*/
                model.addTrainingSentence(tbTrees[i], parseTrees[i], pbInstances==null?null:pbInstances.get(i), true);
            }
        }
        model.finalizeDictionary(Integer.parseInt(trainProps.getProperty("dictionary.cutoff", "2")));
        
        for (Map.Entry<String, TBTree[]> entry : parseTrain.entrySet())
        {
            TBTree[] tbTrees = tbMapTrain.get(entry.getKey());
            TBTree[] parseTrees = entry.getValue();
            SortedMap<Integer, List<PBInstance>> pbInstances = propTrain.get(entry.getKey());
            
            for (int i=0; i<parseTrees.length; ++i)
                model.addTrainingSentence(tbTrees[i], parseTrees[i], pbInstances==null?null:pbInstances.get(i), false);
        }

        model.train(trainProps);
        
        ObjectOutputStream mOut = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(trainProps.getProperty("model_file"))));
        mOut.writeObject(model);
        mOut.close();
        
        //validate(model, props);
        
    }
    
    public static void main(String[] args) throws Exception
    {
        ChineseECDepTagger options = new ChineseECDepTagger();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
            if (options.task==null)
                options.help = true;
        } catch (CmdLineException e)
        {
            System.err.println("invalid options:"+e);
            parser.printUsage(System.err);
            System.exit(0);
        }
        
        if (options.help){
            parser.printUsage(System.err);
            System.exit(0);
        }
        
        if (options.verbose)
            logger.setLevel(Level.FINE);
        
        Properties props = new Properties();
        Reader in = new InputStreamReader(new FileInputStream(options.propFile), "UTF-8");
        props.load(in);
        in.close();
        props = PropertyUtil.resolveEnvironmentVariables(props);
        props = PropertyUtil.filterProperties(props, "ectagger.", true);
        
        if (options.modelName!=null)
            props.setProperty("model_file", options.modelName);
        
        if (options.corpus!=null)
            props.setProperty("corpus", options.corpus);
        logger.info(PropertyUtil.toString(props));
        
        ChineseUtil chLangUtil = new ChineseUtil();
        if (!chLangUtil.init(PropertyUtil.filterProperties(props, "chinese.", true)))
            System.exit(-1);
        
        switch (options.task) {
        case EXTRACT:
            extract(props);
            break;
        case TRAIN:
            if (options.corpus!=null)
                props.setProperty("train.corpus", options.corpus);
            train(props, chLangUtil);
            break;
        case VALIDATE:
            if (options.corpus!=null)
                props.setProperty("validate.corpus", options.corpus);
            validate(null, props, chLangUtil);
            break;
        case DECODE:
            break;
        }
    }
}
