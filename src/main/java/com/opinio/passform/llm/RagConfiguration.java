package com.opinio.passform.llm;

import java.util.Map;

import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import com.opinio.passform.domain.analysis.RagSettings;

// config/rag.yaml을 읽는다. 값이 없으면 앱이 뜰 때 실패한다.
@Configuration
public class RagConfiguration {

	@Bean
	public RagSettings ragSettings() {
		return load();
	}

	public static RagSettings load() {
		YamlMapFactoryBean yaml = new YamlMapFactoryBean();
		yaml.setResources(new ClassPathResource("config/rag.yaml"));
		Map<String, Object> root = yaml.getObject();
		Map<String, Object> retrieval = section(root, "retrieval");
		Map<String, Object> theme = section(retrieval, "theme");
		Map<String, Object> topic = section(retrieval, "topic");
		Map<String, Object> summary = section(root, "summary");
		return new RagSettings(number(theme, "top_k").intValue(), number(theme, "min_score").doubleValue(),
				number(topic, "top_k").intValue(), number(topic, "min_score").doubleValue(),
				number(summary, "classify_batch_size").intValue(), number(summary, "max_responses_per_request").intValue(),
				number(summary, "max_themes").intValue(), number(section(root, "chunking"), "max_chars").intValue());
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> section(Map<String, Object> parent, String key) {
		if (parent != null && parent.get(key) instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		throw new IllegalStateException("config/rag.yaml에 없다: " + key);
	}

	private static Number number(Map<String, Object> parent, String key) {
		if (parent.get(key) instanceof Number value) {
			return value;
		}
		throw new IllegalStateException("config/rag.yaml에 없다: " + key);
	}

}
