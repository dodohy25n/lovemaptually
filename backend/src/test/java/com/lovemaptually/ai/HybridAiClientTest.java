package com.lovemaptually.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 매칭표는 진짜 구현을 쓰고 LLM 자리만 스텁으로 바꿉니다. 네트워크도 스프링 컨텍스트도 쓰지 않습니다.
 */
class HybridAiClientTest {

    private final MatchingTableAiClient matching = new MatchingTableAiClient(new ObjectMapper());

    private static final List<TagDefinition> DICTIONARY = List.of(
            new TagDefinition("조용함", "조용한", "시끄러운"),
            new TagDefinition("좌석", "편한 좌석", "불편한 좌석"),
            new TagDefinition("청결", "깨끗한", "지저분한"),
            new TagDefinition("넓이", "넓은", "좁은"),
            new TagDefinition("조명", "밝은", "어두운"),
            new TagDefinition("응대", "친절한", "불친절한"),
            new TagDefinition("가격", "비싼", "저렴한"),
            new TagDefinition("웨이팅", "긴 웨이팅", "짧은 웨이팅"));

    @Test
    void 매칭표가_문장을_다_먹으면_LLM을_부르지_않습니다() {
        StubAiClient stub = new StubAiClient(List.of(
                new TagCandidate("응대", "친절한", null, "직원", TagCandidate.SOURCE_LLM)));
        HybridAiClient hybrid = new HybridAiClient(matching, stub);

        List<TagCandidate> tags = hybrid.extractTags("조용", DICTIONARY);

        assertThat(stub.calls).isZero();
        assertThat(tags).extracting(TagCandidate::tagName).containsExactly("조용함");
        assertThat(tags).allMatch(tag -> TagCandidate.SOURCE_MATCHING.equals(tag.source()));
    }

    @Test
    void 남은_문장이_있으면_LLM_결과가_합쳐집니다() {
        StubAiClient stub = new StubAiClient(List.of(
                new TagCandidate("응대", "친절한", "친절한", "직원분이 메뉴를 하나하나 설명해", TagCandidate.SOURCE_LLM)));
        HybridAiClient hybrid = new HybridAiClient(matching, stub);

        List<TagCandidate> tags = hybrid.extractTags(
                "조용해서 좋았고 직원분이 메뉴를 하나하나 설명해 주셔서 편했어요", DICTIONARY);

        assertThat(stub.calls).isEqualTo(1);
        assertThat(tags).extracting(TagCandidate::tagName).contains("조용함", "응대");
        assertThat(stub.lastContent).doesNotContain("조용");
        // 이미 확정된 태그는 사전에서 빼고 물어봐야 모델이 같은 답을 다시 내놓지 않습니다.
        assertThat(stub.lastDictionary).extracting(TagDefinition::name).doesNotContain("조용함");
    }

    @Test
    void 같은_태그가_겹치면_매칭표가_이깁니다() {
        StubAiClient stub = new StubAiClient(List.of(
                new TagCandidate("조용함", "시끄러운", "시끄러운", "떠들썩", TagCandidate.SOURCE_LLM)));
        HybridAiClient hybrid = new HybridAiClient(matching, stub);

        List<TagCandidate> tags = hybrid.extractTags(
                "조용해서 좋았고 직원분이 메뉴를 하나하나 설명해 주셔서 편했어요", DICTIONARY);

        TagCandidate quiet = tags.stream().filter(tag -> "조용함".equals(tag.tagName())).findFirst().orElseThrow();
        assertThat(quiet.factLabel()).isEqualTo("조용한");
        assertThat(quiet.source()).isEqualTo(TagCandidate.SOURCE_MATCHING);
    }

    @Test
    void LLM이_죽어도_매칭표_결과는_남고_예외가_새지_않습니다() {
        StubAiClient stub = new StubAiClient(new AiExtractionException("모델이 응답하지 않습니다"));
        HybridAiClient hybrid = new HybridAiClient(matching, stub);

        List<TagCandidate> tags = hybrid.extractTags(
                "조용해서 좋았고 직원분이 메뉴를 하나하나 설명해 주셔서 편했어요", DICTIONARY);

        assertThat(stub.calls).isEqualTo(1);
        assertThat(tags).extracting(TagCandidate::tagName).containsExactly("조용함");
    }

    @Test
    void 합친_결과는_다섯_개를_넘지_않습니다() {
        List<TagCandidate> many = new ArrayList<>();
        for (TagDefinition definition : DICTIONARY) {
            many.add(new TagCandidate(definition.name(), definition.highLabel(), null, "조각",
                    TagCandidate.SOURCE_LLM));
        }
        StubAiClient stub = new StubAiClient(many);
        HybridAiClient hybrid = new HybridAiClient(matching, stub);

        List<TagCandidate> tags = hybrid.extractTags(
                "조용해서 좋았고 직원분이 메뉴를 하나하나 설명해 주셔서 정말 편했어요", DICTIONARY);

        assertThat(tags).hasSizeLessThanOrEqualTo(5);
        assertThat(tags).extracting(TagCandidate::tagName).doesNotHaveDuplicates();
    }

    private static final class StubAiClient implements AiClient {

        private final List<TagCandidate> answer;
        private final RuntimeException failure;
        private int calls;
        private String lastContent;
        private List<TagDefinition> lastDictionary = List.of();

        private StubAiClient(List<TagCandidate> answer) {
            this.answer = answer;
            this.failure = null;
        }

        private StubAiClient(RuntimeException failure) {
            this.answer = List.of();
            this.failure = failure;
        }

        @Override
        public List<TagCandidate> extractTags(String content, List<TagDefinition> dictionary) {
            calls++;
            lastContent = content;
            lastDictionary = dictionary;
            if (failure != null) {
                throw failure;
            }
            return answer;
        }

        @Override
        public String name() {
            return "stub";
        }
    }
}
