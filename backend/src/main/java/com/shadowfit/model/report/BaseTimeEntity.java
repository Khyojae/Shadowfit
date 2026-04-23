package com.shadowfit.model.report;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass //  JPA Entity들이 이 클래스를 상속받을 경우 필드들도 컬럼으로 인식하게 함
@EntityListeners(AuditingEntityListener.class) //  엔티티의 변화를 감시해서 시간을 자동으로 넣어줌
public abstract class BaseTimeEntity {

    @CreatedDate // 엔티티가 생성되어 저장될 때 시간이 자동 저장됨
    @Column(updatable = false) // 생성 시간은 수정되면 안 되니까!
    private LocalDateTime createdAt;

    @LastModifiedDate //  조회한 엔티티의 값을 변경할 때 시간이 자동 저장됨
    private LocalDateTime updatedAt;
}