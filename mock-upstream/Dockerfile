FROM python:3-alpine

WORKDIR /app

COPY server.py .

EXPOSE 8081

ENV PYTHONUNBUFFERED=1

CMD ["python", "server.py"]
