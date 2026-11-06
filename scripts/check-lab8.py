#!/usr/bin/env python3
"""Run after mvn verify and docker compose --profile apps up -d --build.
Creates labelled demo bookings and temporarily restarts the lab containers.
"""
import base64
import json
import subprocess
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
if not (ROOT / 'compose.yml').exists():
    raise SystemExit('Run this script from the Travel Agency project (compose.yml is required).')
MARKER = 'lab8-check-' + str(uuid.uuid4())[:8]
TOKEN = None

def request(url, payload=None, method='GET', token=None, rabbit=False, expected=200):
    headers = {}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode()
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if rabbit:
        headers['Authorization'] = 'Basic ' + base64.b64encode(b'travel:travel').decode()
    try:
        with urlopen(Request(url, data=data, headers=headers, method=method), timeout=8) as response:
            assert response.status == expected, (response.status, expected)
            body = response.read()
            return json.loads(body) if body else None
    except HTTPError as error:
        if error.code == expected:
            return json.loads(error.read())
        raise

def wait(label, condition, timeout=100):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            result = condition()
            if result:
                return result
        except (HTTPError, URLError, ConnectionError, TimeoutError, AssertionError) as error:
            last = error
        time.sleep(1)
    raise AssertionError(f'Timeout: {label}; last error={last}')

def compose(*args):
    result = subprocess.run(['docker', 'compose', '--profile', 'apps', *args], cwd=ROOT,
                            text=True, capture_output=True, timeout=90)
    if result.returncode:
        raise RuntimeError(result.stderr)

def login():
    data = urlencode({'grant_type': 'password', 'client_id': 'travel-agency-ui',
                      'client_secret': 'travel-agency-secret', 'username': 'admin', 'password': 'admin'}).encode()
    with urlopen(Request('http://localhost:8081/realms/travel-agency/protocol/openid-connect/token', data=data), timeout=5) as response:
        return json.load(response)['access_token']

def receipts():
    return [row for row in request('http://localhost:8080/api/booking-events', token=TOKEN)
            if row['event']['customerName'].startswith(MARKER)]

def create(label):
    return request('http://localhost:8082/api/bookings',
                   {'customerName': MARKER + label, 'tourId': 1, 'seats': 2}, method='POST', expected=201)

def queue():
    return request('http://localhost:15672/api/queues/%2F/travel.tour.booking-events', rabbit=True)

def main():
    global TOKEN
    TOKEN = wait('Keycloak token', login)
    wait('booking API', lambda: request('http://localhost:8082/api/bookings') is not None)
    wait('consumer API', lambda: request('http://localhost:8080/api/booking-events', token=TOKEN) is not None)

    # Three separate actions, one logical event each, and a visible consumer result.
    booking = create('-lifecycle')
    wait('CREATED', lambda: len(receipts()) == 1)
    request(f'http://localhost:8082/api/bookings/{booking["id"]}/confirm', method='POST')
    wait('CONFIRMED', lambda: len(receipts()) == 2)
    request(f'http://localhost:8082/api/bookings/{booking["id"]}/cancel', method='POST')
    wait('CANCELLED', lambda: len(receipts()) == 3)
    assert [row['event']['type'] for row in receipts()] == ['CREATED', 'CONFIRMED', 'CANCELLED']
    print('PASS: three actions -> three distinct persisted events', flush=True)

    request(f'http://localhost:8082/api/bookings/{booking["id"]}/confirm', method='POST', expected=409)
    request('http://localhost:8082/api/bookings', {'customerName': '', 'tourId': 0, 'seats': 0}, method='POST', expected=400)
    time.sleep(2)
    assert len(receipts()) == 3
    print('PASS: failed actions produce no event (rollback also covered by Maven tests)', flush=True)

    # Durable messages must remain available while the receiver is stopped, even after a broker restart.
    compose('stop', 'tour-service')
    offline_ids = {create('-offline-a')['id'], create('-offline-b')['id']}
    wait('two durable queued messages', lambda: queue().get('messages_ready', 0) >= 2)
    compose('restart', 'rabbitmq')
    wait('messages survive broker restart', lambda: queue().get('messages_ready', 0) >= 2)
    compose('start', 'tour-service')
    wait('both offline events consumed', lambda: offline_ids.issubset({row['event']['bookingId'] for row in receipts()}))
    assert len(receipts()) == 5
    print('PASS: producer succeeds with consumer stopped; both messages survive RabbitMQ restart and arrive later', flush=True)

    # Republish exactly the same event; the received_at and row count must not change.
    before = receipts()
    event = before[0]['event']
    reply = request('http://localhost:15672/api/exchanges/%2F/travel.booking.events/publish', {
        'routing_key': 'booking.' + event['type'].lower(),
        'properties': {'delivery_mode': 2, 'content_type': 'application/json', 'message_id': event['eventId']},
        'payload': json.dumps(event), 'payload_encoding': 'string'
    }, method='POST', rabbit=True)
    assert reply['routed']
    time.sleep(3)
    wait('duplicate acknowledged', lambda: queue().get('messages', 0) == 0)
    assert receipts() == before
    print('PASS: real duplicate delivery leaves the persisted result unchanged', flush=True)

    # The outbox also survives loss of broker connectivity and a producer restart.
    compose('stop', 'rabbitmq')
    pending = create('-broker-offline')
    compose('restart', 'booking-service')
    compose('start', 'rabbitmq')
    wait('outbox retried after producer restart', lambda: any(row['event']['bookingId'] == pending['id'] for row in receipts()))
    assert len(receipts()) == 6
    print('PASS: committed outbox survives producer restart while RabbitMQ is unavailable', flush=True)
    print('All broker checks passed. Demo customer prefix: ' + MARKER, flush=True)

if __name__ == '__main__':
    try:
        main()
    finally:
        compose('start', 'rabbitmq', 'booking-service', 'tour-service')
