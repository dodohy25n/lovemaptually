package com.lovemaptually.group.service;

import com.lovemaptually.global.exception.AppException;
import com.lovemaptually.group.dto.request.CreateGroupRequest;
import com.lovemaptually.group.dto.response.GroupResponse;
import com.lovemaptually.group.dto.response.GroupResponse.MemberResponse;
import com.lovemaptually.group.dto.response.MyGroupsResponse;
import com.lovemaptually.group.entity.GroupType;
import com.lovemaptually.invite.dto.request.CreateInviteRequest;
import com.lovemaptually.invite.dto.response.InvitePreviewResponse;
import com.lovemaptually.invite.dto.response.InviteResponse;
import com.lovemaptually.user.entity.User;
import com.lovemaptually.user.repository.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 외부 계약을 먼저 연결하기 위한 재현 가능한 인메모리 Mock 구현입니다.
 * 이후 DB 구현체로 바꿔도 Controller와 DTO는 그대로 유지됩니다.
 * 기본 빈은 JpaGroupService이고, 이 구현은 demo-group 프로파일에서만 올라옵니다.
 */
@Profile("demo-group")
@Service
public class DemoGroupService implements GroupUseCase {

    private final UserRepository userRepository;
    private final Clock clock;
    private final AtomicLong groupSequence = new AtomicLong(7000);
    private final AtomicLong inviteSequence = new AtomicLong(9000);
    private final Map<Long, DemoGroup> groups = new LinkedHashMap<>();
    private final Map<String, DemoInvite> invites = new LinkedHashMap<>();

    public DemoGroupService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.clock = Clock.systemUTC();
    }

    @Override
    public synchronized GroupResponse createGroup(Long userId, CreateGroupRequest request) {
        User user = requireUser(userId);
        if (request.groupType() == GroupType.COUPLE && hasActiveCouple(userId)) {
            throw new AppException(HttpStatus.CONFLICT, "COUPLE_GROUP_ALREADY_EXISTS", "이미 참여 중인 커플 그룹이 있습니다");
        }
        OffsetDateTime now = now();
        long groupId = groupSequence.incrementAndGet();
        String name = request.name() == null || request.name().isBlank() ? null : request.name().trim();
        DemoGroup group = new DemoGroup(groupId, request.groupType(), name, now, new LinkedHashMap<>());
        group.members().put(userId, new DemoMember(userId, user.getNickname(), "OWNER", now));
        groups.put(groupId, group);
        return toResponse(group);
    }

    @Override
    public synchronized MyGroupsResponse getMyGroups(Long userId) {
        requireUser(userId);
        List<GroupResponse> mine = groups.values().stream()
                .filter(group -> group.members().containsKey(userId))
                .sorted(Comparator.comparing(DemoGroup::id))
                .map(this::toResponse)
                .toList();
        return new MyGroupsResponse(mine);
    }

    @Override
    public synchronized InviteResponse createInvite(Long userId, Long groupId, CreateInviteRequest request) {
        DemoGroup group = requireGroup(groupId);
        DemoMember member = group.members().get(userId);
        if (member == null || !"OWNER".equals(member.role())) {
            throw new AppException(HttpStatus.FORBIDDEN, "GROUP_ACCESS_DENIED", "내가 소유한 그룹이 아닙니다");
        }
        int maxUses = request.maxUses() == null ? 1 : request.maxUses();
        int expiresInHours = request.expiresInHours() == null ? 24 : request.expiresInHours();
        long id = inviteSequence.incrementAndGet();
        OffsetDateTime createdAt = now();
        String code = "LOVE" + id;
        DemoInvite invite = new DemoInvite(
                id, code, groupId, maxUses, 0, createdAt.plusHours(expiresInHours), createdAt
        );
        invites.put(code, invite);
        return toResponse(invite);
    }

    @Override
    public synchronized InvitePreviewResponse previewInvite(String code) {
        DemoInvite invite = requireInvite(code);
        ensureAvailable(invite);
        DemoGroup group = requireGroup(invite.groupId());
        return new InvitePreviewResponse(
                group.id(), group.type(), group.name(), group.members().size(), true, invite.expiresAt()
        );
    }

    @Override
    public synchronized GroupResponse joinGroup(Long userId, String inviteCode) {
        User user = requireUser(userId);
        DemoInvite invite = requireInvite(inviteCode);
        ensureAvailable(invite);
        DemoGroup group = requireGroup(invite.groupId());
        if (group.members().containsKey(userId)) {
            throw new AppException(HttpStatus.CONFLICT, "GROUP_MEMBER_ALREADY_EXISTS", "이미 참여 중인 그룹입니다");
        }
        if (group.type() == GroupType.COUPLE && hasActiveCouple(userId)) {
            throw new AppException(HttpStatus.CONFLICT, "COUPLE_GROUP_ALREADY_EXISTS", "이미 참여 중인 커플 그룹이 있습니다");
        }
        if (group.type() == GroupType.COUPLE && group.members().size() >= 2) {
            throw new AppException(HttpStatus.CONFLICT, "COUPLE_GROUP_FULL", "커플 그룹은 최대 2명까지 참여할 수 있습니다");
        }
        group.members().put(userId, new DemoMember(userId, user.getNickname(), "MEMBER", now()));
        invite.incrementUseCount();
        return toResponse(group);
    }

    private boolean hasActiveCouple(Long userId) {
        return groups.values().stream()
                .anyMatch(group -> group.type() == GroupType.COUPLE && group.members().containsKey(userId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new AppException(
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증된 사용자를 찾을 수 없습니다"
        ));
    }

    private DemoGroup requireGroup(Long groupId) {
        DemoGroup group = groups.get(groupId);
        if (group == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "그룹을 찾을 수 없습니다");
        }
        return group;
    }

    private DemoInvite requireInvite(String rawCode) {
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        DemoInvite invite = invites.get(code);
        if (invite == null) {
            throw new AppException(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND", "초대 코드를 찾을 수 없습니다");
        }
        return invite;
    }

    private void ensureAvailable(DemoInvite invite) {
        if (!now().isBefore(invite.expiresAt()) || invite.useCount() >= invite.maxUses()) {
            throw new AppException(HttpStatus.GONE, "INVITE_UNAVAILABLE", "초대 코드가 만료되었거나 모두 사용되었습니다");
        }
    }

    private GroupResponse toResponse(DemoGroup group) {
        List<MemberResponse> members = group.members().values().stream()
                .map(member -> new MemberResponse(
                        member.userId(), member.nickname(), member.role(), member.joinedAt()
                ))
                .toList();
        return new GroupResponse(group.id(), group.type(), group.name(), group.createdAt(), members);
    }

    private InviteResponse toResponse(DemoInvite invite) {
        String status = invite.useCount() >= invite.maxUses() || !now().isBefore(invite.expiresAt())
                ? "EXPIRED" : "ACTIVE";
        return new InviteResponse(
                invite.id(), invite.code(), invite.maxUses(), invite.useCount(), status,
                invite.expiresAt(), invite.createdAt()
        );
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private record DemoGroup(
            long id,
            GroupType type,
            String name,
            OffsetDateTime createdAt,
            Map<Long, DemoMember> members
    ) {
    }

    private record DemoMember(Long userId, String nickname, String role, OffsetDateTime joinedAt) {
    }

    private static final class DemoInvite {
        private final long id;
        private final String code;
        private final long groupId;
        private final int maxUses;
        private int useCount;
        private final OffsetDateTime expiresAt;
        private final OffsetDateTime createdAt;

        private DemoInvite(
                long id,
                String code,
                long groupId,
                int maxUses,
                int useCount,
                OffsetDateTime expiresAt,
                OffsetDateTime createdAt
        ) {
            this.id = id;
            this.code = code;
            this.groupId = groupId;
            this.maxUses = maxUses;
            this.useCount = useCount;
            this.expiresAt = expiresAt;
            this.createdAt = createdAt;
        }

        long id() { return id; }
        String code() { return code; }
        long groupId() { return groupId; }
        int maxUses() { return maxUses; }
        int useCount() { return useCount; }
        OffsetDateTime expiresAt() { return expiresAt; }
        OffsetDateTime createdAt() { return createdAt; }
        void incrementUseCount() { useCount++; }
    }
}
