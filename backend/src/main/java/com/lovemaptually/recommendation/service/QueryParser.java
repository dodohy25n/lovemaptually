package com.lovemaptually.recommendation.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 질의 해석은 규칙입니다. LLM을 쓰지 않습니다.
 * 지역을 못 읽으면 region이 null이고 컨트롤러가 422로 되묻습니다.
 */
@Component
public class QueryParser {

    private static final Map<String, String> REGIONS = Map.ofEntries(
            Map.entry("인사동", "인사동"), Map.entry("여의도", "여의도"),
            Map.entry("성수동", "성수"), Map.entry("성수", "성수"),
            Map.entry("연남동", "연남"), Map.entry("연남", "연남"),
            Map.entry("홍대", "홍대"), Map.entry("종로", "종로"),
            Map.entry("강남", "강남"), Map.entry("이태원", "이태원"),
            Map.entry("서촌", "서촌"), Map.entry("북촌", "북촌")
    );
    private static final List<String> KOREAN_NUMBERS =
            List.of("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열");
    private static final Pattern DIGIT_COUNT = Pattern.compile("([0-9]+)\\s*(곳|개|군데)");
    private static final Pattern MONEY = Pattern.compile("([0-9]+)\\s*만\\s*원");

    public Intent parse(String query) {
        String text = query == null ? "" : query.trim();
        return new Intent(regionOf(text), countOf(text), budgetOf(text));
    }

    private String regionOf(String text) {
        String found = null;
        int at = Integer.MAX_VALUE;
        for (Map.Entry<String, String> entry : REGIONS.entrySet()) {
            int index = text.indexOf(entry.getKey());
            if (index >= 0 && index < at) {
                at = index;
                found = entry.getValue();
            }
        }
        return found;
    }

    private int countOf(String text) {
        Matcher matcher = DIGIT_COUNT.matcher(text);
        if (matcher.find()) {
            return clamp(Integer.parseInt(matcher.group(1)));
        }
        for (int index = 0; index < KOREAN_NUMBERS.size(); index++) {
            String pattern = KOREAN_NUMBERS.get(index);
            if (text.contains(pattern + " 곳") || text.contains(pattern + "곳")
                    || text.contains(pattern + " 개") || text.contains(pattern + "개")) {
                return clamp(index + 1);
            }
        }
        return 3;
    }

    private Integer budgetOf(String text) {
        Matcher matcher = MONEY.matcher(text);
        if (matcher.find()) {
            int amount = Integer.parseInt(matcher.group(1));
            if (amount < 1) {
                return 1;
            }
            if (amount <= 3) {
                return 2;
            }
            return amount <= 5 ? 3 : 4;
        }
        if (text.contains("저렴") || text.contains("가성비") || text.contains("싼")) {
            return 1;
        }
        if (text.contains("적당")) {
            return 2;
        }
        if (text.contains("비싼") || text.contains("고급") || text.contains("근사")) {
            return 4;
        }
        return null;
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(10, value));
    }

    public record Intent(String region, int count, Integer budget) {
    }
}
