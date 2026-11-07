package com.opinio.passform.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import com.opinio.passform.support.DatabaseTest;

// 이 클래스가 실행된다는 것 자체가 빈 DB에서 Flyway 후 ddl-auto=validate로 앱이 떴다는 뜻이다.
class SchemaStartupTest extends DatabaseTest {

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Environment environment;

	@Test
	void 앱이_validate로_떠서_health가_200이다() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).build(),
					HttpResponse.BodyHandlers.ofString());

		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("{\"status\":\"ok\"}");
	}

	@Test
	void Flyway가_아키텍처_10절의_테이블을_모두_만든다() {
		List<String> tables = jdbc.queryForList("""
				select table_name from information_schema.tables
				where table_schema = current_schema() and table_name <> 'flyway_schema_history'
				""", String.class);

		assertThat(tables).containsExactlyInAnyOrder("users", "profiles", "forms", "form_versions", "responses",
				"saved_forms", "notifications", "consent_templates", "question_templates", "attachments");
		assertThat(jdbc.queryForObject("select version from flyway_schema_history where success order by installed_rank desc limit 1",
				String.class))
			.isEqualTo("2");
	}

	@ParameterizedTest
	@CsvSource({ "users,oauth_provider,character varying", "users,oauth_subject,character varying",
			"forms,ai_summary_notice,jsonb", "forms,deleted_at,timestamp with time zone",
			"forms,working_definition,jsonb", "form_versions,definition,jsonb", "responses,response_id,uuid",
			"responses,content,jsonb", "responses,creator_deleted_at,timestamp with time zone",
			"responses,history_form_title,text", "responses,history_questions,jsonb",
			"responses,submitted_at,timestamp with time zone", "profiles,personal_info,jsonb",
			"saved_forms,saved_at,timestamp with time zone" })
	void 지시된_컬럼이_정해진_형식으로_있다(String table, String column, String type) {
		assertThat(jdbc.queryForObject("""
				select data_type from information_schema.columns
				where table_schema = current_schema() and table_name = ? and column_name = ?
				""", String.class, table, column))
			.isEqualTo(type);
	}

	@Test
	void 폐기한_화면_모드_컬럼은_없다() {
		assertThat(jdbc.queryForObject("""
				select count(*) from information_schema.columns
				where table_schema = current_schema() and table_name = 'users' and column_name = 'ui_mode'
				""", Integer.class))
			.isZero();
	}

	@Test
	void 저장_시각_컬럼은_모두_시간대_포함_형식이다() {
		List<String> notUtc = jdbc.queryForList("""
				select table_name || '.' || column_name from information_schema.columns
				where table_schema = current_schema() and table_name <> 'flyway_schema_history'
				  and (column_name like '%\\_at' or column_name = 'deadline')
				  and data_type <> 'timestamp with time zone'
				""", String.class);

		assertThat(notUtc).isEmpty();
	}

}
