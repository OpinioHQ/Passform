package com.opinio.passform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.opinio.passform.service.DeadlineReminderService;

// 마감 알림 스케줄러(AC18). 주기는 설정값이다(운영 기본 10분, 로컬 확인 때 DEADLINE_REMINDER_INTERVAL=PT1M).
// test 프로필에서는 돌지 않는다. 테스트는 고정 시계로 DeadlineReminderService.run()을 직접 부르고, 실제 SMTP를 부르지 않는다.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!test")
public class DeadlineReminderScheduling {

	private static final Logger log = LoggerFactory.getLogger(DeadlineReminderScheduling.class);

	private final DeadlineReminderService reminders;

	public DeadlineReminderScheduling(DeadlineReminderService reminders) {
		this.reminders = reminders;
	}

	@Scheduled(fixedDelayString = "${passform.deadline-reminder.interval:PT10M}",
			initialDelayString = "${passform.deadline-reminder.initial-delay:PT1M}")
	void remind() {
		try {
			int created = reminders.run();
			if (created > 0) {
				log.info("deadline_reminder created={}", created);
			}
		}
		catch (RuntimeException e) {
			// 다음 주기에 다시 돈다. 예외 메시지에 값이 섞일 수 있어 종류만 남긴다.
			log.warn("deadline_reminder_failed cause={}", e.getClass().getSimpleName());
		}
	}

}
