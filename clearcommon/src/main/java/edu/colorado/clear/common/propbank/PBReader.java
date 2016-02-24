package edu.colorado.clear.common.propbank;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import edu.colorado.clear.common.treebank.TBReader;
import edu.colorado.clear.common.util.FileUtil;

public class PBReader {

    Queue<String>    fileList;
    TBReader         tbReader;
    PBFileReader     pbReader;
    PBTokenizer      tokenizer;
    
    public PBReader(TBReader tbReader, String dir, String regex, PBTokenizer tokenizer)
    {
        this.tbReader = tbReader;
        fileList = new ArrayDeque<String>(FileUtil.getFiles(new File(dir), regex, true));
        this.tokenizer = tokenizer;
        pbReader = null;
    }

    public void close()
    {
        if (pbReader!=null)
            pbReader.close();
        pbReader = null;
        fileList.clear();
        tbReader.close();
    }
    
    public List<PBInstance> nextPropSet()
    { 
        while (pbReader==null || !pbReader.isOpen())
        {
            if (fileList.isEmpty())
                return null;
            try {
                pbReader = new PBFileReader(tbReader, fileList.remove(), tokenizer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        List<PBInstance> instances = pbReader.nextPropSet();

        if (instances==null) return nextPropSet();
        
        return instances;
    }

}
