FROM python:3.10

WORKDIR /export

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    libx11-6 \
 && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir \
    torch torchvision torchaudio \
      -f https://download.pytorch.org/whl/cpu \
    ultralytics \
    onnx

COPY best.pt .

CMD ["python", "-c", "\
from ultralytics import YOLO; \
print('Loading model...'); \
m = YOLO('best.pt'); \
print('Exporting ONNX...'); \
m.export(format='onnx'); \
print('✓ Export complete: best.onnx'); \
"]
