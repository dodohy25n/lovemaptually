package com.lovemaptually.review.service;

import com.lovemaptually.ai.AiClient;
import com.lovemaptually.ai.TagCandidate;
import com.lovemaptually.global.config.AiConfig.AiClientSelector;
import com.lovemaptually.global.exception.AppException;
import com.lovemaptually.group.entity.GroupMember;
import com.lovemaptually.group.service.GroupAccess;
import com.lovemaptually.place.entity.GroupPlace;
import com.lovemaptually.place.entity.PlaceLabel;
import com.lovemaptually.place.repository.GroupPlaceRepository;
import com.lovemaptually.place.repository.PlaceRepository;
import com.lovemaptually.review.dto.request.CreateReviewRequest;
import com.lovemaptually.review.dto.response.GroupReviewsResponse;
import com.lovemaptually.review.dto.response.PlaceLabelResponse;
import com.lovemaptually.review.dto.response.ReviewResponse;
import com.lovemaptually.review.dto.response.ReviewTagResponse;
import com.lovemaptually.review.entity.Review;
import com.lovemaptually.review.entity.ReviewTag;
import com.lovemaptually.review.entity.TagStatus;
import com.lovemaptually.review.repository.ReviewRepository;
import com.lovemaptually.review.repository.ReviewTagRepository;
import com.lovemaptually.review.service.AggregateRecalculator.LabelSnapshot;
import com.lovemaptually.tag.entity.AttrLevel;
import com.lovemaptually.tag.entity.Tag;
import com.lovemaptually.tag.service.TagCatalog;
import com.lovemaptually.user.entity.User;
import com.lovemaptually.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 흐름 1 리뷰 작성. STEP 1부터 7까지가 한 트랜잭션입니다(D-25).
 * AI가 실패해도 리뷰는 남고 201이며 tagStatus가 FAILED입니다.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final ReviewTagRepository reviewTagRepository;
    private final PlaceRepository placeRepository;
    private final GroupPlaceRepository groupPlaceRepository;
    private final UserRepository userRepository;
    private final TagCatalog tagCatalog;
    private final AggregateRecalculator recalculator;
    private final AiClientSelector aiClientSelector;
    private final GroupAccess groupAccess;
    private final EntityManager entityManager;

    public ReviewService(ReviewRepository reviewRepository, ReviewTagRepository reviewTagRepository,
                         PlaceRepository placeRepository, GroupPlaceRepository groupPlaceRepository,
                         UserRepository userRepository, TagCatalog tagCatalog,
                         AggregateRecalculator recalculator, AiClientSelector aiClientSelector,
                         GroupAccess groupAccess, EntityManager entityManager) {
        this.reviewRepository = reviewRepository;
        this.reviewTagRepository = reviewTagRepository;
        this.placeRepository = placeRepository;
        this.groupPlaceRepository = groupPlaceRepository;
        this.userRepository = userRepository;
        this.tagCatalog = tagCatalog;
        this.recalculator = recalculator;
        this.aiClientSelector = aiClientSelector;
        this.groupAccess = groupAccess;
        this.entityManager = entityManager;
    }

    @Transactional
    public ReviewResponse create(Long userId, CreateReviewRequest request) {
        if (request.rating() < 1 || request.rating() > 5) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "RATING_OUT_OF_RANGE", "별점은 1점부터 5점까지입니다");
        }
        placeRepository.findById(request.placeId()).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "장소를 찾을 수 없습니다"));
        if (request.withGroupId() != null) {
            groupAccess.requireMember(request.withGroupId(), userId);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Review review = new Review(userId, request.placeId(), request.visitedOn(), request.withGroupId(),
                request.rating().shortValue(), request.content().trim(), now);
        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(HttpStatus.CONFLICT, "REVIEW_DUPLICATED", "같은 날 같은 장소에 이미 리뷰를 남겼습니다");
        }

        Map<String, Tag> dictionary = tagCatalog.byName();
        List<ReviewTag> saved = new ArrayList<>();
        TagStatus status;
        try {
            List<TagCandidate> candidates = currentAiClient().extractTags(review.getContent(), tagCatalog.definitions());
            saved.addAll(persistTags(review, candidates, dictionary));
            status = TagStatus.COMPLETED;
        } catch (RuntimeException exception) {
            log.warn("태그 추출에 실패해 리뷰만 저장합니다 reviewId={}", review.getId(), exception);
            status = TagStatus.FAILED;
        }
        review.markTagStatus(status);

        Set<Long> tagIds = new LinkedHashSet<>(saved.stream().map(ReviewTag::getTagId).toList());
        recalculator.recomputePlaceTags(review.getPlaceId(), tagIds);
        recalculator.recomputeUserTags(userId, tagIds);

        LabelSnapshot snapshot = null;
        if (review.getWithGroupId() != null) {
            // 지도에 없는 장소에 리뷰가 붙으면 라벨을 적을 행이 없어 응답과 저장이 어긋납니다.
            if (groupPlaceRepository.findByGroupIdAndPlaceId(review.getWithGroupId(), review.getPlaceId()).isEmpty()) {
                groupPlaceRepository.saveAndFlush(
                        new GroupPlace(review.getWithGroupId(), review.getPlaceId(), userId, now));
            }
            snapshot = recalculator.recomputeGroupPlace(review.getWithGroupId(), review.getPlaceId(), now);
        }
        return toResponse(review, saved, snapshot, nicknameOf(userId));
    }

    @Transactional(readOnly = true)
    public ReviewResponse get(Long reviewId, Long userId) {
        Review review = requireReview(reviewId);
        if (!review.getUserId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "REVIEW_ACCESS_DENIED", "내 리뷰가 아닙니다");
        }
        return toResponse(review, reviewTagRepository.findByReviewIdOrderByIdAsc(reviewId),
                labelOf(review), nicknameOf(review.getUserId()));
    }

    /**
     * 남의 리뷰가 안 보이는 것은 권한이 아니라 상태라서 200입니다. 잠금 여부는 otherReviewsLocked가 알립니다.
     */
    @Transactional(readOnly = true)
    public GroupReviewsResponse groupReviews(Long groupId, Long placeId, Long userId) {
        groupAccess.requireMember(groupId, userId);
        placeRepository.findById(placeId).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "장소를 찾을 수 없습니다"));

        List<Review> reviews = reviewRepository.findByWithGroupIdAndPlaceIdOrderByVisitedOnDescIdDesc(groupId, placeId);
        Optional<GroupPlace> groupPlace = groupPlaceRepository.findByGroupIdAndPlaceId(groupId, placeId);
        PlaceLabel label = groupPlace.map(GroupPlace::getLabel).orElse(null);
        int reviewedCount = groupPlace.map(GroupPlace::getReviewedCount).orElse(0);
        int likedCount = groupPlace.map(GroupPlace::getLikedCount).orElse(0);

        Review mine = reviews.stream().filter(review -> review.getUserId().equals(userId)).findFirst().orElse(null);
        List<Review> others = reviews.stream().filter(review -> !review.getUserId().equals(userId)).toList();
        boolean locked = mine == null && !others.isEmpty();

        ReviewResponse myReview = mine == null ? null
                : toResponse(mine, reviewTagRepository.findByReviewIdOrderByIdAsc(mine.getId()),
                new LabelSnapshot(label, reviewedCount, likedCount), nicknameOf(mine.getUserId()));
        List<ReviewResponse> otherReviews = locked ? List.of() : others.stream()
                .map(review -> toResponse(review, reviewTagRepository.findByReviewIdOrderByIdAsc(review.getId()),
                        new LabelSnapshot(label, reviewedCount, likedCount), nicknameOf(review.getUserId())))
                .toList();

        return new GroupReviewsResponse(label, reviewedCount, likedCount, myReview, otherReviews, locked,
                locked ? "내 리뷰를 남기면 다른 구성원의 리뷰가 함께 공개됩니다" : null);
    }

    private AiClient currentAiClient() {
        return aiClientSelector.current();
    }

    /**
     * 사전 밖 이름과 라벨은 버리고 unmatched_tag_logs에 남깁니다(D-21).
     */
    private List<ReviewTag> persistTags(Review review, List<TagCandidate> candidates, Map<String, Tag> dictionary) {
        List<ReviewTag> saved = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (TagCandidate candidate : candidates) {
            Tag tag = dictionary.get(candidate.tagName());
            if (tag == null) {
                logUnmatched(review.getId(), candidate.tagName());
                continue;
            }
            AttrLevel fact = tag.levelOf(candidate.factLabel());
            AttrLevel want = tag.levelOf(candidate.wantLabel());
            if (fact == null && want == null) {
                logUnmatched(review.getId(), candidate.tagName());
                continue;
            }
            if (!seen.add(tag.getId())) {
                continue;
            }
            saved.add(reviewTagRepository.save(
                    new ReviewTag(review.getId(), tag.getId(), fact, want, candidate.evidence())));
        }
        reviewTagRepository.flush();
        return saved;
    }

    private void logUnmatched(Long reviewId, String rawTag) {
        entityManager.createNativeQuery("""
                INSERT INTO unmatched_tag_logs (review_id, raw_tag) VALUES (:reviewId, :rawTag)
                """)
                .setParameter("reviewId", reviewId)
                .setParameter("rawTag", rawTag == null ? "" : rawTag)
                .executeUpdate();
    }

    private LabelSnapshot labelOf(Review review) {
        if (review.getWithGroupId() == null) {
            return null;
        }
        return groupPlaceRepository.findByGroupIdAndPlaceId(review.getWithGroupId(), review.getPlaceId())
                .map(place -> new LabelSnapshot(place.getLabel(), place.getReviewedCount(), place.getLikedCount()))
                .orElse(null);
    }

    private Review requireReview(Long reviewId) {
        return reviewRepository.findById(reviewId).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다"));
    }

    private String nicknameOf(Long userId) {
        return userRepository.findById(userId).map(User::getNickname).orElse(null);
    }

    private ReviewResponse toResponse(Review review, List<ReviewTag> tags, LabelSnapshot snapshot, String nickname) {
        Map<Long, Tag> byId = tagCatalog.byId();
        List<ReviewTagResponse> tagResponses = tags.stream()
                .sorted(Comparator.comparing(ReviewTag::getId))
                .map(reviewTag -> {
                    Tag tag = byId.get(reviewTag.getTagId());
                    return new ReviewTagResponse(tag.getName(), tag.labelOf(reviewTag.getFactValue()),
                            tag.labelOf(reviewTag.getWantValue()), reviewTag.getEvidenceText());
                })
                .toList();
        PlaceLabelResponse placeLabel = snapshot == null ? null
                : new PlaceLabelResponse(snapshot.label(), snapshot.reviewedCount(), snapshot.likedCount());
        return new ReviewResponse(review.getId(), review.getPlaceId(), review.getUserId(), nickname,
                review.getVisitedOn(), review.getRating().intValue(), review.getContent(), review.getTagStatus(),
                tagResponses, placeLabel, review.getCreatedAt());
    }

    List<GroupMember> membersOf(Long groupId) {
        return groupAccess.activeMembers(groupId);
    }
}
