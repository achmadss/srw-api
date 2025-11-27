#!/usr/bin/env python3
import json
import os
import pika
import random
import time
from typing import List, Dict
from config_loader import get_trash_types_config, TrashTypesConfig

RABBITMQ_URL = os.getenv("RABBITMQ_URL", "amqp://admin:admin@rabbitmq:5672")
ML_QUEUE = "ml_processing_queue"
RESULTS_QUEUE = "ml_results_queue"
ACK_QUEUE = "ml_job_ack_queue"

def process_image(image_id: str, image_url: str, trash_config: TrashTypesConfig) -> Dict:
    """Simulate ML processing for a single image"""
    print(f"Processing image {image_id}: {image_url}")

    # Random processing time: 2-5 seconds
    processing_time = random.uniform(2, 5)
    time.sleep(processing_time)

    # 10% chance of failure for testing
    if random.random() < 0.1:
        print(f"  ❌ Failed to process image {image_id}")
        return {
            "imageId": image_id,
            "success": False,
            "error": "Simulated ML processing failure"
        }

    # Get valid trash types from config
    trash_types = trash_config.get_trash_types()

    # Generate random trash items (1-3 types)
    num_types = random.randint(1, 3)
    selected_types = random.sample(trash_types, num_types)

    trash_items = [
        {
            "type": trash_type,
            "amount": random.randint(1, 10)
        }
        for trash_type in selected_types
    ]

    # Map ML outputs to trash types using the configuration
    # TODO: Add more ML model outputs to mlMappings in trash-types.json as you discover them
    mapped_items = trash_config.map_trash_items(trash_items)

    print(f"  ✓ Successfully processed image {image_id}: {mapped_items}")
    return {
        "imageId": image_id,
        "success": True,
        "trash": mapped_items
    }

def process_ml_job(job_data: Dict, trash_config: TrashTypesConfig) -> Dict:
    """Process an ML job"""
    submission_id = job_data["submissionId"]
    images = job_data["images"]

    print(f"\n{'='*60}")
    print(f"📦 Processing ML job for submission {submission_id}")
    print(f"   Total images: {len(images)}")
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
    """RabbitMQ message callback"""
    try:
        # Load trash types configuration
        trash_config = get_trash_types_config()

        job_data = json.loads(body)
        print(f"📨 Received ML job: {job_data['submissionId']}")

        # Send acknowledgment
        ch.basic_publish(
            exchange='',
            routing_key=ACK_QUEUE,
            body=json.dumps({"submissionId": job_data["submissionId"]})
        )
        print(f"✓ Sent ACK for submission {job_data['submissionId']}")

        # Process the job
        result = process_ml_job(job_data, trash_config)

        # Publish results to results queue
        ch.basic_publish(
            exchange='',
            routing_key=RESULTS_QUEUE,
            body=json.dumps(result)
        )

        # Acknowledge message
        ch.basic_ack(delivery_tag=method.delivery_tag)
        print(f"✓ Published results for submission {job_data['submissionId']}")

    except Exception as e:
        print(f"❌ Error processing ML job: {str(e)}")
        import traceback
        traceback.print_exc()
        # Reject and requeue
        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)

def main():
    print("🚀 Starting ML Worker Service...")
    print(f"   RabbitMQ URL: {RABBITMQ_URL}")
    print(f"   Processing Queue: {ML_QUEUE}")
    print(f"   Results Queue: {RESULTS_QUEUE}")

    # Load trash types configuration (fail-fast if invalid)
    print("\n📋 Loading trash types configuration...")
    try:
        trash_config = get_trash_types_config()
        print(f"✓ Configuration loaded: {len(trash_config.get_trash_types())} trash types available")
    except Exception as e:
        print(f"\n❌ FATAL: Failed to load trash types configuration")
        print(f"   {str(e)}")
        print("\nML Worker cannot start without valid configuration. Exiting.")
        return

    # Connect to RabbitMQ with retry logic
    max_retries = 5
    retry_delay = 5

    for attempt in range(1, max_retries + 1):
        try:
            print(f"\n🔌 Connecting to RabbitMQ (attempt {attempt}/{max_retries})...")
            parameters = pika.URLParameters(RABBITMQ_URL)
            connection = pika.BlockingConnection(parameters)
            channel = connection.channel()
            print("✓ Connected to RabbitMQ successfully!")
            break
        except Exception as e:
            print(f"❌ Connection failed: {str(e)}")
            if attempt < max_retries:
                print(f"   Retrying in {retry_delay} seconds...")
                time.sleep(retry_delay)
            else:
                print("❌ Max retries reached. Exiting.")
                return

    # Declare queues
    channel.queue_declare(queue=ML_QUEUE, durable=True)
    channel.queue_declare(queue=RESULTS_QUEUE, durable=True)
    channel.queue_declare(queue=ACK_QUEUE, durable=True)

    # Set QoS - only process one message at a time
    channel.basic_qos(prefetch_count=1)

    # Start consuming
    channel.basic_consume(queue=ML_QUEUE, on_message_callback=callback)

    print("\n" + "="*60)
    print("✓ ML Worker started successfully!")
    print("  Waiting for messages... (Press CTRL+C to exit)")
    print("="*60 + "\n")

    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        print("\n\n👋 Shutting down ML Worker...")
        channel.stop_consuming()
        connection.close()
        print("✓ Shutdown complete")

if __name__ == "__main__":
    main()
