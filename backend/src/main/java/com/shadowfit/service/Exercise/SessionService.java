package com.shadowfit.service.Exercise;

import com.shadowfit.dto.exercises.VideoRequestDto;
import com.shadowfit.global.error.BusinessException;
import com.shadowfit.global.error.ErrorCode;
import com.shadowfit.model.exercise.Exercise;
import com.shadowfit.model.exercise.Session;
import com.shadowfit.model.exercise.Status;
import com.shadowfit.model.member.Member;
import com.shadowfit.repository.ExercisesRepository;
import com.shadowfit.repository.MemberRepository;
import com.shadowfit.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shadowfit.grpc.SessionStatus;
import com.shadowfit.grpc.SessionCompleteRequest;

import java.time.LocalDateTime;

//공통세션
@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository sessionRepository;
    private final ExercisesRepository exercisesRepository;
    private final MemberRepository memberRepository;

    /**
     * [세션 생성] 새로운 운동 분석 프로세스를 시작하기 위한 초기 레코드를 생성합니다.
     * @param appDto 사용자가 선택한 운동 및 영상 정보
     * @param currentMemberId 현재 로그인한 사용자 ID
     * @return 생성된 세션 엔티티
     */
    @Transactional
    public Session createSession(VideoRequestDto appDto, Long currentMemberId) {
        Member member = memberRepository.findById(currentMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Exercise exercise = exercisesRepository.findById(appDto.getExerciseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.EXERCISE_NOT_FOUND));

        Session session = Session.builder()
                .user(member)
                .exercise(exercise)
                .referenceSource(appDto.getReferenceSource())
                .startTime(LocalDateTime.now())
                .status(Status.IN_PROGRESS)
                .build();

        return sessionRepository.save(session);
    }

    /**
     * [세션 완료] AI 서버로부터 수신한 분석 결과를 바탕으로 세션을 최종 업데이트합니다.
     * @param request AI 서버(gRPC)에서 넘어온 최종 분석 데이터
     */
    @Transactional
    public void completeSession(SessionCompleteRequest request){
        Session session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(()->new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        session.setStatus(Status.COMPLETED);
        session.setEndTime(LocalDateTime.now());

        session.setTotalReps(request.getTotalReps());
        session.setAvgSyncRate(java.math.BigDecimal.valueOf(request.getAvgSyncRate()));
        session.setCaloriesBurned(java.math.BigDecimal.valueOf(request.getCaloriesBurned()));

        sessionRepository.save(session);
    }

    /**
     * ✅ 구버전 WebClient 방식 (필요 시 사용)
     */
    @Transactional
    public Long sendToAnalysisServer(VideoRequestDto appDto, Long currentMemberId) {
        Member member = memberRepository.findById(currentMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Exercise exercise = exercisesRepository.findById(appDto.getExerciseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.EXERCISE_NOT_FOUND));

        Session session = Session.builder()
                .user(member)
                .exercise(exercise)
                .referenceSource(appDto.getReferenceSource())
                .startTime(LocalDateTime.now())
                .status(Status.IN_PROGRESS)
                .build();

        Session savedSession = sessionRepository.save(session);
        return savedSession.getId();
    }
}
