package clearcommon.util;

import java.util.Properties;

public class PropertyUtil {
	
	public static Properties filterProperties(Properties in, String filter)
	{
		return filterProperties(in, filter, false);
	}
	
    public static Properties filterProperties(Properties in, String filter, boolean inherit)
    {
        Properties out = new Properties();
        
        for (String propName:in.stringPropertyNames())
        {
            if (propName.startsWith(filter))
                out.setProperty(propName.substring(filter.length()), in.getProperty(propName));
            else if (inherit)
            	out.setProperty(propName, in.getProperty(propName));
        }
        
        return out;
    }
}
