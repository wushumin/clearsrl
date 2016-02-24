package edu.colorado.clear.srl.align;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

public class CountResult {
    public static void main(String[] args) throws IOException
    {   
        BufferedReader reader = new BufferedReader(new FileReader(args[0]));
        
        int c1=0, c2=0, t1=0, t2=0;
        int skipped=0;
        
        String line;
        while ((line = reader.readLine())!=null)
        {   
            String [] tokens = line.trim().split("\\s+");
            
            if (tokens.length<3) continue;
            
            if (!tokens[2].matches("v|x"))
            {
                skipped++;
                System.err.println(Arrays.asList(tokens));
                continue;
            }
            
            
            if (tokens[2].equals("v"))
            {
                c1++;
                c2+= Integer.parseInt(tokens[1]);
            }
            
            t1++;
            t2+= Integer.parseInt(tokens[1]);
            
        }
        System.out.println(skipped);
        System.out.printf("%d/%d %d/%d\n", c1, t1, c2, t2);
    }
    
}
