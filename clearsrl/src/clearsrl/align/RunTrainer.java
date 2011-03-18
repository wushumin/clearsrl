package clearsrl.align;

import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntObjectHashMap;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Scanner;
import java.util.regex.Pattern;

import clearcommon.util.PropertyUtil;

public class RunTrainer {
	public static void main(String[] args) throws IOException
	{
	    Properties props = new Properties();
        {
            FileInputStream in = new FileInputStream(args[0]);
            InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
            props.load(iReader);
            iReader.close();
            in.close();
        }
		
        SentencePairReader sentencePairReader = new LDCSentencePairReader(PropertyUtil.filterProperties(props, "ldc."));
        sentencePairReader.initialize();
        
		//Aligner aligner = new Aligner(sentencePairReader);
		int goodCnt = 0;
		int badCnt = 0;
        
		while (true)
        {
            SentencePair sentencePair = sentencePairReader.nextPair();
            if (sentencePair==null) break;
            if (sentencePair.id >=0)
                ++goodCnt;
            else
                ++badCnt;
        }
		
		System.out.println(goodCnt+" "+badCnt);
	}
}
