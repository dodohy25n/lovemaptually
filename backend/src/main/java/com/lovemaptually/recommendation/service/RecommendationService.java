package com.lovemaptually.recommendation.service;

import com.lovemaptually.global.exception.AppException;
import com.lovemaptually.group.entity.GroupMember;
import com.lovemaptually.group.service.GroupAccess;
import com.lovemaptually.place.entity.Place;
import com.lovemaptually.place.repository.PlaceRepository;
import com.lovemaptually.recommendation.client.RecommendationClient;
import com.lovemaptually.recommendation.client.RecommendationCommand;
import com.lovemaptually.recommendation.client.RecommendationItem;
import com.lovemaptually.recommendation.client.RecommendationResult;
import com.lovemaptually.recommendation.dto.request.CreateRecommendationRequest;
import com.lovemaptually.recommendation.dto.response.RecommendationAcceptedResponse;
import com.lovemaptually.recommendation.dto.response.RecommendationIntentResponse;
import com.lovemaptually.recommendation.dto.response.RecommendationItemResponse;
import com.lovemaptually.recommendation.dto.response.RecommendationResultResponse;
import com.lovemaptually.recommendation.entity.RecBasis;
import com.lovemaptually.recommendation.entity.Recommendation;
import com.lovemaptually.recommendation.entity.RecStatus;
import com.lovemaptually.recommendation.entity.RecommendationRequest;
import com.lovemaptually.recommendation.repository.RecommendationRepository;
import com.lovemaptually.recommendation.repository.RecommendationRequestRepository;
import com.lovemaptually.recommendation.service.QueryParser.Intent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천은 두 단계입니다. 접수는 202로 즉시 끝내고 순위 계산은 워커가 합니다.
 * 엔진이 응답하지 않으면 규칙 폴백이 받고 결과에 degraded를 남깁니다.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationRequestRepository requestRepository;
    private final RecommendationRepository recommendationRepository;
    private final PlaceRepository placeRepository;
    private final GroupAccess groupAccess;
    private final QueryParser queryParser;
    private final RecommendationClient httpClient;
    private final RecommendationClient fallbackClient;
    /**
     * degraded와 notice는 명세에 없는 추가 필드이고 저장할 컬럼이 없어 요청 단위로 들고 있습니다.
     * 재기동하면 사라지므로 화면은 이 값이 없을 때를 정상으로 다뤄야 합니다. 컬럼으로 올리는 것은 R1입니다.
     */
    private static final int META_LIMIT = 500;
    private final Map<Long, RequestMeta> meta = new ConcurrentHashMap<>();

    public RecommendationService(RecommendationRequestRepository requestRepository,
                                 RecommendationRepository recommendationRepository,
                                 PlaceRepository placeRepository, GroupAccess groupAccess,
                                 QueryParser queryParser,
                                 RecommendationClient httpRecommendationClient,
                                 RecommendationClient ruleFallbackRecommendationClient) {
        this.requestRepository = requestRepository;
        this.recommendationRepository = recommendationRepository;
        this.placeRepository = placeRepository;
        this.groupAccess = groupAccess;
        this.queryParser = queryParser;
        this.httpClient = httpRecommendationClient;
        this.fallbackClient = ruleFallbackRecommendationClient;
    }

    @Transactional
    public RecommendationAcceptedResponse accept(Long userId, Long groupId, CreateRecommendationRequest request) {
        groupAccess.requireMember(groupId, userId);
        Intent intent = queryParser.parse(request.query());
        if (intent.region() == null) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "REGION_NOT_FOUND",
                    "어느 동네를 찾으시는지 알려 주세요");
        }
        RecommendationRequest saved = requestRepository.saveAndFlush(new RecommendationRequest(
                groupId, userId, request.query().trim(), intent.region(), (short) intent.count(),
                intent.budget() == null ? null : intent.budget().shortValue(),
                OffsetDateTime.now(ZoneOffset.UTC)));
        return new RecommendationAcceptedResponse(saved.getId(), saved.getStatus(), saved.getCreatedAt());
    }

    @Transactional
    public void run(Long requestId) {
        RecommendationRequest request = requestRepository.findById(requestId).orElseThrow();
        List<Long> memberIds = groupAccess.activeMembers(request.getGroupId()).stream()
                .map(GroupMember::getUserId).toList();
        RecommendationCommand command = new RecommendationCommand(request.getGroupId(), memberIds,
                request.getIntentRegion(), request.getIntentCount() == null ? 3 : request.getIntentCount(),
                request.getIntentBudget() == null ? null : request.getIntentBudget().intValue());

        RecommendationResult result;
        try {
            result = httpClient.recommend(command);
        } catch (RuntimeException exception) {
            log.warn("추천 엔진에 연결하지 못해 규칙 폴백으로 넘어갑니다 requestId={} 사유={}", requestId, exception.toString());
            result = fallbackClient.recommend(command);
        }

        short order = 1;
        for (RecommendationItem item : result.items()) {
            recommendationRepository.save(new Recommendation(request.getId(), item.placeId(),
                    item.matchedTags().toArray(String[]::new), RecBasis.valueOf(item.basis()),
                    item.reason(), order++));
        }
        request.complete(result.candidateCount(),
                BigDecimal.valueOf(result.cfWeight()).setScale(2, RoundingMode.HALF_UP),
                OffsetDateTime.now(ZoneOffset.UTC));
        if (meta.size() >= META_LIMIT) {
            meta.clear();
        }
        meta.put(request.getId(), new RequestMeta(result.degraded(), result.notice()));
    }

    @Transactional
    public void markFailed(Long requestId) {
        requestRepository.findById(requestId)
                .ifPresent(request -> request.fail(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Transactional(readOnly = true)
    public RecommendationResultResponse get(Long requestId, Long userId) {
        RecommendationRequest request = requestRepository.findById(requestId).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND", "추천 요청을 찾을 수 없습니다"));
        groupAccess.requireMember(request.getGroupId(), userId);

        List<RecommendationItemResponse> items = new ArrayList<>();
        for (Recommendation recommendation : recommendationRepository
                .findByRequestIdOrderByDisplayOrderAsc(requestId)) {
            Place place = placeRepository.findById(recommendation.getPlaceId()).orElse(null);
            if (place == null) {
                continue;
            }
            items.add(new RecommendationItemResponse(recommendation.getId(), place.getId(), place.getName(),
                    place.getCategory(), place.getPriceBand() == null ? null : place.getPriceBand().intValue(),
                    place.getLatitude(), place.getLongitude(),
                    recommendation.getMatchedTags() == null ? List.of() : List.of(recommendation.getMatchedTags()),
                    recommendation.getBasis(), recommendation.getReason(),
                    recommendation.getDisplayOrder().intValue()));
        }
        RequestMeta requestMeta = meta.getOrDefault(requestId, new RequestMeta(false, null));
        return new RecommendationResultResponse(request.getId(), request.getQuery(),
                new RecommendationIntentResponse(request.getIntentRegion(),
                        request.getIntentCount() == null ? null : request.getIntentCount().intValue(),
                        request.getIntentBudget() == null ? null : request.getIntentBudget().intValue()),
                request.getCandidateCount(), request.getCfWeight(), request.getStatus(),
                requestMeta.degraded(), requestMeta.notice(), items);
    }

    private record RequestMeta(boolean degraded, String notice) {
    }
}
