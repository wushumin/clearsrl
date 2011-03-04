package clearcommon.propbank;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import clearcommon.treebank.ParseException;
import clearcommon.treebank.TBReader;
import clearcommon.treebank.TreeFileResolver;
import clearcommon.util.FileUtil;

public class PBReader {

    Queue<String> fileList;
    TBReader      tbReader;
    PBFileReader  pbReader;
    PBInstance    lastInstance;
    TreeFileResolver resolver;
    
    public PBReader(TBReader tbReader, String dir, String regex, TreeFileResolver resolver)
    {
        this.tbReader = tbReader;
        fileList = new ArrayDeque<String>(FileUtil.getFiles(new File(dir), regex, true));
        this.resolver = resolver;
        pbReader = null;
    }

    public void close()
    {
        if (pbReader!=null)
            pbReader.close();
        pbReader = null;
        lastInstance = null;
        fileList.clear();
    }
    
    public List<PBInstance> nextPropSet()
    {
        LinkedList<PBInstance> retList = new LinkedList<PBInstance>();
        if (lastInstance!=null)
        {
            retList.add(lastInstance);
            lastInstance = null;
            if (pbReader==null) return retList;
        }
        
        while (pbReader==null)
        {
            if (fileList.isEmpty())
                return null;
            try {
                pbReader = new PBFileReader(tbReader, fileList.remove(), resolver);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        for (;;)
        {
            try {
                lastInstance = pbReader.nextProp();
                
                if (lastInstance==null) 
                {
                    pbReader = null;
                    break;
                }
                if (retList.isEmpty())
                {
                    retList.add(lastInstance);
                    lastInstance = null;
                }
                else
                {
                    if (!retList.getLast().tree.getFilename().equals(lastInstance.tree.getFilename())||
                        retList.getLast().tree.getIndex()!=lastInstance.tree.getIndex())
                        return retList;
                    else
                    {
                        retList.add(lastInstance);
                        lastInstance = null;
                    }
                }
            } catch (PBFormatException e) {
                e.printStackTrace();
                continue;
            } catch (ParseException e) {
                e.printStackTrace();
                pbReader.close();
                break;
            } catch (Exception e) {
                System.err.print(pbReader.annotationFile+": ");
                e.printStackTrace();
                break;
            }
        }
        
        if (retList.isEmpty())
            return nextPropSet();
        
        return retList;
    }

}
