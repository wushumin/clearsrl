package clearcommon.util;

import java.util.Arrays;
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
            else if (inherit && out.getProperty(propName) == null)
            	out.setProperty(propName, in.getProperty(propName));
        }
        
        return out;
    }
    
    public static String toString(Properties props)
    {
    	StringBuilder builder = new StringBuilder();
    	
        String[] keys = props.keySet().toArray(new String[0]);
        Arrays.sort(keys);
        for(String key:keys)
            builder.append(key+" = "+props.getProperty(key)+"\n");
        return builder.toString();
    }
}
