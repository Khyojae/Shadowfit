package com.shadowfit.repository;

import com.shadowfit.model.report.Report;
import com.shadowfit.model.report.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report,Long> {
    // 특정 세션의 분석 보고서 가져오기 (1:1 관계)
    Optional<Report> findBySessionId(Long sessionId);

    // 특정 사용자의 리포트 목록 최신순 조회 (주간/월간 리포트 모아보기용)
    List<Report> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 특정 타입(SESSION, WEEKLY 등)의 리포트만 필터링
    List<Report> findByUserIdAndReportType(Long userId, ReportType reportType);

}
