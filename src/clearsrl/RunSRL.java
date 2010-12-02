package clearsrl;

import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TObjectFloatHashMap;
import gnu.trove.TObjectIntIterator;
import harvest.alg.Classification.InstanceFormat;
import harvest.propbank.PBArg;
import harvest.propbank.PBInstance;
import harvest.propbank.PBUtil;
import harvest.treebank.TBHeadRules;
import harvest.treebank.TBNode;
import harvest.treebank.TBTree;
import harvest.treebank.TBUtil;
import harvest.util.JIO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import liblinearbinary.Linear;
import liblinearbinary.SolverType;

import srl.SRLModel.Feature;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.morph.WordnetStemmer;

public class RunSRL {
	static final float THRESHOLD=0.8f;
	
	public static void main(String[] args) throws Exception
	{	
		Properties props = new Properties();
		FileInputStream in = new FileInputStream(args[0]);
		props.load(in);
		in.close();
		
		URL url = new URL("file", null, props.getProperty("wordnet_dic"));
		// construct the dictionary object and open it
		Dictionary dict = new Dictionary(url);
		dict.getCache().setMaximumCapacity(5000);
		dict.open();
		WordnetStemmer stemmer = new WordnetStemmer(dict);
		
		TBHeadRules headrules = new TBHeadRules(props.getProperty("headrules"));

		ObjectInputStream mIn = new ObjectInputStream(new FileInputStream(props.getProperty("model_file")));
		SRLModel model = (SRLModel)mIn.readObject();
		mIn.close();
		System.out.println(model.featureSet);
		
		model.setWordNetStemmer(stemmer);
		model.initScore();
		int cCount = 0;
		int pCount = 0;
		String dataFormat = props.getProperty("format", "default");
		if (dataFormat.equals("default"))
		{		
			String testRegex = props.getProperty("test.regex");
			Map<String, TBTree[]> treeBank = TBUtil.readTBDir(props.getProperty("tbdir"), testRegex);
			Map<String, TIntObjectHashMap<List<PBInstance>>>  propBank = PBUtil.readPBDir(props.getProperty("pbdir"), testRegex, treeBank, false);
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
    					if (pbInstance.tree.getTokenCount()== parsedTreeBank.get(entry.getKey())[pbInstance.treeIndex].getTokenCount())
    						SRLUtil.getSamplesFromParse(new SRInstance(pbInstance), parsedTreeBank.get(entry.getKey())[pbInstance.treeIndex], 
    							THRESHOLD, argNodes, labels);
    					
    					SRInstance trainInstance = new SRInstance(pbInstance.predicateNode, parsedTreeBank.get(entry.getKey())[pbInstance.treeIndex]);
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
    					if (pbInstance.tree.getTokenCount()==trees[pbInstance.treeIndex].getTokenCount())
    						pInstance = new SRInstance(trees[pbInstance.treeIndex].getRootNode().getTokenNodes().get(pbInstance.predicateNode.tokenIndex), trees[pbInstance.treeIndex]);
    					else
    						pInstance = new SRInstance(pbInstance.predicateNode, trees[pbInstance.treeIndex]);
    					SRInstance instance = new SRInstance(pbInstance);
    					cCount += model.predict(pInstance, instance, null);
    					pCount += pInstance.getArgs().size()-1;
    					//System.out.println(instance);
    					//System.out.println(pInstance+"\n");
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
			    TBUtil.findHeads(sentence.parse.getRootNode(), headrules);
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
			
			PrintStream output = null;
			
			try {
				output = new PrintStream(props.getProperty("test.output"));
			} catch (Exception e){
				output = System.out;
			}
			
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
					outStr[instance.predicateNode.tokenIndex][0] = instance.rolesetId;
					
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
					String a = instance.toString().trim();
					String b = pInstance.toString().trim();

					Collections.sort(pInstance.args);
					Map<String, SRArg> labelMap= new HashMap<String, SRArg>();
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
			if (output != System.out)
				output.close();
		}
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
		dict.close();

		//System.out.printf("%d/%d %.2f%%\n", count, y.length, count*100.0/y.length);
		
		//System.out.println(SRLUtil.getFMeasure(model.labelStringMap, testProb.y, y));
	}
}
