import java.util.List;
import java.util.Map;

/**
 * Minimal JSON writer - no external library needed, keeps the project
 * to a single jar dependency (the MySQL driver) for easy classpath setup.
 */
public class Json {

    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    public static String obj(Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":").append(value(e.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    public static String value(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return "\"" + escape(v.toString()) + "\"";
    }

    public static String array(List<String> jsonObjects) {
        return "[" + String.join(",", jsonObjects) + "]";
    }

    public static String message(String key, String value) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"").append(escape(key)).append("\":\"").append(escape(value)).append("\"");
        sb.append("}");
        return sb.toString();
    }
}
