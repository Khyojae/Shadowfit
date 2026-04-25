package com.shadowfit.service.Report;

import com.shadowfit.dto.report.record.DailyLogRequestDto;
import com.shadowfit.dto.report.record.DailyLogResponseDto;
import com.shadowfit.global.error.BusinessException;
import com.shadowfit.global.error.ErrorCode;
import com.shadowfit.model.member.Member;
import com.shadowfit.model.report.DailyLog;
import com.shadowfit.repository.DailyLogRepository;
import com.shadowfit.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyLogService {
    private final DailyLogRepository dailyLogRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void saveOrUpdateLog(Long memberId, DailyLogRequestDto dto){
        log.info("일지 저장 요청 - 사용자: {}, 날짜: {}", memberId, dto.getLogDate());

        //1. 일지 조회
        DailyLog dailyLog = dailyLogRepository.findByMemberIdAndLogDate(memberId,dto.getLogDate())
                .map(existingLog->{
                    existingLog.setMemo(dto.getMemo());
                    existingLog.setMood(dto.getMood());
                    return existingLog;
                })
                .orElseGet(() -> {
                    //2.존재하지 않는다면 새로운 엔티티 생성
                    Member member = memberRepository.findById(memberId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                    return DailyLog.builder()
                            .member(member)
                            .logDate(dto.getLogDate())
                            .memo(dto.getMemo())
                            .mood(dto.getMood())
                            .build();
                });
        dailyLogRepository.save(dailyLog);
    }
    @Transactional(readOnly = true)
    public DailyLogResponseDto getDailyLog(Long memberId, LocalDate date) {
        DailyLog log = dailyLogRepository.findByMemberIdAndLogDate(memberId, date)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

        // 엔티티를 응답 DTO로 변환하여 반환
        return new DailyLogResponseDto(log);
    }
}
