package clearsrl;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntIterator;
import clearcommon.alg.Classifier;
import clearcommon.alg.LinearClassifier;
import clearcommon.alg.PairWiseClassifier;
import clearcommon.alg.Classifier.InstanceFormat;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearsrl.LanguageUtil.POS;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class SRLModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public static final String NOT_ARG="!ARG";
	public static final String IS_ARG="ARG";
	
	public enum Feature
	{
		// Constituent independent features
		PREDICATE,
		PREDICATEPOS,
		VOICE,
		SUBCATEGORIZATION,
		PREDICATE_PREDICATEPOS,
		PREDICATE_VOICE,
		
		// Constituent dependent features
		PATH,
		PATHG1,           
		PATHG2,
		PATHG3,
		PATHG4,
		PHRASETYPE,
		POSITION,
		CONSTITUENTDIST,
		VOICE_POSITION,
		PREDICATE_POSITION,
		PREDICATE_VOICE_POSITION,
		PATH_POSITION,
		PATH_POSITION_VOICE,
		PATH_CONSTITUENTDIST,
		PATH_POSITION_VOICE_CONSTITUENTDIST,
		FIRSTCONSTITUENTREl,
		FIRSTCONSTITUENTABS,
		HEADWORD,
		HEADWORDPOS,
		HEADWORDDUPE,
		HEADWORDDUPE_HEADWORDPOS,
		PREDICATE_HEADWORD,
		FIRSTWORD,
		FIRSTWORDPOS,
		LASTWORD,
		LASTWORDPOS,
		FIRSTWORDPOS_LASTWORDPOS,
		SYNTACTICFRAME,
		NAMEDENTITIES
	};
	
	public enum PredicateFeature
	{
	    PREDICATE,
	    PREDICATEPOS,
	    PREDICATE_PREDICATEPOS,
	    PARENTPOS,
	    PREDICATE_PARENTPOS,
	    PREDICATE_PREDICATEPOS_PARENTPOS,
	    LEFTWORD,
	    LEFTWORDPOS,
	    LEFTWORD_PREDICATE,
	    LEFTWORDPOS_PREDICATE,
	    RIGHTHEADWORD,
	    RIGHTHEADWORDPOS,
	    RIGHTPHASETYPE,
	    PREDICATE_RIGHTHEADWORD,
	    PREDICATE_RIGHTHEADWORDPOS,
	    PREDICATE_RIGHTPHASETYPE
	};
	
	public static String UP_CHAR = "^";
	public static String DOWN_CHAR = "v";
	public static String RIGHT_ARROW = "->";

	boolean                                           labeled;
	public boolean isLabeled() {
		return labeled;
	}

	public void setLabeled(boolean labeled) {
		this.labeled = labeled;
	}

	transient LanguageUtil                            langUtil;
	EnumSet<Feature>                                  featureSet;
	EnumSet<PredicateFeature>                         predicateFeatureSet;
	
	SortedSet<String>                                 labelStringSet;
	
	Map<Feature, SortedSet<String>>                   featureStringSet;
	Map<PredicateFeature, SortedSet<String>>          predicateFeatureStringSet;

	Classifier                                        classifier;
	Classifier                                        predicateClassifier;
	
	transient TObjectIntHashMap<String>               labelStringMap;
	transient TIntObjectHashMap<String>               labelIndexMap;
	
	transient double[]                                labelValues;
	
	transient Map<Feature, TObjectIntHashMap<String>> featureStringMap;
	transient Map<PredicateFeature, TObjectIntHashMap<String>> predicateFeatureStringMap;
	
	transient ArrayList<int[]>                        trainingFeatures;
	transient ArrayList<String>                       trainingLabels;
	
	transient ArrayList<int[]>                        predicateTrainingFeatures;
	transient TIntArrayList                           predicateTrainingLabels;

	transient int                                     hit = 0;
	transient int                                     total = 0;
	
	transient private SRLScore                        score;
	
	public SRLModel (EnumSet<Feature> featureSet, EnumSet<PredicateFeature> predicateFeatureSet)
	{
		labeled       = true;
		this.featureSet = featureSet;
		this.predicateFeatureSet = predicateFeatureSet;
		
		trainingFeatures = new ArrayList<int[]>();
		trainingLabels = new ArrayList<String>();
		
		predicateTrainingFeatures = new ArrayList<int[]>();
		predicateTrainingLabels = new TIntArrayList();
	}
	
	public void setLanguageUtil(LanguageUtil langUtil)
	{
	    this.langUtil = langUtil;
	}
	
	public void initDictionary()
	{
		labelStringMap     = new TObjectIntHashMap<String>();
		featureStringMap   = new TreeMap<Feature, TObjectIntHashMap<String>>();
		for (Feature feature:featureSet)
			featureStringMap.put(feature, new TObjectIntHashMap<String>());
		
		if (predicateFeatureSet == null) return;
		predicateFeatureStringMap = new TreeMap<PredicateFeature, TObjectIntHashMap<String>>();
		for (PredicateFeature feature:predicateFeatureSet)
		    predicateFeatureStringMap.put(feature, new TObjectIntHashMap<String>());
		
	}
	
	public TObjectIntHashMap<String> getLabelValueMap()
	{
		return labelStringMap;
	}

	public void finalizeDictionary(int cutoff)
	{
		featureStringSet = new TreeMap<Feature, SortedSet<String>>();
		for (Feature feature:featureSet)
			featureStringSet.put(feature, trimMap(featureStringMap.get(feature),cutoff));
		labelStringSet = trimMap(labelStringMap,35);
	
		if (predicateFeatureSet!=null)
		{
            predicateFeatureStringSet = new TreeMap<PredicateFeature, SortedSet<String>>();
            for (PredicateFeature feature:predicateFeatureSet)
                predicateFeatureStringSet.put(feature, trimMap(predicateFeatureStringMap.get(feature),cutoff));		
		}
		/*
		
		labelStringSet = new TreeSet<String>();
		for (String str:labelStringMap.keys(new String[labelStringMap.size()]))
			labelStringSet.add(str);
		*/
		System.out.println("Labels: ");
		for (String label:labelStringSet)
		{
			System.out.println("  "+label+" "+labelStringMap.get(label));
		}	
		rebuildMaps();
		for (Map.Entry<Feature, SortedSet<String>>entry:featureStringSet.entrySet())
			System.out.println(entry.getKey()+": "+entry.getValue().size()+(entry.getValue().size()<=50?" "+entry.getValue():""));
		System.out.println("labels: "+labelStringSet.size()+" "+labelStringSet);
	}
	
    TreeSet<String> trimMap(TObjectIntHashMap<String> featureMap, long threshold)
    {
        TreeSet<String> featureStrings = new TreeSet<String>();
        for (TObjectIntIterator<String> iter = featureMap.iterator(); iter.hasNext();)
        {
            iter.advance();
            if (iter.value()>=threshold)
                featureStrings.add(iter.key());
        }
        return featureStrings;
    }
    
    void rebuildMaps()
    {   
        featureStringMap = new TreeMap<Feature, TObjectIntHashMap<String>>();
        for (Map.Entry<Feature, SortedSet<String>> entry: featureStringSet.entrySet())
        {
            featureStringMap.put(entry.getKey(), new TObjectIntHashMap<String>());  
            buildMapIndex(entry.getValue(), featureStringMap.get(entry.getKey()));      
        }
        
        buildMapIndex(labelStringSet, labelStringMap = new TObjectIntHashMap<String>());
        
        labelIndexMap = new TIntObjectHashMap<String>();
        for (TObjectIntIterator<String> iter=labelStringMap.iterator();iter.hasNext();)
        {
            iter.advance();
            labelIndexMap.put(iter.value(),iter.key());
        }
        labelValues = new double[labelIndexMap.size()];
        
        if (predicateFeatureSet!=null)
        {
            predicateFeatureStringMap = new TreeMap<PredicateFeature, TObjectIntHashMap<String>>();
            for (Map.Entry<PredicateFeature, SortedSet<String>> entry: predicateFeatureStringSet.entrySet())
            {
                predicateFeatureStringMap.put(entry.getKey(), new TObjectIntHashMap<String>());  
                buildMapIndex(entry.getValue(), predicateFeatureStringMap.get(entry.getKey()));      
            }
        }
    }
	
    public void addTrainingSentence(TBTree tree, List<SRInstance> instances, String[] namedEntities, boolean buildDictionary)
    {
        BitSet isPredicate = new BitSet();
        for (SRInstance instance:instances)
        {
            addTrainingSamples(instance, namedEntities, buildDictionary);
            isPredicate.set(instance.predicateNode.getTokenIndex());
        }
        
        List<TBNode> nodes = tree.getRootNode().getTokenNodes();
        
        ArrayList<TBNode> predicateCandidates = new ArrayList<TBNode>();
        for (TBNode node: nodes)
            if (node.getPOS().startsWith("V"))
                predicateCandidates.add(node);
        
        // add predicate training samples
        if (buildDictionary)
        {
            for (TBNode predicateCandidate:predicateCandidates)
                for(Map.Entry<PredicateFeature,List<String>> entry:extractPredicateFeature(predicateCandidate, nodes).entrySet())
                {
                    TObjectIntHashMap<String> fMap = predicateFeatureStringMap.get(entry.getKey());
                    for (String fVal:entry.getValue())
                        fMap.put(fVal, fMap.get(fVal)+1);
                }
        }
        else
        {
            for (TBNode predicateCandidate:predicateCandidates)
            {
                predicateTrainingFeatures.add(getPredicateFeatureVector(extractPredicateFeature(predicateCandidate, nodes)));
                predicateTrainingLabels.add(isPredicate.get(predicateCandidate.getTokenIndex())?1:2);
            }
        }
    }
    
    public void addTrainingSamples(SRInstance sampleInstance, String[] namedEntities, boolean buildDictionary)    
	//public void addTrainingSamples(TBNode predicateNode, ArrayList<TBNode> argNodes, ArrayList<String> labels, String[] namedEntities, boolean buildDictionary)
	{
        if (sampleInstance.args.size()<=1) return;
        
        ArrayList<TBNode> argNodes = new ArrayList<TBNode>();
        ArrayList<String> labels = new ArrayList<String>();
        
        for (SRArg arg:sampleInstance.args)
        {
            if (arg.isPredicate()) continue;
            argNodes.add(arg.node);
            labels.add(arg.label);
        }
        
		ArrayList<EnumMap<Feature,List<String>>> samples = extractSampleFeature(sampleInstance.predicateNode, argNodes, namedEntities);
		
		if (buildDictionary)
		{
			int c=0;
			for (EnumMap<Feature,List<String>>sample:samples)
			{
				if (!NOT_ARG.equals(labels.get(c)))
				{
					for(Map.Entry<Feature,List<String>> entry:sample.entrySet())
					{
						TObjectIntHashMap<String> fMap = featureStringMap.get(entry.getKey());
						for (String fVal:entry.getValue())
							fMap.put(fVal, fMap.get(fVal)+1);
					}
				}
				//if (!NOT_ARG.equals(SRLUtil.getMaxLabel(labels.get(c))))
				//	System.out.println(sample.get(Feature.PATH));
				labelStringMap.put(labels.get(c), labelStringMap.get(labels.get(c))+1);
				++c;
			}
		}
		else
		{
		    int c=0;
			for (EnumMap<Feature,List<String>>sample:samples)
			{
			    if (labelStringSet.contains(labels.get(c)))
			    {
			        trainingFeatures.add(getFeatureVector(sample));
			        trainingLabels.add(labels.get(c));
			    }
			    ++c;
			}
		}
	}
	

	public ArrayList<EnumMap<Feature,List<String>>> extractSampleFeature(TBNode predicateNode, List<TBNode> argNodes, String[] namedEntities)
	{
		ArrayList<EnumMap<Feature,List<String>>> samples = new ArrayList<EnumMap<Feature,List<String>>>();
		
		//EnumMap<Feature,List<String>> defaultMap = new EnumMap<Feature,List<String>>(Feature.class);
		
		// find predicate lemma
		String predicateLemma = predicateNode.getWord();
		{
			List<String> stems = langUtil.findStems(predicateLemma, LanguageUtil.POS.VERB);
			if (!stems.isEmpty()) predicateLemma = stems.get(0);
		}
		
        List<String> predicateAlternatives = langUtil.getPredicateAlternatives(predicateLemma, featureStringSet==null?null:featureStringSet.get(Feature.PREDICATE));
		
		// build subcategorization feature
		String subCat = null;
		{
			StringBuilder builder = new StringBuilder();
			TBNode predicateParentNode = predicateNode.getParent();
			builder.append(predicateParentNode.getPOS()+RIGHT_ARROW);
			for (TBNode node:predicateParentNode.getChildren())
				builder.append(node.getPOS()+"-");
			subCat = builder.toString();
		}
		
		// figure out whether predicate is passive or not
		boolean isPassive = langUtil.getPassive(predicateNode)>0;			


		EnumMap<Feature,List<String>> defaultMap = new EnumMap<Feature,List<String>>(Feature.class);
		for (Feature feature:featureSet)
		{
			switch (feature) {
			case PREDICATE:
				//defaultMap.put(feature, Arrays.asList(predicateLemma));
				defaultMap.put(feature, predicateAlternatives);
				break;
			case PREDICATEPOS:
				if (predicateNode.getPOS().matches("V.*"))
					defaultMap.put(feature, Arrays.asList(predicateNode.getPOS()));
				break;
			case VOICE:
				//if (isPassive) defaultMap.put(feature, Arrays.asList("Passive"));
			    defaultMap.put(feature, Arrays.asList(isPassive?"Passive":"Active"));
				break;
			case SUBCATEGORIZATION:
				defaultMap.put(feature, Arrays.asList(subCat.toString()));
				break;
			case PREDICATE_PREDICATEPOS:
				if (predicateNode.getPOS().matches("V.*"))
					//defaultMap.put(feature, Arrays.asList(predicateLemma+'_'+predicateNode.getPOS()));
					defaultMap.put(feature, permute(predicateAlternatives, Arrays.asList(predicateNode.getPOS())));
				break;
			case PREDICATE_VOICE:
				//defaultMap.put(feature, Arrays.asList(predicateLemma+'_'+(isPassive?"Passive":"Active")));
				defaultMap.put(feature, permute(predicateAlternatives, Arrays.asList(isPassive?"Passive":"Active")));
				break;
			default:
				break;
			}
		}
				
		for (TBNode argNode:argNodes)
		{
			EnumMap<Feature,List<String>> sample = new EnumMap<Feature,List<String>>(defaultMap);
			List<TBNode> tnodes = argNode.getTokenNodes();
			
			List<TBNode> argToTopNodes = argNode.getPathToRoot();
		    List<TBNode> predToTopNodes = predicateNode.getPathToRoot();
		    TBNode joinNode = trimPathNodes(argToTopNodes, predToTopNodes);
			
			List<String> path = getPath(argToTopNodes, predToTopNodes, joinNode);
			
			// compute head
			TBNode head = argNode.getHead();
			if (argNode.getPOS().matches("PP.*"))
			{
			    int i = argNode.getChildren().length-1;
				for (; i>=0; --i)
				{
					if (argNode.getChildren()[i].getPOS().matches("NP.*"))
					{
						if (argNode.getChildren()[i].getHead()!=null && argNode.getChildren()[i].getHeadword()!=null)
							head = argNode.getChildren()[i].getHead();
						break;
					}
				}
				if (i<0 && argNode.getChildren()[argNode.getChildren().length-1].getHead()!=null && 
				        argNode.getChildren()[argNode.getChildren().length-1].getHeadword()!=null)
				    head = argNode.getChildren()[argNode.getChildren().length-1].getHead();
			}
			
			boolean isBefore = tnodes.get(0).getTokenIndex() < predicateNode.getTokenIndex();
			//System.out.println(predicateNode+" "+predicateNode.tokenIndex+": "+argNode.getParent()+" "+argToTopNodes.size());
			int cstDst = countConstituents(argToTopNodes.get(0).getPOS(), isBefore?argToTopNodes:predToTopNodes, isBefore?predToTopNodes:argToTopNodes, joinNode);
			//System.out.println(cstDst+" "+path);
			
			for (Feature feature:featureSet)
			{
				String pathStr=null;
				{
					StringBuilder buffer = new StringBuilder();
					for (String node:path) buffer.append(node);
					pathStr = buffer.toString();
				}
				switch (feature) {
				case PATH:
					sample.put(feature, Arrays.asList(pathStr));
					break;
				case PATHG1:
				{
					StringBuilder buffer = new StringBuilder();
					for (String node:path) 
					{
						if (node.equals(DOWN_CHAR)) break;
						buffer.append(node);
					}
					sample.put(feature, Arrays.asList(buffer.toString()));
					break;
				}
				case PATHG2:
				{
					boolean inSameClause = true;
					
					StringBuilder buffer = new StringBuilder();
					for (int i=1; i<path.size()-2; i++) 
					{
						if (path.get(i).startsWith("S") || path.get(i).equals("LP"))
						{
							buffer.append("S");
							if (!path.get(i-1).equals(UP_CHAR)||!path.get(i+1).equals(DOWN_CHAR))
								inSameClause = false;
						}
						else
							buffer.append(path.get(i));
					}

					ArrayList<String> values = new ArrayList<String>();
					if (inSameClause)
						values.add("SameClause");
					if (path.size()>2)
					{
						values.add(path.get(0)+buffer.toString().replaceAll("[^S\\^v][A-Z]*","*")+path.get(path.size()-2)+path.get(path.size()-1));
						values.add(path.get(0)+buffer.toString().replaceAll("[\\^v][^S][A-Z]*", "")+path.get(path.size()-2)+path.get(path.size()-1));
						values.add(path.get(0)+buffer.toString().replaceAll("S([\\^v][^S][A-Z]*)+\\^S", "S^S").replaceAll("S([\\^v][^S][A-Z]*)+vS", "SvS")+path.get(path.size()-2)+path.get(path.size()-1));
					}
					sample.put(feature, values);
					break;
				}
				case PATHG3:
				{
					ArrayList<String> trigram = new ArrayList<String>();
					for (int i=0; i<path.size()-4; i+=2)
						trigram.add(path.get(i)+path.get(i+1)+path.get(i+2)+path.get(i+3)+path.get(i+4));
						
					sample.put(feature, trigram);
					break;
				}
				case PATHG4:
				{
					StringBuilder buffer = new StringBuilder();
					for (String node:path) buffer.append(node.charAt(0));
					sample.put(feature, Arrays.asList(buffer.toString()));
					break;
				}
				
				case PHRASETYPE:
					if (argNode.getPOS().matches("PP.*") && argNode.getHead()!=null && argNode.getHeadword()!=null)
					{
						ArrayList<String> list = new ArrayList<String>();
						list.add("PP"); list.add("PP-"+argNode.getHeadword()); 
						sample.put(feature, list);
					}
					else
						sample.put(feature, Arrays.asList(argNode.getPOS()));
					break;
				case POSITION:
					//if (isBefore) sample.put(feature, Arrays.asList("before"));
				    sample.put(feature, Arrays.asList(isBefore?"before":"after"));
					break;
				case CONSTITUENTDIST:
				{
				    ArrayList<String> list = new ArrayList<String>();
				    //if (cstDst!=1) list.add("notclosest");
				    //list.add(cstDst==1?"closest":"notclosest");
				    list.add(Integer.toString(cstDst>5?5:cstDst));
				    for (int i=1; i<=5; ++i)
				        if (cstDst<=i)
				            list.add("<="+Integer.toString(i));
				    sample.put(feature, list);
                    break;
				}
				case HEADWORD:
					sample.put(feature, Arrays.asList(head.getWord()));
					break;
				case HEADWORDPOS:
					sample.put(feature, Arrays.asList(head.getPOS()));
					break;
				case HEADWORDDUPE:
				    if (argNode.getParent()!=null && argNode.getHead()==argNode.getParent().getHead())
				        sample.put(feature, Arrays.asList("HeadDupe"));
				    //else
				    //   sample.put(feature, Arrays.asList("HeadUnique"));
                    break;
				case HEADWORDDUPE_HEADWORDPOS:
                    if (argNode.getParent()!=null && argNode.getHead()==argNode.getParent().getHead())
                        sample.put(feature, Arrays.asList("HeadDupe_"+argNode.getHead().getPOS()));
                    //else
                    //    sample.put(feature, Arrays.asList("HeadUnique_"+SRLUtil.removeTrace(argNode.head.pos)));
                    break;
				case PREDICATE_HEADWORD:
					sample.put(feature, permute(predicateAlternatives, Arrays.asList(head.getPOS())));
					break;
				case FIRSTWORD:
					sample.put(feature, Arrays.asList(tnodes.get(0).getWord()));
					break;
				case FIRSTWORDPOS:
					sample.put(feature, Arrays.asList(tnodes.get(0).getPOS()));
					break;
				case LASTWORD:
					sample.put(feature, Arrays.asList(tnodes.get(tnodes.size()-1).getWord()));
					break;
				case LASTWORDPOS:
					sample.put(feature, Arrays.asList(tnodes.get(tnodes.size()-1).getPOS()));
					break;
				case FIRSTWORDPOS_LASTWORDPOS:
					sample.put(feature, Arrays.asList(tnodes.get(0).getPOS()+"_"+tnodes.get(tnodes.size()-1).getPOS()));
					break;
				case VOICE_POSITION:
				{
					String vp = (isPassive?"Passive":"Active")+(isBefore?"_before":"_after");
					if (!vp.isEmpty()) sample.put(feature, Arrays.asList(vp));
					break;
				}
				case PREDICATE_POSITION:
					//sample.put(feature, Arrays.asList(predicateLemma+(isBefore?"_before":"+_after")));
					sample.put(feature, permute(predicateAlternatives, Arrays.asList((isBefore?"_before":"+_after"))));
					break;
				case PREDICATE_VOICE_POSITION:
					//sample.put(feature, Arrays.asList(predicateLemma+(isPassive?"_Passive":"_Active")+(isBefore?"_before":"_after")));
					sample.put(feature, permute(predicateAlternatives, Arrays.asList((isPassive?"_Passive":"_Active")+(isBefore?"_before":"+_after"))));
					break;
				case PATH_POSITION:
					sample.put(feature, Arrays.asList(pathStr+(isBefore?"_before":"_after")));
					break;
				case PATH_POSITION_VOICE:
					sample.put(feature, Arrays.asList(pathStr+(isBefore?"_before":"_after")+(isPassive?"_Passive":"_Active")));
					break;
				case PATH_CONSTITUENTDIST:
				    sample.put(feature, Arrays.asList(pathStr+(cstDst==1?"_closest":"_notclosest")));
                    break;
                case PATH_POSITION_VOICE_CONSTITUENTDIST:
                    sample.put(feature, Arrays.asList(pathStr+(isPassive?"_Passive":"Active")+(isBefore?"_before":"after")+(cstDst==1?"_closest":"_notclosest")));
                    break;
				case SYNTACTICFRAME:
				{
					StringBuilder builder = new StringBuilder();
					for (TBNode node:argNode.getParent().getChildren())
					{
						if (node != argNode)
							builder.append(node.getPOS().toLowerCase()+"-");
						else
							builder.append(node.getPOS().toUpperCase()+"-");
					}
					sample.put(feature, Arrays.asList(builder.toString()));
					break;
				}
				case NAMEDENTITIES:
				{
				    if (namedEntities==null) break;
					Set<String> neSet = new TreeSet<String>();
					for (TBNode node:tnodes)
					{
						if (namedEntities[node.getTokenIndex()]!=null)
							neSet.add(namedEntities[node.getTokenIndex()]);
					}
					if (!neSet.isEmpty())
					{
						StringBuilder buffer = new StringBuilder();
						for (String ne:neSet) buffer.append(ne+" ");
						neSet.add(buffer.toString().trim());
						sample.put(feature, Arrays.asList(neSet.toArray(new String[neSet.size()])));
					}
					break;
				}
				default:
					break;
				}
			}
			
			
			samples.add(sample);
		}
		
		return samples;
	}

    int[] getFeatureVector(EnumMap<Feature,List<String>> sample)
    {
        int index = 0;
        TIntHashSet featureSet = new TIntHashSet();
        
        for(Map.Entry<Feature,List<String>> entry:sample.entrySet())
        {
            TObjectIntHashMap<String> fMap = featureStringMap.get(entry.getKey());
            for (String fVal:entry.getValue())
            {
                int mapIdx = fMap.get(fVal);
                if (mapIdx>0) featureSet.add(index+mapIdx-1);
            }               
            index += fMap.size();
        }
        int [] features = featureSet.toArray();
        Arrays.sort(features);
        return features;
    }
    
    public EnumMap<PredicateFeature,List<String>> extractPredicateFeature(TBNode predicateNode, List<TBNode> nodes)
    {
        EnumMap<PredicateFeature,List<String>> sample = new EnumMap<PredicateFeature,List<String>>(PredicateFeature.class);
        
        // find predicate lemma
        String predicateLemma = predicateNode.getWord();
        {
            List<String> stems = langUtil.findStems(predicateLemma, LanguageUtil.POS.VERB);
            if (!stems.isEmpty()) predicateLemma = stems.get(0);
        }

        TBNode parent = predicateNode.getParent();
        TBNode leftNode = predicateNode.getTokenIndex()>0? nodes.get(predicateNode.getTokenIndex()-1):null;
        TBNode rightSibling = (predicateNode.getParent()!=null&&predicateNode.getChildIndex()<predicateNode.getParent().getChildren().length-1)?
                predicateNode.getParent().getChildren()[predicateNode.getChildIndex()+1]:null;
        TBNode rightHeadnode = (rightSibling==null||rightSibling.getHead()==null)?null:rightSibling.getHead();
        
        for (PredicateFeature feature:predicateFeatureSet)
        {
            switch (feature)
            {
            case PREDICATE:
                sample.put(feature, Arrays.asList(predicateLemma));
                break;
            case PREDICATEPOS:
                sample.put(feature, Arrays.asList(predicateNode.getPOS()));
                break;
            case PREDICATE_PREDICATEPOS:
                sample.put(feature, Arrays.asList(predicateLemma+"_"+predicateNode.getPOS()));
                break;
            case PARENTPOS:
                if (parent!=null)
                    sample.put(feature, Arrays.asList(parent.getPOS()));
                break;
            case PREDICATE_PARENTPOS:
                if (parent!=null)
                    sample.put(feature, Arrays.asList(predicateLemma+"_"+parent.getPOS()));
                break;
            case PREDICATE_PREDICATEPOS_PARENTPOS:
                if (parent!=null)
                    sample.put(feature, Arrays.asList(predicateLemma+"_"+predicateNode.getPOS()+"_"+parent.getPOS()));
                break;
            case LEFTWORD:
                if (leftNode!=null) sample.put(feature, Arrays.asList(leftNode.getWord()));   
                break;
            case LEFTWORDPOS:
                if (leftNode!=null)
                    sample.put(feature, Arrays.asList(leftNode.getPOS()));
                break;
            case LEFTWORD_PREDICATE:
                if (leftNode!=null)
                    sample.put(feature, Arrays.asList(leftNode.getWord()+"_"+predicateLemma));
                break;
            case LEFTWORDPOS_PREDICATE:
                if (leftNode!=null)
                    sample.put(feature, Arrays.asList(leftNode.getPOS()+"_"+predicateLemma));
                break;
            case RIGHTHEADWORD:
                if (rightHeadnode!=null) sample.put(feature, Arrays.asList(rightHeadnode.getWord()));   
                break;
            case RIGHTHEADWORDPOS:
                if (rightHeadnode!=null)
                    sample.put(feature, Arrays.asList(rightHeadnode.getPOS()));
                break;
            case RIGHTPHASETYPE:
                if (rightSibling!=null)
                    sample.put(feature, Arrays.asList(rightSibling.getPOS()));
                break;
            case PREDICATE_RIGHTHEADWORD:
                if (rightHeadnode!=null) 
                    sample.put(feature, Arrays.asList(predicateLemma+"_"+rightHeadnode.getWord()));
                break;
            case PREDICATE_RIGHTHEADWORDPOS:
                if (rightHeadnode!=null) 
                    sample.put(feature, Arrays.asList(predicateLemma+"_"+rightHeadnode.getPOS()));
                break;
            case PREDICATE_RIGHTPHASETYPE:
                if (rightSibling!=null)
                    sample.put(feature, Arrays.asList(predicateLemma+"_"+rightSibling.getPOS()));
                break;
            }
        }
        return sample;
    }
    
    static List<String> permute(List<String> lhs, List<String> rhs)
    {
    	ArrayList<String> ret = new ArrayList<String>(lhs.size()*rhs.size());
    	for (String a1:lhs)
    		for (String a2:rhs)
    			ret.add(a1+"_"+a2);
    	return ret;
    }
    
    int[] getPredicateFeatureVector(EnumMap<PredicateFeature,List<String>> sample)
    {
        int index = 0;
        TIntHashSet featureSet = new TIntHashSet();
        
        for(Map.Entry<PredicateFeature,List<String>> entry:sample.entrySet())
        {
            TObjectIntHashMap<String> fMap = predicateFeatureStringMap.get(entry.getKey());
            for (String fVal:entry.getValue())
            {
                int mapIdx = fMap.get(fVal);
                if (mapIdx>0) featureSet.add(index+mapIdx-1);
            }               
            index += fMap.size();
        }
        int [] features = featureSet.toArray();
        Arrays.sort(features);
        return features;
    }
    
    private void buildMapIndex(SortedSet<String> setObj, TObjectIntHashMap<String> mapObj)
    {
        int i=0;
        for (Iterator<String> iter=setObj.iterator(); iter.hasNext();)
            mapObj.put(iter.next(), ++i);
    }
    
    public void train(Properties prop)
    {   
        if (predicateFeatureSet!=null && !predicateTrainingFeatures.isEmpty())
        {
            TObjectIntHashMap<String> predicateLabelMap = new TObjectIntHashMap<String>();
            predicateLabelMap.put("predicate", 1);
            predicateLabelMap.put("not_predicate", 2);
            
            int dist = 0;
            
            for (int i=0; i<predicateTrainingLabels.size(); ++i)
                dist += (predicateTrainingLabels.get(i)==1)?1:0;
            System.out.printf("Training predicates: %d/%d/%d\n", dist, predicateTrainingLabels.size()-dist, predicateTrainingLabels.size());
                
            predicateClassifier = new LinearClassifier(predicateLabelMap, prop);
            predicateClassifier.train(predicateTrainingFeatures.toArray(new int[predicateTrainingFeatures.size()][]),
                    predicateTrainingLabels.toNativeArray());
            
            double score = 0;
            for (int i=0; i<predicateTrainingFeatures.size(); ++i)
                score += (predicateClassifier.predict(predicateTrainingFeatures.get(i))==predicateTrainingLabels.get(i))?1:0;
            System.out.printf("Predicate training accuracy: %f\n", score/predicateTrainingLabels.size());
        }
        
        int[][] X = trainingFeatures.toArray(new int[trainingFeatures.size()][]);
        int[] y = new int[trainingLabels.size()];
        for (int i=0; i<y.length; ++i)
            y[i] = labelStringMap.get(trainingLabels.get(i));

        double[] weights = null;
        if (!labeled)
        {
            int notArgIndex = labelStringMap.get(NOT_ARG);
            for (int i=0; i<y.length; ++i)
                y[i] = y[i]==notArgIndex?notArgIndex:notArgIndex+1;
        }
        else
        {
            weights = new double[labelStringMap.size()];
            
            String[] labels = labelStringMap.keys(new String[labelStringMap.size()]);
            Arrays.sort(labels);
 
            int idx=0;
            for (String label:labels)
            {
                if (label.equals(SRLModel.NOT_ARG))
                    weights[idx] = 1;
                else
                    weights[idx] = 1;
                ++idx;
            }
        }
        
        //liblinearbinary.Problem problem = LinearClassifier.convertToProblem(X, y, Double.parseDouble(prop.getProperty("liblinear.bias", "-1")));
        //classifier = new LinearClassifier(labelStringMap, prop);
        classifier = new PairWiseClassifier(labelStringMap, prop);
        //classifier = new TwoStageClassifier(labelStringMap, 0.95, prop);
        
        //classifier = new LinearClassifier(labelStringMap, prop);
        classifier.train(X, y, weights);

    }

    public List<SRInstance> predict(TBTree parseTree, SRInstance[] goldSRLs, String[] namedEntities)
    {   
        List<SRInstance> predictions = new ArrayList<SRInstance>();
        
        List<TBNode> nodes = parseTree.getRootNode().getTokenNodes();
        double[] vals = new double[2];
        for (TBNode node: nodes)
            if (node.getPOS().startsWith("V") &&
                predicateClassifier.predictValues(getPredicateFeatureVector(extractPredicateFeature(node, nodes)), vals)==1)
            {
            	List<String> stem = langUtil.findStems(node.getWord(), POS.VERB);
                predictions.add(new SRInstance(node, parseTree, stem.get(0)+".XX", vals[1]-vals[0]));
                /*
                SRInstance goldSRL = null;
                for (SRInstance srl:goldSRLs)
                    if (node.getTokenIndex()==srl.predicateNode.getTokenIndex())
                    {
                        goldSRL = srl;
                        break;
                    }
				*/
                predict(predictions.get(predictions.size()-1), namedEntities);
            }
        return predictions;
    }

    public int predict(SRInstance prediction, /*SRInstance goldSRL,*/ String[] namedEntities)
    {
        ArrayList<TBNode> argNodes = SRLUtil.filterPredicateNode(SRLUtil.getArgumentCandidates(prediction.tree.getRootNode()),prediction.tree,prediction.predicateNode);
        ArrayList<EnumMap<Feature,List<String>>> samples = extractSampleFeature(prediction.predicateNode, argNodes, namedEntities);
        
        for (int i=0; i<samples.size(); ++i)
        {
            int labelIndex = classifier.predictValues(getFeatureVector(samples.get(i)), labelValues);
            double value = labelValues[classifier.getLabelIdxMap().get(labelIndex)];
            if (labeled)
            {
                String label;
                if (!(label = labelIndexMap.get(labelIndex)).equals(NOT_ARG))
                    prediction.addArg(new SRArg(label, argNodes.get(i), value));
            } 
        }

        prediction.cleanUpArgs();
        
        /*
        if (goldSRL!=null)
        {
            if (labeled)
                score.addResult(SRLUtil.convertSRInstanceToTokenMap(prediction), SRLUtil.convertSRInstanceToTokenMap(goldSRL));
            else
            {
                Map<String, BitSet> goldMap = SRLUtil.convertSRInstanceToTokenMap(goldSRL);
                Map<String, BitSet> newGoldMap = new TreeMap<String, BitSet>();
                newGoldMap.put(IS_ARG, new BitSet());
                for (Map.Entry<String, BitSet> entry: goldMap.entrySet())
                    newGoldMap.get(IS_ARG).or(entry.getValue());
                score.addResult(SRLUtil.convertSRInstanceToTokenMap(prediction), newGoldMap);
            }
        }*/
        
        return samples.size();
    }

    private void initScore()
    {
        score = new SRLScore(labelStringSet);
    }

    private void writeObject(ObjectOutputStream out) throws IOException
    {
        out.defaultWriteObject();
    }
    
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
    {
        in.defaultReadObject();
        rebuildMaps();
    }

    void writeData(PrintStream out, int[] featureVec, int label, InstanceFormat format)
    {
        if (format==InstanceFormat.DEFAULT)
        {
            out.print(label-1);
            for (int f : featureVec)
                out.print(" "+f);
        }
        else if (format==InstanceFormat.SVM)
        {
            out.print(label);
            for (int f : featureVec)
                out.print(" "+(f+1)+":1");
        }
        out.print("\n");
    }
    
    public void writeTrainingData(String filename, InstanceFormat format) throws FileNotFoundException
    {
        PrintStream fout = new PrintStream(new FileOutputStream(filename));
        
        for (int i=0; i<trainingFeatures.size();++i)
            writeData(fout, trainingFeatures.get(i), labelStringMap.get(trainingLabels.get(i)), format);

        fout.close();
    }
    
    public void writeSample(PrintStream out, TBNode predicateNode, ArrayList<TBNode> argNodes, ArrayList<String> labels, String[] namedEntities, InstanceFormat format)
    {
        ArrayList<EnumMap<Feature,List<String>>> samples = extractSampleFeature(predicateNode, argNodes, namedEntities);
        
        for (int i=0; i<samples.size(); ++i)
        {
            int lIdx = labelStringMap.get(labels.get(i));
            if (lIdx==0)
            {
                System.err.println("Unknown label: "+labels.get(i)+", converting to "+NOT_ARG);
                lIdx = labelStringMap.get(NOT_ARG);
            }
            writeData(out, getFeatureVector(samples.get(i)), lIdx, format);
        }
    }   

	
	int countConstituents(String label, List<TBNode> lnodes, List<TBNode> rnodes, TBNode joinNode)
	{   
	    int count = 0;
	    
	    count += countConstituents(label, new LinkedList<TBNode>(lnodes), true, 100);
        count += countConstituents(label, new LinkedList<TBNode>(rnodes), false, 100);
        
        for (int i=lnodes.get(lnodes.size()-1).getChildIndex()+1; i<rnodes.get(rnodes.size()-1).getChildIndex(); ++i)
            count += countConstituents(label, joinNode.getChildren()[i], lnodes.size()>rnodes.size()?lnodes.size():rnodes.size());
        
        count += joinNode.getPOS().startsWith(label)?1:0;
        
	    return count;
	}
	
    int countConstituents(String label, Deque<TBNode> nodes, boolean left, int depth)
    {   
        TBNode node = nodes.pop();
        int count = node.getPOS().startsWith(label)?1:0;

        if (nodes.isEmpty())
            return count;
        
        ++depth;
        
        if (left)
            for (int i=node.getChildIndex()+1; i<node.getParent().getChildren().length;++i)
                count += countConstituents(label, node.getParent().getChildren()[i], depth);
        else
            for (int i=0; i<node.getChildIndex()-1;++i)
                count += countConstituents(label, node.getParent().getChildren()[i], depth);
        
        return count + countConstituents(label, nodes, left, depth);
    }
	 
    int countConstituents(String label, TBNode node, int depth)
    {   
        int count = node.getPOS().startsWith(label)?1:0;
        
        if (node.isTerminal() || depth == 0)
            return count;
        
        for (TBNode cNode:node.getChildren())
            count += countConstituents(label, cNode, depth-1);
        
        return count;
    }
    
    protected TBNode trimPathNodes(List<TBNode> argNodes, List<TBNode> predNodes)
    {
        TBNode joinNode = null;
        do
        {
            joinNode = argNodes.get(argNodes.size()-1);
            argNodes.remove(argNodes.size()-1);
            predNodes.remove(predNodes.size()-1);
        } while (!argNodes.isEmpty() && !predNodes.isEmpty() && 
                argNodes.get(argNodes.size()-1).getChildIndex()==predNodes.get(predNodes.size()-1).getChildIndex());
                //argNodes.get(argNodes.size()-1).terminalIndex==predNodes.get(predNodes.size()-1).terminalIndex);
        return joinNode;
    }
    
    protected List<String> getPath(TBNode argNode, TBNode predNode)
    {
        List<TBNode> argNodes = argNode.getPathToRoot();
        List<TBNode> predNodes = predNode.getPathToRoot();
        
        TBNode joinNode = trimPathNodes(argNodes, predNodes);
    
        return getPath(argNodes, predNodes, joinNode);
    }
    
    protected List<String> getPath(List<TBNode> argNodes, List<TBNode> predNodes, TBNode joinNode)
    {
        ArrayList<String> path = new ArrayList<String>();
        
        for (TBNode node:argNodes)
        {
            path.add(node.getPOS());
            path.add(UP_CHAR);
        }
        path.add(joinNode.getPOS());
        for (int i=predNodes.size()-1; i>=0; --i)
        {
            path.add(DOWN_CHAR);
            path.add(predNodes.get(i).getPOS());
        }
        return path;
    }       
}
