package clearsrl;

import gnu.trove.TObjectFloatHashMap;
import gnu.trove.TObjectFloatIterator;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import harvest.propbank.PBArg;
import harvest.propbank.PBInstance;
import harvest.treebank.TBHeadRule;
import harvest.treebank.TBHeadRules;
import harvest.treebank.TBNode;
import harvest.treebank.TBTree;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SRLUtil {
	
	static final Pattern TRACE_PATTERN = Pattern.compile("([A-Z]+(\\-[A-Z]+)*)(\\-\\d+)?+(=\\d+)?+");
	public static String removeTrace(String pos)
	{
		Matcher matcher = TRACE_PATTERN.matcher(pos);
		if (matcher.matches())
			return matcher.group(1);
		else
			return pos;
	}
	
	static final Pattern ARG_PATTERN = Pattern.compile("([A-Z]+\\d)\\-[A-Za-z]+");
	public static String removeArgModifier(String argType)
	{
		Matcher matcher = ARG_PATTERN.matcher(argType);
		if (matcher.matches())
			return matcher.group(1);
		else
			return argType;
	}
	/*
	public static void getSamplesFromPBInstance(PBInstance instance, ArrayList<TBNode> argNodes, ArrayList<String> labels)
	{
		if (instance.getArgs().size()==1) // skip if there are no arguments
			return;

		for (Map.Entry<String, PBArg> entry:instance.getArgs().entrySet())
		{
			if (entry.getValue().isPredicate()) continue;
			for (TBNode argNode:entry.getValue().getNodes())
			{
				if (argNode.getTokenNodes().isEmpty()) continue;

				argNodes.add(argNode);
				labels.add(removeArgModifier(entry.getKey()));
			}
		}
	}
	*/
	public static float getFScore(float lhs, float rhs)
	{
		float denom = lhs + rhs;
		return denom==0?0:2*lhs*rhs/denom;
	}
	/*
	public static void getSamplesFromParse(PBInstance instance, TBTree parsedTree, float threshold, ArrayList<TBNode> candidateNodes, ArrayList<String> labels)
	{
		if (instance.getArgs().size()==1) // skip if there are no arguments
			return;
		
		ArrayList<TBNode> tmpNodes = getArgumentCandidates(parsedTree.getRootNode());
		ArrayList<BitSet> candidateTokens = new ArrayList<BitSet>();
		 
		candidateNodes.clear();
		labels.clear();
		
		for (int i=0; i<tmpNodes.size();++i)
		{
			BitSet tmp = convertToBitSet(tmpNodes.get(i),parsedTree.getTokenCount());
			if (!tmp.get(instance.predicateNode.tokenIndex))
			{
				// initialize all candidates to not argument
				candidateNodes.add(tmpNodes.get(i));
				candidateTokens.add(tmp);
				labels.add(SRLModel.NOT_ARG);
			}
		}
		
		if (candidateNodes.isEmpty())
			return;
		
		BitSet candidateBitSet = new BitSet(candidateNodes.size());
		
		// find a good match between parse candidates and argument boundary of gold SRL
		for (Map.Entry<String, PBArg> entry:instance.getArgs().entrySet())
		{
			if (entry.getValue().isPredicate()) continue;
			for (TBNode argNode:entry.getValue().getNodes())
			{
				BitSet argBitSet =  convertToBitSet(argNode,parsedTree.getTokenCount());
				if (argBitSet.isEmpty()) continue;
				float fScore;
				float bestFScore = 0.0f;
				int bestCandidate = 0;
				
				for (int i=0; i<candidateTokens.size(); ++i)
				{
					BitSet clone = (BitSet)candidateTokens.get(i).clone();
					clone.and(argBitSet);
					fScore = getFScore(clone.cardinality()*1.0f/candidateTokens.get(i).cardinality(), clone.cardinality()*1.0f/argBitSet.cardinality());
					if (fScore > bestFScore)
					{
						bestFScore = fScore;
						bestCandidate = i;
						if (bestFScore==1)
							break;
					}
				}
				if (bestFScore>=threshold)
				{
					if (candidateBitSet.get(bestCandidate))
						System.err.println("Candidate reused: "+candidateNodes.get(bestCandidate));
					candidateBitSet.set(bestCandidate);
					labels.set(bestCandidate, removeArgModifier(entry.getKey()));
				}
				else if (bestFScore>=0.5)
				{
					labels.set(bestCandidate, null);
				}
			}
		}
		for (int i=0; i<labels.size();)
		{
			if (labels.get(i)==null)
			{
				labels.remove(i);
				candidateNodes.remove(i);
			}
			else ++i;
		}
	}
	*/
	public static void getSamplesFromParse(SRInstance instance, 
			TBTree parsedTree, float threshold, 
			ArrayList<TBNode> candidateNodes, 
			ArrayList<TObjectFloatHashMap<String>> labels)
	{
		if (instance.getArgs().size()==1) // skip if there are no arguments
			return;
		
		ArrayList<TBNode> tmpNodes = getArgumentCandidates(parsedTree.getRootNode());
		ArrayList<BitSet> candidateTokens = new ArrayList<BitSet>();

		candidateNodes.clear();
		labels.clear();

		for (int i=0; i<tmpNodes.size();++i)
		{
			BitSet tmp = convertToBitSet(tmpNodes.get(i),parsedTree.getTokenCount());
			if (!tmp.get(instance.predicateNode.tokenIndex))
			{
				// initialize all candidates to not argument
				candidateNodes.add(tmpNodes.get(i));
				candidateTokens.add(tmp);
				labels.add(new TObjectFloatHashMap<String>());
				labels.get(labels.size()-1).put(SRLModel.NOT_ARG, 1.0f);
			}
		}
		
		if (candidateNodes.isEmpty())
			return;
		
		BitSet candidateBitSet = new BitSet(candidateNodes.size());
		BitSet removalBitSet = new BitSet(candidateNodes.size());
		
		// find a good match between parse candidates and argument boundary of gold SRL
		for (SRArg arg:instance.getArgs())
		{
			if (arg.isPredicate()) continue;
			BitSet argBitSet = arg.getTokenSet();
		
			if (argBitSet.isEmpty()) continue;
			float []fScores = new float[candidateTokens.size()];
			
			for (int i=0; i<candidateTokens.size(); ++i)
			{
				BitSet clone = (BitSet)candidateTokens.get(i).clone();
				clone.and(argBitSet);
				fScores[i] = getFScore(clone.cardinality()*1.0f/candidateTokens.get(i).cardinality(), clone.cardinality()*1.0f/argBitSet.cardinality());
				if (fScores[i]>0.5f)
					labels.get(i).put(removeArgModifier(arg.label), fScores[i]);
			}
			int index = SRLUtil.getMaxIndex(fScores);
			
			// if the best matched candidate phrase fscore is above threshold, labeled it the argument
			if (fScores[index]>=0.9999f)
			{
				if (candidateBitSet.get(index))
					System.err.println("Candidate reused: "+candidateNodes.get(index));
				candidateBitSet.set(index);
				labels.get(index).remove(SRLModel.NOT_ARG);
			}
			// if an argument is not matched to any candidate above the threshold, 
			// at least remove the best matched candidate (if fscore>0.5) from training 
			/*
			else if (fScores[index]>threshold)
			{
				removalBitSet.set(index);
			}*/
		}
		
		for (int i=removalBitSet.nextSetBit(0); i>=0; i=removalBitSet.nextSetBit(i+1))
			if (SRLUtil.getMaxLabel(labels.get(i)).equals(SRLModel.NOT_ARG))
				labels.set(i, null);
		
		for (int i=0; i<labels.size();++i)
		{
			if (labels.get(i)==null)
			{
				labels.remove(i);
				candidateNodes.remove(i--);
			}
		}
	}

	
	public static BitSet convertToBitSet(TBNode node, int nbits)
	{
		BitSet tokenSet = new BitSet(nbits);
		for (TBNode aNode:node.getTokenNodes())
			tokenSet.set(aNode.tokenIndex);
		return tokenSet;
	}
	
	public static ArrayList<TBNode> getArgumentCandidates(TBNode node)
	{
		ArrayList<TBNode> nodes = new ArrayList<TBNode>();
		if (node.getChildren()==null)
			return nodes;
		
		for (TBNode childNode:node.getChildren())
		{
			if (childNode.isToken())
				nodes.add(childNode);
			else
			{
				ArrayList<TBNode> childNodes = getArgumentCandidates(childNode);
				if (childNodes.size()>0 && node.getChildren().size()>1)
					nodes.add(childNode);
				if (childNodes.size()>1)
					nodes.addAll(childNodes);
			}
		}
		return nodes;
	}
	
	public static ArrayList<TBNode> filterPredicateNode(ArrayList<TBNode> argNodes, TBTree tree, TBNode predicateNode)
	{
		for (int i=0; i<argNodes.size();)
		{
			BitSet tmp = convertToBitSet(argNodes.get(i),tree.getTokenCount());
			if (tmp.get(predicateNode.tokenIndex))
				argNodes.remove(i);
			else
				++i;
		}
		return argNodes;
	}
	
	
	public static Map<String, BitSet> convertSRInstanceToTokenMap(SRInstance instance)
	{
		Map<String, BitSet> tokenMap = new TreeMap<String, BitSet>();
		
		BitSet tokenSet=null;
		for (SRArg arg:instance.getArgs())
		{
			if ((tokenSet=tokenMap.get(arg.label))==null)
				tokenMap.put(arg.label, arg.getTokenSet());
			else
				tokenSet.or(arg.getTokenSet());
		}
		return tokenMap;
	}
	
	public static int getMaxIndex(float[] array)
	{
		float val = Float.NEGATIVE_INFINITY;
		int index = -1;
		for (int i=0; i<array.length; ++i)
			if (array[i]>val)
			{
				val = array[i];
				index = i;
			}
		return index;
	}

	public static String getMaxLabel(TObjectFloatHashMap<String> labels)
	{
		float val = Float.NEGATIVE_INFINITY;
		String maxLabel = null;
		for (TObjectFloatIterator<String> iter=labels.iterator(); iter.hasNext();)
		{
			iter.advance();
			if (iter.value()>val)
			{
				val = iter.value();
				maxLabel = iter.key();
			}
		}
		return maxLabel;
	}

	public static void removeOverlap(SRInstance instance)
	{		
		boolean overlapped = false;
		
		do {
			overlapped = false;
			for (int i=0; i<instance.args.size();++i)
			{
				BitSet argi = instance.args.get(i).tokenSet;
				for (int j=i+1; j<instance.args.size();++j)
				{
					BitSet argj= instance.args.get(j).tokenSet; 
					if (argj.intersects(argi))
					{
						//if (instance.args.get(i).label.equals(instance.args.get(j).label))
						{
							instance.args.remove(argi.cardinality()<argj.cardinality()?i:j);
							overlapped = true;
							break;
						}
					}	
				}
				if (overlapped) break;
			}
		} while (overlapped);
		
		for (int i=0; i<instance.args.size();++i)
		{
			BitSet argi = instance.args.get(i).tokenSet;
			for (int j=i+1; j<instance.args.size();++j)
			{
				BitSet argj= instance.args.get(j).tokenSet; 
				if (argj.intersects(argi))
				{
					System.out.println(instance);
					return;
				}
			}
		}
	}
	
	
    public static void removeOverlap(LinkedList<SRArg> args)
    {       
        LinkedList<SRArg> argQueue = new LinkedList<SRArg>(args);
        args.clear();
        
        while (!argQueue.isEmpty())
        {
            LinkedList<SRArg> overlappedArgs = new LinkedList<SRArg>();
            BitSet tokenSet = (BitSet)argQueue.element().tokenSet.clone();
            
            overlappedArgs.add(argQueue.pop());
            boolean overlapFound = false;
            do
            {
                overlapFound = false;
                for (ListIterator<SRArg> iter=argQueue.listIterator(); iter.hasNext();)
                {
                    SRArg arg = iter.next();
                    if (tokenSet.intersects(arg.tokenSet))
                    {
                        overlapFound = true;
                        tokenSet.or(arg.tokenSet);
                        overlappedArgs.add(arg);
                        iter.remove();
                        break;
                    }
                }
            } while (overlapFound);
          
            if (overlappedArgs.size()>1)
            {
                SRArg topArg = overlappedArgs.get(0);
                for (SRArg arg:overlappedArgs)
                    if (arg.score>topArg.score) topArg = arg;

                for (ListIterator<SRArg> iter=overlappedArgs.listIterator(); iter.hasNext();)
                {
                    SRArg arg = iter.next();
                    if (arg==topArg) continue;
                    if (arg.tokenSet.intersects(topArg.tokenSet))
                        iter.remove();
                }
            }
            removeOverlap(overlappedArgs);
     
            args.addAll(overlappedArgs);
        }
    }	
}
