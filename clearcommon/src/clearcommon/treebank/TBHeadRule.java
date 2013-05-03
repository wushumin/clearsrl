/**
* Copyright (c) 2010, Regents of the University of Colorado
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
 * Treebank headrule.
 * @author Jinho D. Choi
 * <b>Last update:</b> 9/1/2010
 */
public class TBHeadRule
{
    static public final String HEAD_DELIM  = ";";
    
    static public final TBHeadRule DEFAULT = new TBHeadRule("default r .*");
    
    enum Direction
    {
        LEFT,
        RIGHT
    };
    
    public String      ruleName;
	public Direction[] dirs;
	public String[]    rules;
	
	public TBHeadRule(String textRule)
	{
	    rules = textRule.trim().split(HEAD_DELIM);
	    dirs = new Direction[rules.length];
	  
	    String[] toks = rules[0].split("\\s+");
	    ruleName = toks[0];
	    dirs[0] = toks[1].equals("l")?Direction.LEFT:Direction.RIGHT;
	    rules[0] = toks[2];
	    
	    for (int i=1; i<rules.length; ++i) {
	        toks = rules[i].split("\\s+");
	        if (toks.length<2) {
	            dirs[i] = dirs[i-1];
	            rules[i] = toks[0];
	        } else {
	            dirs[i] = toks[0].equals("l")?Direction.LEFT:Direction.RIGHT;
	            rules[i] = toks[1];
	        }   
	    }

	}
}
