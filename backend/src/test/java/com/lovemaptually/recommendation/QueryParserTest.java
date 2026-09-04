package com.lovemaptually.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.lovemaptually.recommendation.service.QueryParser;
import com.lovemaptually.recommendation.service.QueryParser.Intent;
import org.junit.jupiter.api.Test;

/**
 * 질의 해석은 규칙이라 LLM 없이 결정적으로 검증됩니다. 스프링 컨텍스트도 필요 없습니다.
 */
class QueryParserTest {

    private final QueryParser queryParser = new QueryParser();

    @Test
    void readsRegionAndCountFromAFullSentence() {
        Intent intent = queryParser.parse("오늘 인사동 갈 건데 3곳 정도 추천해줘");

        assertThat(intent.region()).isEqualTo("인사동");
        assertThat(intent.count()).isEqualTo(3);
        assertThat(intent.budget()).isNull();
    }

    @Test
    void readsKoreanNumeralCountWithoutARegion() {
        Intent intent = queryParser.parse("세 곳 추천해줘");

        assertThat(intent.region()).isNull();
        assertThat(intent.count()).isEqualTo(3);
    }

    @Test
    void readsRegionCountAndBudgetTogether() {
        Intent intent = queryParser.parse("여의도에서 저렴한 데 다섯 곳");

        assertThat(intent.region()).isEqualTo("여의도");
        assertThat(intent.count()).isEqualTo(5);
        assertThat(intent.budget()).isEqualTo(1);
    }

    @Test
    void normalisesRegionAliasToItsCanonicalName() {
        assertThat(queryParser.parse("성수동").region()).isEqualTo("성수");
    }
}
