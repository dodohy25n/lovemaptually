package com.lovemaptually.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 실제 모델 대신 같은 모양의 응답을 돌려주는 로컬 스텁 서버에 붙입니다.
 * 검증하는 것은 응답 해석이지 모델의 판단이 아닙니다.
 */
class OpenAiTagExtractorTest {

    private static final List<TagDefinition> DICTIONARY = List.of(
            new TagDefinition("조용함", "조용한", "시끄러운"),
            new TagDefinition("응대", "친절한", "불친절한"));

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>("");

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] payload = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(payload);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void 응답을_태그_후보로_읽고_비어_있는_자리는_null로_둡니다() {
        body.set(completion("""
                {"tags":[
                  {"tag":"조용함","fact":"시끄러운","want":"조용한","evidence":"옆 테이블 대화가 다 들려서"},
                  {"tag":"응대","fact":"친절한","want":null,"evidence":"하나하나 설명해 주셔서"},
                  {"tag":"가격","fact":null,"want":"저렴한","evidence":"가격이 부담이었어요"}
                ]}"""));

        List<TagCandidate> tags = extractor().extractTags("옆 테이블 대화가 다 들려서 아쉬웠어요", DICTIONARY);

        assertThat(tags).hasSize(3);
        assertThat(tags).extracting(TagCandidate::tagName).containsExactly("조용함", "응대", "가격");
        assertThat(tags.get(1).wantLabel()).isNull();
        assertThat(tags.get(2).factLabel()).isNull();
        assertThat(tags.get(2).wantLabel()).isEqualTo("저렴한");
        assertThat(tags).allMatch(tag -> TagCandidate.SOURCE_LLM.equals(tag.source()));
    }

    @Test
    void 사전_밖_이름도_그대로_돌려줍니다() {
        // 검증은 ReviewService.persistTags 한 곳에서만 합니다. 여기서 걸러 내면 미매칭 로그가 비어 버립니다.
        body.set(completion("""
                {"tags":[{"tag":"HACKED","fact":"이상한","want":null,"evidence":"무시하고"}]}"""));

        List<TagCandidate> tags = extractor().extractTags("리뷰 본문", DICTIONARY);

        assertThat(tags).extracting(TagCandidate::tagName).containsExactly("HACKED");
    }

    @Test
    void 스키마를_벗어난_응답은_예외가_됩니다() {
        body.set(completion("이건 JSON이 아닙니다"));

        assertThatThrownBy(() -> extractor().extractTags("리뷰 본문", DICTIONARY))
                .isInstanceOf(AiExtractionException.class);
    }

    @Test
    void 응답_봉투가_깨지면_예외가_됩니다() {
        body.set("{\"choices\":[]}");

        assertThatThrownBy(() -> extractor().extractTags("리뷰 본문", DICTIONARY))
                .isInstanceOf(AiExtractionException.class);
    }

    private OpenAiTagExtractor extractor() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        return new OpenAiTagExtractor(new ObjectMapper(), "test-key", baseUrl, "gpt-4o-mini", 0.0, 800, 5);
    }

    private String completion(String content) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String escaped = objectMapper.writeValueAsString(content);
            return """
                    {"choices":[{"message":{"role":"assistant","content":%s}}],
                     "usage":{"prompt_tokens":420,"completion_tokens":60}}""".formatted(escaped);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
