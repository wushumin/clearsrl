package edu.colorado.clear.srl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.colorado.clear.common.propbank.PBInstance;
import edu.colorado.clear.common.treebank.TBNode;
import edu.colorado.clear.common.util.LanguageUtil;
import edu.colorado.clear.common.util.PropertyUtil;
import edu.colorado.clear.srl.SRArg;
import edu.colorado.clear.srl.SRInstance;
import edu.colorado.clear.srl.SRLUtil;
import edu.colorado.clear.srl.Sentence;
import edu.colorado.clear.srl.Sentence.Source;

public class MakeGoldSRL {
	
	static Logger logger = Logger.getLogger("clearsrl");

	static void mapArgs(SRInstance prediction, SRInstance gold, SRInstance support, LanguageUtil langUtil) {
		List<TBNode> argNodes = SRLUtil.getArgumentCandidates(prediction.getPredicateNode(), false, support, langUtil, 10, true);
		Map<TBNode, SRArg> candidateMap = SRLUtil.mapArguments(gold, prediction.getTree(), argNodes);
		
		for (TBNode node:argNodes)
            if (candidateMap.get(node)!=null)
            	prediction.addArg(new SRArg(candidateMap.get(node).getLabel(), node));
		
        prediction.cleanUpArgs();
		
	}
	
    public static void main(String[] args) throws Exception {   
    	Properties props = new Properties();
        FileInputStream in = new FileInputStream(args[0]);
        props.load(in);
        in.close();
        props = PropertyUtil.resolveEnvironmentVariables(props);
        props = PropertyUtil.filterProperties(props, "srl.", true);
        {
	        String logLevel = props.getProperty("logger.level");
	        if (logLevel!=null) {
		        ConsoleHandler ch = new ConsoleHandler();
		        ch.setLevel(Level.parse(logLevel));
		        logger.addHandler(ch);
		        logger.setLevel(Level.parse(logLevel));
	        }
        }
        
        System.out.print(PropertyUtil.toString(props));
        
        Properties langProps = PropertyUtil.filterProperties(props, props.getProperty("language").trim()+'.');
        LanguageUtil langUtil = (LanguageUtil) Class.forName(langProps.getProperty("util-class")).newInstance();
        if (!langUtil.init(langProps))
            System.exit(-1);
        
        System.out.println("Processing corpus "+args[1]);
        Properties srcProps = PropertyUtil.filterProperties(props, args[1]+".", true);
        System.out.println(PropertyUtil.toString(srcProps));
        
        Map<String, Sentence[]> sentenceMap = Sentence.readCorpus(srcProps, Source.PARSE, EnumSet.of(Source.PARSE, Source.PROPBANK, Source.TREEBANK), langUtil);
        
        for (Map.Entry<String, Sentence[]> entry: sentenceMap.entrySet()) {
        	logger.info("Processing "+entry.getKey());
        	
        	File outFile = new File(args[2], entry.getKey().substring(0, entry.getKey().lastIndexOf('.'))+".prop");
            if (outFile.getParentFile()!=null)
                outFile.getParentFile().mkdirs();
        	
        	PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"));
        	
            for (Sentence sent:entry.getValue()) {                        
                logger.fine("Processing tree "+(sent.parse==null?sent.treeTB.getIndex():sent.parse.getIndex()));
                if (sent.parse!=null && sent.treeTB!=null && sent.parse.getTokenCount()!=sent.treeTB.getTokenCount()) {
                	logger.warning("tree "+entry.getKey()+":"+sent.parse.getIndex()+" inconsistent, skipping");
                	continue;
                }
                if (sent.propPB!=null) {
                	Collections.sort(sent.propPB);
                	BitSet predMask = new BitSet();
                	for (Iterator<PBInstance> iter=sent.propPB.iterator();iter.hasNext();) {
                		PBInstance instance = iter.next();
                		if (predMask.get(instance.getPredicate().getTokenIndex())) {
                			logger.warning("deleting duplicate props: "+sent.propPB);
                			iter.remove();
                			continue;
                		}
                		predMask.set(instance.getPredicate().getTokenIndex());
                	}
                	List<SRInstance> goldSRLs = SRLUtil.convertToSRInstance(sent.propPB);
                	
                	ArrayList<SRInstance> predictions = new ArrayList<SRInstance>();
                	for (SRInstance goldSRL:goldSRLs) {
                        TBNode node = sent.parse.getNodeByTokenIndex(goldSRL.getPredicateNode().getTokenIndex());
                        predictions.add(new SRInstance(node, sent.parse, goldSRL.getRolesetId(), 1.0));

                    }
                	int[] supportIds = SRLUtil.findSupportPredicates(predictions, goldSRLs, langUtil, SRLUtil.SupportType.VERB, false);
                	
                	 BitSet predicted = new BitSet(supportIds.length);
                     
                     // classify verb predicates first
                     int cardinality = 0;        
                     do {
                         cardinality = predicted.cardinality();
                         for (int i=predicted.nextClearBit(0); i<supportIds.length; i=predicted.nextClearBit(i+1))
                             if (!langUtil.isVerb(predictions.get(i).getPredicateNode().getPOS()))
                                 continue;
                             else if (supportIds[i]<0 || predicted.get(supportIds[i])) {
                            	 mapArgs(predictions.get(i), goldSRLs.get(i), supportIds[i]<0?null:predictions.get(supportIds[i]), langUtil);                                 
                                 predicted.set(i);
                             }
                     } while (predicted.cardinality()>cardinality);
                     
                     // then classify nominal predicates, by now we'll have the verb arguments to help find support verbs
                     supportIds = SRLUtil.findSupportPredicates(predictions, goldSRLs, langUtil, SRLUtil.SupportType.NOMINAL, false);
                     do {
                         cardinality = predicted.cardinality();
                         for (int i=predicted.nextClearBit(0); i<supportIds.length; i=predicted.nextClearBit(i+1))
                             if (langUtil.isVerb(predictions.get(i).getPredicateNode().getPOS()))
                                 continue;
                             else if (supportIds[i]<0 || predicted.get(supportIds[i])) {
                            	 mapArgs(predictions.get(i), goldSRLs.get(i), supportIds[i]<0?null:predictions.get(supportIds[i]), langUtil); 
                                 predicted.set(i);
                             }
                     } while (predicted.cardinality()>cardinality);
                     for (SRInstance prediction:predictions)
                    	 writer.println(prediction.toPropbankString());
                }
            }
            writer.close();
        }
    }
}
