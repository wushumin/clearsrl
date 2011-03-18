package clearsrl.align;

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

public 	class SentencePair implements Serializable {

   /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
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
	
	private String getAlignmentString(SortedMap<Long, int[]> alignment, String[] src, String[] dst)
	{
		StringBuilder ret = new StringBuilder();

		int i=0;
		for (SortedMap.Entry<Long, int[]> entry:alignment.entrySet())
		{
			ret.append(src[i++]); 
			ret.append(' ');
			for (int dstKey:entry.getValue())
			{
				ret.append(dst[dstKey]);
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
	
	private void parseAlign(String line, SortedMap<Long, int[]> alignment, int dstLen)  throws BadInstanceException
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
	
	public SortedMap<Long, int[]> getAlignment(boolean isSrc)
	{
	    return isSrc?srcAlignment:dstAlignment;
	}
	
	public Sentence getSentence(boolean isSrc)
	{
		return isSrc?src:dst;
	}
	
	public String toString()
	{
		StringBuilder builder = new StringBuilder(src+"\n"+dst+"\n");
		int cnt=0;
		for (Map.Entry<Long, int[]> entry:srcAlignment.entrySet())
		{
			builder.append(src.tokens[cnt++]+" [");
			for (int id:entry.getValue())
				builder.append(dst.tokens[id]+' ');
			builder.append("] ");
		}
		builder.append('\n');
		
		cnt=0;
		for (Map.Entry<Long, int[]> entry:dstAlignment.entrySet())
		{
			builder.append(dst.tokens[cnt++]+" [");
			for (int id:entry.getValue())
				builder.append(src.tokens[id]+' ');
			builder.append("] ");
		}
		builder.append('\n');
		
		return builder.toString();
	}

}
