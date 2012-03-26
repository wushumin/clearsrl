package clearsrl.align;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Properties;

import clearcommon.util.PropertyUtil;

public class TestLDC09SentencePairReader {
	public static void main(String[] args) throws IOException{
		 Properties props = new Properties();
        {
            FileInputStream in = new FileInputStream(args[0]);
            InputStreamReader iReader = new InputStreamReader(in, Charset.forName("UTF-8"));
            props.load(iReader);
            iReader.close();
            in.close();
        }
		LDC09SentencePairReader reader = new LDC09SentencePairReader(PropertyUtil.filterProperties(props,"align."), true);
		
		reader.initialize();
		SentencePair pair;
		while ((pair=reader.nextPair())!=null)
		{
			System.out.println(pair);
		}
		
		for (String filename:reader.fileList)
			System.out.println(filename);
		
	}
}
