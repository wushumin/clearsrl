package clearsrl.ec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import clearcommon.treebank.TBNode;
import clearcommon.treebank.TBTree;
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
		H_CONSTITUENT, 
		H_ORG_LEMMA,  // modified head (find head of VP)
		H_ORG_POS,    // modified head POS
		H_ORG_SPATH,  // syntactic path from head to modified head
		H_ORG_DPATH,  // dependency path from head to modified head
		H_H_LEMMA,    // head of head
		H_H_POS,      // head of head lemma
		H_H_SPATH,    // syntactic path to head of head
		H_SFRAME,
		H_VOICE,
		
		// dependency features
		D_POSITION,
		D_DIST,
		D_SPATH,
		D_DPATH,
		
		PARSE_FRAME,
		
		// SRL feature
		SRL_LEMMA,              // lemma of the closest predicate to the right
		SRL_POS,                // part-of-speech of the closest predicate to the right
		SRL_ANCESTOR,           // ancestor POS of the predicate
		SRL_NOARG0,             // predicate has no ARG0
		SRL_NOLEFTCOREARG,      // predicate has no core argument to its left
		SRL_NOLEFTNPARG,        // predicate has no NP argument to its left
		SRL_PATH,               // tree path from position to predicate
		SRL_FRAME,              // relative position of argument types in front of predicate
		SRL_INFRONTARG,         // positioned right in front of an argument (include arg label)
		SRL_BEHINDARG,          // positioned behind an argument
		SRL_LEFTARGTYPE,        // argument types to the left 
		SRL_FIRSTARGPOS,        // positioned right in front of the first argument
		SRL_INARG,              // positioned inside an argument of the predicate
		SRL_BETWEENCOREARG,     // positioned between the predicate and one of its core argument 
		SRL_PARENTPRED,
		SRL_PARALLEL,
		
		// previous token EC feature
		ECP1,
		ECN1, 
		ECALL
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
	
	public static List<String> makeECDepLabels(TBTree goldTree, BitSet[] headCandidates) {
		TLongObjectMap<String> ecMap = new TLongObjectHashMap<String>();
		int tokenIdx=0;
		for (TBNode node:goldTree.getTerminalNodes()) {
			if (node.isToken()) {
				tokenIdx = node.getTokenIndex()+1;
				continue;
			}
			TBNode head = node.getHeadOfHead();
			if (head!=null && head.isToken())
				ecMap.put(makeIndex(head.getTokenIndex(), tokenIdx), node.getECType());
		}
		List<String> labels = new ArrayList<String>();
		String label;
		for (int i=0; i<headCandidates.length; ++i)
			for (int j=headCandidates[i].nextSetBit(0); j>=0; j=headCandidates[i].nextSetBit(j+1)) {
				label = ecMap.get(makeIndex(i,j));
				labels.add(label==null?ECCommon.NOT_EC:label);
			}
		return labels;
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
			
			TBNode node = head;
			while (node!=headConstituent) {
				node = node.getParent();
				candidateSet.set(node.getTokenSet().nextSetBit(0));
				for (TBNode child:node.getChildren()) {
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

		int tokenIdx = 0;
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
				labels[tokenIdx]=labels[tokenIdx]==ECCommon.NOT_EC?ecType:labels[tokenIdx]+' '+ecType;
		}
		
		return labels;
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
