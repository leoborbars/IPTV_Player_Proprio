FROM python:3.12-alpine
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY server.py /app/server.py
RUN addgroup -S app && adduser -S -G app app && mkdir -p /data && chown app:app /data
USER app
EXPOSE 8787
CMD ["python", "/app/server.py"]
