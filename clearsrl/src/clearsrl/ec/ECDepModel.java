package clearsrl.ec;

import clearsrl.ec.ECCommon.Feature;
import clearsrl.ec.ECCommon.LabelType;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import clearcommon.alg.Classifier;
import clearcommon.alg.CrossValidator;
import clearcommon.alg.FeatureSet;
import clearcommon.alg.LinearClassifier;
import clearcommon.alg.PairWiseClassifier;
import clearcommon.propbank.PBArg;
import clearcommon.propbank.PBInstance;
import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearcommon.util.ChineseUtil;
import clearcommon.util.ECDependent;

public class ECDepModel extends ECModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	transient int propCnt;
	transient int propTotal;
	transient int elipsisCnt;
	transient int elipsisTotal;
	
	
	public ECDepModel (Set<EnumSet<Feature>> featureSet) {
		this(featureSet, LabelType.ALL);
	}
	
	public ECDepModel (Set<EnumSet<Feature>> featureSet, LabelType labelType) {
		super(featureSet, labelType);
		propTotal = propCnt = 0;
		elipsisCnt = elipsisTotal = 0;
	}
	
	public EnumMap<Feature,List<String>> getHeadFeatures(TBNode head, PBInstance instance) {
		EnumMap<Feature,List<String>> sample = new EnumMap<Feature,List<String>>(Feature.class);
		return sample;
	}
	
    static String makePath(List<TBNode> nodes, boolean includeToken) {
    	StringBuilder builder = new StringBuilder();
    	for (int i=(includeToken?0:1); i<nodes.size()-(includeToken?0:1); ++i) {
    		if (builder.length()!=0)
    			builder.append(nodes.get(i-1).getParent()==nodes.get(i)?ECCommon.UP_CHAR:ECCommon.DOWN_CHAR);
    		builder.append(nodes.get(i).getPOS());
    	}
    	return builder.toString();
    }
        
	public List<EnumMap<Feature,List<String>>> extractSampleFeature(TBTree tree, BitSet[] headMasks, List<String> labels, List<PBInstance> pList, boolean buildDictionary) {
		List<EnumMap<Feature,List<String>>> samples = new ArrayList<EnumMap<Feature,List<String>>>();

		TBNode[] tokens = tree.getTokenNodes();
		String[] lemmas = new String[tokens.length];
		String[] poses = new String[tokens.length];
		for (int i=0; i<tokens.length;++i) {
			lemmas[i] = tokens[i].getWord();
			poses[i] = tokens[i].getPOS();
		}
		
		List<EnumMap<Feature,List<String>>> positionSamples = new ArrayList<EnumMap<Feature,List<String>>>();
		List<EnumMap<Feature,List<String>>> headSamples = new ArrayList<EnumMap<Feature,List<String>>>();

		PBInstance[] props = new PBInstance[tokens.length];
		if (pList!=null)
			for (PBInstance instance:pList)
				props[instance.getPredicate().getTokenIndex()] = instance;

		//Add head only features
		for (int t=0; t<tokens.length; ++t) {
			TBNode token = tokens[t];
			TBNode head = tokens[t];
			/*
			if (head.getPOS().matches("BA|LB|SB")) {
				List<TBNode> nodes = head.getDependentNodes();
				if (nodes.size()==1 || nodes.get(0).getPOS().startsWith("V"))
					head = nodes.get(0);
				else if (head.getChildIndex()==0 && head.getParent().getChildren().length==2)
					head = head.getParent().getChildren()[1].getHead();
				else {
					for (int c = head.getChildIndex(); c<head.getParent().getChildren().length; ++c)
						if (head.getParent().getChildren()[c].isPos("VP")) {
							head = head.getParent().getChildren()[c].getHead();
							break;
						}
				}
			}*/
			int h = head.getTokenIndex();
			PBInstance prop = props[t] = props[h];
			
			EnumMap<Feature,List<String>> sample = new EnumMap<Feature,List<String>>(Feature.class);
			for (Feature feature:features.getFeaturesFlat())
				switch (feature) {
				case H_LEMMA:
					sample.put(feature, ECCommon.getPartial(lemmas[head.getTokenIndex()]));
					break;
				case H_POS:
					sample.put(feature, Arrays.asList(poses[head.getTokenIndex()]));
					break;
				case H_CONSTITUENT:
					if (head.getConstituentByHead()!=null)
						sample.put(feature, Arrays.asList(head.getConstituentByHead().getPOS()));
					break;
				case H_ORG_LEMMA:
					if (token!=head)
						sample.put(feature, ECCommon.getPartial(lemmas[head.getTokenIndex()]));
					break;
				case H_ORG_POS:
					if (token!=head)
						sample.put(feature, Arrays.asList(poses[head.getTokenIndex()]));
					break;
				case H_ORG_SPATH: 
					if (token!=head)
						sample.put(feature, Arrays.asList(makePath(head.getConstituentByHead().getPath(token), true)));
					break;
				case H_ORG_DPATH:  // dependency path from head to modified head
					// TODO:
					break;		
				case H_H_LEMMA:    // head of head
					if (token.getHeadOfHead()!=null)
						sample.put(feature, ECCommon.getPartial(lemmas[token.getHeadOfHead().getTokenIndex()]));
					break;
				case H_H_POS:      // head of head lemma
					if (token.getHeadOfHead()!=null)
						sample.put(feature, Arrays.asList(poses[token.getHeadOfHead().getTokenIndex()]));
					break;
				case H_H_SPATH:    // syntactic path to head of head
					if (token.getHeadOfHead()!=null)
						sample.put(feature, Arrays.asList(makePath(token.getConstituentByHead().getPath(token.getHeadOfHead()), true)));
					break;
				case H_SFRAME:
				{
					StringBuilder builder = new StringBuilder();
					for (TBNode node:head.getParent().getChildren()) {
						if (node != head)
							builder.append(node.getPOS().toLowerCase()+"-");
						else
							builder.append(node.getPOS().toUpperCase()+"-");
					}
					sample.put(feature, Arrays.asList(builder.toString()));
					break;
				}
				case H_VOICE:
					if (chLangUtil.isVerb(head.getPOS())) {
						int passive = chLangUtil.getPassive(head);
						if (passive !=0)
							sample.put(feature, Arrays.asList("passive", Integer.toString(passive)));
						else
							sample.put(feature, Arrays.asList("active"));
					}
					break;
				case SRL_NOARG0:             // predicate has no ARG0
					if (prop!= null) {
						for (PBArg arg:prop.getAllArgs())
							if (arg.getLabel().equals("ARG0")) {
								sample.put(feature, Arrays.asList(Boolean.toString(false), arg.getNode().getPOS()));
								break;
							}
						if (sample.get(feature)==null)
							sample.put(feature, Arrays.asList(Boolean.toString(true)));
					}
					break;
				case SRL_NOLEFTCOREARG:      // predicate has no core argument to its left
					if (prop!= null) {
						for (PBArg arg:prop.getArgs())
							if (arg.getTerminalSet().nextSetBit(0)<prop.getPredicate().getTerminalIndex() 
									&& arg.getLabel().matches("(R-)*ARG\\d")) {
								sample.put(feature, Arrays.asList(Boolean.toString(false), arg.getLabel()));
								break;
							}
						if (sample.get(feature)==null)
							sample.put(feature, Arrays.asList(Boolean.toString(true)));
					}
					break;
				case SRL_NOLEFTNPARG:      // predicate has no NP argument to its left
					if (prop!= null) {
						for (PBArg arg:prop.getArgs())
							if (arg.getTerminalSet().nextSetBit(0)<prop.getPredicate().getTerminalIndex() 
									&& arg.getNode().getPOS().equals("NP")) {
								sample.put(feature, Arrays.asList(Boolean.toString(false), arg.getLabel()));
								break;
							}
						if (sample.get(feature)==null)
							sample.put(feature, Arrays.asList(Boolean.toString(true)));
					}
					break;
				case SRL_FRAME:
					if (prop!= null) {
						boolean addedEC = false;
						ArrayList<String> pathList = new ArrayList<String>();
						
						List<PBArg> frontList = new ArrayList<PBArg>();
						List<PBArg> backList = new ArrayList<PBArg>();
						
						StringBuilder pathStr = new StringBuilder();
						for (PBArg arg:prop.getArgs()) {
							if (arg.getLabel().equals("rel")) {
								if (!addedEC) {
									pathStr.append("_EC");
									addedEC = true;
								}
								break;
							}
							BitSet tokenSet = arg.getTokenSet();
							if (tokenSet.nextSetBit(0)!=h && tokenSet.get(h)) {
								addedEC = false;
								break;
							}
							if (tokenSet.nextSetBit(0)>=h && addedEC==false) {
								pathStr.append("_EC");
								addedEC = true;
								
							}
							if (addedEC)backList.add(arg);
							else frontList.add(arg);
							pathStr.append("_"+arg.getLabel());
						}
						if (addedEC){
							for (PBArg arg:frontList) {
								pathList.add("f-"+arg.getLabel());
								pathList.add("f-"+arg.getNode().getPOS());
								pathList.add("f-"+arg.getLabel()+"-"+arg.getNode().getPOS());
								pathList.add("f-"+arg.getNode().getHeadword());
							}
							for (PBArg arg:backList) {
								pathList.add("b-"+arg.getLabel());
								pathList.add("b-"+arg.getNode().getPOS());
								pathList.add("b-"+arg.getLabel()+"-"+arg.getNode().getPOS());
								pathList.add("b-"+arg.getNode().getHeadword());
							}
							pathList.add(pathStr.toString());
						}
						
						TBNode ancestor = head;
						while (!ancestor.getPOS().matches("(IP|FRAG)") && ancestor.getParent()!=null && ancestor.getParent().getTokenSet().nextSetBit(0)==h)
							ancestor = ancestor.getParent();
						
						if (ancestor.getPOS().matches("(IP|FRAG)")) {
							if (ancestor.getHead()==prop.getPredicate()) {
								pathStr = new StringBuilder(ancestor.getPOS()+"-ec");
								StringBuilder pathStr2 = new StringBuilder(ancestor.getPOS()+"-ec");
								for (int c=0; c<ancestor.getChildren().length; ++c) {
									PBArg foundArg = null;
									for (PBArg arg:prop.getArgs())
										if (arg.getNode()==ancestor.getChildren()[c]) {
											foundArg = arg;
											break;
										}
									String label = foundArg==null?ancestor.getChildren()[c].getPOS():foundArg.getLabel();
									if (!pathStr.toString().endsWith(label)) pathStr.append("-"+label);
									if (foundArg!=null && !pathStr2.toString().endsWith(label) || ancestor.getHead()==ancestor.getChildren()[c].getHead()) pathStr2.append("-"+label);
									
									if (ancestor.getHead()==ancestor.getChildren()[c].getHead()) break;
								}
								pathList.add(pathStr.toString()); pathList.add(pathStr2.toString()+"-2"); 
							}	
						} else if (ancestor.getParent()!=null) {
							pathStr = new StringBuilder(ancestor.getParent().getPOS());
							for (int c=0; c<ancestor.getParent().getChildren().length; ++c) {
								if (c==ancestor.getChildIndex())
									pathStr.append("-ec");

								PBArg foundArg = null;
								for (PBArg arg:prop.getArgs())
									if (arg.getNode()==ancestor.getParent().getChildren()[c]) {
										foundArg = arg;
										break;
									}
								String label = foundArg==null?ancestor.getParent().getChildren()[c].getPOS():foundArg.getLabel();
								if (!pathStr.toString().endsWith(label)) pathStr.append("-"+label);
								if (ancestor.getParent().getChildren()[c].getHead()==prop.getPredicate()) break;
							}
							pathList.add(pathStr.toString());			
						}
					
						pathList.trimToSize();
						if (!pathList.isEmpty()) sample.put(feature, pathList);
					}
					break;
				case SRL_LEFTARGTYPE:
					if (prop!= null) {
						List<String> argTypes = new ArrayList<String>();
						for (PBArg arg:prop.getArgs())
							if (h>0 && arg.getTokenSet().get(h-1) && arg.getTokenSet().get(h)) {
								argTypes.clear();
								break;
							} else if (arg.getTokenSet().length()<=h)
								argTypes.add(arg.getLabel());
						if (argTypes.isEmpty()) sample.put(feature, argTypes);
					}
					break;
				default:
					break;
				}
			headSamples.add(sample);
		}
		
		// Add position only features
		for (int t=0; t<=tokens.length; ++t) {
			EnumMap<Feature,List<String>> sample = new EnumMap<Feature,List<String>>(Feature.class);
			for (Feature feature:features.getFeaturesFlat())
				switch (feature) {
				case T_L_LEMMA:
					if (t>0) sample.put(feature, ECCommon.getPartial(lemmas[t-1]));
					break;
				case T_L_POS:
					if (t>0) sample.put(feature, Arrays.asList(poses[t-1]));
					break;
				case T_R_LEMMA:
					if (t<tokens.length) sample.put(feature, ECCommon.getPartial(lemmas[t]));
					break;
				case T_R_POS:
					if (t<tokens.length) sample.put(feature, Arrays.asList(poses[t]));
					break;
				case T_R_PARSE_FRAME:
					if (t<tokens.length) {
						TBNode ancestor = tokens[t];
						while (!ancestor.getPOS().matches("(IP|FRAG)") && ancestor.getParent()!=null && ancestor.getParent().getTokenSet().nextSetBit(0)==t)
							ancestor = ancestor.getParent();
						if (ancestor.getPOS().matches("(IP|FRAG)")) {
							StringBuilder pathStr = new StringBuilder(ancestor.getPOS());
							for (int c=0; c<ancestor.getChildren().length; ++c) {
								if (c>0 && ancestor.getChildren()[c].getPOS().equals(ancestor.getChildren()[c-1].getPOS()))
									continue;
								pathStr.append("-"+ancestor.getChildren()[c].getPOS());
								
								if (ancestor.getHead()==ancestor.getChildren()[c].getHead()) break;
							}
							sample.put(feature, Arrays.asList(pathStr.toString()));
						} else if (ancestor.getParent()!=null) {
							StringBuilder pathStr = new StringBuilder(ancestor.getParent().getPOS());
							for (int c=0; c<ancestor.getParent().getChildren().length; ++c) {
								if (c==ancestor.getChildIndex())
									pathStr.append("-ec");
								else if (c>0 && ancestor.getParent().getChildren()[c].getPOS().equals(ancestor.getParent().getChildren()[c-1].getPOS()))
									continue;
								pathStr.append("-"+ancestor.getParent().getChildren()[c].getPOS());
								
								if (ancestor.getParent().getChildren()[c].getHead()==ancestor.getParent().getHead()) break;
							}
							if (pathStr.toString().indexOf("-VP")>0)
								sample.put(feature, Arrays.asList(pathStr.toString(), ancestor.getParent().getPOS()+"-ec-"+ancestor.getPOS()+"-2"));							
						}
					}
					break;
				default:
					break;
				}
			positionSamples.add(sample);
		}
		
		// dependency args
		for (int h=0; h<tokens.length; ++h) {
			PBInstance prop = props[h];
			for (int t=headMasks[h].nextSetBit(0); t>=0; t=headMasks[h].nextSetBit(t+1)) {
				EnumMap<Feature,List<String>> sample = new EnumMap<Feature,List<String>>(Feature.class);
				sample.putAll(headSamples.get(h));
				sample.putAll(positionSamples.get(t));
				for (Feature feature:features.getFeaturesFlat())
					switch (feature) {
					case D_POSITION:
						sample.put(feature, Arrays.asList(t<=h?"left":"right"));
						break;
					case D_DIST:
						sample.put(feature, Arrays.asList(Integer.toString(t-h)));
						break;
					case D_SPATH:
						if (t<=h && t>0)
							sample.put(feature, Arrays.asList(makePath(tokens[t-1].getPath(tokens[h]), false)));
						else if (t>h && t<tokens.length)
							sample.put(feature, Arrays.asList(makePath(tokens[t].getPath(tokens[h]), false)));
						break;
					case D_DPATH:
						// TODO:
						break;
					case SRL_INFRONTARG:
						if (prop!= null) {
							for (PBArg arg:prop.getArgs())
								if (arg.getTokenSet().nextSetBit(0)==t) {
									sample.put(feature, Arrays.asList(arg.getLabel(), Boolean.toString(true)));
									break;
								}
						}
						break;
					case SRL_BEHINDARG:
						if (prop!= null) {
							for (PBArg arg:prop.getArgs())
								if (arg.getTokenSet().length()==t) {
									sample.put(feature, Arrays.asList(arg.getLabel(), Boolean.toString(true)));
									break;
								}
						}
						break;
					case SRL_INARG:              // positioned inside an argument of the predicate
						if (prop!= null) {
							for (PBArg arg:prop.getArgs())
								if (t>0 && arg.getTokenSet().get(t-1) && arg.getTokenSet().get(t)) {
									sample.put(feature, Arrays.asList(Boolean.toString(true)));
									break;
								}
						}
						break;
					default:
						break;
					}
					
					/*
				case SRL_ANCESTOR:      
					List<String> before = new LinkedList<String>();
					List<String> after = new LinkedList<String>();
					
					TBNode node = pred;
					while (node.getParent()!=null) {
						node = node.getParent();
						if (node.getHead()!=pred) break;
						before.add(node.getPOS());
					}
					after.add(node.getPOS());

					if (node.getPOS().matches("LCP|CP")) {
						if (node.getPOS().equals("CP") && node.getParent()!=null) {
							node = node.getParent();
							after.add(node.getPOS());
						}
						if (node.getParent()!=null) {
							node = node.getParent();
							after.add(node.getPOS());
						}
					} 
					
					sample.put(feature, Arrays.asList(before.toString(), "a-"+after.toString(), 
							"al-"+after.get(after.size()-1), "al-"+after.get(after.size()-1)+"-"+node.getHeadword()));

					break;
				case SRL_PARENTPRED: 
					{
						TBNode enclosingNode = null;
						PBArg enclosingArg = null;
						for (int e=0; e<predNodes.size(); ++e) {
							if (p==e || props[e]==null) continue;
							for (PBArg arg:props[e].getArgs())
								if (arg.getTokenSet().get(pred.getTokenIndex())) {
									if (enclosingNode==null || enclosingNode.getLevelToRoot() < predNodes.get(e).getLevelToRoot()) {
										enclosingNode = predNodes.get(e);
										enclosingArg = arg;
									}
									break;
								}
						}
						if (enclosingNode!=null)
							sample.put(feature, Arrays.asList(Boolean.TRUE.toString(), enclosingNode.getWord(), enclosingArg.getLabel(), enclosingArg.getNode().getPOS()));
					}
					break;
					*/
				samples.add(sample);
			}
		}
		return samples;
	}


	public void addTrainingSentence(TBTree goldTree, TBTree parsedTree, List<PBInstance> props, boolean buildDictionary) {
		parsedTree = parsedTree==null?goldTree:parsedTree;
		
		BitSet[] headCandidates = ECCommon.getECCandidates(parsedTree);
		// TODO: test whether this is better?
		ECCommon.addGoldCandidates(goldTree, headCandidates);
		
		List<String> labels = ECCommon.makeECDepLabels(goldTree, headCandidates);
		
		List<EnumMap<Feature,List<String>>> samples = extractSampleFeature(parsedTree==null?goldTree:parsedTree, headCandidates, labels, props, buildDictionary);
		
		if (buildDictionary) {
			for (int i=0; i<samples.size();++i) {
				boolean notEC = ECCommon.NOT_EC.equals(labels.get(i));
				features.addToDictionary(samples.get(i), notEC?notECFeatureWeight:1f);
				labelStringMap.adjustOrPutValue(labels.get(i), 1, 1);
			}
		} else {  
			List<Sample> sampleList = new ArrayList<Sample>();
            for (int i=0; i<samples.size();++i)
                if (labelStringMap.containsKey(labels.get(i)))
                    sampleList.add(new Sample(samples.get(i), labels.get(i)));
            
            if (!sampleList.isEmpty()) {
                List<Sample[]> tSamples = trainingSamples.get(parsedTree.getFilename());
                if (tSamples == null) {
                    tSamples = new ArrayList<Sample[]>();
                    trainingSamples.put(parsedTree.getFilename(), tSamples);
                }
                tSamples.add(sampleList.toArray(new Sample[sampleList.size()]));
            }
		}
	}
	
    String[] train(int folds, int threads, String[] labels) {   			
        int[][] X = null;
        int[] y = null;
        int[] seed = null;
    	
    	boolean hasSequenceFeature = features.getFeaturesFlat().contains(Feature.ECP1) || features.getFeaturesFlat().contains(Feature.ECN1);
        List<int[]> xList = new ArrayList<int[]>();
        TIntList yList = new TIntArrayList();
        TIntList seedList = new TIntArrayList();
        int lCnt = 0;
        int treeCnt = 0;
        for (Map.Entry<String, List<Sample[]>> entry:trainingSamples.entrySet()) {
            for (Sample[] samples:entry.getValue())
                for (int i=0; i<samples.length;++i) {
                	if (hasSequenceFeature) {
                		if (features.getFeaturesFlat().contains(Feature.ECN1)) {
                			if (i+1<samples.length) {
                				String label = labels==null?samples[i+1].label:labels[lCnt+1];
                        		samples[i].features.put(Feature.ECN1, Arrays.asList(label));
                			}
                		} else if (features.getFeaturesFlat().contains(Feature.ECP1)) {
                			if (i>0) {	
                        		String label = labels==null?samples[i-1].label:labels[lCnt-1];
                        		samples[i].features.put(Feature.ECP1, Arrays.asList(label));
                			}
                		}
                	}
                	
                	xList.add(features.getFeatureVector(features.convertFlatSample(samples[i].features)));
                    yList.add(labelStringMap.get(samples[i].label));
                    seedList.add(treeCnt);
                    ++lCnt;
                }
            ++treeCnt;
        }
        X = xList.toArray(new int[xList.size()][]);
        y = yList.toArray();
        seed = seedList.toArray();

        double[] weights = new double[labelStringMap.size()];
            
        String[] labelTypes = labelStringMap.keys(new String[labelStringMap.size()]);
        Arrays.sort(labelTypes);
 
        int idx=0;
        for (String label:labelTypes) {
            if (label.equals(ECCommon.NOT_EC))
                weights[idx] = 1;
            else
                weights[idx] = 1;
            ++idx;
        }

        int[] yV;
        if (folds>1) {
            CrossValidator validator = new CrossValidator(classifier, threads);
            yV =  validator.validate(folds, X, y, seed, true);
        } else {
            classifier.train(X, y);
            yV = new int[y.length];
            for (int i=0; i<y.length; ++i)
                yV[i] = classifier.predict(X[i]);
        }
        
        String[] newLabels = new String[yV.length];
        for (int i=0; i<yV.length; ++i)
        	newLabels[i] = labelIndexMap.get(yV[i]);
        
        return newLabels;
    }
	
    // TODO: this is probably not the right algorithm
    void fillLinearLabel(TBNode head, String[][] predictions, String[] labels) {
    	for (TBNode dep:head.getDependentNodes())
    		fillLinearLabel(dep, predictions, labels);
    	for (int i=0; i<predictions[head.getTokenIndex()].length; ++i)
    		if (predictions[head.getTokenIndex()][i]!=null && !ECCommon.NOT_EC.equals(predictions[head.getTokenIndex()][i])) {
    			if (labels[i]==null)
    				labels[i] = predictions[head.getTokenIndex()][i];
    			else if (i<head.getTokenIndex())
    				labels[i] = predictions[head.getTokenIndex()][i]+' '+labels[i];
    			else
    				labels[i] += ' '+predictions[head.getTokenIndex()][i];
    		}
    }
    
    public String[] predict(TBTree tree, List<PBInstance> props) {
		BitSet[] headMasks = ECCommon.getECCandidates(tree);

    	List<EnumMap<Feature,List<String>>> samples = extractSampleFeature(tree, headMasks, null, props, false);
    	
    	String[] prediction = new String[samples.size()];
    	for (int i=0; i<samples.size(); ++i)
    		prediction[i] = labelIndexMap.get(classifier.predict(features.getFeatureVector(samples.get(i))));
    	
    	String[][] headPrediction = new String[headMasks.length][headMasks.length+1];
    	
    	int cnt=0;
    	for (int h=0; h<headMasks.length; ++h)
    		for (int t=headMasks[h].nextSetBit(0);t>0;t=headMasks[h].nextSetBit(t+1))
    			headPrediction[h][t] = prediction[cnt++];

    	
    	String[] labels = new String[tree.getTokenCount()+1];
    	
    	fillLinearLabel(tree.getRootNode().getHead(), headPrediction, labels);
    	
    	for (int l=0; l<labels.length; ++l)
    		if (labels[l]==null)
    			labels[l] = ECCommon.NOT_EC;
    	
        return labels;
    }
    
    public void train(Properties prop) {
    	System.out.printf("pro score %d/%d\n", propCnt, propTotal);
    	System.out.printf("elipsis score %d/%d\n", elipsisCnt, elipsisTotal);
    	int folds = Integer.parseInt(prop.getProperty("crossvalidation.folds","5"));
    	int threads = Integer.parseInt(prop.getProperty("crossvalidation.threads","2"));

    	boolean hasSequenceFeature = features.getFeaturesFlat().contains(Feature.ECP1) || features.getFeaturesFlat().contains(Feature.ECN1);
    	
        classifier = new LinearClassifier();
        classifier.initialize(labelStringMap, prop);
        
        int rounds = hasSequenceFeature?5:1;
        double threshold = 0.001;
        
        String[] goldLabels = null;

        List<String> labelList = new ArrayList<String>();
        for (Map.Entry<String, List<Sample[]>> entry:trainingSamples.entrySet())
            for (Sample[] samples:entry.getValue())
                for (Sample sample:samples)
                	labelList.add(sample.label);
        goldLabels = labelList.toArray(new String[labelList.size()]);

        String[] labels = goldLabels;
        for (int r=0; r<rounds; ++r) {
        	String[] newLabels = train(hasSequenceFeature?folds:1, threads, labels);
        	int cnt=0;
        	for (int i=0; i<labels.length; ++i) {
        		//if (i<50) System.out.println(i+" "+labels[i]+" "+newLabels[i]);
        		if (labels[i].equals(newLabels[i])) ++cnt;
        	}
        	double agreement = cnt*1.0/labels.length;
        	System.out.printf("Round %d: %f\n", r, agreement);
        	labels = newLabels;
        
        	ECScore score = new ECScore(new TreeSet<String>(Arrays.asList(labelStringMap.keys(new String[labelStringMap.size()]))));
        	for (int i=0; i<labels.length; ++i)
        		score.addResult(labels[i], goldLabels[i]);
        
        	System.out.println(score.toString());
        	
        	if (1-agreement<=threshold) break;
        }
    }

}
