package clearcommon.util;

import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    
    // match ${ENV_VAR_NAME}
    static final Pattern p = Pattern.compile("\\$\\{(\\w+)\\}");
    public static Properties resolveEnvironmentVariables(Properties in)
    {
        Properties out = new Properties();
        
        for (String propName:in.stringPropertyNames())
        {
        	String value = in.getProperty(propName);
        	
        	if (value==null)
	    	{
        		out.setProperty(propName, null);
        		break;
	    	}
        	Matcher m = p.matcher(value); // get a matcher object
        	StringBuffer sb = new StringBuffer();
        	while(m.find()){
        		String envVarName = null == m.group(1) ? m.group(2) : m.group(1);
        		String envVarValue = System.getenv(envVarName);
        		m.appendReplacement(sb, null == envVarValue ? "" : envVarValue);
        	}
        	m.appendTail(sb);

        	out.setProperty(propName, sb.toString());
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
