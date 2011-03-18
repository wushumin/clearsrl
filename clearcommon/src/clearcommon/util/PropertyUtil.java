package clearcommon.util;

import java.util.Properties;

public class PropertyUtil {
    public static Properties filterProperties(Properties in, String filter)
    {
        Properties out = new Properties();
        
        for (String propName:in.stringPropertyNames())
        {
            if (propName.startsWith(filter))
                out.setProperty(propName.substring(filter.length()), in.getProperty(propName));
        }
        return out;
    }
}
