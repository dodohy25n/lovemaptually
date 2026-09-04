# Docker Compose 로 전체 스택 띄우기

아키텍처 그림에 있는 Docker Compose 상자와 nginx 상자를 실제로 만든 문서입니다. 저장소 루트의
`compose.yml` 하나로 DB, 백엔드, 추천 엔진, 프론트가 전부 컨테이너로 뜹니다. 로컬에 PostgreSQL 이나
JDK, Node, uv 가 깔려 있지 않아도 Docker 만 있으면 됩니다.

## 한 줄로 띄우기

저장소 루트에서 실행합니다.

```bash
docker compose up -d --build
```

처음 실행하면 Gradle 이 백엔드 jar 를 만들고 npm 이 프론트를 빌드하기 때문에 몇 분 걸립니다.
두 번째부터는 레이어 캐시가 남아 훨씬 빠릅니다.

내릴 때는 다음과 같이 합니다.

```bash
docker compose down       # 컨테이너만 정리하고 DB 데이터는 남깁니다
docker compose down -v    # 이름 붙은 볼륨까지 지워 DB 를 완전히 비웁니다
```

## 서비스 구성

| 서비스 | 이미지 | 하는 일 |
| --- | --- | --- |
| `db` | `postgres:17-alpine` | 애플리케이션 데이터베이스입니다. 계정과 DB 이름은 모두 `lovemaptually` 이고, 데이터는 이름 붙은 볼륨 `db-data` 에 남습니다. |
| `backend` | 직접 빌드 (`backend/Dockerfile`) | Spring Boot API 입니다. Gradle 로 jar 를 만드는 단계와 JRE 만 있는 실행 단계를 나눈 멀티 스테이지 빌드라 최종 이미지가 가볍습니다. 기동할 때 Flyway 가 스키마를 만듭니다. |
| `recommender` | 직접 빌드 (`recommender/Dockerfile`) | FastAPI 추천 엔진입니다. Python 3.11 slim 위에 `fastapi`, `uvicorn`, `psycopg[binary]`, `numpy` 만 올렸습니다. |
| `frontend` | 직접 빌드 (`frontend/Dockerfile`) | Vite 로 빌드한 정적 파일을 `nginx:alpine` 이 서빙합니다. 개발 서버가 아니라 실제 정적 서버라서, 아키텍처 그림의 nginx 상자와 같은 구성입니다. |
| `seed` | 직접 빌드 (`scripts/seed/Dockerfile`) | 데모 데이터를 넣는 일회성 컨테이너입니다. `seed` 프로필에 묶여 있어 평소에는 뜨지 않습니다. |

각 서비스에는 헬스체크가 걸려 있습니다. `db` 는 `pg_isready`, `backend` 는 `/v3/api-docs`,
`recommender` 는 `/health`, `frontend` 는 루트 경로를 확인합니다. `backend` 와 `recommender` 는
`db` 가 healthy 가 된 뒤에야 기동합니다.

## 포트 배치

로컬 개발 스택(`scripts/demo-up.sh`)이 이미 떠 있는 상태에서도 그대로 같이 띄울 수 있게, 공개 포트를
전부 한 칸씩 밀어 두었습니다. 컨테이너 안쪽 포트는 원래 값 그대로입니다.

| 서비스 | 로컬 스택 | Compose 스택 | 컨테이너 내부 |
| --- | --- | --- | --- |
| PostgreSQL | 5432 | **5433** | 5432 |
| 백엔드 | 8080 | **8081** | 8080 |
| 추천 엔진 | 8000 | **8001** | 8000 |
| 프론트 | 5173 | **5174** | 80 |

포트를 밀어 둔 이유는 두 가지입니다. 첫째, 발표 중에 로컬 데모가 돌고 있어도 컨테이너 스택이 그 포트를
빼앗지 않습니다. 둘째, 로컬 PostgreSQL 의 `lovemaptually` 데이터베이스와 컨테이너 안의 데이터베이스가
완전히 분리되기 때문에, 컨테이너 쪽에서 무슨 짓을 해도 로컬 데이터는 그대로입니다.

서비스끼리는 공개 포트가 아니라 Compose 네트워크의 서비스 이름으로 통신합니다. 백엔드는
`jdbc:postgresql://db:5432/lovemaptually` 와 `http://recommender:8000` 을 봅니다.

한 가지 주의할 점이 있습니다. 프론트는 **브라우저에서** 백엔드를 부르기 때문에 서비스 이름이 아니라
호스트 주소가 필요합니다. 그래서 `VITE_API_BASE_URL` 은 `http://localhost:8081` 이고, 이에 맞춰
백엔드의 `CORS_ALLOWED_ORIGINS` 도 `http://localhost:5174` 로 맞춰 두었습니다. 둘 중 하나만 바꾸면
브라우저 요청이 preflight 에서 전부 막히므로 항상 같이 바꿔야 합니다.

## 시드 넣기

컨테이너 DB 는 처음에 비어 있습니다. Flyway 가 스키마와 태그 기본값까지는 만들지만 장소, 사용자,
리뷰는 없습니다. 데모 데이터는 `seed` 프로필로 넣습니다.

```bash
docker compose --profile seed run --rm seed
```

이 컨테이너는 세 단계를 차례로 돕니다.

1. `generate_seed.py` 로 고정 시드 데이터를 만듭니다. 장소 50곳, 사용자 26명, 커플 13팀, 리뷰 260건입니다.
2. `load_seed.py` 로 `http://backend:8080` 의 HTTP API 를 통해 밀어 넣습니다. 회원가입과 그룹 생성부터
   실제 API 를 그대로 타기 때문에 백엔드의 태그 추출까지 함께 검증됩니다.
3. `batch_similarity.py` 로 장소 유사도 배치를 돌려 `place_similarity` 를 채웁니다.

`--profile seed` 를 붙이지 않으면 이 서비스는 뜨지 않으므로, 평소 `docker compose up` 이 데이터를
건드릴 일은 없습니다. 시드가 끝나면 다음 계정으로 로그인할 수 있습니다.

```
이메일   dohyeon@lovemap.dev
비밀번호 demo1234!
```

시드를 처음부터 다시 넣고 싶으면 `docker compose down -v` 로 볼륨을 지우고 다시 올린 뒤 시드를
실행하는 편이 깔끔합니다.

## OPENAI_API_KEY 넘기기

키 없이도 스택은 정상적으로 뜹니다. 기본값이 `AI_CLIENT=matching`(매칭 테이블 기반 태그 추출)과
`REPORT_WRITER=template`(템플릿 리포트)이라 외부 호출 없이 동작하기 때문입니다.

실제 LLM 을 쓰려면 호스트 환경변수로 넘깁니다. `compose.yml` 이 `${OPENAI_API_KEY:-}` 로 받고 있어서
셸에 export 만 해 두면 그대로 전달됩니다.

```bash
export OPENAI_API_KEY=sk-...
export REPORT_WRITER=openai
docker compose up -d
```

또는 저장소 루트에 `.env` 파일을 두어도 Compose 가 자동으로 읽습니다. 이 파일은 `.gitignore` 에
걸려 있으니 커밋될 걱정은 없습니다.

```
OPENAI_API_KEY=sk-...
REPORT_WRITER=openai
```

키는 이미지 안에 굽지 않고 항상 런타임 환경변수로만 넘깁니다. 반대로 `VITE_` 로 시작하는 값들은 Vite 가
빌드 시점에 번들에 그대로 박아 넣기 때문에 런타임 환경변수로 바꿀 수 없고, `compose.yml` 의 빌드 인자로
넘어갑니다. 값을 바꿨다면 `docker compose up -d --build` 로 프론트를 다시 빌드해야 반영됩니다.

## scripts/demo-up.sh 와 무엇이 다른가

두 방식은 없애고 대체하는 관계가 아니라 용도가 다릅니다.

| | `scripts/demo-up.sh` | `docker compose up` |
| --- | --- | --- |
| 대상 | 개발 중인 본인 장비 | 처음 받아 보는 사람, 발표, 배포 리허설 |
| DB | 호스트에 설치된 PostgreSQL (Homebrew) | 컨테이너 안의 `postgres:17-alpine` |
| 백엔드 | `./gradlew bootRun` | 미리 빌드한 jar 를 JRE 이미지에서 실행 |
| 추천 엔진 | `uv run uvicorn` | 컨테이너의 uvicorn |
| 프론트 | `npm run dev` (Vite 개발 서버, HMR 있음) | 빌드된 정적 파일을 nginx 가 서빙 |
| 사전 준비 | JDK 21, Node, uv, PostgreSQL 을 각각 설치 | Docker 하나 |
| 기동 속도 | 빠릅니다 | 첫 빌드는 몇 분 걸리고 이후는 빠릅니다 |
| 코드 수정 반영 | 즉시 (HMR, devtools) | 이미지를 다시 빌드해야 합니다 |

정리하면 **평소 개발은 `scripts/demo-up.sh` 가 여전히 빠른 길**이고, **환경에 상관없이 같은 결과를
보여야 할 때는 Compose** 를 씁니다. 포트가 겹치지 않으므로 둘을 동시에 띄워 비교해도 됩니다.

## 문제가 생겼을 때

```bash
docker compose ps                  # 각 서비스의 상태와 헬스체크 결과
docker compose logs -f backend     # 특정 서비스 로그 따라가기
docker compose config              # 최종 해석된 설정 확인
```

백엔드가 계속 재시작한다면 대개 스키마 문제입니다. `ddl-auto` 가 `validate` 라서 엔티티와 실제 테이블이
어긋나면 기동에 실패합니다. 이때는 `docker compose down -v` 로 볼륨을 비우고 다시 올려 Flyway 가
처음부터 스키마를 만들게 하는 편이 빠릅니다.
