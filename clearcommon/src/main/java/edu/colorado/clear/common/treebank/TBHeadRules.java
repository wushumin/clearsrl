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
package edu.colorado.clear.common.treebank;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Scanner;

/**
 * Treebank headrules.
 * @author Jinho D. Choi
 * <b>Last update:</b> 9/1/2010
 */
public class TBHeadRules {
    static public final String FIELD_DELIM = "\t";
    static public final String HEAD_DELIM  = ";";
    
    private HashMap<String, TBHeadRule> m_headrules;
    
    public TBHeadRules(String inputFile) throws FileNotFoundException {
        Scanner scan = new Scanner(new BufferedReader(new FileReader(inputFile)));
        m_headrules  = new HashMap<String, TBHeadRule>();
        
        while (scan.hasNextLine()) {
            String line = scan.nextLine();
            if (line.charAt(0) == '#')  continue;
            TBHeadRule rule = new TBHeadRule(line);
            m_headrules.put(rule.ruleName, rule);
        }
        scan.close();
    }
    
    public TBHeadRule getHeadRule(String pos) {
        return m_headrules.get(pos);
    }
}
