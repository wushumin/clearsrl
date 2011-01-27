package clearsrl;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectFloatHashMap;
import clearcommon.propbank.PBInstance;
import clearcommon.propbank.PBUtil;
import clearcommon.treebank.OntoNoteTreeFileResolver;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.treebank.TBUtil;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class RunSRL {
	static final float THRESHOLD=0.8f;
	
	public static void main(String[] args) throws Exception
	{	
		Properties props = new Properties();
		FileInputStream in = new FileInputStream(args[0]);
		props.load(in);
		in.close();

		LanguageUtil langUtil = (LanguageUtil) Class.forName(props.getProperty("language.util-class")).newInstance();
		if (!langUtil.init(props))
		    System.exit(-1);

		ObjectInputStream mIn = new ObjectInputStream(new FileInputStream(props.getProperty("model_file")));
		SRLModel model = (SRLModel)mIn.readObject();
		mIn.close();
		System.out.println(model.featureSet);
		
		model.setLanguageUtil(langUtil);
		model.initScore();
		
		int cCount = 0;
		int pCount = 0;
		String dataFormat = props.getProperty("data.format", "default");
		/*
		if (dataFormat.equals("default"))
		{		
			String testRegex = props.getProperty("test.regex");
			//Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), testRegex);
			Map<String, TIntObjectHashMap<List<PBInstance>>>  propBank = PBUtil.readPBDir(props.getProperty("pbdir"), testRegex, props.getProperty("tbdir"), false);
			Map<String, TBTree[]> parsedTreeBank = TBUtil.readTBDir(props.getProperty("parsedir"), testRegex);
			
			for (Map.Entry<String, TBTree[]> entry: parsedTreeBank.entrySet())
				for (TBTree tree: entry.getValue())
					TBUtil.findHeads(tree.getRootNode(), headrules);
			
			model.initScore();
			for (Map.Entry<String, TIntObjectHashMap<List<PBInstance>>> entry:propBank.entrySet())
                for (TIntObjectIterator<List<PBInstance>> iter = entry.getValue().iterator(); iter.hasNext();)
				{
					iter.advance();
					for (PBInstance pbInstance:iter.value())
                    {
    					ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
    					ArrayList<TObjectFloatHashMap<String>> labels = new ArrayList<TObjectFloatHashMap<String>>();
    					if (pbInstance.tree.getTokenCount()== parsedTreeBank.get(entry.getKey())[pbInstance.tree.getTreeIndex()].getTokenCount())
    						SRLUtil.getSamplesFromParse(new SRInstance(pbInstance), parsedTreeBank.get(entry.getKey())[pbInstance.tree.getTreeIndex()], 
    							THRESHOLD, argNodes, labels);
    					
    					SRInstance trainInstance = new SRInstance(pbInstance.predicateNode, parsedTreeBank.get(entry.getKey())[pbInstance.tree.getTreeIndex()]);
    					for (int i=0; i<labels.size(); ++i)
    					{
    						if (SRLUtil.getMaxLabel(labels.get(i)).equals(SRLModel.NOT_ARG)) continue;
    						trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(i)), argNodes.get(i)));
    					}
    					trainInstance.addArg(new SRArg("rel", pbInstance.predicateNode));
    					
    					model.score.addResult(SRLUtil.convertSRInstanceToTokenMap(trainInstance), SRLUtil.convertSRInstanceToTokenMap(new SRInstance(pbInstance)));
    					String a = new SRInstance(pbInstance).toString().trim();
    					String b = trainInstance.toString().trim();
    					if (!a.equals(b))
    					{
    						System.out.println(a);
    						System.out.println(b);
    						for (SRArg arg: trainInstance.args)
    							System.out.println(arg.node.toParse());
    						System.out.print("\n");
    					}
                    }
				}
			model.score.printResults(System.out);

			model.initScore();
		
			for (Map.Entry<String, TIntObjectHashMap<List<PBInstance>>> entry:propBank.entrySet())
			{
				TBTree[] trees = parsedTreeBank.get(entry.getKey());
				for (TIntObjectIterator<List<PBInstance>> iter = entry.getValue().iterator(); iter.hasNext();)
				{
				    for (PBInstance pbInstance:iter.value())
                    {
    					iter.advance();
    					SRInstance pInstance = null;
    					if (pbInstance.tree.getTokenCount()==trees[pbInstance.tree.getTreeIndex()].getTokenCount())
    						pInstance = new SRInstance(trees[pbInstance.tree.getTreeIndex()].getRootNode().getTokenNodes().get(pbInstance.predicateNode.tokenIndex), trees[pbInstance.tree.getTreeIndex()]);
    					else
    						pInstance = new SRInstance(pbInstance.predicateNode, trees[pbInstance.tree.getTreeIndex()]);
    					SRInstance instance = new SRInstance(pbInstance);
    					cCount += model.predict(pInstance, instance, null);
    					pCount += pInstance.getArgs().size()-1;
    					//System.out.println(instance);
    					//System.out.println(pInstance+"\n");
                    }
				} 
			}
		}
		*/
        boolean modelPredicate = !props.getProperty("model_predicate", "false").equals("false");
		
        SRInstance.OutputFormat outputFormat = SRInstance.OutputFormat.valueOf(props.getProperty("output.format",SRInstance.OutputFormat.TEXT.toString()));
        PrintStream output = null;
        try {
            output = new PrintStream(props.getProperty("output.file"));
        } catch (Exception e){
            output = System.out;
        }
        
        if (!dataFormat.equals("conll"))
        {       
            Map<String, TIntObjectHashMap<List<PBInstance>>>  propBank = null;
            if (props.getProperty("pbdir")!=null)
            {
                String testRegex = props.getProperty("train.regex");
                //Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), testRegex);
                propBank = PBUtil.readPBDir(props.getProperty("pbdir"), 
                                     testRegex, 
                                     props.getProperty("tbdir"), 
                                     dataFormat.equals("ontonotes")?new OntoNoteTreeFileResolver():null);
            }
            Map<String, TBTree[]> parsedTreeBank = TBUtil.readTBDir(props.getProperty("parsedir"), props.getProperty("regex"));
            
            for (Map.Entry<String, TBTree[]> entry: parsedTreeBank.entrySet())
            {
                TIntObjectHashMap<List<PBInstance>> pbFileMap = propBank==null?null:propBank.get(entry.getKey());
                TBTree[] trees = entry.getValue(); 
                for (int i=0; i<trees.length; ++i)
                {
                    //System.out.println(i+" "+trees[i].getRootNode().toParse());
                    //List<TBNode> nodes = SRLUtil.getArgumentCandidates(trees[i].getRootNode());
                    //for (TBNode node:nodes)
                    //    System.out.println(node.toParse());
                    TBUtil.findHeads(trees[i].getRootNode(), langUtil.getHeadRules());
                    List<PBInstance> pbInstances = pbFileMap==null?null:pbFileMap.get(i);
                    if (modelPredicate)
                    {
                        SRInstance[] goldInstances = pbInstances==null?new SRInstance[0]:new SRInstance[pbInstances.size()];
                        for (int j=0; j<goldInstances.length; ++j)
                            goldInstances[j] = new SRInstance(pbInstances.get(j));
                        List<SRInstance> predictions = model.predict(trees[i], goldInstances, null);
                        for (SRInstance instance:predictions)
                            output.println(instance.toString(outputFormat));
                        
                        if (propBank==null) continue;
                        
                        BitSet goldPredicates = new BitSet();
                        for (SRInstance instance:goldInstances)
                            goldPredicates.set(instance.getPredicateNode().getTokenIndex());
                        
                        BitSet predPredicates = new BitSet();
                        for (SRInstance instance:predictions)
                            predPredicates.set(instance.getPredicateNode().getTokenIndex());
                        /*
                        if (!goldPredicates.equals(predPredicates))
                        {
                            System.out.print(entry.getKey()+","+i+": ");
                            List<TBNode> nodes = trees[i].getRootNode().getTokenNodes();
                            for (int j=0; j<nodes.size(); ++j)
                            {
                                if (goldPredicates.get(j)&& predPredicates.get(j))
                                    System.out.print("["+nodes.get(j).getWord()+" "+nodes.get(j).getPOS()+"] ");
                                else if (goldPredicates.get(j))
                                    System.out.print("("+nodes.get(j).getWord()+" "+nodes.get(j).getPOS()+") ");
                                else if (predPredicates.get(j))
                                    System.out.print("{"+nodes.get(j).getWord()+" "+nodes.get(j).getPOS()+"} ");
                                else
                                    System.out.print(nodes.get(j).getWord()+" ");
                            }
                            System.out.print("\n");
                        }*/
                    }
                }
            }      
        }		
		else if (dataFormat.equals("conll"))
		{
			ArrayList<CoNLLSentence> testing = CoNLLSentence.read(new FileReader(props.getProperty("test.input")), false);
			
			model.initScore();
			for (CoNLLSentence sentence:testing)
			{
			    TBUtil.findHeads(sentence.parse.getRootNode(), langUtil.getHeadRules());
				for (SRInstance instance:sentence.srls)
				{
					ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
					ArrayList<TObjectFloatHashMap<String>> labels = new ArrayList<TObjectFloatHashMap<String>>();
					SRLUtil.getSamplesFromParse(instance, sentence.parse, THRESHOLD, argNodes, labels);
					
					SRInstance trainInstance = new SRInstance(instance.predicateNode, sentence.parse);
					for (int i=0; i<labels.size(); ++i)
					{
						if (SRLUtil.getMaxLabel(labels.get(i)).equals(SRLModel.NOT_ARG)) continue;
						trainInstance.addArg(new SRArg(SRLUtil.getMaxLabel(labels.get(i)), argNodes.get(i)));
					}
					trainInstance.addArg(new SRArg("rel", instance.predicateNode));
					
					model.score.addResult(SRLUtil.convertSRInstanceToTokenMap(trainInstance), SRLUtil.convertSRInstanceToTokenMap(instance));
				}				
			}
			model.score.printResults(System.out);

			
			model.initScore();
			for (CoNLLSentence sentence:testing)
			{
				String[][] outStr = new String[sentence.parse.getTokenCount()][sentence.srls.length+1];
				for (int i=0; i<outStr.length; ++i)
				{
					outStr[i][0] = "-";
					for (int j=1; j<outStr[i].length; ++j)
						outStr[i][j] = "*";
				}
				for (int j=1; j<=sentence.srls.length; ++j)
				//for (SRInstance instance:sentence.srls)
				{
					SRInstance instance = sentence.srls[j-1];
					outStr[instance.predicateNode.getTokenIndex()][0] = instance.rolesetId;
					
					Map<String, BitSet> argBitSet = new HashMap<String, BitSet>();
					for (SRArg arg:instance.args)
					{
						if (arg.isPredicate()) continue;
						BitSet bits = argBitSet.get(arg.label);
						if (bits!=null)
							bits.or(arg.getTokenSet());
						else
							argBitSet.put(arg.label, arg.getTokenSet());
					}
					SRInstance pInstance = new SRInstance(instance.predicateNode, instance.tree);
					cCount += model.predict(pInstance, instance, sentence.namedEntities);
					pCount += pInstance.getArgs().size()-1;
					//pCount += instance.getArgs().size()-1;
					//System.out.println(instance);
					//System.out.println(pInstance+"\n");
					//String a = instance.toString().trim();
					//String b = pInstance.toString().trim();

					Collections.sort(pInstance.args);
					//Map<String, SRArg> labelMap= new HashMap<String, SRArg>();
					for (SRArg arg:pInstance.args)
					{
						String label = arg.label;
						if (label.equals("rel"))
							label = "V";
						else if (label.startsWith("ARG"))
							label = "A"+label.substring(3);
						else if (label.startsWith("C-ARG"))
							label = "C-A"+label.substring(5);
						else if (label.startsWith("R-ARG"))
							label = "R-A"+label.substring(5);
				
						BitSet bitset = arg.getTokenSet();
						if (bitset.cardinality()==1)
						{
							outStr[bitset.nextSetBit(0)][j] = "("+label+"*)";
						}
						else
						{
							int start = bitset.nextSetBit(0);
							outStr[start][j] = "("+label+"*";
							outStr[bitset.nextClearBit(start+1)-1][j] = "*)";
						}
					}
				}
				for (int i=0; i<outStr.length; ++i)
				{
					for (int j=0; j<outStr[i].length; ++j)
						output.print(outStr[i][j]+"\t");
					output.print("\n");
				}
				output.print("\n");
			}

		}
        if (output != System.out)
            output.close();
		/*
        else if (dataFormat.equals("conll"))
        {
            float p=0, r=0;
            int ptCnt=0,rtCnt=0;
            
            ArrayList<CoNLLSentence> testing = CoNLLSentence.read(new FileReader(props.getProperty("test.input")), false);
            model.initScore();
            for (CoNLLSentence sentence:testing)
            {
                TBUtil.findHeads(sentence.parse.getRootNode(), headrules);
                List<SRInstance> predictions = model.predict(sentence.parse, sentence.srls, sentence.namedEntities);
                for (SRInstance prediction:predictions)
                {
                    for (SRInstance srl:sentence.srls)
                        if (prediction.predicateNode.tokenIndex==srl.predicateNode.tokenIndex)
                        {
                            ++p; ++r;
                        }
                }
                
                ptCnt += predictions.size();
                rtCnt += sentence.srls.length;
            }
            p /=ptCnt; r /= rtCnt;
            System.out.printf("predicate prediction: %f, %f, %f\n", p, r, 2*p*r/(p+r));
            
        }		
		*/
		model.score.printResults(System.out);
		System.out.println("Constituents predicted: "+pCount);
		System.out.println("Constituents considered: "+cCount);

		//System.out.printf("%d/%d %.2f%%\n", count, y.length, count*100.0/y.length);
		
		//System.out.println(SRLUtil.getFMeasure(model.labelStringMap, testProb.y, y));
	}
}
