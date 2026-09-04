# 시드 전용 일회성 이미지입니다. 빌드 컨텍스트는 저장소 루트입니다.
# generate_seed.py 가 저장소 루트 기준으로 matching-table.json 을 찾기 때문에
# 컨테이너 안에서도 같은 경로 구조를 그대로 만들어 둡니다.
FROM python:3.11-slim
WORKDIR /repo

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# batch_similarity.py 는 DB 접근을 psql CLI 로만 하므로 postgresql-client 가 필요합니다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client \
    && rm -rf /var/lib/apt/lists/* \
    && pip install --no-cache-dir numpy

COPY scripts/seed/generate_seed.py scripts/seed/load_seed.py scripts/seed/batch_similarity.py /repo/scripts/seed/
COPY backend/src/main/resources/ai/matching-table.json /repo/backend/src/main/resources/ai/matching-table.json

CMD ["sh", "-c", "python scripts/seed/generate_seed.py && python scripts/seed/load_seed.py && python scripts/seed/batch_similarity.py"]
