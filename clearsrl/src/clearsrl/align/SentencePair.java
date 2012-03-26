package clearsrl.align;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import gnu.trove.TLongArrayList;
import gnu.trove.TLongHashSet;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import clearcommon.treebank.TBNode;

public 	class SentencePair implements Serializable {

   /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
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
	
	public String getSrcAlignmentString()
	{
		return getAlignmentString(srcAlignment, src.tokens, dst.tokens);
	}
	
	public String getDstAlignmentString()
	{
		return getAlignmentString(dstAlignment, dst.tokens, src.tokens);
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
		//	System.out.print(" "+i);
		//System.out.print("\n");
	}
	
	public void setAlignment(int[] srcAlignmentIdx, int[] dstAlignmentIdx)
	{
		TIntObjectHashMap<TIntHashSet> srcAlignmentMap = new TIntObjectHashMap<TIntHashSet>();
        TIntObjectHashMap<TIntHashSet> dstAlignmentMap = new TIntObjectHashMap<TIntHashSet>();
        for (int i=0; i<srcAlignmentIdx.length; ++i)
        {
        	if (srcAlignmentIdx[i]<0 || dstAlignmentIdx[i]<0)
        		continue;
        	TIntHashSet srcSet = srcAlignmentMap.get(srcAlignmentIdx[i]);
        	if (srcSet==null) srcAlignmentMap.put(srcAlignmentIdx[i], srcSet=new TIntHashSet());
        	srcSet.add(dstAlignmentIdx[i]);
        	
        	TIntHashSet dstSet = dstAlignmentMap.get(dstAlignmentIdx[i]);
        	if (dstSet==null) dstAlignmentMap.put(dstAlignmentIdx[i], dstSet=new TIntHashSet());
        	dstSet.add(srcAlignmentIdx[i]);
        }
        srcAlignment = convertAlignment(src.indices, srcAlignmentMap);
        dstAlignment = convertAlignment(dst.indices, dstAlignmentMap);
        
	}

	SortedMap<Long, int[]> convertAlignment(long[] indices, TIntObjectHashMap<TIntHashSet> inAlignment)
	{
		SortedMap<Long, int[]> outAlignment = new TreeMap<Long, int[]>();
		for (long index:indices)
			outAlignment.put(index, SentencePair.EMPTY_INT_ARRAY);
		
		for (TIntObjectIterator<TIntHashSet> iter = inAlignment.iterator(); iter.hasNext();)
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
	
	static long makeLong(int a, int b)
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
			alignment = getWordAlignment(srcAlignment, src.indices, true).toNativeArray();
			break;
		case DST:
			alignment = getWordAlignment(dstAlignment, dst.indices, false).toNativeArray();
			break;
		case UNION:
			TLongHashSet unionSet = new TLongHashSet();
			unionSet.addAll(getWordAlignment(srcAlignment, src.indices, true).toNativeArray());
			unionSet.addAll(getWordAlignment(dstAlignment, dst.indices, false).toNativeArray());
			alignment = unionSet.toArray();
			break;
		case INTERSECTION:
			TLongHashSet intersectionSet = new TLongHashSet();
			intersectionSet.addAll(getWordAlignment(srcAlignment, src.indices, true).toNativeArray());
			intersectionSet.retainAll(getWordAlignment(dstAlignment, dst.indices, false).toNativeArray());
			alignment = intersectionSet.toArray();
			break;
		}
		Arrays.sort(alignment);
		return alignment;
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
	
	public String toString()
	{
		StringBuilder builder = new StringBuilder(src+"\n"+dst+"\n");
		int cnt=0;
		for (Map.Entry<Long, int[]> entry:srcAlignment.entrySet())
		{
			builder.append(src.tokens[cnt++].getWord()+" [");
			for (int id:entry.getValue())
				builder.append(dst.tokens[id].getWord()+' ');
			builder.append("] ");
		}
		builder.append('\n');
		
		cnt=0;
		for (Map.Entry<Long, int[]> entry:dstAlignment.entrySet())
		{
			builder.append(dst.tokens[cnt++].getWord()+" [");
			for (int id:entry.getValue())
				builder.append(src.tokens[id].getWord()+' ');
			builder.append("] ");
		}
		builder.append('\n');
		
		return builder.toString();
	}

}
