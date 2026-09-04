package com.lovemaptually.group.repository;

import com.lovemaptually.group.entity.InviteCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    Optional<InviteCode> findByCode(String code);
}
