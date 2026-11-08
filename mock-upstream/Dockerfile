FROM python:3-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY server.py .
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8081

ENV PYTHONUNBUFFERED=1

CMD ["python", "server.py"]
