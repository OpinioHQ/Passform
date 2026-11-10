package com.opinio.passform.service;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import com.opinio.passform.mail.ReminderMailer;
import com.opinio.passform.mail.SmtpReminderMailer;
import com.opinio.passform.mail.UnconfiguredReminderMailer;

// service 패키지에 둔다. 권한 골든 테스트가 이 패키지만 스캔해 서비스를 조립하기 때문이다(FileStorageConfig와 같다).
// 사용자 이름이나 앱 비밀번호가 비어 있으면 메일 클라이언트를 만들지 않는다. 값은 로그에 쓰지 않는다.
@Configuration(proxyBeanMethods = false)
public class ReminderMailerConfig {

	private static final Logger log = LoggerFactory.getLogger(ReminderMailerConfig.class);

	private static final String TIMEOUT_MILLIS = "10000";

	@Bean
	ReminderMailer reminderMailer(@Value("${passform.mail.host:smtp.gmail.com}") String host,
			@Value("${passform.mail.port:587}") int port, @Value("${passform.mail.username:}") String username,
			@Value("${passform.mail.password:}") String password, @Value("${passform.mail.from:}") String from,
			@Value("${passform.deadline-reminder.hours:24}") long hours) {
		if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
			log.info("mail_not_configured: 마감 알림은 사이트 알림만 만든다");
			return new UnconfiguredReminderMailer();
		}
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(host);
		sender.setPort(port);
		sender.setUsername(username.strip());
		sender.setPassword(password.strip());
		sender.setDefaultEncoding("UTF-8");
		Properties properties = sender.getJavaMailProperties();
		properties.put("mail.transport.protocol", "smtp");
		properties.put("mail.smtp.auth", "true");
		properties.put("mail.smtp.starttls.enable", "true");
		properties.put("mail.smtp.starttls.required", "true");
		properties.put("mail.smtp.connectiontimeout", TIMEOUT_MILLIS);
		properties.put("mail.smtp.timeout", TIMEOUT_MILLIS);
		properties.put("mail.smtp.writetimeout", TIMEOUT_MILLIS);
		return new SmtpReminderMailer(sender, StringUtils.hasText(from) ? from.strip() : username.strip(), hours);
	}

}
