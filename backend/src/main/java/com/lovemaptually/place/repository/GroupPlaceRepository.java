package com.lovemaptually.place.repository;

import com.lovemaptually.place.entity.GroupPlace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupPlaceRepository extends JpaRepository<GroupPlace, Long> {

    Optional<GroupPlace> findByGroupIdAndPlaceId(Long groupId, Long placeId);

    List<GroupPlace> findByGroupIdOrderByCreatedAtAscIdAsc(Long groupId);
}
