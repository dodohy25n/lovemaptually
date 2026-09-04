# Love Map-tually Backend

ERD v3.1 / REST API v3.2가 기준인 Java 21 + Spring Boot Data JPA + PostgreSQL 모놀리식 API입니다.

## 실행

```bash
docker compose up --build
```

서버는 `http://localhost:8080`에서 실행됩니다. Flyway가 최초 실행 시 14개 테이블, enum, 제약, 인덱스, 33개 태그를 생성합니다.

로컬 DB를 따로 실행할 때:

```bash
./gradlew bootRun
```

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_SECONDS`로 설정을 덮어쓸 수 있습니다.

## 구현 범위

- BCrypt 회원가입/로그인, HMAC-SHA256 JWT 인증
- 그룹 생성, 목록, 다회용 초대 코드, 참여
- DB 기반 장소 검색/상세, 그룹 지도/핀
- 리뷰 저장/조회, 규칙 기반 태그 추출, 사용자·장소·그룹 집계 재계산
- 그룹 취향 요약
- 지역/개수/예산 질의 해석, 태그 매칭 + CF 가중치 추천 스냅샷
- API v3.2 공통 성공/오류 응답 봉투

## 패키지 구조

- `controller`: HTTP 요청/응답 처리
- `dto/request`, `dto/response`: API 전용 입출력 모델
- `service`: 트랜잭션과 비즈니스 규칙
- `entity`: ERD 14개 테이블의 JPA 엔티티
- `repository`: Spring Data `JpaRepository`
- `common`, `auth`, `config`: 공통 응답·인증·설정

기본 CRUD는 JPA Repository를 사용합니다. 사용자/장소 태그 전체 재집계와 추천 후보 랭킹처럼 PostgreSQL의 `FILTER`, `DISTINCT ON`이 필요한 연산만 서비스 계층의 native SQL로 유지했습니다.

API 명세의 외부 지도 API와 LLM은 키 없이도 시연할 수 있게 각각 DB 장소 검색과 규칙/템플릿 기반 구현으로 대체했습니다. 운영 연동 시 `PlaceController` 검색 경계와 `ReviewService`/`RecommendationService`의 추출·이유 생성 경계를 외부 클라이언트로 교체하면 됩니다.
