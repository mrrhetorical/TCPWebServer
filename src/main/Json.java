import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Class for handling json parsing. Probably this is not the most efficient way to handle this, but it makes sense intuitively to me.
 * I tried to account for as much as I can reasonably think of including numeric representations incl. scientific, booleans, and strings.
 * I know this is overkill but I actually really enjoyed working on this.
 * Tested mostly w/ regex101.com. Might not handle overly complex models.
 * The one scenario I can't figure out a solution for is parsing an object nested within an array. Arrays within objects works fine.
 *
 * Basic Process:
 * 1. Parse through each key + value pair
 * 	a. If value is simple, just add it to the map
 *   b. If value is an array, parse the array, then add it to the map
 *   c. If value is an object, parse the object recursively by going back to step 1 by parsing the value as the new input string.
 * 		Once that object is parsed, add it to the map with a key/value pair
 *
 * */
class Json {
	private static final Pattern jsonObjectPattern = Pattern.compile("^\\s*\\{[\\s\\S]*\\}\\s*$");
	private static final Pattern jsonPropertyPattern = Pattern.compile("\"(?<key>.*?)\"\\s*:\\s*(?<value>\".*?\"|\\d+|true|false|null|\\[.*?\\]|\\{[\\s\\S]*\\})");
	private static final Pattern jsonArrayPattern = Pattern.compile("^\\[(?:[^\\[\\]]|[^,]+)*(?:,\\s*(?:[^\\[\\]]|[^,]+)*)*\\]$");
	private static final Pattern jsonArrayElementPattern = Pattern.compile("(?<=\\[|\\G)(?:(?<element>\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|true|false|null))(?:,\\s*)?");
	private static final Pattern jsonValuePattern = Pattern.compile("^((\"(?<str>.+)\")|([^\"]+))$");

	public static Map<String, Object> parseJson(String string) {
		Matcher matcher = jsonPropertyPattern.matcher(string);


		if (!jsonObjectPattern.matcher(string).matches()) {
			throw new IllegalArgumentException("Invalid JSON syntax");
		}

		Map<String, Object> result = new HashMap<>();
		return parseJson(result, matcher);
	}

	// Handles parsing of key-value pairs. Handles nested objects and arrays.
	private static Map<String, Object> parseJson(Map<String, Object> result, Matcher matcher) {

		while (matcher.find()) {
			String key = matcher.group("key");
			String value = matcher.group("value");
			if (jsonObjectPattern.matcher(value).matches()) {
				result.put(key, parseJson(new HashMap<>(), jsonPropertyPattern.matcher(value)));
			} else if (jsonArrayPattern.matcher(value).matches()) {
				result.put(key, parseArray(value));
			} else {
				result.put(key, getValue(value));
			}
		}

		return result;
	}

	// Extracts the raw value if it is a string, otherwise leaves it untouched.
	private static String getValue(String value) {
		Matcher rawValueMatcher = jsonValuePattern.matcher(value);
		if (!rawValueMatcher.matches()) {
			throw new IllegalArgumentException("Invalid JSON syntax. Invalid value format!");
		}

		return rawValueMatcher.group("str") != null ? rawValueMatcher.group("str") : value;
	}

	// Handles parsing of arrays to get each element and handle parsing individually for complex objects / arrays
	private static List<Object> parseArray(String string) {

		Matcher matcher = jsonArrayElementPattern.matcher(string);

		List<Object> result = new ArrayList<>();

		while (matcher.find()) {
			String element = matcher.group("element");
			if (element != null) {
				if (jsonObjectPattern.matcher(element).matches()) {
					Map<String, Object> objectMap = parseJson(new HashMap<>(), jsonPropertyPattern.matcher(element));
					result.add(objectMap);
				} else if (jsonArrayPattern.matcher(element).matches()) {
					List<Object> arrayList = parseArray(element);
					result.add(arrayList);
				} else {
					result.add(getValue(element));
				}
			}
		}

		return result;
	}
}
