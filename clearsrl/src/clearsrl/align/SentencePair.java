package clearsrl.align;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;

import java.util.Arrays;
import java.util.BitSet;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public 	class SentencePair {

	static final Pattern alignPattern = Pattern.compile("\\(\\{([0-9 ]+)\\}\\)");
	
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
		srcAlignment = new TIntObjectHashMap<TIntHashSet>();
		dstAlignment = new TIntObjectHashMap<TIntHashSet>();
	}
		
	public void parseSrcAlign(String line) throws BadInstanceException
	{
		for (int i=0; i<src.indices.length; ++i)
			srcAlignment.put(src.indices[i], new TIntHashSet());
		parseAlign(line, srcAlignment, dst.indices.length);
	}
	
	public void parseDstAlign(String line) throws BadInstanceException
	{
		for (int i=0; i<dst.indices.length; ++i)
			dstAlignment.put(dst.indices[i], new TIntHashSet());
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
	
	private String getAlignmentString(TIntObjectHashMap<TIntHashSet> alignment, String[] src, String[] dst)
	{
		StringBuilder ret = new StringBuilder();
		int[] keys = alignment.keys();
		Arrays.sort(keys);
		
		for (int i=0; i<keys.length; ++i)
		{
			ret.append(src[i]); ret.append(' ');
			int[] dstKeys = alignment.get(keys[i]).toArray();
			Arrays.sort(dstKeys);
			for (int dstKey:dstKeys)
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
	
	private String getAlignmentIndex(TIntObjectHashMap<TIntHashSet> alignment)
	{
		StringBuilder ret = new StringBuilder();
		int[] keys = alignment.keys();
		Arrays.sort(keys);
		
		for (int i=0; i<keys.length; ++i)
		{
			int[] dstKeys = alignment.get(keys[i]).toArray();
			Arrays.sort(dstKeys);
			for (int dstKey:dstKeys)
			{
				ret.append(dstKey);
				ret.append(' ');
			}
			ret.append('|');
		}
		return ret.toString();	
	}
	
	private void parseAlign(String line, TIntObjectHashMap<TIntHashSet> alignment, int dstLen)  throws BadInstanceException
	{
		int[] keys = alignment.keys();
		Arrays.sort(keys);
	
		Matcher matcher = alignPattern.matcher(line);
		int srcCnt = -1;
		int dstCnt = 0;
		int val;
		BitSet dstBitSet = new BitSet(dstLen);
		TIntHashSet alignSet;
		while (matcher.find())
		{
			//System.out.print(matcher.group(1)+'|');
			StringTokenizer tok = new StringTokenizer(matcher.group(1));
			alignSet = srcCnt>=0?alignment.get(keys[srcCnt]):null;
			while (tok.hasMoreTokens())
			{
				val = Integer.parseInt(tok.nextToken())-1;
				if (alignSet!=null)
					alignSet.add(val);
				dstBitSet.set(val);
				
				++dstCnt;
			}
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

	public int                    id;
	public Sentence               src;
	public Sentence               dst;
	public TIntObjectHashMap<TIntHashSet> srcAlignment;
	public TIntObjectHashMap<TIntHashSet> dstAlignment;
}
