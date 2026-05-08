package com.shadowfit.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.context.annotation.Bean;

/**
 * 스케줄러 설정
 *
 * 타임아웃 체크 및 기타 정기 작업을 관리합니다.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    /**
     * 스케줄 실행용 스레드 풀 설정
     *
     * maxPoolSize: 스케줄러 작업을 처리할 최대 스레드 수
     * threadNamePrefix: 스레드 이름 접두사 (로그 추적용)
     * waitForTasksToCompleteOnShutdown: 애플리케이션 종료 시 진행 중인 작업 대기
     * awaitTerminationSeconds: 작업 완료 대기 시간
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("shadowfit-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }
}

