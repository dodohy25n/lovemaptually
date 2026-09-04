package com.lovemaptually.group.repository;

import com.lovemaptually.group.entity.GroupMember;
import com.lovemaptually.group.entity.GroupType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    List<GroupMember> findByGroupIdOrderByJoinedAtAscIdAsc(Long groupId);

    List<GroupMember> findByUserIdAndLeftAtIsNullOrderByGroupIdAsc(Long userId);

    @Query("""
            select count(m) > 0 from GroupMember m, RelationGroup g
            where m.groupId = g.id and m.userId = :userId and m.leftAt is null and g.groupType = :type
            """)
    boolean existsActiveMembershipOfType(Long userId, GroupType type);
}
