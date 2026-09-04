package com.lovemaptually.place.service;

import com.lovemaptually.global.exception.AppException;
import com.lovemaptually.group.service.GroupAccess;
import com.lovemaptually.place.dto.request.AddGroupPlaceRequest;
import com.lovemaptually.place.dto.request.PlaceInput;
import com.lovemaptually.place.dto.response.GroupMapResponse;
import com.lovemaptually.place.dto.response.GroupPlaceResponse;
import com.lovemaptually.place.dto.response.MapMarkerResponse;
import com.lovemaptually.place.dto.response.PinDetailResponse;
import com.lovemaptually.place.dto.response.PlaceDetailResponse;
import com.lovemaptually.place.dto.response.PlaceResponse;
import com.lovemaptually.place.dto.response.PlaceSearchResponse;
import com.lovemaptually.place.dto.response.PlaceTagResponse;
import com.lovemaptually.place.dto.response.VisitSummaryResponse;
import com.lovemaptually.place.entity.GroupPlace;
import com.lovemaptually.place.entity.Place;
import com.lovemaptually.place.entity.PlaceLabel;
import com.lovemaptually.place.repository.GroupPlaceRepository;
import com.lovemaptually.place.repository.PlaceRepository;
import com.lovemaptually.review.dto.response.GroupReviewsResponse;
import com.lovemaptually.review.entity.Review;
import com.lovemaptually.review.repository.ReviewRepository;
import com.lovemaptually.review.service.ReviewService;
import com.lovemaptually.tag.entity.AttrLevel;
import com.lovemaptually.tag.entity.Tag;
import com.lovemaptually.tag.service.TagCatalog;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 검색과 우리 지도. 카카오 키가 없어 검색은 우리 DB의 places를 봅니다(MapClient 자리는 R1에서 채웁니다).
 */
@Service
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final GroupPlaceRepository groupPlaceRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final GroupAccess groupAccess;
    private final TagCatalog tagCatalog;
    private final EntityManager entityManager;

    public PlaceService(PlaceRepository placeRepository, GroupPlaceRepository groupPlaceRepository,
                        ReviewRepository reviewRepository, ReviewService reviewService,
                        GroupAccess groupAccess, TagCatalog tagCatalog, EntityManager entityManager) {
        this.placeRepository = placeRepository;
        this.groupPlaceRepository = groupPlaceRepository;
        this.reviewRepository = reviewRepository;
        this.reviewService = reviewService;
        this.groupAccess = groupAccess;
        this.tagCatalog = tagCatalog;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PlaceSearchResponse search(String query, String region, int page, int size) {
        if (query == null || query.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "SEARCH_QUERY_REQUIRED", "검색어를 입력해 주세요");
        }
        Page<Place> found = placeRepository.search(query.trim(),
                region == null || region.isBlank() ? null : region.trim(), PageRequest.of(page, size));
        return new PlaceSearchResponse(found.getContent().stream().map(PlaceResponse::from).toList(),
                page, size, found.getTotalElements(), found.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PlaceDetailResponse detail(Long placeId) {
        Place place = requirePlace(placeId);
        return new PlaceDetailResponse(PlaceResponse.from(place), placeTags(placeId));
    }

    @Transactional
    public GroupPlaceResponse addToGroupMap(Long userId, Long groupId, AddGroupPlaceRequest request) {
        groupAccess.requireMember(groupId, userId);
        Place place = resolvePlace(request);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            GroupPlace groupPlace = groupPlaceRepository.saveAndFlush(
                    new GroupPlace(groupId, place.getId(), userId, now));
            return new GroupPlaceResponse(groupPlace.getId(), groupId, place.getId(), userId, null, now);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(HttpStatus.CONFLICT, "PLACE_ALREADY_ADDED", "이미 우리 지도에 담은 장소입니다");
        }
    }

    @Transactional(readOnly = true)
    public GroupMapResponse map(Long userId, Long groupId, String label) {
        groupAccess.requireMember(groupId, userId);
        PlaceLabel filter = parseLabel(label);
        List<MapMarkerResponse> markers = new ArrayList<>();
        for (GroupPlace groupPlace : groupPlaceRepository.findByGroupIdOrderByCreatedAtAscIdAsc(groupId)) {
            if (filter != null && groupPlace.getLabel() != filter) {
                continue;
            }
            Place place = requirePlace(groupPlace.getPlaceId());
            markers.add(new MapMarkerResponse(groupPlace.getId(), place.getId(), place.getName(), place.getAddress(),
                    place.getCategory(), place.getLatitude(), place.getLongitude(), groupPlace.getLabel(),
                    groupPlace.getReviewedCount(), groupPlace.getLikedCount()));
        }
        return new GroupMapResponse(markers);
    }

    @Transactional(readOnly = true)
    public PinDetailResponse pin(Long userId, Long groupId, Long placeId) {
        groupAccess.requireMember(groupId, userId);
        GroupPlace groupPlace = groupPlaceRepository.findByGroupIdAndPlaceId(groupId, placeId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "GROUP_PLACE_NOT_FOUND",
                        "우리 지도에 없는 장소입니다"));
        Place place = requirePlace(placeId);
        GroupReviewsResponse reviews = reviewService.groupReviews(groupId, placeId, userId);

        Map<LocalDate, Integer> visits = new LinkedHashMap<>();
        for (Review review : reviewRepository
                .findByWithGroupIdAndPlaceIdOrderByVisitedOnDescIdDesc(groupId, placeId)) {
            visits.merge(review.getVisitedOn(), 1, Integer::sum);
        }
        List<VisitSummaryResponse> visitSummaries = visits.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Integer>comparingByKey().reversed())
                .map(entry -> new VisitSummaryResponse(entry.getKey(), entry.getValue()))
                .toList();

        List<com.lovemaptually.review.dto.response.ReviewResponse> visible = new ArrayList<>();
        if (reviews.myReview() != null) {
            visible.add(reviews.myReview());
        }
        visible.addAll(reviews.otherReviews());

        return new PinDetailResponse(groupPlace.getId(), PlaceResponse.from(place), groupPlace.getLabel(),
                groupPlace.getReviewedCount(), groupPlace.getLikedCount(), groupPlace.getLabelUpdatedAt(),
                visitSummaries, visible, reviews.otherReviewsLocked(), reviews.lockedReason());
    }

    @SuppressWarnings("unchecked")
    private List<PlaceTagResponse> placeTags(Long placeId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT tag_id, fact_high_count, fact_low_count
                FROM place_tags WHERE place_id = :placeId
                """)
                .setParameter("placeId", placeId)
                .getResultList();
        Map<Long, Tag> byId = tagCatalog.byId();
        List<PlaceTagResponse> tags = new ArrayList<>();
        for (Object[] row : rows) {
            Tag tag = byId.get(((Number) row[0]).longValue());
            int high = ((Number) row[1]).intValue();
            int low = ((Number) row[2]).intValue();
            if (tag == null || high == low) {
                continue;
            }
            AttrLevel fact = high > low ? AttrLevel.HIGH : AttrLevel.LOW;
            tags.add(new PlaceTagResponse(tag.getName(), fact.name(), tag.labelOf(fact), Math.max(high, low)));
        }
        return tags.stream().sorted(Comparator.comparingInt(PlaceTagResponse::count).reversed()).toList();
    }

    private Place resolvePlace(AddGroupPlaceRequest request) {
        if (request.placeId() != null) {
            return requirePlace(request.placeId());
        }
        PlaceInput input = request.place();
        if (input == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "PLACE_REQUIRED", "placeId 또는 place 정보를 보내 주세요");
        }
        return placeRepository.findByProviderAndProviderPlaceId(input.provider(), input.providerPlaceId())
                .orElseGet(() -> placeRepository.saveAndFlush(new Place(
                        input.provider(), input.providerPlaceId(), input.name(), input.address(), input.region(),
                        input.category(), input.priceBand() == null ? null : input.priceBand().shortValue(),
                        scale(input.latitude()), scale(input.longitude()), OffsetDateTime.now(ZoneOffset.UTC))));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(7, java.math.RoundingMode.HALF_UP);
    }

    private PlaceLabel parseLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        try {
            return PlaceLabel.valueOf(label.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_LABEL", "라벨 값을 확인해 주세요");
        }
    }

    private Place requirePlace(Long placeId) {
        return placeRepository.findById(placeId).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "장소를 찾을 수 없습니다"));
    }
}
