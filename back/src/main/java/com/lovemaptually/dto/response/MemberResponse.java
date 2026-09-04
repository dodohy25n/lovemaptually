package com.lovemaptually.dto.response;import java.time.OffsetDateTime;public record MemberResponse(Long userId,String nickname,String role,OffsetDateTime joinedAt){}
