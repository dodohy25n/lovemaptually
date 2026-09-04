package com.lovemaptually.group.service;

import com.lovemaptually.global.exception.AppException;
import com.lovemaptually.group.dto.request.CreateGroupRequest;
import com.lovemaptually.group.dto.response.GroupResponse;
import com.lovemaptually.group.dto.response.GroupResponse.MemberResponse;
import com.lovemaptually.group.dto.response.MyGroupsResponse;
import com.lovemaptually.group.entity.GroupMember;
import com.lovemaptually.group.entity.GroupType;
import com.lovemaptually.group.entity.InviteCode;
import com.lovemaptually.group.entity.MemberRole;
import com.lovemaptually.group.entity.RelationGroup;
import com.lovemaptually.group.repository.GroupMemberRepository;
import com.lovemaptually.group.repository.InviteCodeRepository;
import com.lovemaptually.group.repository.RelationGroupRepository;
import com.lovemaptually.invite.dto.request.CreateInviteRequest;
import com.lovemaptually.invite.dto.response.InvitePreviewResponse;
import com.lovemaptually.invite.dto.response.InviteResponse;
import com.lovemaptually.user.entity.User;
import com.lovemaptually.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DemoGroupService와 같은 계약을 DB 위에서 지킵니다. 리뷰의 with_group_id가 FK라 그룹은 실제 행이어야 합니다.
 */
@Primary
@Service
public class JpaGroupService implements GroupUseCase {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final RelationGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final InviteCodeRepository inviteRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public JpaGroupService(RelationGroupRepository groupRepository, GroupMemberRepository memberRepository,
                           InviteCodeRepository inviteRepository, UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.inviteRepository = inviteRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public GroupResponse createGroup(Long userId, CreateGroupRequest request) {
        requireUser(userId);
        if (request.groupType() == GroupType.COUPLE
                && memberRepository.existsActiveMembershipOfType(userId, GroupType.COUPLE)) {
            throw coupleAlreadyExists();
        }
        OffsetDateTime now = now();
        String name = request.name() == null || request.name().isBlank() ? null : request.name().trim();
        RelationGroup group = groupRepository.save(new RelationGroup(request.groupType(), name, now));
        memberRepository.save(new GroupMember(group.getId(), userId, MemberRole.OWNER, now));
        return toResponse(group);
    }

    @Override
    @Transactional(readOnly = true)
    public MyGroupsResponse getMyGroups(Long userId) {
        requireUser(userId);
        List<GroupResponse> groups = memberRepository.findByUserIdAndLeftAtIsNullOrderByGroupIdAsc(userId).stream()
                .map(member -> toResponse(groupRepository.findById(member.getGroupId()).orElseThrow()))
                .toList();
        return new MyGroupsResponse(groups);
    }

    @Override
    @Transactional
    public InviteResponse createInvite(Long userId, Long groupId, CreateInviteRequest request) {
        RelationGroup group = groupRepository.findById(groupId).orElseThrow(JpaGroupService::groupNotFound);
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId).orElse(null);
        if (member == null || !member.isActive() || member.getRole() != MemberRole.OWNER) {
            throw new AppException(HttpStatus.FORBIDDEN, "GROUP_ACCESS_DENIED", "내가 소유한 그룹이 아닙니다");
        }
        int maxUses = request.maxUses() == null ? 1 : request.maxUses();
        int expiresInHours = request.expiresInHours() == null ? 24 : request.expiresInHours();
        OffsetDateTime now = now();
        InviteCode invite = inviteRepository.save(new InviteCode(
                newCode(), group.getId(), userId, maxUses, now.plusHours(expiresInHours), now));
        return toResponse(invite);
    }

    @Override
    @Transactional(readOnly = true)
    public InvitePreviewResponse previewInvite(String code) {
        InviteCode invite = requireInvite(code);
        ensureAvailable(invite);
        RelationGroup group = groupRepository.findById(invite.getGroupId()).orElseThrow(JpaGroupService::groupNotFound);
        int memberCount = (int) memberRepository.findByGroupIdOrderByJoinedAtAscIdAsc(group.getId()).stream()
                .filter(GroupMember::isActive).count();
        return new InvitePreviewResponse(group.getId(), group.getGroupType(), group.getName(),
                memberCount, true, invite.getExpiresAt());
    }

    @Override
    @Transactional
    public GroupResponse joinGroup(Long userId, String inviteCode) {
        requireUser(userId);
        InviteCode invite = requireInvite(inviteCode);
        ensureAvailable(invite);
        RelationGroup group = groupRepository.findById(invite.getGroupId()).orElseThrow(JpaGroupService::groupNotFound);
        List<GroupMember> members = memberRepository.findByGroupIdOrderByJoinedAtAscIdAsc(group.getId());
        if (members.stream().anyMatch(m -> m.getUserId().equals(userId) && m.isActive())) {
            throw new AppException(HttpStatus.CONFLICT, "GROUP_MEMBER_ALREADY_EXISTS", "이미 참여 중인 그룹입니다");
        }
        if (group.getGroupType() == GroupType.COUPLE) {
            if (memberRepository.existsActiveMembershipOfType(userId, GroupType.COUPLE)) {
                throw coupleAlreadyExists();
            }
            if (members.stream().filter(GroupMember::isActive).count() >= 2) {
                throw new AppException(HttpStatus.CONFLICT, "COUPLE_GROUP_FULL", "커플 그룹은 최대 2명까지 참여할 수 있습니다");
            }
        }
        try {
            memberRepository.saveAndFlush(new GroupMember(group.getId(), userId, MemberRole.MEMBER, now()));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(HttpStatus.CONFLICT, "GROUP_MEMBER_ALREADY_EXISTS", "이미 참여 중인 그룹입니다");
        }
        invite.consume();
        return toResponse(group);
    }

    private GroupResponse toResponse(RelationGroup group) {
        Map<Long, User> users = userRepository.findAllById(
                        memberRepository.findByGroupIdOrderByJoinedAtAscIdAsc(group.getId()).stream()
                                .map(GroupMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<MemberResponse> members = memberRepository.findByGroupIdOrderByJoinedAtAscIdAsc(group.getId()).stream()
                .filter(GroupMember::isActive)
                .map(member -> new MemberResponse(member.getUserId(),
                        users.get(member.getUserId()).getNickname(), member.getRole().name(), member.getJoinedAt()))
                .toList();
        return new GroupResponse(group.getId(), group.getGroupType(), group.getName(), group.getCreatedAt(), members);
    }

    private InviteResponse toResponse(InviteCode invite) {
        String status = invite.isAvailable(now()) ? "ACTIVE" : "EXPIRED";
        return new InviteResponse(invite.getId(), invite.getCode(), invite.getMaxUses(), invite.getUseCount(),
                status, invite.getExpiresAt(), invite.getCreatedAt());
    }

    private InviteCode requireInvite(String rawCode) {
        return inviteRepository.findByCode(rawCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND", "초대 코드를 찾을 수 없습니다"));
    }

    private void ensureAvailable(InviteCode invite) {
        if (!invite.isAvailable(now())) {
            throw new AppException(HttpStatus.GONE, "INVITE_UNAVAILABLE", "초대 코드가 만료되었거나 모두 사용되었습니다");
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new AppException(
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증된 사용자를 찾을 수 없습니다"));
    }

    private String newCode() {
        StringBuilder code = new StringBuilder("LOVE");
        for (int i = 0; i < 6; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private static AppException coupleAlreadyExists() {
        return new AppException(HttpStatus.CONFLICT, "COUPLE_GROUP_ALREADY_EXISTS", "이미 참여 중인 커플 그룹이 있습니다");
    }

    private static AppException groupNotFound() {
        return new AppException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "그룹을 찾을 수 없습니다");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
