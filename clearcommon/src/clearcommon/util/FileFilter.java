package clearcommon.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Pattern;

public class FileFilter implements FilenameFilter{

	String regex;
	
	public FileFilter(String filter)
	{
		regex = filter;
	}
	
	@Override
	public boolean accept(File arg0, String arg1) {
		return Pattern.matches(regex, arg1);
	}
}
