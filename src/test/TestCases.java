import java.util.Map;

public class TestCases {

	public static void main(String[] args) {
		Map<String, Object> result = Json.parseJson("""
				{
				"name": "Caleb",
				"age": 23,
				"profession": ["musician", "programmer"],
				"fiance": {
					"name": "Noell",
					"age": 25,
					"profession": ["stylist", "musician"]
					}
				}
				""");

		System.out.println(result);
	}

}
