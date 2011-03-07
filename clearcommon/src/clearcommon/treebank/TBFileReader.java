/**
* Copyright (c) 2007, Regents of the University of Colorado
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
* Neither the name of the University of Colorado at Boulder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
* LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
* CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
* ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
*/
package clearcommon.treebank;

/**
 * TBFileReader reads a Treebank file, and returns each tree as TBTree format.

 * For example, here is the output generated by the following code.
 * <pre>
 * "wsj_0001.mrg":
 * ( (S 
 *   (NP-SBJ 
 *    (NP (NNP Pierre) (NNP Vinken) )
 *    (, ,) 
 *    (ADJP 
 *      (NP (CD 61) (NNS years) )
 *      (JJ old) )
 *    (, ,) )
 *  (VP (MD will) 
 *    (VP (VB join) 
 *      (NP (DT the) (NN board) )
 *      (PP-CLR (IN as) 
 *        (NP (DT a) (JJ nonexecutive) (NN director) ))
 *      (NP-TMP (NNP Nov.) (CD 29) )))
 *  (. .) ))
 * ( (S 
 *   (NP-SBJ (NNP Mr.) (NNP Vinken) )
 *   (VP (VBZ is) 
 *     (NP-PRD 
 *       (NP (NN chairman) )
 *       (PP (IN of) 
 *         (NP 
 *           (NP (NNP Elsevier) (NNP N.V.) )
 *           (, ,) 
 *           (NP (DT the) (NNP Dutch) (VBG publishing) (NN group) )))))
 *   (. .) ))
 *  
 * Code:
 * TBReader tbank = new TBReader("wsj_0001.mrg");
 * TBTree tree;
 *		
 * while ((tree = tbank.nextTree()) != null)
 *	System.out.println(tree.getWords());
 * 
 * Output:
 * Pierre Vinken , 61 years old , will join the board as a nonexecutive director Nov. 29 .
 * Mr. Vinken is chairman of Elsevier N.V. , the Dutch publishing group .
 * </pre>
 * @see TBTree
 * @author Jinho D. Choi
 * <b>Last update:</b> 02/05/2010
 */
public abstract class TBFileReader
{
	boolean       closed;
	String        fileName;
	
	/**
	 * Initializes the Treebank reader.
	 * @param filename name of the Treebank file
	 * @throws FileNotFoundException 
	 */
	TBFileReader(String fileName)
	{
		this.fileName = fileName;
	    closed = false;
	}
	
	/**
	 * Returns the next tree in the Treebank.
	 * If there is none, returns null.
	 * @throws ParseException 
	 */
	public abstract TBTree nextTree() throws ParseException;
	
	/**
	 * Returns the indexed tree in the Treebank.
	 * If there is none, returns null. Must be called in order.
	 * @throws ParseException 
	 */
    public abstract TBTree getTree(int index) throws ParseException;
	
	public abstract void close();
	
	public boolean isOpen()
	{
		return !closed;
	}	
}
