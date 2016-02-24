package edu.colorado.clear.common.util;

import java.io.InputStreamReader;

import edu.colorado.clear.common.treebank.ParseException;
import edu.colorado.clear.common.treebank.SerialTBFileReader;
import edu.colorado.clear.common.treebank.TBFileReader;
import edu.colorado.clear.common.treebank.TBTree;

public class PrintTBTrees {
    public static void main(String[] argv) {
        TBFileReader treeReader = new SerialTBFileReader(new InputStreamReader(System.in));
        TBTree tree;
        for (;;)
        {
            try {
                tree=treeReader.nextTree();
                if (tree==null) break;
                System.out.println(tree);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }
}
