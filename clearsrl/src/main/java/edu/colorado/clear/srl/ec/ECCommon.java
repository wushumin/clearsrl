package clearsrl.ec;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
import clearsrl.SRLModel.Feature;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TLongObjectHashMap;

public final class ECCommon {
    public static final String NOT_EC = "!";
    public static final String IS_EC = "*";
    
    public static final String UP_CHAR = "^";
    public static final String DOWN_CHAR = "v";
    
    public enum Feature {
        // token features
        T_L_LEMMA,
        T_L_POS,
        T_R_LEMMA,
        T_R_POS,
        T_R_PARSE_FRAME,
        
        // head feature
        H_LEMMA,
        H_POS,
        H_TOP_VERB,
        H_CONSTITUENT, // top level constituent of headed by this node
        H_CONSTITUENT_PARENT,
        H_CONSTITUENT_VP_FIRST, // for VP constituent, whether it's the first
        H_ORG_LEMMA,  // modified head (find head of VP)
        H_ORG_POS,    // modified head POS
        H_ORG_SPATH,  // syntactic path from head to modified head
        H_ORG_DPATH,  // dependency path from head to modified head
        H_H_LEMMA,    // head of head
        H_H_POS,      // head of head lemma
        H_H_SPATH,    // syntactic path to head of head
        H_SFRAME,     // syntactic frame of head
        H_CONSTSFRAME, 
        
        H_VOICE,

        
     // SRL feature
        SRL_LEMMA,              // lemma of the closest predicate to the right
        SRL_POS,                // part-of-speech of the closest predicate to the right
        SRL_ANCESTOR,           // ancestor POS of the predicate
        SRL_NONLOCAL_ARG,
        SRL_FRAMEFILE,
        SRL_FRAMEFILE_LOCAL,
        SRL_NOARG0,             // predicate has no ARG0
        SRL_NOARG1,             // predicate has no ARG1
        SRL_LOCAL_NOARG0,       // predicate has no local ARG0
        SRL_LOCAL_NOARG1,       // predicate has no local ARG1
        SRL_NOLEFTCOREARG,      // predicate has no core argument to its left
        SRL_NOLEFTNPARG,        // predicate has no NP argument to its left
        SRL_PATH,               // tree path from position to predicate
        SRL_FRAME,              // relative position of argument types in front of predicate
        SRL_LEFTARGTYPE,        // argument types to the left 
        SRL_FIRSTARGPOS,        // positioned right in front of the first argument
        SRL_BETWEENCOREARG,     // positioned between the predicate and one of its core argument 
        SRL_PARENTPRED,
        SRL_PARALLEL,
        
        // dependency features
        D_POSITION,
        D_DIST,
        D_SPATH,
        D_SFRONTIER,
        D_DPATH,
        D_V_DIST,
        D_COMMA_DIST,
        D_SRL_ARG01_SAME_SIDE,
        D_SRL_INFRONTARG,         // positioned right in front of an argument (include arg label)
        D_SRL_BEHINDARG,          // positioned behind an argument
        D_SRL_INARG,              // positioned inside an argument of the predicate


        D_IP_ISCHILD,             // for position left of verb, if the common ancestor has an IP
        D_IP_LEFTSIB,             // POS of the left sibling under IP
        D_IP_RIGHTSIB,            // POS of right sibling under IP
        D_IP_INCP,                // if IP is in CP (for both left & right)
        D_VP_ISCHILD,             // for position right of verb, if the common ancestor has an VP
        D_VP_LEFTSIB,             // POS of the left sibling under VP
        D_VP_RIGHTSIB,            // POS of right sibling under VP
        
        PARSE_FRAME,
        
        
        
        // sequence feature
        EC_LABEL(true),
        ECP1(true),
        ECN1(true), 
        ECALL(true),
        
        EC_TOKEN_LEFT(true),
        EC_TOKEN_RIGHT(true),
        EC_TOKEN_SAMESIDE(true),
        EC_TOKEN_ALL(true),
        EC_CP_CHILD(true),
        EC_HEAD_PARENT(true),
        EC_HEAD_ALL;
        
        boolean sequence;

        Feature() {
            this.sequence = false;
        }
        
        Feature(boolean sequence) {
            this.sequence = sequence;
        }
        
        public boolean isSequence() {
            return sequence;
        }
        
        static boolean hasSequenceFeature(EnumSet<Feature> features) {
            for (Feature feature:features)
                if (feature.isSequence())
                    return true;
            return false;
        }
    };
    
    public enum LabelType {
        ALL,
        PRO,
        LITTLEPRO,
        UNLABELED
    }
    
    public static void addGoldCandidates(TBTree goldTree, BitSet[] headCandidates) {
        int tokenIdx=0;
        for (TBNode node:goldTree.getTerminalNodes()) {
            if (node.isToken())
                tokenIdx = node.getTokenIndex()+1;
            TBNode head = node.getHeadOfHead();
            if (head!=null && head.isToken())
                headCandidates[head.getTokenIndex()].set(tokenIdx);
        }   
    }
    
    public static long makeIndex(int treeIndex, int terminalIndex) {
        return (((long)treeIndex)<<32)|terminalIndex;
    }
    
    public static String[] makeECDepLabels(TBTree goldTree, BitSet[] headCandidates) {
        TLongObjectMap<String> ecMap = new TLongObjectHashMap<String>();
        int tokenIdx=0;
        for (TBNode node:goldTree.getTerminalNodes()) {
            if (node.isToken()) {
                tokenIdx = node.getTokenIndex()+1;
                continue;
            }
            TBNode head = node.getHeadOfHead();
            if (head!=null && head.isToken()) {
            	long index = makeIndex(head.getTokenIndex(), tokenIdx);
            	if (ecMap.containsKey(index))
            		ecMap.put(index, ecMap.get(index)+' '+node.getECType());
            	else
            		ecMap.put(makeIndex(head.getTokenIndex(), tokenIdx), node.getECType());
            }
        }
        List<String> labels = new ArrayList<String>();
        String label;
        for (int i=0; i<headCandidates.length; ++i)
            for (int j=headCandidates[i].nextSetBit(0); j>=0; j=headCandidates[i].nextSetBit(j+1)) {
                label = ecMap.get(makeIndex(i,j));
                labels.add(label==null?ECCommon.NOT_EC:label);
            }
        return labels.toArray(new String[labels.size()]);
    }
    
    public static BitSet[] getECCandidates(TBTree tree, boolean full) {
        BitSet[] candidateSets = new BitSet[tree.getTokenCount()];
        if (full) {
            for (int i=0; i<candidateSets.length; ++i) {
                candidateSets[i] = new BitSet(tree.getTokenCount()+1);
                candidateSets[i].set(0, tree.getTokenCount()+1);
            }
            return candidateSets;
        }
        
        for (TBNode head:tree.getTokenNodes()) {
            BitSet candidateSet = new BitSet();
            candidateSets[head.getTokenIndex()] = candidateSet;
            
            TBNode headConstituent = head.getConstituentByHead();
            if (headConstituent.isTerminal())
                continue;
            
            if (!head.getPOS().startsWith("V")) {
	        	TBNode constituent = head;
	        	while (constituent!=headConstituent) {
	        		if (constituent.getPOS().matches("[CIQ]P"))
	        			break;
	        		constituent = constituent.getParent();
	        	}
	        	if (!constituent.getPOS().matches("[CIQ]P"))
	        		continue;
	        }
 
            TBNode node = head;
            while (node!=headConstituent) {
                node = node.getParent();
                candidateSet.set(node.getTokenSet().nextSetBit(0));
                for (TBNode child:node.getChildren()) {
                	// code for pre 1.7 compatibility
                    /*BitSet tokenSet = child.getTokenSet();
                    int idx = -1;
                    while (tokenSet.nextSetBit(idx+1)>=0 && tokenSet.nextSetBit(idx+1)<=tree.getTokenCount())
                        idx = tokenSet.nextSetBit(idx+1);*/
                	// code that runs post 1.7
                    int idx = child.getTokenSet().previousSetBit(tree.getTokenCount());
                    if (idx>=0)
                        candidateSet.set(idx+1);
                }
            }
        }
        return candidateSets;
    }
    
    public static BitSet[] getECCandidates(TBTree tree) {
        return getECCandidates(tree, false);
    }
    
    public static String convertLabel(TBNode ec, LabelType labelType) {
        if (ec==null) return ECCommon.NOT_EC;
        
        switch (labelType) {
        case PRO:
            return ec.getECType().toLowerCase().equals("*pro*")?ec.getECType():ECCommon.NOT_EC;
        case LITTLEPRO:
            return ec.getECType().equals("*pro*")?ec.getECType():ECCommon.NOT_EC;
        case UNLABELED:
            return ec.isEC()?ECCommon.IS_EC:ECCommon.NOT_EC;
        default:
            return ec.getECType();
        }
    }
    
    static List<String> getPartial(String word) {
        
        /*int length = 4;
        
        List<String> partials = new ArrayList<String>();
        partials.add(word);
        
        for (int i=1;i<=word.length() && i<=length; ++i)
            partials.add("p-"+word.substring(0,i));
        
        for (int i=(word.length()-length>0?word.length()-length:0); i<=word.length(); ++i)
            partials.add("s-"+word.substring(i));*/
        
        return Arrays.asList(word); 
    }
    
    public static String[] getECLabels(TBTree tree, LabelType labelType) {
        String[] labels = new String[tree.getTokenCount()+1];
        Arrays.fill(labels, ECCommon.NOT_EC);

        int tokenIdx = -1;
        for (TBNode terminal: tree.getTerminalNodes()) {
            if (terminal.isToken()) {
                tokenIdx = terminal.getTokenIndex();
                continue;
            }
            String ecType = terminal.getECType();
            
            switch (labelType) {
            case ALL:
                break;
            case PRO:
                if (!ecType.toLowerCase().equals("*pro*"))
                    ecType=ECCommon.NOT_EC;
                break;
            case LITTLEPRO:
                if (!ecType.equals("*pro*"))
                    ecType=ECCommon.NOT_EC;
                break;
            case UNLABELED:
                ecType = ECCommon.IS_EC;
                break;
            }
            if (ecType!=ECCommon.NOT_EC)
                labels[tokenIdx+1]=labels[tokenIdx+1]==ECCommon.NOT_EC?ecType:labels[tokenIdx+1]+' '+ecType;
        }
        
        return labels;
    }
    
    public static String[][] getECDepLabels(TBTree tree, LabelType labelType) {
    	String[][] labels = new String[tree.getTokenCount()][tree.getTokenCount()+1];
    	
        int tokenIdx = -1;
        for (TBNode terminal: tree.getTerminalNodes()) {
        	if (terminal.isToken()) {
                tokenIdx = terminal.getTokenIndex();
                continue;
            }
        	if (terminal.getHeadOfHead()==null || !terminal.getHeadOfHead().isToken()) {
        		System.err.println("Weird head link "+tree.getFilename()+":"+tree.getIndex()+" "+terminal.getTerminalIndex());
        		System.err.println(tree.toPrettyParse());
        		continue;
        	}
        	
            String ecType = terminal.getECType();
            
            switch (labelType) {
            case ALL:
                break;
            case PRO:
                if (!ecType.toLowerCase().equals("*pro*"))
                    ecType=ECCommon.NOT_EC;
                break;
            case LITTLEPRO:
                if (!ecType.equals("*pro*"))
                    ecType=ECCommon.NOT_EC;
                break;
            case UNLABELED:
                ecType = ECCommon.IS_EC;
                break;
            }
            if (!ecType.equals(ECCommon.NOT_EC)) {
            	if (labels[terminal.getHeadOfHead().getTokenIndex()][tokenIdx+1]!=null && !ECCommon.NOT_EC.equals(labels[terminal.getHeadOfHead().getTokenIndex()][tokenIdx+1])) {
            		System.err.println("Weird head link "+tree.getFilename()+":"+tree.getIndex()+" "+terminal.getTerminalIndex());
            		System.err.println(tree.toPrettyParse());
            	}
            	
                labels[terminal.getHeadOfHead().getTokenIndex()][tokenIdx+1]=(labels[terminal.getHeadOfHead().getTokenIndex()][tokenIdx+1]==null||labels[terminal.getHeadOfHead().getTokenIndex()][tokenIdx+1]==ECCommon.NOT_EC)?ecType:labels[terminal.getHeadOfHead().getTokenIndex()][tokenIdx+1]+' '+ecType;
            }
        }
        
        return labels;
    }
    
    public static Map<String, Map<Integer, String[][]>> readDepEC(File ecDir, Map<String, TBTree[]> parseMap) {
    	Map<String, Map<Integer, String[][]>>  ecLabelMap = new HashMap<String, Map<Integer, String[][]>>();
    	
    	for (Map.Entry<String, TBTree[]> entry:parseMap.entrySet()) {
    		try {
    			File ecFile = new File(ecDir, entry.getKey()+".ec");
    			readDepEC(new BufferedReader(new FileReader(ecFile)), parseMap, ecLabelMap);
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    	}
    	return ecLabelMap;
    }
    
    public static void readDepEC(BufferedReader reader, Map<String, TBTree[]> parseMap, Map<String, Map<Integer, String[][]>> ecLabelMap) throws IOException {
    	String line = null;
    	while ((line=reader.readLine())!=null){
    		String[] tokens = line.trim().split("\\s");
    		String name = tokens[0];
    		int index = Integer.parseInt(tokens[1]);
    		
    		TBTree tree = parseMap.get(name)[index];
    		
    		Map<Integer, String[][]> ecInnerMap = ecLabelMap.get(name);
    		if (ecInnerMap==null) {
    			ecInnerMap = new TreeMap<Integer, String[][]>();
    			ecLabelMap.put(name, ecInnerMap);
    		}
    		String[][] labels = ecInnerMap.get(index);
    		if (labels==null) {
    			labels = new String[tree.getTokenCount()][tree.getTokenCount()+1];
    			ecInnerMap.put(index, labels);
    		}
    		
    		String[] hLabels = labels[Integer.parseInt(tokens[2])];
    		for (int i=4; i<tokens.length; ++i) {
    			int t = Integer.parseInt(tokens[i].substring(0, tokens[i].indexOf(':')));
    			hLabels[t] = tokens[i].substring(tokens[i].indexOf(':')+1).replaceAll(",", " ");
    		}
    	}
    }
    
    public static void writeDepEC(PrintWriter writer, TBTree tree, String[][] labels) throws IOException{
    	TBNode[] tokens = tree.getTokenNodes();
    	
    	for (int h=0; h<labels.length; ++h) {
    		boolean found = false;
    		for (int t=0; t<labels[h].length; ++t) {
    			if (labels[h][t]==null || ECCommon.NOT_EC.equals(labels[h][t]))
    				continue;
    			if (!found) {
    				writer.printf("%s %d %d %s", tree.getFilename(), tree.getIndex(), h, tokens[h].getWord());
    				found = true;
    			}
    			
    			writer.printf(" %d:",t);
    			String[] subLabels = labels[h][t].trim().split("\\s+");
    			
    			for (int l=0; l<subLabels.length; ++l)
    				writer.print(l==0?subLabels[l]:";"+subLabels[l]);
    		}
    		if (found)
    			writer.print('\n');
    	}
    }

    
    public static double getFMeasure(TObjectIntMap<String> labelMap, int[] yRef, int[]ySys) {
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
        
        int notECIdx = labelMap.get(NOT_EC);
        
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
}
