package com.lovemaptually.service;

import com.lovemaptually.common.ApiException;
import org.springframework.http.HttpStatus;
import com.lovemaptually.repository.*;
import org.springframework.stereotype.Service;

@Service
public class AccessService {
    private final RelationGroupRepository groups;
    private final GroupMemberRepository members;
    public AccessService(RelationGroupRepository groups, GroupMemberRepository members) { this.groups=groups; this.members=members; }
    public void groupExists(long groupId) {
        if (!groups.existsById(groupId))
            throw new ApiException(HttpStatus.NOT_FOUND,"GROUP_NOT_FOUND","그룹을 찾을 수 없습니다");
    }
    public void member(long groupId,long userId) {
        groupExists(groupId);
        if (!members.existsByGroupGroupIdAndUserUserIdAndLeftAtIsNull(groupId,userId))
            throw new ApiException(HttpStatus.FORBIDDEN,"GROUP_ACCESS_DENIED","내 그룹이 아닙니다");
    }
}
