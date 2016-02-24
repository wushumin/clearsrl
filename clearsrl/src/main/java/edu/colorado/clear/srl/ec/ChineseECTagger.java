package edu.colorado.clear.srl.ec;

import edu.colorado.clear.common.alg.FeatureSet;
import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.util.ChineseUtil;
import edu.colorado.clear.common.util.PropertyUtil;
import edu.colorado.clear.srl.Sentence;
import edu.colorado.clear.srl.Sentence.Source;
import edu.colorado.clear.srl.ec.ECCommon.Feature;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class ChineseECTagger {

    enum Task {
        TRAIN,
        VALIDATE,
        DECODE
    }
    
    private static Logger logger = Logger.getLogger("clearsrl.ec");

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

    static String removeTraceIndex(String trace) {
        return TBNode.WORD_PATTERN.matcher(trace).group(1);
        
        /*
        Matcher matcher = TBTree.TRACE_PATTERN.matcher(trace);
        if (matcher.matches())
            return matcher.group(1);
        
        return trace;
        */
    }
    
    static String findLittlePro(TBNode node, String cLabel) {
        if (node.getWord().equals("*pro*"))
            return node.getWord();
        return ECCommon.NOT_EC;
    }
    
    static String findAllTraces(TBNode node, String cLabel, boolean uniTrace) {
        /*
        if (node.word.equals("*OP*"))
            return ECCommon.NOT_EC;
        if (node.trace!=null && (node.trace.pos.equals("WHNP") || node.trace.pos.equals("WHPP")))
            return cLabel;
        */
        if (cLabel.equals(ECCommon.NOT_EC) || uniTrace)
            return removeTraceIndex(node.getWord());
        return cLabel+"-"+removeTraceIndex(node.getWord());
    }
    
    static String getFeatures(TBNode node, ArrayList<String> tokens, ArrayList<String> poses, ArrayList<String> labels, String cLabel){
        if (node.isTerminal()) {
            if (!node.isEC()) {
                tokens.add(node.getWord());
                poses.add(node.getPOS());
                labels.add(cLabel);
                return ECCommon.NOT_EC;
            }
            //return ECModel.IS_EC;
            //return findLittlePro(node, cLabel);
            return findAllTraces(node, cLabel, true);
        }
        for (TBNode child:node.getChildren())
            cLabel = getFeatures(child, tokens, poses, labels, cLabel);
        return cLabel;
    }

    static void validate(ECModel model, Properties inProps, ChineseUtil langUtil) throws IOException, ClassNotFoundException {
        Properties props = PropertyUtil.filterProperties(inProps, "validate.", true);
        
        if (model == null) {
            ObjectInputStream mIn = new ObjectInputStream(new GZIPInputStream(new FileInputStream(props.getProperty("model_file"))));
            model = (ECModel)mIn.readObject();
            mIn.close();
            model.setLangUtil(langUtil);
        }

        if (model instanceof ECDepModel) {
        	((ECDepModel)model).setQuickClassify(!props.getProperty("quickClassify","false").equals("false"));
        	((ECDepModel)model).setFullPredict(!props.getProperty("fullPredict","false").equals("false"));
        }
        
        TreeSet<String> labelSet = new TreeSet<String>(Arrays.asList(model.labelStringMap.keys(new String[model.labelStringMap.size()])));
        labelSet.add("*OP*");
        
        ECScore score = new ECScore(labelSet);
        ECScore score2 = new ECScore(labelSet);
        
        ECScore dScore = null;
        ECScore dScore2 = null;

        ECScore hScore = null;
        ECScore hScore2 = null;

        if (model instanceof ECDepModel) {
        	dScore = new ECScore(labelSet);
        	dScore2 = new ECScore(labelSet);
        	
        	hScore = new ECScore(labelSet);
        	hScore2 = new ECScore(labelSet);
        }
        
        String corpus = props.getProperty("corpus");
        corpus=corpus==null?"":corpus+"."; 
       
        Map<String, Sentence[]> sentenceMap = Sentence.readCorpus(PropertyUtil.filterProperties(props, corpus, true), Source.PARSE_HEAD, 
        		EnumSet.of(Source.PARSE_HEAD, Source.SRL, Source.TB_HEAD), langUtil);

        int ecCount=0;
        int ecDepCount=0;
        
        for (Map.Entry<String, Sentence[]> entry : sentenceMap.entrySet()) {
            logger.info("Validating: "+entry.getKey());
            for (Sentence sent: entry.getValue()) {
            	String[] goldLabels = ECCommon.getECLabels(sent.treeTB, model.labelType);
            	
                String[] labels = null;
                if (model instanceof ECDepModel) {
                	((ECDepModel)model).setQuickClassify(true);
                	String[][] depLabels = ((ECDepModel)model).predictDep(sent.parse, sent.props);
                	labels = ECDepModel.makeLinearLabel(sent.parse, depLabels);
                	String[][] goldDepLabels = ECCommon.getECDepLabels(sent.treeTB, model.labelType);
                	
                	
                	for (int h=0; h<depLabels.length;++h)
                		for (int t=0; t<depLabels[h].length;++t) {
                			//if (depLabels[h][t]!=null&&!ECCommon.NOT_EC.equals(depLabels[h][t]) ||
                			//		goldDepLabels[h][t]!=null&&!ECCommon.NOT_EC.equals(goldDepLabels[h][t]))
                				dScore.addResult(depLabels[h][t]==null?ECCommon.NOT_EC:depLabels[h][t],
                						goldDepLabels[h][t]==null?ECCommon.NOT_EC:goldDepLabels[h][t]);
                				if (goldDepLabels[h][t]!=null&&!ECCommon.NOT_EC.equals(goldDepLabels[h][t])) {
                					String[] tokens = goldDepLabels[h][t].trim().split("\\s+");
                					if (tokens.length>1)
                						System.err.println(Arrays.asList(tokens));
                					ecDepCount+=tokens.length;
                				}
                		}

                	for (int h=0; h<depLabels.length; ++h) {
                		String headLabel = null;
                		String goldHeadLabel = null;
                		for (int t=0; t<depLabels[h].length;++t) {
                			if (depLabels[h][t]!=null && !ECCommon.NOT_EC.equals(depLabels[h][t]))
                				headLabel = headLabel==null?depLabels[h][t]:headLabel+' '+depLabels[h][t];
            				if (goldDepLabels[h][t]!=null && !ECCommon.NOT_EC.equals(goldDepLabels[h][t]))
                				goldHeadLabel = goldHeadLabel==null?goldDepLabels[h][t]:goldHeadLabel+' '+goldDepLabels[h][t];
                		}
                		hScore.addResult(headLabel==null?ECCommon.NOT_EC:headLabel, goldHeadLabel==null?ECCommon.NOT_EC:goldHeadLabel);
                	}
                } else 
                	labels = model.predict(sent.parse, sent.props);
                
                for (int l=0; l<labels.length; ++l) {
                    score.addResult(labels[l], goldLabels[l]);
                }
                
                String[] labels2 = null;
                if (model instanceof ECDepModel && ((ECDepModel)model).stage2Classifier!=null) {
                	((ECDepModel)model).setQuickClassify(false);
                	String[][] depLabels = ((ECDepModel)model).predictDep(sent.parse, sent.props);
                	labels2 = ECDepModel.makeLinearLabel(sent.parse, depLabels);
                	String[][] goldDepLabels = ECCommon.getECDepLabels(sent.treeTB, model.labelType);
                	
                	for (int h=0; h<depLabels.length;++h)
                		for (int t=0; t<depLabels[h].length;++t)
                			if (depLabels[h][t]!=null&&!ECCommon.NOT_EC.equals(depLabels[h][t]) ||
                					goldDepLabels[h][t]!=null&&!ECCommon.NOT_EC.equals(goldDepLabels[h][t]))
                				dScore2.addResult(depLabels[h][t]==null?ECCommon.NOT_EC:depLabels[h][t],
                						goldDepLabels[h][t]==null?ECCommon.NOT_EC:goldDepLabels[h][t]);
                	
                	for (int h=0; h<depLabels.length; ++h) {
                		String headLabel = null;
                		String goldHeadLabel = null;
                		for (int t=0; t<depLabels[h].length;++t) {
                			if (depLabels[h][t]!=null && !ECCommon.NOT_EC.equals(depLabels[h][t]))
                				headLabel = headLabel==null?depLabels[h][t]:headLabel+' '+depLabels[h][t];
            				if (goldDepLabels[h][t]!=null && !ECCommon.NOT_EC.equals(goldDepLabels[h][t]))
                				goldHeadLabel = goldHeadLabel==null?goldDepLabels[h][t]:goldHeadLabel+' '+goldDepLabels[h][t];
                		}
                		hScore2.addResult(headLabel==null?ECCommon.NOT_EC:headLabel, goldHeadLabel==null?ECCommon.NOT_EC:goldHeadLabel);
                	}
                	
                } 
                
                boolean same=true;
                
                //if (labels2!=null) {
	                for (int l=0; l<labels2.length; ++l) {
	                    score2.addResult(labels2[l], goldLabels[l]);
	                    if (goldLabels[l]!=null&&!ECCommon.NOT_EC.equals(goldLabels[l]))
	                    	ecCount+=goldLabels[l].trim().split("\\s+").length;
	                }
	                    
	               
	                for (int l=0; l<labels.length; ++l)
	                	if (!labels[l].equals(labels2[l])) {
	                		same = false;
	                		break;
	                	}
                //}
                if (!same) {
                	System.out.println(sent.treeTB.getFilename()+" "+sent.treeTB.getIndex());
                	System.out.println(sent.treeTB.toPrettyParse());
                	System.out.println(sent.parse.toPrettyParse());
	                TBNode[] tokens = sent.treeTB.getTokenNodes();
	                printEC(tokens, goldLabels);
	                printEC(tokens, labels);
	                printEC(tokens, labels2);
	                if (sent.props!=null) 
	                	for (PBInstance prop:sent.props)
	                		System.out.println(prop.toText());
	                //System.out.println(score.toString(score.countMatrix, true));
	                //System.out.println(score2.toString(score2.countMatrix, true));	                
	                System.out.println("");
                }
                /*
                for (int l=0; l<labels.length; ++l)
                	if (ECCommon.NOT_EC.equals(goldLabels[l]))
                		System.out.print(goldLabels);*/
                
            }
        }
        System.out.println("linear");
        System.out.println(score.toString());
        if (model instanceof ECDepModel && ((ECDepModel)model).stage2Classifier!=null)
        	System.out.println(score2.toString());
        
        if (dScore!=null) {
        	System.out.println("dependency");
        	System.out.println(dScore.toString());
	        if (dScore2!=null && ((ECDepModel)model).stage2Classifier!=null)
	        	System.out.println(dScore2.toString());
        }
        
        if (hScore!=null) {
        	System.out.println("head");
        	System.out.println(hScore.toString());
        	if (hScore2!=null && ((ECDepModel)model).stage2Classifier!=null)
             	System.out.println(hScore2.toString());
        }

        System.out.println("dependency confusion matrix");
        if (dScore!=null) {
        	System.out.println(dScore.getLabeledMatrix());
        }
        
        if (dScore2!=null) {
        	System.out.println(dScore2.getLabeledMatrix());
        }
        
        System.out.printf("EC count: %d, dep count: %d\n", ecCount, ecDepCount);
        
    }
            
    static void printEC(TBNode[] nodes, String[] labels) {
    	for (int i=0; i<nodes.length;++i) {
    		if (!ECCommon.NOT_EC.equals(labels[i]))
    			System.out.print(labels[i]+' ');
    		System.out.print(nodes[i].getWord()+' ');
    	}
    	System.out.println(ECCommon.NOT_EC.equals(labels[nodes.length])?"":labels[nodes.length]);
    }
    
    static Set<EnumSet<Feature>> makeTypedBigramFeatures(Set<EnumSet<Feature>> features, String[] lprefixes, String[] rprefixes) {
    	Set<EnumSet<Feature>> ret = new HashSet<EnumSet<Feature>>(features);
    	Map<String, Set<Feature>> fSSetMap = new HashMap<String, Set<Feature>>();
    	
    	for (EnumSet<Feature> fSet:features)
    		for (Feature fVal:fSet) {
    			String prefix=fVal.toString();
    			if (prefix.indexOf('_')>0)
    				prefix = prefix.substring(0, prefix.indexOf('_'));
    			Set<Feature> fSSet = fSSetMap.get(prefix);
    			if (fSSet==null)
    				fSSetMap.put(prefix, fSSet=new HashSet<Feature>());
    			fSSet.add(fVal);
    		}
    			
    	String[] prefixes = fSSetMap.keySet().toArray(new String[fSSetMap.size()]);
    	
    	if (lprefixes==null || rprefixes==null) {
    		int len = prefixes.length*(prefixes.length-1)/2;
    		lprefixes = new String[len];
    		rprefixes = new String[len];
    		len=0;
    		for (int i=0; i<prefixes.length; ++i)
    			for (int j=1+i; j<prefixes.length; ++j) {
    				lprefixes[len] = prefixes[i];
    				rprefixes[len++] = prefixes[j];
    			}
    	}
    	
    	for (int i=0; i<lprefixes.length; ++i) {
    		Set<Feature> lhs = fSSetMap.get(lprefixes[i]);
    		Set<Feature> rhs = fSSetMap.get(rprefixes[i]);
    		for (Feature lf:lhs)
				for (Feature rf:rhs)
					ret.add(EnumSet.of(lf, rf));
    	}
    		
    	
    	/*
    	for (int i=0; i<prefixes.length; ++i) {
    		Set<Feature> lhs = fSSetMap.get(prefixes[i]);
    		for (int j=1+i; j<prefixes.length; ++j) {
    			Set<Feature> rhs = fSSetMap.get(prefixes[j]);
    			for (Feature lf:lhs)
    				for (Feature rf:rhs)
    					ret.add(EnumSet.of(lf, rf));
    		}
    	}*/
    	return ret;
    }
    
    
    static void train(Properties inProps, ChineseUtil langUtil) throws IOException, ClassNotFoundException {  
        Properties props = PropertyUtil.filterProperties(inProps, "train.", true);
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
        //String[] lhs = {"D","D","D",  "H","H"};
        //String[] rhs = {"H","T","SRL","T","SRL"};
        //features = makeTypedBigramFeatures(features, lhs, rhs);
        
        features = makeTypedBigramFeatures(features, null, null);
        //features = FeatureSet.getBigramSet(features);
        
        logger.info("features: "+features);
        
        boolean useDepModel = !props.getProperty("dependency", "false").equals("false");

        ECModel model = useDepModel?new ECDepModel(features):new ECModel(features);
        model.setLangUtil(langUtil);
        
        String corpus = props.getProperty("corpus");
        corpus=corpus==null?"":corpus+"."; 
        
        Map<String, Sentence[]> sentenceMap = Sentence.readCorpus(PropertyUtil.filterProperties(props, corpus, true), Source.PARSE_HEAD, 
        		EnumSet.of(Source.PARSE_HEAD, Source.SRL, Source.TB_HEAD), langUtil);

        for (Map.Entry<String, Sentence[]> entry : sentenceMap.entrySet())
            for (Sentence sent:entry.getValue()) {
                model.addTrainingSentence(sent.treeTB, sent.parse, sent.props, true);
                
                /*
                logger.info(tbTrees[i].toString());
                
                StringBuilder builder = new StringBuilder();
                for (TBNode node : tbTrees[i].getTerminalNodes()) {
                    if (node.isEC())
                        if (node.getParent().hasFunctionTag("SBJ")) {
                            builder.append(node.getWord()+"-sbj-"+node.getHeadOfHead().getWord()+' ');
                            continue;
                        } else if (node.getParent().hasFunctionTag("OBJ")) {
                            builder.append(node.getWord()+"-obj-"+node.getHeadOfHead().getWord()+' ');
                            continue;
                        }
                    builder.append(node.getWord()+' ');
                }
                logger.info(builder.toString());
                */
            }

        System.out.printf("%d/%d %d/%d\n",model.count,model.tCount, model.hCount, model.thCount);
        model.finalizeDictionary(Integer.parseInt(props.getProperty("dictionary.cutoff", "5")));
        
        for (Map.Entry<String, Sentence[]> entry : sentenceMap.entrySet())
            for (Sentence sent:entry.getValue())
                model.addTrainingSentence(sent.treeTB, sent.parse, sent.props, false);

        model.train(props);
        
        ObjectOutputStream mOut = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(props.getProperty("model_file"))));
        mOut.writeObject(model);
        mOut.close();
        
        //validate(model, props);   
    }
   
	static void decode(Properties parentProps, ChineseUtil langUtil) throws IOException, ClassNotFoundException {
	    // TODO Auto-generated method stub
        Properties props= PropertyUtil.filterProperties(parentProps, "decode.", true);
    
        ObjectInputStream mIn = new ObjectInputStream(new GZIPInputStream(new FileInputStream(props.getProperty("model_file"))));
        ECModel model = (ECModel)mIn.readObject();
        mIn.close();
        model.setLangUtil(langUtil);

        if (model instanceof ECDepModel) {
        	((ECDepModel)model).setQuickClassify(!props.getProperty("quickClassify","false").equals("false"));
        	((ECDepModel)model).setFullPredict(!props.getProperty("fullPredict","false").equals("false"));
        }

        String corpus = props.getProperty("corpus");
        corpus=corpus==null?"":corpus+"."; 
        
        Map<String, Sentence[]> sentenceMap = Sentence.readCorpus(PropertyUtil.filterProperties(props, corpus, true), Source.PARSE_HEAD, 
        		EnumSet.of(Source.PARSE_HEAD, Source.SRL), langUtil);

        File outputDir = new File(props.getProperty(corpus+"ecdep.dir"));
        
        for (Map.Entry<String, Sentence[]> entry : sentenceMap.entrySet()) {
            logger.info("Decoding: "+entry.getKey());

            File outFile = new File(outputDir, entry.getKey()+".ec");
            if (outFile.getParentFile()!=null)
                outFile.getParentFile().mkdirs();
            PrintWriter writer = new PrintWriter(outFile);
            
            for (Sentence sent:entry.getValue()) {
                String[] labels = null;
                if (model instanceof ECDepModel) {
                	((ECDepModel)model).setQuickClassify(true);
                	String[][] depLabels = ((ECDepModel)model).predictDep(sent.parse, sent.props);
                	//labels = ECDepModel.makeLinearLabel(sent.parse, depLabels);           
                	ECCommon.writeDepEC(writer, sent.parse, depLabels);
                } else 
                	labels = model.predict(sent.parse, sent.props);
            }
            writer.close();
        }
        
    }
    
    public static void main(String[] args) throws Exception {
        ChineseECTagger options = new ChineseECTagger();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
            if (options.task==null)
                options.help = true;
        } catch (CmdLineException e) {
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
            props.setProperty("validate.corpus", options.corpus);
        
        logger.info(PropertyUtil.toString(props));
        
        ChineseUtil chLangUtil = new ChineseUtil();
        if (!chLangUtil.init(PropertyUtil.filterProperties(props, "chinese.", true)))
            System.exit(-1);
        
        switch (options.task) {
        case TRAIN:
            train(props, chLangUtil);
            break;
        case VALIDATE:
            validate(null, props, chLangUtil);
            break;
        case DECODE:
        	decode(props, chLangUtil);
            break;
        }
    }

}