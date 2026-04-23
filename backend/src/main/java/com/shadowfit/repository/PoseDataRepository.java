package com.shadowfit.repository;

import com.shadowfit.model.exercise.PoseData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PoseDataRepository extends JpaRepository<PoseData, Long> {
    // 특정 세션의 모든 좌표 데이터를 시간순으로 조회하고 싶을 때 사용
    List<PoseData> findBySessionIdOrderByTimestampSecAsc(Long sessionId);
}