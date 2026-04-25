package com.shadowfit.model.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshToken {
    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "token", nullable = false, length = 512)
    private String token;

}
