package com.lovemaptually.group.service;

import com.lovemaptually.global.exception.AppException;
import com.lovemaptually.group.entity.GroupMember;
import com.lovemaptually.group.entity.RelationGroup;
import com.lovemaptually.group.repository.GroupMemberRepository;
import com.lovemaptually.group.repository.RelationGroupRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 경로의 {groupId}가 요청자의 그룹인지 확인하는 한 곳입니다. 아니면 403이고, 그룹이 없으면 404입니다.
 */
@Component
public class GroupAccess {

    private final RelationGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;

    public GroupAccess(RelationGroupRepository groupRepository, GroupMemberRepository memberRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
    }

    public RelationGroup requireGroup(Long groupId) {
        return groupRepository.findById(groupId).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "그룹을 찾을 수 없습니다"));
    }

    public RelationGroup requireMember(Long groupId, Long userId) {
        RelationGroup group = requireGroup(groupId);
        boolean member = memberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(GroupMember::isActive)
                .orElse(false);
        if (!member) {
            throw new AppException(HttpStatus.FORBIDDEN, "NOT_GROUP_MEMBER", "내 그룹이 아닙니다");
        }
        return group;
    }

    public List<GroupMember> activeMembers(Long groupId) {
        return memberRepository.findByGroupIdOrderByJoinedAtAscIdAsc(groupId).stream()
                .filter(GroupMember::isActive)
                .toList();
    }
}
