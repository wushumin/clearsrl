package edu.colorado.clear.srl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ConvertWSJOld {
    public static void main(String[] args) throws Exception
    {
        BufferedReader reader = new BufferedReader(new FileReader(args[0]));
        String line;
        String fnameold = "!!!";
        File dir = new File(args[1]);
        BufferedWriter writer=null;
        while ((line=reader.readLine())!=null)
        {
            if (line.isEmpty()) continue;
            String fname = line.substring(0, line.indexOf(' '));
            if (!fname.equals(fnameold))
            {
                if (writer!=null)
                    writer.close();
                File file = new File(dir, fname);
                file.getParentFile().mkdirs();
                writer = new BufferedWriter(new FileWriter(file));
                fnameold = fname;
            }
            writer.write(line+"\n");
        }
        if (writer!=null) writer.close();
    }
}
