FROM python:3.11-slim

WORKDIR /app

# Install system deps for audio/image processing
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Default: run all services via uvicorn
EXPOSE 8002
CMD ["uvicorn", "agent_dialog.main:app", "--host", "0.0.0.0", "--port", "8002"]
