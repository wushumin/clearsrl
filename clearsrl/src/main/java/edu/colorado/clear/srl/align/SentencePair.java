package edu.colorado.clear.srl.align;

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.set.hash.TLongHashSet;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.colorado.clear.common.treebank.TBNode;

public  class SentencePair implements Serializable {

   /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    public enum WordAlignmentIndex {
        TOKEN,
        TERMINAL,
        PRO
    }
    
    public enum WordAlignmentType {
        SRC,
        DST,
        UNION,
        INTERSECTION
    };
    
    static final Pattern alignPattern = Pattern.compile("\\(\\{([0-9 ]+)\\}\\)");
    static final int[] EMPTY_INT_ARRAY = new int[0];

    public int                       id;
    public Sentence                  src;
    public Sentence                  dst;
    public SortedMap<Long, int[]> srcAlignment;
    public SortedMap<Long, int[]> dstAlignment;
    
    public class BadInstanceException extends Exception {
        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public BadInstanceException(String message)
        {
            super(message);
        }
    }
    
    public SentencePair(int sentId)
    {   
        id = sentId;
        src = new Sentence();
        dst = new Sentence();
        srcAlignment = new TreeMap<Long, int[]>();
        dstAlignment = new TreeMap<Long, int[]>();
    }
        
    public void parseSrcAlign(String line) throws BadInstanceException
    {
        for (int i=0; i<src.indices.length; ++i)
            srcAlignment.put(src.indices[i], EMPTY_INT_ARRAY);
        parseAlign(line, srcAlignment, dst.indices.length);
    }
    
    public void parseDstAlign(String line) throws BadInstanceException
    {
        for (int i=0; i<dst.indices.length; ++i)
            dstAlignment.put(dst.indices[i], EMPTY_INT_ARRAY);
        parseAlign(line, dstAlignment, src.indices.length);
    }
    
    static Pattern pattern = Pattern.compile("(\\d+).*");
    TIntList getIntList(String str) {
    	TIntList retList = new TIntArrayList();
    	for (String tok:str.trim().split(",")) {
    		if (tok.trim().isEmpty())
    			continue;
    		Matcher matcher = pattern.matcher(tok);
    		if (matcher.matches())
    			retList.add(Integer.parseInt(matcher.group(1)));
    	}
    	return retList;
    }
    
    public void parseAlign(String line, boolean zeroIndexed, boolean terminalIndexed, boolean reverse) throws BadInstanceException {
    	if (line.trim().equals("rejected")) {
    		setAlignment(new int[0], new int[0]);
    		return;
    	}
    	
        String[] alignmentStrs = line.trim().isEmpty()?new String[0]:line.trim().split("[ \t]+");
            
        TIntList srcAlignmentIdx = new TIntArrayList();
        TIntList dstAlignmentIdx = new TIntArrayList();
        
        for (int i=0; i<alignmentStrs.length; ++i) {
        	String lhs =alignmentStrs[i].substring(0, alignmentStrs[i].indexOf('-'));
        	String rhs =alignmentStrs[i].substring(alignmentStrs[i].indexOf('-')+1);
        	if (lhs.isEmpty() || rhs.isEmpty())
        		continue;
        	
        	TIntList lhsIntList = getIntList(lhs);
        	TIntList rhsIntList = getIntList(rhs);
        	
        	for (int l:lhsIntList.toArray()) {
        		if (!zeroIndexed)
        			l--;
        		for (int r:rhsIntList.toArray()) {
        			if (!zeroIndexed)
        				r--;
        
        			if (terminalIndexed) {
        				if (src.terminalToTokenMap[reverse?r:l]<0 ||
        						dst.terminalToTokenMap[reverse?l:r]<0)
        					continue;
        				srcAlignmentIdx.add(src.terminalToTokenMap[reverse?r:l]);
            			dstAlignmentIdx.add(dst.terminalToTokenMap[reverse?l:r]);
        			} else {
        				srcAlignmentIdx.add(reverse?r:l);
        				dstAlignmentIdx.add(reverse?l:r);
        			}
        			
        		}
        	}
        }
        setAlignment(srcAlignmentIdx.toArray(), dstAlignmentIdx.toArray());
    }
    
    public String getSrcAlignmentString()
    {
        return getAlignmentString(srcAlignment, src.tokens, dst.tokens);
    }
    
    public String getDstAlignmentString()
    {
        return getAlignmentString(dstAlignment, dst.tokens, src.tokens);
    }
    
    /*
    void addToAlignmentMap(int idx1, int idx2, TIntObjectHashMap<TIntHashSet> alignmentMap) {
    	TIntHashSet alignmentSet1 = alignmentMap.get(idx1);
    	TIntHashSet alignmentSet2 = alignmentMap.get(idx2);
    	
    	if (alignmentSet1==null && alignmentSet2==null) {
    		alignmentSet1 = new TIntHashSet();
    		alignmentSet1.add(idx1);
    		alignmentSet1.add(idx2);
    		alignmentMap.put(idx1, alignmentSet1);
    		alignmentMap.put(idx2, alignmentSet1);
    	} else if (alignmentSet1==null) {
    		alignmentSet2.add(idx1);
    		alignmentMap.put(idx1, alignmentSet2);
    	} else if (alignmentSet2==null) {
    		alignmentSet1.add(idx2);
    		alignmentMap.put(idx2, alignmentSet1);
    	} else if (alignmentSet1!=alignmentSet2) {
    		// need to merge the sets
    		if (alignmentSet1.size()<alignmentSet2.size()) {
    			for (int idx:alignmentSet1.toArray())
    				alignmentMap.put(idx, alignmentSet2);
    			alignmentSet2.addAll(alignmentSet1);
    		} else {
    			for (int idx:alignmentSet2.toArray())
    				alignmentMap.put(idx, alignmentSet1);
    			alignmentSet1.addAll(alignmentSet2);
    		}
    	}
    }*/
    
    void addToAlignmentMap(int[] indices, TIntObjectHashMap<TIntHashSet> alignmentMap) {
    	TIntHashSet alignmentSet = null;
    	
    	for (int idx:indices) {
    		TIntHashSet currentSet = alignmentMap.get(idx);
    		if (alignmentSet==null)
    			alignmentSet = currentSet;
    		
    		if (alignmentSet==null || currentSet==null) {
    			if (alignmentSet==null)
    				alignmentSet = new TIntHashSet();
    			alignmentSet.add(idx);
    			alignmentMap.put(idx, alignmentSet);
    		} else if (alignmentSet!=currentSet) {
    			for (int idx1:currentSet.toArray())
    				alignmentMap.put(idx1, alignmentSet);
    			alignmentSet.addAll(currentSet);
    		}
    	}
    }
    
    public TIntObjectHashMap<TIntHashSet> getAlignmentIdxMap() {
    	TIntObjectHashMap<TIntHashSet> alignmentMap = new TIntObjectHashMap<TIntHashSet>();
    	
    	int idx = 0;
    	for (SortedMap.Entry<Long, int[]> entry:srcAlignment.entrySet()) {
    		int[] indices = new int[entry.getValue()==null?1:entry.getValue().length+1];
    		for (int i=0; i<entry.getValue().length; ++i)
    			indices[i] = -1-entry.getValue()[i];
    		indices[indices.length-1] = idx++;
    		addToAlignmentMap(indices, alignmentMap);
    	}

    	idx=0;
    	for (SortedMap.Entry<Long, int[]> entry:dstAlignment.entrySet()) {
    		int[] indices = entry.getValue()==null?new int[1]:Arrays.copyOf(entry.getValue(), entry.getValue().length+1);
    		indices[indices.length-1] = --idx;
    		addToAlignmentMap(indices, alignmentMap);
    	}
    	
    	return alignmentMap;
 
    }
    
    private String getAlignmentString(SortedMap<Long, int[]> alignment, TBNode[] src, TBNode[] dst)
    {
        StringBuilder ret = new StringBuilder();

        int i=0;
        for (SortedMap.Entry<Long, int[]> entry:alignment.entrySet())
        {
            ret.append(src[i++].getWord()); 
            ret.append(' ');
            for (int dstKey:entry.getValue())
            {
                ret.append(dst[dstKey].getWord());
                ret.append(' ');
            }
            ret.append('|');
        }
        
        return ret.toString();  
    }
    
    public String getSrcAlignmentIndex()
    {
        return getAlignmentIndex(srcAlignment);
    }
    
    public String getDstAlignmentIndex()
    {
        return getAlignmentIndex(dstAlignment);
    }
    
    private String getAlignmentIndex(SortedMap<Long, int[]> alignment)
    {
        StringBuilder ret = new StringBuilder();
        
        for (SortedMap.Entry<Long, int[]> entry:alignment.entrySet())
        {
            for (int dstKey:entry.getValue())
            {
                ret.append(dstKey);
                ret.append(' ');
            }
            ret.append('|');
        }
        return ret.toString();  
    }
    
    void parseAlign(String line, SortedMap<Long, int[]> alignment, int dstLen)  throws BadInstanceException
    {
        Matcher matcher = alignPattern.matcher(line);
        int srcCnt = -1;
        int dstCnt = 0;

        BitSet dstBitSet = new BitSet(dstLen);
        Iterator<Map.Entry<Long, int[]>> iter = alignment.entrySet().iterator();
        Entry<Long, int[]> entry = null;
        while (matcher.find())
        {
            //System.out.print(matcher.group(1)+'|');
            String alignments = matcher.group(1).trim();
            String[] tokens = alignments.isEmpty()?new String[0]:alignments.split(" +");

            int[] vals = new int[tokens.length];
            if (srcCnt>=0) 
            {
                entry = iter.next();
                if (vals.length>0) entry.setValue(vals);
            }
            for (int i=0; i<tokens.length; ++i)
            {
                vals[i] = Integer.parseInt(tokens[i])-1;
                dstBitSet.set(vals[i]);
                ++dstCnt;
            }
            Arrays.sort(vals);
            ++srcCnt;
        }
        //System.out.print("\n");
        if (dstCnt != dstLen || dstCnt != dstBitSet.cardinality() || dstBitSet.nextSetBit(dstCnt)!=-1 || srcCnt!=alignment.size())
        {
            throw new BadInstanceException("mismatch detected: "+srcCnt+", "+alignment.size()+", "+dstCnt+", "+dstLen+", "+src.tbFile+" "+dst.tbFile);
            //System.exit(1);
        }
        //for (int i:align)
        //  System.out.print(" "+i);
        //System.out.print("\n");
    }
    
    public void mergeAlignment()
    {

        long[] idx = getWordAlignment(WordAlignmentType.UNION);
        int[] src = new int[idx.length];
        int[] dst = new int[idx.length];
        
        for (int i=0; i<idx.length; ++i)
        {
            src[i] = (int)(idx[i]>>>32)-1;
            dst[i] = (int)(idx[i]&0xffffffff)-1;
        }
        setAlignment(src, dst);
    }
    
    public void setAlignment(int[] srcAlignmentIdx, int[] dstAlignmentIdx)
    {
        TIntObjectMap<TIntSet> srcAlignmentMap = new TIntObjectHashMap<TIntSet>();
        TIntObjectMap<TIntSet> dstAlignmentMap = new TIntObjectHashMap<TIntSet>();
        for (int i=0; i<srcAlignmentIdx.length; ++i)
        {
            if (srcAlignmentIdx[i]<0 || dstAlignmentIdx[i]<0)
                continue;
            TIntSet srcSet = srcAlignmentMap.get(srcAlignmentIdx[i]);
            if (srcSet==null) srcAlignmentMap.put(srcAlignmentIdx[i], srcSet=new TIntHashSet());
            srcSet.add(dstAlignmentIdx[i]);
            
            TIntSet dstSet = dstAlignmentMap.get(dstAlignmentIdx[i]);
            if (dstSet==null) dstAlignmentMap.put(dstAlignmentIdx[i], dstSet=new TIntHashSet());
            dstSet.add(srcAlignmentIdx[i]);
        }
        srcAlignment = convertAlignment(src.indices, srcAlignmentMap);
        dstAlignment = convertAlignment(dst.indices, dstAlignmentMap);
        
    }

    SortedMap<Long, int[]> convertAlignment(long[] indices, TIntObjectMap<TIntSet> inAlignment)
    {
        SortedMap<Long, int[]> outAlignment = new TreeMap<Long, int[]>();
        for (long index:indices)
            outAlignment.put(index, SentencePair.EMPTY_INT_ARRAY);
        
        for (TIntObjectIterator<TIntSet> iter = inAlignment.iterator(); iter.hasNext();)
        {
            iter.advance();
            if (!iter.value().isEmpty())
            {
                int[] iArray = iter.value().toArray();
                Arrays.sort(iArray);
                outAlignment.put(indices[iter.key()], iArray);
            }
        }
        
        return outAlignment;
    }
    
    public SortedMap<Long, int[]> getAlignment(boolean isSrc)
    {
        return isSrc?srcAlignment:dstAlignment;
    }
    
    public Sentence getSentence(boolean isSrc)
    {
        return isSrc?src:dst;
    }
    
    public static long makeLong(int a, int b)
    {
        return (((long)a)<<32)|b;
    }

    /*
    public long[] getSrcWordAlignment()
    {
        TLongArrayList alignSet = getWordAlignment(srcAlignment, src.indices, true);
        long[] alignment = alignSet.toNativeArray();
        Arrays.sort(alignment);
        return alignment;
    }
    
    public long[] getDstWordAlignment()
    {
        TLongArrayList alignSet = getWordAlignment(dstAlignment, dst.indices, false);
        long[] alignment = alignSet.toNativeArray();
        Arrays.sort(alignment);
        return alignment;
    }
    */
    public long[] getWordAlignment(WordAlignmentType type)
    {
        long[] alignment = null;
        switch (type)
        {
        case SRC:
            alignment = getWordAlignment(srcAlignment, src.indices, true).toArray();
            break;
        case DST:
            alignment = getWordAlignment(dstAlignment, dst.indices, false).toArray();
            break;
        case UNION:
            TLongSet unionSet = new TLongHashSet();
            unionSet.addAll(getWordAlignment(srcAlignment, src.indices, true).toArray());
            unionSet.addAll(getWordAlignment(dstAlignment, dst.indices, false).toArray());
            alignment = unionSet.toArray();
            break;
        case INTERSECTION:
            TLongSet intersectionSet = new TLongHashSet();
            intersectionSet.addAll(getWordAlignment(srcAlignment, src.indices, true).toArray());
            intersectionSet.retainAll(getWordAlignment(dstAlignment, dst.indices, false).toArray());
            alignment = intersectionSet.toArray();
            break;
        }
        Arrays.sort(alignment);
        return alignment;
    }
    
    public Map<BitSet, BitSet> getWordAlignmentSet(WordAlignmentType type)
    {
        Map<BitSet, BitSet> alignSet1 = new HashMap<BitSet, BitSet>();
        
        for (long a:getWordAlignment(type))
        {
            int src = (int)(a>>>32);
            int dst = (int)(0xffffffff&a);
            BitSet dSet  = new BitSet();
            dSet.set(dst);
            
            BitSet sSet;
            
            if ((sSet=alignSet1.get(dSet))==null)
            {
                sSet = new BitSet();
                alignSet1.put(dSet, sSet);
            }
            
            sSet.set(src);
        }
        
        Map<BitSet, BitSet> alignSet2 = new HashMap<BitSet, BitSet>();
        
        for (Map.Entry<BitSet, BitSet> entry:alignSet1.entrySet())
        {
            BitSet dSet;
            
            if ((dSet = alignSet2.get(entry.getValue()))==null)
                alignSet2.put(entry.getValue(), entry.getKey());
            else
                dSet.or(entry.getKey());
        }
        
        return alignSet2;
    }
    
    /*
    public long[] getWordAlignment()
    {
        TLongHashSet srcAlignSet = new TLongHashSet(getWordAlignment(srcAlignment, src.indices, true).toNativeArray());
        srcAlignSet.addAll(getDstWordAlignment());
        
        long[] alignment = srcAlignSet.toArray();
        Arrays.sort(alignment);
        return alignment;
    }
    */
    static TLongArrayList getWordAlignment(SortedMap<Long, int[]> alignMap, long[] indices, boolean isSrc)
    {
        TLongArrayList alignSet = new TLongArrayList();
        for (int i=0; i<indices.length; ++i)
        {
            int[] targets = alignMap.get(indices[i]);
            if (targets == null) continue;
            for (int t:targets)
                alignSet.add(isSrc?makeLong(i+1,t+1):makeLong(t+1, i+1));
        }
        return alignSet;
    }
    
    public void printPredicates(PrintStream out)
    {
        for (int i=0; i<src.tokens.length; ++i)
        {
            if (src.tokens[i].getPOS().startsWith("V"))
                out.printf("%d %d %s\n", id, i, src.tokens[i].getWord());
        }
    }
    
    public String toAlignmentString() {
    	StringBuilder builder = new StringBuilder();
    	int cnt=0;
        for (SortedMap.Entry<Long, int[]> entry:srcAlignment.entrySet())
        {
            builder.append(src.tokens[cnt++].getWord()+" [");
            for (int id:entry.getValue())
                builder.append(dst.tokens[id].getWord()+' ');
            builder.append("] ");
        }
        builder.append('\n');
        
        cnt=0;
        for (SortedMap.Entry<Long, int[]> entry:dstAlignment.entrySet())
        {
            builder.append(dst.tokens[cnt++].getWord()+" [");
            for (int id:entry.getValue())
                builder.append(src.tokens[id].getWord()+' ');
            builder.append("] ");
        }
        return builder.toString();
    }
    
    @Override
	public String toString() {
        return src+"\n"+dst+"\n"+toAlignmentString();
    }

}
