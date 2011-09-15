package clearcommon.util;

import java.io.InputStreamReader;

import clearcommon.treebank.ParseException;
import clearcommon.treebank.SerialTBFileReader;
import clearcommon.treebank.TBFileReader;
import clearcommon.treebank.TBTree;

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
