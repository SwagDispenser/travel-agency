#!/usr/bin/env python3
"""Smoke-test both packaged services with isolated in-memory databases.

Run after: bash scripts/mvn17.sh clean verify
No Docker, RabbitMQ or Keycloak required. Existing app processes/data are untouched.
"""
import json
import os
import platform
import socket
import subprocess
import tempfile
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]


def free_port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def request(port, path):
    try:
        with urlopen(f"http://localhost:{port}{path}", timeout=3) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.load(error)


def main():
    java_home = os.environ.get("JAVA_HOME")
    if not java_home and platform.system() == "Darwin":
        java_home = subprocess.check_output(
            ["/usr/libexec/java_home", "-v", "17"], text=True).strip()
    java = str(Path(java_home) / "bin/java") if java_home else "java"
    processes = []
    logs = []

    with tempfile.TemporaryDirectory(prefix="travel-lab9-") as work:
        def start(service, port, *properties):
            jar = ROOT / service / "target" / f"{service}-0.0.1-SNAPSHOT.jar"
            if not jar.exists():
                raise RuntimeError("Build first: bash scripts/mvn17.sh clean verify")
            log = Path(work) / f"{len(logs)}-{service}.log"
            logs.append(log)
            with log.open("w") as output:
                process = subprocess.Popen(
                    [java, "-jar", str(jar), f"--server.port={port}",
                     f"--spring.datasource.url=jdbc:h2:mem:lab9;DB_CLOSE_DELAY=-1",
                     "--travel.messaging.enabled=false", *properties],
                    cwd=work, stdout=output, stderr=subprocess.STDOUT)
            processes.append(process)
            return process

        def stop(process):
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)

        def ready(process, port, path):
            deadline = time.monotonic() + 40
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Service exited before becoming ready")
                try:
                    if request(port, path)[0] == 200:
                        return
                except (URLError, ConnectionError, TimeoutError):
                    pass
                time.sleep(0.2)
            raise RuntimeError("Service startup timed out")

        try:
            tour_port, grpc_port, booking_port = free_port(), free_port(), free_port()
            tour = start("tour-service", tour_port, "--spring.profiles.active=docker",
                         "--spring.quartz.auto-startup=false",
                         "--travel.catalog.grpc.enabled=true",
                         f"--travel.catalog.grpc.port={grpc_port}")
            ready(tour, tour_port, "/api/catalog/tours/1/price")

            baseline = None
            for protocol in ("http", "grpc"):
                booking = start("booking-service", booking_port,
                                f"--travel.tour-client.protocol={protocol}",
                                f"--travel.tour-client.rest-url=http://localhost:{tour_port}",
                                "--travel.tour-client.grpc-host=localhost",
                                f"--travel.tour-client.grpc-port={grpc_port}")
                ready(booking, booking_port, "/api/bookings")
                responses = {}
                for action in ("estimate?passengers=2", "availability?passengers=2", "itinerary"):
                    status, body = request(booking_port, f"/api/bookings/planning/1/{action}")
                    assert status == 200, (protocol, action, status, body)
                    responses[action] = body
                assert responses["estimate?passengers=2"]["total"] == 440
                assert responses["availability?passengers=2"]["available"] is True
                assert responses["itinerary"]["city"] == "Krakow"
                for action in ("estimate", "availability", "itinerary"):
                    assert request(booking_port, f"/api/bookings/planning/999999/{action}")[0] == 404
                assert request(booking_port, "/api/bookings/planning/1/estimate?passengers=0")[0] == 400
                if baseline is None:
                    baseline = responses
                else:
                    assert responses == baseline, (responses, baseline)
                print(f"{protocol}: all three actions, 404 and 400 passed", flush=True)
                stop(booking)

            stop(tour)
            for protocol in ("http", "grpc"):
                booking = start("booking-service", booking_port,
                                f"--travel.tour-client.protocol={protocol}",
                                f"--travel.tour-client.rest-url=http://localhost:{tour_port}",
                                "--travel.tour-client.grpc-host=localhost",
                                f"--travel.tour-client.grpc-port={grpc_port}")
                ready(booking, booking_port, "/api/bookings")
                for action in ("estimate", "availability", "itinerary"):
                    started = time.monotonic()
                    status, _ = request(booking_port, f"/api/bookings/planning/1/{action}")
                    assert status == 503, (protocol, action, status)
                    assert time.monotonic() - started < 3
                print(f"{protocol}: stopped provider returns bounded 503 for all actions", flush=True)
                stop(booking)
            print("PASS: HTTP and gRPC return identical business results; failures are isolated.", flush=True)
        except Exception:
            for log in logs:
                print(f"--- {log.name} ---")
                print("\n".join(log.read_text().splitlines()[-25:]))
            raise
        finally:
            for process in reversed(processes):
                stop(process)


if __name__ == "__main__":
    main()
