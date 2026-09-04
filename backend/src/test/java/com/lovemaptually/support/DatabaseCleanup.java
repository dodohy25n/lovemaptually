package com.lovemaptually.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테스트 사이에 도메인 테이블을 비웁니다.
 * 그룹과 장소가 users를 FK로 잡고 있어 userRepository.deleteAll()로는 지워지지 않습니다.
 * tags는 Flyway가 심은 33행이라 건드리지 않습니다.
 */
public final class DatabaseCleanup {

    private static final String TABLES = String.join(", ",
            "unmatched_tag_logs",
            "review_tags",
            "recommendations",
            "recommendation_requests",
            "place_similarity",
            "monthly_reports",
            "subscriptions",
            "reviews",
            "place_tags",
            "user_tags",
            "group_places",
            "invite_codes",
            "group_members",
            "relation_groups",
            "places",
            "users");

    private DatabaseCleanup() {
    }

    public static void clean(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE " + TABLES + " RESTART IDENTITY CASCADE");
    }
}
