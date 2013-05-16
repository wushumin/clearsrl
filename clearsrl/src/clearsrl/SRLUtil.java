package clearsrl;

import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.util.LanguageUtil;

import gnu.trove.TIntIntHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SRLUtil {
	
    /*
	static final Pattern TRACE_PATTERN = Pattern.compile("([A-Z]+(\\-[A-Z]+)*)(\\-\\d+)?+(=\\d+)?+");
	public static String removeTrace(String pos)
	{
		Matcher matcher = TRACE_PATTERN.matcher(pos);
		if (matcher.matches())
			return matcher.group(1);
		else
			return pos;
	}*/
	
	static final Pattern ARG_PATTERN = Pattern.compile("(([RC]-)?(A[A-Z]*\\d))(\\-[A-Za-z]+)?");
	public static String removeArgModifier(String argType)
	{
		Matcher matcher = ARG_PATTERN.matcher(argType);
		if (matcher.matches())
		    return matcher.group(1);
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
	
	/*
	public static void getSamplesFromParse(SRInstance instance, 
			TBTree parsedTree, LanguageUtil langUtil, float threshold, 
			ArrayList<TBNode> candidateNodes, 
			ArrayList<Map<String, Float>> labels)
	{
		if (instance.getArgs().size()==1) // skip if there are no arguments
			return;
		
		ArrayList<TBNode> tmpNodes = getArgumentCandidates(parsedTree.getRootNode());
		ArrayList<BitSet> candidateTokens = new ArrayList<BitSet>();

		candidateNodes.clear();
		labels.clear();

		for (int i=0; i<tmpNodes.size();++i)
		{
			BitSet tmp = tmpNodes.get(i).getTokenSet();
			if (!tmp.get(instance.predicateNode.getTokenIndex()))
			{
				// initialize all candidates to not argument
				candidateNodes.add(tmpNodes.get(i));
				candidateTokens.add(tmp);
				labels.add(new HashMap<String, Float>());
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
			
			//else if (fScores[index]>threshold)
			//{
			//	removalBitSet.set(index);
			//}
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
*/

	public enum SupportType {
		NOMINAL,
		VERB,
		ALL
	}
	
	public static int[] findSupportPredicates(List<SRInstance> instances, LanguageUtil langUtil, SupportType type, boolean training) {
		int[] supports = new int[instances.size()];
		if (supports.length==0) return supports;
		Arrays.fill(supports, -1);
		
		TIntIntHashMap instanceMap = new TIntIntHashMap();
		for (int i=0; i<instances.size();++i)
			instanceMap.put(instances.get(i).getPredicateNode().getTokenIndex(), i+1);

		for (int i=0; i<instances.size();++i) {
			SRInstance instance = instances.get(i);			
			boolean isVerb = langUtil.isVerb(instance.getPredicateNode().getPOS());
			if (isVerb && type.equals(SupportType.NOMINAL) || !isVerb && type.equals(SupportType.VERB))
				continue;
			
			TBNode head = instance.getPredicateNode();
			TBNode constituent = head.getConstituentByHead();

			boolean foundSupport=false;
			if (!isVerb && constituent.getPOS().equals("NP")) {
				for (TBNode node:head.getDependentNodes()) {
					if (langUtil.isVerb(node.getPOS()) && node.getTokenIndex()>head.getTokenIndex() && langUtil.getPassive(node)>0) {
						supports[i] = instanceMap.get(node.getTokenIndex())-1;
						if (supports[i]>0)
							for (SRArg sarg:instances.get(supports[i]).getArgs())
								if (sarg.getLabel().matches("ARG1|ARGM-PRX")&&sarg.tokenSet.get(head.getTokenIndex())) {
									foundSupport = true;
									break;
								}	
						
						//System.out.println(instance);
						//if (supports[i]>=0)
						//	System.out.println(Boolean.toString(foundSupport)+" "+instances.get(supports[i])+"\n");
						break;
					}
				}
			}
			if (!foundSupport) {
				//while (constituent.getParent()!=null && constituent.getPOS().equals("VP") && constituent.getParent().getPOS().equals("VP")) {
				//	constituent = constituent.getParent();
				//	head = constituent.getHead();
				//}
	
				while (constituent.getParent()!=null && !langUtil.isClause(constituent.getParent().getPOS())) {
					if (constituent.getParent().getHead()!=head && 
							langUtil.isVerb(constituent.getParent().getHead().getPOS()) && 
							head.getLevelToRoot()>=constituent.getParent().getHead().getLevelToRoot()) {
						supports[i] = instanceMap.get(constituent.getParent().getHead().getTokenIndex())-1;
						break;
					}
					constituent = constituent.getParent();
				}
			}
			if (training) {

				TBNode lvNode = null;
				for (SRArg arg:instance.getArgs())
					if (arg.getLabel().endsWith("LVB")) {
						lvNode = arg.node;
						break;
					}
				if (lvNode!=null) {
					if (supports[i]<0 || instances.get(supports[i]).getPredicateNode()!=lvNode) {
						if (supports[i]<0)
							System.err.println("Light verb not found for "+instance);
						else
							System.err.println("Wrong support for "+instance);
						boolean found = false;
						for (SRInstance support:instances)
							if (support.getPredicateNode()==lvNode) {
								found = true;
								System.err.println(Boolean.toString(langUtil.getPassive(support.predicateNode)>0)+" "+support);
								break;
							}
						if (!found)
							System.err.println("Light verb not in list");
					} 
				}
	
				boolean nonlocalArg = false;
				if (supports[i]>=0) {
					TBNode node = instances.get(supports[i]).getPredicateNode().getParent();
					while (node.getParent()!=null && !node.getPOS().equals("VP") && !langUtil.isClause(node.getPOS()))
						node = node.getParent();
					for (SRArg sarg:instances.get(supports[i]).getArgs()) {
						if (sarg.getLabel().equals(SRLModel.NOT_ARG)) continue;
						BitSet tokenSet = sarg.getTokenSet();
						tokenSet.or(node.getTokenSet());
						if (tokenSet.get(instance.getPredicateNode().getTokenIndex())) {
							for (SRArg arg:instance.getArgs()) {
								if (arg.getLabel().equals(SRLModel.NOT_ARG)) continue;
								if (!tokenSet.intersects(arg.getTokenSet())) {
									nonlocalArg=true;
									break;
								}
							}
							break;
						}
					}
				}
				
				if (lvNode!=null && supports[i]>=0) {
					//re-populate the arguments of the light verb
					BitSet tokenSet=null;
					
					for (SRArg sarg:instances.get(supports[i]).getArgs())
						if ((tokenSet = sarg.getTokenSet()).get(instance.getPredicateNode().getTokenIndex()))
							break;
						else
							tokenSet = null;
					if (tokenSet!=null) {
						TBNode node = instances.get(supports[i]).getPredicateNode().getParent();
						while (node.getParent()!=null && !node.getPOS().equals("VP") && !langUtil.isClause(node.getPOS()))
							node = node.getParent();
						
						BitSet vpSet = node.getTokenSet();
						
						for (int a=0; a<instance.getArgs().size(); ++a)
							if (!tokenSet.intersects(instance.getArgs().get(a).getTokenSet())) {
								boolean found = false;
								for (SRArg sarg:instances.get(supports[i]).getArgs())
									if (instance.getArgs().get(a).node==sarg.node) {
										found = true;
										if (!instance.getArgs().get(a).getLabel().equals(sarg.getLabel()) && !sarg.getLabel().equals("rel")) {
											System.err.println("LVC issue: "+instance.getArgs().get(a).getLabel()+" "+sarg.getLabel());
											System.err.println(instance);
											System.err.println(instances.get(supports[i])+"\n");
										}
										break;
									}
								if (!found) {
									instances.get(supports[i]).addArg(instance.getArgs().get(a));
									//if (!vpSet.intersects(instance.getArgs().get(a).getTokenSet()))
									//	instance.getArgs().remove(a);
									continue;
								}
							}
						//System.out.println(instance);
						//System.out.println(instances.get(supports[i])+"\n");
					}			
				}
				
				//if (lvNode==null && nonlocalArg && !isVerb) {
				//	System.out.println(instance);
				//	System.out.println(instances.get(supports[i])+"\n");
				//}
			}
		}
		return supports;
	}

	
	/**
	 * @param instance instance to get samples from, nodes can be based off of gold tree
	 * @param support support instance and its arguments, should be based off of parsed tree
	 * @param parsedTree the parsed tree to perform SRL off of 
	 * @param langUtil language utility class
	 * @param levelDown numbers of levels down each sibling node to collect candidates
	 * @param threshold word overlap threshold for an argument and constituent to be considered a match
	 * @param candidateNodes candidate constituents for argument consideration
	 * @param labels argument labels (matched against instance arguments)
	 */
	@SuppressWarnings("serial")
	public static void getSamplesFromParse(SRInstance instance, SRInstance support,
			TBTree parsedTree, LanguageUtil langUtil, int levelDown, boolean allHeadPhrases, float threshold, 
			ArrayList<TBNode> candidateNodes, 
			ArrayList<Map<String, Float>> labels) {
		//if (instance.getArgs().size()==1) // skip if there are no arguments
		//	return;
		
		candidateNodes.clear();
		labels.clear();

		candidateNodes.addAll(getArgumentCandidates(parsedTree.getNodeByTokenIndex(instance.getPredicateNode().getTokenIndex()), support, langUtil, levelDown, allHeadPhrases));
		if (candidateNodes.isEmpty()) return;
		
		ArrayList<BitSet> candidateTokens = new ArrayList<BitSet>(candidateNodes.size());
		for (TBNode node:candidateNodes) {
			// initialize all candidates to not argument
			candidateTokens.add(node.getTokenSet());
			labels.add(new HashMap<String, Float>(){{put(SRLModel.NOT_ARG, 1.0f);}});
		}
		
		BitSet candidateBitSet = new BitSet(candidateNodes.size());
		BitSet removalBitSet = new BitSet(candidateNodes.size());
		
		// find a good match between parse candidates and argument boundary of gold SRL
		for (SRArg arg:instance.getArgs()) {
			if (arg.isPredicate()) continue;
			BitSet argBitSet = arg.getTokenSet();
		
			if (argBitSet.isEmpty()) continue;
			float []fScores = new float[candidateTokens.size()];
			
			for (int i=0; i<candidateTokens.size(); ++i) {
				BitSet clone = (BitSet)candidateTokens.get(i).clone();
				clone.and(argBitSet);
				fScores[i] = getFScore(clone.cardinality()*1.0f/candidateTokens.get(i).cardinality(), clone.cardinality()*1.0f/argBitSet.cardinality());
				if (fScores[i]>0.5f)
					labels.get(i).put(removeArgModifier(arg.label), fScores[i]);
			}
			int index = SRLUtil.getMaxIndex(fScores);
			
			// if the best matched candidate phrase fscore is above threshold, labeled it the argument
			if (fScores[index]>=0.9999f) {
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
			if (labels.get(i)==null) {
				labels.remove(i);
				candidateNodes.remove(i);
				continue;
			}
	}
	/*
	public static List<TBNode> getArgumentCandidates(TBNode predicate, SRInstance support, LanguageUtil langUtil, int levelDown, boolean allHeadPhrases) {
		List<TBNode> nodes = getArgumentCandidates(predicate.getRoot());
		filterPredicateNode(nodes, predicate);
		return nodes;
	}*/
	
	public static List<TBNode> getArgumentCandidates(TBNode node)
	{
		List<TBNode> nodes = new ArrayList<TBNode>();
		if (node.isTerminal())
			return nodes;
		
		for (TBNode childNode:node.getChildren())
		{
			if (childNode.isToken())
				nodes.add(childNode);
			else
			{
				List<TBNode> childNodes = getArgumentCandidates(childNode);
				if (childNodes.size()>0)// && node.getChildren().length>1)
					nodes.add(childNode);
				if (childNodes.size()>1)
				{
				    if (childNode.getChildren().length>1)
				        nodes.addAll(childNodes);
				    else
				        nodes.addAll(childNodes.subList(1, childNodes.size()));
				}
			}
		}
		return nodes;
	}
	
	static List<TBNode> getArgumentCandidates(TBNode predicate, SRInstance support, LanguageUtil langUtil, int levelDown, boolean allHeadPhrases) {
		boolean toRoot = true;
		
		boolean isVerb = langUtil.isVerb(predicate.getPOS());
		if (support != null) {
			int levelUp = 0;			
			SRArg foundArg = null;
			for (SRArg arg:support.getArgs()) {
				if (arg.getLabel().equals(SRLModel.NOT_ARG)) continue;
				if (arg.tokenSet.get(predicate.getTokenIndex())) {
					foundArg = arg;
					TBNode node = predicate;
					while (node != arg.node) {
						++levelUp;
						node=node.getParent();
					}
					break;	
				}
			}
			if (foundArg!=null) {
				Set<TBNode> candidates = new HashSet<TBNode>(getNodes(predicate, levelUp, levelDown, allHeadPhrases));
				if (!isVerb) {
					for (SRArg arg:support.getArgs())
						if (arg!=foundArg && !arg.getLabel().equals(SRLModel.NOT_ARG))
							candidates.add(arg.node);
				} else {
					TBNode argNode = foundArg.node;
					
					if (toRoot)
						levelUp = argNode.getLevelToRoot();
					else {
						levelUp = 0;
						boolean foundVP=false;
						while (argNode!=null) {
							++levelUp;
							if (foundVP && (langUtil.isClause(argNode.getPOS()) || argNode.getPOS().equals("FRAG")))
								break;
							if (argNode.getPOS().equals("VP"))
								foundVP = true;
							argNode = argNode.getParent();
						}
					}
					
					if (levelUp>0)
						candidates.addAll(getNodes(foundArg.node, levelUp, levelDown-1, allHeadPhrases));
					for (SRArg arg:support.getArgs())
						if (arg!=foundArg && !arg.getLabel().equals(SRLModel.NOT_ARG)) {
							boolean foundCandidate = false;
							for (TBNode node:candidates)
								if (arg.node==node) {
									foundCandidate = true;
									break;
								}
							if (!foundCandidate)
								candidates.add(arg.node);
						}
				}
				return new ArrayList<TBNode>(candidates);
			}
			if (langUtil.isVerb(predicate.getPOS())) {
				TBNode ancestor = predicate.getLowestCommonAncestor(support.predicateNode);
				if (ancestor.getPOS().equals("VP") && (levelUp=predicate.getLevelToNode(ancestor))==support.predicateNode.getLevelToNode(ancestor)) {
					// support probably is the head of VP conjunction
					Set<TBNode> candidates = new HashSet<TBNode>(getNodes(predicate, levelUp, levelDown, allHeadPhrases));
					if (toRoot) {
						levelUp = ancestor.getLevelToRoot();
						candidates.addAll(getNodes(ancestor, levelUp, levelDown-1, allHeadPhrases));
					}
					for (SRArg arg:support.getArgs())
						if (arg!=foundArg && !arg.getLabel().equals(SRLModel.NOT_ARG) && !arg.getLabel().equals("rel")) {
							boolean foundCandidate = false;
							for (TBNode node:candidates)
								if (arg.node==node) {
									foundCandidate = true;
									break;
								}
							if (!foundCandidate)
								candidates.add(arg.node);
						}
					return new ArrayList<TBNode>(candidates);
				}
			}
		}
		int levelUp = 0;
		TBNode node = predicate.getParent();
		boolean foundClause=false;
		boolean foundRelativeClause=false;
		while (node!=null) {
			++levelUp;
			if (!isVerb && (node.getPOS().equals("VP")||langUtil.isClause(node.getPOS())) || node.getPOS().equals("FRAG"))
				break;
			
			// work around for relative clauses when the support verb is missing
			if (foundClause && langUtil.isVerb(node.getHead().getPOS()) && node.getHead()!=predicate && 
					(foundRelativeClause || !langUtil.isRelativeClause(node.getPOS())) ) {
				TBNode headConstituent = node.getHead().getConstituentByHead();
				levelUp+=node.getLevelToNode(headConstituent);
				node = headConstituent;
				break;
			}
			
			if (langUtil.isClause(node.getPOS()))
				foundClause = true;
			
			if (langUtil.isRelativeClause(node.getPOS()))
				foundRelativeClause = true;
			
			node = node.getParent();
		}
		Set<TBNode> candidates = new HashSet<TBNode>(getNodes(predicate, levelUp, levelDown, allHeadPhrases));	
		if (toRoot && node!=null) {
			levelUp = node.getLevelToRoot();
			candidates.addAll(getNodes(node, levelUp, levelDown-1, allHeadPhrases));
		}
		return new ArrayList<TBNode>(candidates);	
	}
	
	static List<TBNode> getNodes(TBNode node, int levelUp, int levelDown, boolean getHeadPhrases) {
		List<TBNode> nodes = new ArrayList<TBNode>();
		if (levelUp<=0) 
			return nodes;
		getNodes(node, levelUp, levelDown, nodes, getHeadPhrases);
		return nodes;
	}
	
	static void getNodes(TBNode node, int levelUp, int levelDown, List<TBNode> nodes, boolean getHeadPhrases) {
		if ((levelUp<=0 || node.getParent()==null) && levelDown<=0)
			return;
		
		if (levelUp>0) {
			TBNode parent = node.getParent();
			
			for (int i=0; i<parent.getChildren().length; ++i) {
				if (node.getChildIndex()==i) continue;
				if (parent.getChildren()[i].getTokenSet().cardinality()>0) {
					nodes.add(parent.getChildren()[i]);
					if (levelDown>0)
						nodes.addAll(getNodes(parent.getChildren()[i], 0, levelDown, false));
					if (getHeadPhrases)
						nodes.addAll(getHeadPhrases(parent.getChildren()[i]));			
				}
			}
			if (levelUp>1)
				nodes.addAll(getNodes(parent, levelUp-1, levelDown, getHeadPhrases));
		} else {
			Set<TBNode> nodeSet = new HashSet<TBNode>();
			for (TBNode child:node.getChildren())
				// make sure this is not an empty element node
				if (child.getTokenSet().cardinality()>0) {
					nodeSet.add(child);
					if (levelDown>0)
						nodeSet.addAll(getNodes(child, 0, levelDown-1, getHeadPhrases));
				}
			// if we are going down a level, we need to find more than 1 node, otherwise
			// the single node would have the same token set as the current node
			if (nodeSet.size()>1)
				nodes.addAll(nodeSet);
		}
		return;
	}
	
	static List<TBNode> getHeadPhrases(TBNode node) {
		List<TBNode> nodes = new ArrayList<TBNode>();
		for (TBNode dependent:node.getHead().getDependentNodes(false)) {
			if (!dependent.isDecendentOf(node))
				continue;
			TBNode constituent = dependent.getConstituentByHead();
			if (constituent.isToken()&&constituent.getParent().getPOS().equals("NP"))
				continue;
			nodes.add(constituent);
			nodes.addAll(getHeadPhrases(constituent));
		}
		return nodes;
	}
	
	public static void filterPredicateNode(List<TBNode> argNodes, TBNode predicateNode) {
		for (Iterator<TBNode> iter=argNodes.iterator(); iter.hasNext();)
			if (iter.next().getTokenSet().get(predicateNode.getTokenIndex()))
				iter.remove();
		return;
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

	public static String getMaxLabel(Map<String, Float> labels)
	{
		float val = Float.NEGATIVE_INFINITY;
		String maxLabel = null;
		for (Map.Entry<String, Float> entry:labels.entrySet())
		{
			if (entry.getValue()>val)
			{
				val = entry.getValue();
				maxLabel = entry.getKey();
			}
		}
		return maxLabel;
	}
/*
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
    */
}
