package edu.colorado.clear.srl.align;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FixOrder {

    public static TIntIntMap getOrder(String[] oldStr, String[] newStr)
    {
        TIntIntMap map = new TIntIntHashMap();
        
        Pattern pattern = Pattern.compile(".*fid.*");
        
        ArrayList<String> oldSrc = new ArrayList<String>();
        ArrayList<String> oldDst = new ArrayList<String>();
        
        for (String str:oldStr)
        {
            Matcher matcher = pattern.matcher(str);
            if (matcher.matches())
                oldSrc.add(str);
            else
                oldDst.add(str);
        }
        
        ArrayList<String> newSrc = new ArrayList<String>();
        ArrayList<String> newDst = new ArrayList<String>();
        
        for (String str:newStr)
        {
            Matcher matcher = pattern.matcher(str);
            if (matcher.matches())
                newSrc.add(str);
            else
                newDst.add(str);
        }
        
        for (int i=0; i<oldSrc.size(); ++i)
            for (int j=0; j<newSrc.size(); ++j)
                if (oldSrc.get(i).equals(newSrc.get(j)))
                    map.put(i+1,j+1);
        
        for (int i=0; i<oldDst.size(); ++i)
            for (int j=0; j<newDst.size(); ++j)
                if (oldDst.get(i).equals(newDst.get(j)))
                    map.put(-i-1,-j-1);

        return map;
        //System.out.println(oldDst);
    }
    
    public static void main(String[] args) throws IOException
    {
        BufferedReader oldReader = new BufferedReader(new FileReader(args[0]));
        BufferedReader newReader = new BufferedReader(new FileReader(args[1]));
        
        String line;
        String sentenceId = null;
        int    srcSRLId;
        int    dstSRLId;
        double score;
        
        ArrayList<String> list = new ArrayList<String>();
        
        Map<String, String[]> oldMap = new HashMap<String, String[]>();
        Pattern pattern = Pattern.compile("<h3>(\\d+)</h3>");
        while ((line = oldReader.readLine())!= null)
        {
            Matcher matcher = pattern.matcher(line.trim());
            if (matcher.matches())
            {
                if (sentenceId!=null)
                {
                    
                    oldMap.put(sentenceId, list.toArray(new String[list.size()]));
                }
                sentenceId = matcher.group(1);
                list.clear();
            }
            else
                list.add(line.trim());
        }
        
        oldMap.put(sentenceId, list.toArray(new String[list.size()]));

        
        Map<String, TIntIntMap> orderMap = new HashMap<String, TIntIntMap>();
        
        list.clear();
        
        while ((line = newReader.readLine())!= null)
        {
            Matcher matcher = pattern.matcher(line.trim());
            if (matcher.matches())
            {
                if (sentenceId!=null)
                {
                    orderMap.put(sentenceId,getOrder(oldMap.get(sentenceId), list.toArray(new String[list.size()])));
                }
                sentenceId = matcher.group(1);
                list.clear();
            }
            else
                list.add(line.trim());
        }
        orderMap.put(sentenceId,getOrder(oldMap.get(sentenceId), list.toArray(new String[list.size()])));
        
        
        Scanner scanner = new Scanner(new BufferedReader(new FileReader(args[2])));

        while (scanner.hasNextLine())
        {
            StringTokenizer tokenizer = new StringTokenizer(scanner.nextLine(),",");
            if (tokenizer.countTokens() != 4)
                continue;
            try {
                sentenceId = tokenizer.nextToken().trim();
                srcSRLId = Short.parseShort(tokenizer.nextToken().trim());
                dstSRLId = Short.parseShort(tokenizer.nextToken().trim());
                score = Double.parseDouble(tokenizer.nextToken().trim());
                
                TIntIntMap map = orderMap.get(sentenceId);
                
                System.out.println(sentenceId+","+map.get(srcSRLId)+","+(-map.get(-dstSRLId))+","+score);
            }
            catch (NumberFormatException e)
            {
                System.err.println(e);
            }
        }
    }
}
