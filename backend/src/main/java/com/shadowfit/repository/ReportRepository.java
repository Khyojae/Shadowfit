package com.shadowfit.repository;

import com.shadowfit.model.report.Report;
import com.shadowfit.model.report.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report,Long> {
    // 특정 세션의 분석 보고서 가져오기 (1:1 관계)
    Optional<Report> findBySessionId(Long sessionId);


}
