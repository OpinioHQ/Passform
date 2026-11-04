import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 서버가 쓰는 것과 같은 라이브러리(networknt 3.0.8)로 스키마를 검증한다. 인자: 스키마 케이스파일
public class Validate {

	public static void main(String[] args) throws Exception {
		JsonMapper mapper = JsonMapper.builder().build();
		Schema schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(Files.readString(Path.of(args[0])));
		int passed = 0;
		int wrong = 0;
		for (JsonNode entry : mapper.readTree(Files.readString(Path.of(args[1])))) {
			List<Error> errors = schema.validate(entry.get("instance"));
			boolean expectPass = "pass".equals(entry.get("expect").asString());
			if (errors.isEmpty() == expectPass) {
				passed++;
				continue;
			}
			wrong++;
			System.out.println("불일치: " + entry.get("name").asString() + " (기대 " + entry.get("expect").asString() + ", 오류 " + errors.size() + "건)");
			errors.stream().limit(4).forEach(error -> System.out.println("    " + error));
		}
		System.out.println(Path.of(args[0]).getFileName() + ": 기대대로 " + passed + "건, 불일치 " + wrong + "건");
	}

}
