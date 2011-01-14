package clearsrl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.StringTokenizer;

public class PrepareCoNLL {
	public static void main(String[] args) throws Exception
	{
		File topDir = new File(args[0]);
		if (!topDir.isDirectory()) System.exit(1);
		
		File[] dirs = topDir.listFiles();
		Arrays.sort(dirs);
		
		for (File dir:dirs)
		{
			if (dir.getName().startsWith(".") || !dir.isDirectory())
				continue;
			BufferedWriter writer = new BufferedWriter(new FileWriter(args[1]+File.separator+"train."+dir.getName()+".words"));
			
			File[] files = dir.listFiles();
			Arrays.sort(files);
			for (File file:files)
			{
				if (file.isDirectory()) continue;
				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line;
				while ((line = reader.readLine())!=null)
				{
					StringTokenizer tokenizer = new StringTokenizer(line, " ");
					while (tokenizer.hasMoreTokens())
					{
						String token = tokenizer.nextToken();
						writer.write(token);
						for (int i=token.length(); i<30; ++i)
							writer.write(' ');
						writer.write("\n");
					}
					writer.write("\n");
				}
				reader.close();
			}
			writer.close();
		}
	}
}
