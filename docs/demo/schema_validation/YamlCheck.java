import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

// YAML 문법과 최상위 키만 확인한다. 인자: 파일
public class YamlCheck {

	public static void main(String[] args) throws Exception {
		LoaderOptions options = new LoaderOptions();
		options.setAllowDuplicateKeys(false);
		Map<?, ?> root = new Yaml(new SafeConstructor(options)).load(Files.readString(Path.of(args[0])));
		System.out.println(Path.of(args[0]).getFileName() + " 최상위 키: " + root.keySet());
	}

}
