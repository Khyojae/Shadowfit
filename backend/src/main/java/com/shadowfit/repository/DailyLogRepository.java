package com.shadowfit.repository;

import com.shadowfit.model.report.DailyLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyLogRepository extends JpaRepository<DailyLog,Long> {
    Optional<DailyLog> findByMemberIdAndLogDate(Long memberId, LocalDate logDate);
}
