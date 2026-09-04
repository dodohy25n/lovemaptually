FROM python:3.11-slim
WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# README 에 적힌 실행 의존성만 넣습니다. 테스트용 pytest, httpx 는 이미지에 두지 않습니다.
RUN pip install --no-cache-dir fastapi uvicorn "psycopg[binary]" numpy

COPY app.py scoring.py query_parser.py ./

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
