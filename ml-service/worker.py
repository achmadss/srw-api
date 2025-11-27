#!/usr/bin/env python3
import json
import os
import pika
import time
from typing import Dict
import numpy as np
import onnxruntime as ort
import cv2
from PIL import Image
from io import BytesIO
import requests
from collections import Counter

from config_loader import get_trash_types_config, TrashTypesConfig
from mapper import mapper

# --- ENVIRONMENT ---
RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://admin:admin@rabbitmq:5672")
ML_QUEUE = "ml_processing_queue"
RESULTS_QUEUE = "ml_results_queue"
ACK_QUEUE = "ml_job_ack_queue"

MODEL_PATH = "./best.onnx"

print(f"🔍 Loading ONNX model from {MODEL_PATH} ...")
session = ort.InferenceSession(MODEL_PATH, providers=["CPUExecutionProvider"])
input_name = session.get_inputs()[0].name
print("✓ ONNX model loaded successfully\n")


def preprocess(img: Image.Image) -> np.ndarray:
    """Resize + normalize PIL image to YOLO ONNX input format"""
    img = img.convert("RGB")
    img = np.array(img)

    # YOLO expects 640x640 (or model size). Let's detect automatically:
    _, _, h, w = session.get_inputs()[0].shape  # (1,3,640,640)

    img_resized = cv2.resize(img, (w, h))
    img_resized = img_resized.astype(np.float32) / 255.0
    img_resized = img_resized.transpose(2, 0, 1)  # HWC → CHW
    img_resized = np.expand_dims(img_resized, axis=0)  # NHWC → NCHW

    return img_resized


def postprocess(outputs, score_threshold=0.5):
    """Parse YOLOv8 ONNX output into bounding boxes, scores, and class IDs."""
    preds = outputs[0]  # (1, nc+4, num_boxes)

    preds = preds[0].transpose(1, 0)  # (num_boxes, nc+4)

    boxes = preds[:, :4]
    class_scores = preds[:, 4:]

    class_ids = np.argmax(class_scores, axis=1)
    confidences = np.max(class_scores, axis=1)

    results = []
    for i in range(len(boxes)):
        if confidences[i] < score_threshold:
            continue
        results.append({
            "class_name": mapper[str(class_ids[i])],
            "confidence": float(confidences[i]),
        })

    return results


def process_image(image_id: str, image_url: str, trash_config: TrashTypesConfig) -> Dict:
    print(f"Processing image {image_id}: {image_url}")

    try:
        # 1. Download image
        resp = requests.get(image_url, timeout=10)
        resp.raise_for_status()
        img = Image.open(BytesIO(resp.content))

        # 2. Preprocess
        input_tensor = preprocess(img)

        # 3. ONNX inference
        outputs = session.run(None, {input_name: input_tensor})

        # 4. Postprocess
        detections = postprocess(outputs)

        # 5. Count classes
        detected_classes = [d["class_name"] for d in detections]

        counts = Counter(detected_classes)

        trash_items = [
            {"type": cls_name, "amount": count}
            for cls_name, count in counts.items()
        ]

        # Map to your configured trash types
        mapped_items = trash_config.map_trash_items(trash_items)

        print(f"  ✓ ML result for {image_id}: {mapped_items}")

        return {
            "imageId": image_id,
            "success": True,
            "trash": mapped_items
        }

    except Exception as e:
        print(f"  ❌ Failed to process image {image_id}: {str(e)}")
        return {
            "imageId": image_id,
            "success": False,
            "error": str(e)
        }


def process_ml_job(job_data: Dict, trash_config: TrashTypesConfig) -> Dict:
    submission_id = job_data["submissionId"]
    images = job_data["images"]

    print(f"\n{'='*60}")
    print(f"📦 Processing ML job for submission {submission_id}")
    print(f"📸 Total images: {len(images)}")
    print(f"{'='*60}\n")

    results = []
    for idx, image in enumerate(images, 1):
        print(f"[{idx}/{len(images)}] Processing image...")
        result = process_image(image["id"], image["url"], trash_config)
        results.append(result)

    successful = sum(1 for r in results if r["success"])
    failed = len(results) - successful

    print(f"\n{'='*60}")
    print(f"✅ Completed ML job for submission {submission_id}")
    print(f"   Success: {successful} | Failed: {failed}")
    print(f"{'='*60}\n")

    return {
        "submissionId": submission_id,
        "results": results
    }


def callback(ch, method, properties, body):
    try:
        trash_config = get_trash_types_config()
        job_data = json.loads(body)

        print(f"📨 Received ML job: {job_data['submissionId']}")

        ch.basic_publish(
            exchange='',
            routing_key=ACK_QUEUE,
            body=json.dumps({"submissionId": job_data["submissionId"]})
        )
        print(f"✓ Sent ACK for submission {job_data['submissionId']}")

        result = process_ml_job(job_data, trash_config)

        ch.basic_publish(
            exchange='',
            routing_key=RESULTS_QUEUE,
            body=json.dumps(result)
        )
        print(f"✓ Published results for submission {job_data['submissionId']}")

        ch.basic_ack(delivery_tag=method.delivery_tag)

    except Exception as e:
        print(f"❌ Error processing ML job: {str(e)}")
        import traceback
        traceback.print_exc()
        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)


def main():
    print("🚀 Starting ML Worker Service...")
    print(f"RabbitMQ URL: {RABBITMQ_URL}")

    # Load config
    try:
        trash_config = get_trash_types_config()
        print(f"✓ Loaded {len(trash_config.get_trash_types())} trash types")
    except Exception as e:
        print(f"❌ FATAL: Could not load trash types configuration: {str(e)}")
        return

    # RabbitMQ connection loop
    max_retries = 5
    retry_delay = 5
    for attempt in range(1, max_retries + 1):
        try:
            print(f"\n🔌 Connecting to RabbitMQ ({attempt}/{max_retries})...")
            parameters = pika.URLParameters(RABBITMQ_URL)
            connection = pika.BlockingConnection(parameters)
            channel = connection.channel()
            break
        except Exception as e:
            print(f"❌ Connection failed: {str(e)}")
            if attempt < max_retries:
                time.sleep(retry_delay)
            else:
                return

    channel.queue_declare(queue=ML_QUEUE, durable=True)
    channel.queue_declare(queue=RESULTS_QUEUE, durable=True)
    channel.queue_declare(queue=ACK_QUEUE, durable=True)

    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(queue=ML_QUEUE, on_message_callback=callback)

    print("\n✓ ML Worker started! Waiting for messages...\n")

    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        try:
            channel.stop_consuming()
            connection.close()
        except:
            pass


if __name__ == "__main__":
    main()
