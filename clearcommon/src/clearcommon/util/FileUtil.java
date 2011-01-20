package clearcommon.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class FileUtil
{
	static public List<String> getFiles(File dir, String regex)
	{
		List<String> fileNames = new ArrayList<String>();
		if (!dir.isDirectory())
			return fileNames;
		
		File[] files = dir.listFiles();
		Arrays.sort(files);
		
		for (File file:files)
		{
			if (file.isDirectory())
			{
				if (file.getName().startsWith("."))
					continue;
				List<String> moreFileNames = getFiles(file, regex);
				for (String fileName:moreFileNames)
					fileNames.add(file.getName()+File.separatorChar+fileName);
			}
			else if (Pattern.matches(regex, file.getName()))
				fileNames.add(file.getName());
		}
		return fileNames;
	}
}
