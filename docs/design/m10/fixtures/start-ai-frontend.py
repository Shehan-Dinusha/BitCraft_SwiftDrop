"""Start the local AI/frontend demo without replacing existing application processes.

Run from the repository root. Requires already-packaged applications, Docker and Node.
Creates a dedicated IAM demo database; existing IAM accounts are left untouched.
Local process IDs/logs are saved under analytics-ai-service/target-ai/frontend-demo/.
"""
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[4]
OUTPUT = ROOT / "backend/analytics-ai-service/target-ai/frontend-demo"
MODULES = {"auth-iam-service": 8081, "pricing-zone-service": 8083,
           "customer-order-service": 8082, "analytics-ai-service": 8089, "api-gateway": 8080}


def main():
    for port in MODULES.values():
        with socket.socket() as sock:
            if sock.connect_ex(("127.0.0.1", port)) == 0:
                raise RuntimeError(f"Port {port} is already in use; keep its application or stop it before running this launcher.")
    frontend_running = False
    with socket.socket() as sock:
        frontend_running = sock.connect_ex(("127.0.0.1", 5173)) == 0
    if frontend_running:
        with urllib.request.urlopen("http://127.0.0.1:5173/@vite/client", timeout=5) as response:
            if response.status != 200:
                raise RuntimeError("Port 5173 is occupied by an application other than the Vite frontend.")
    jars = {}
    for module in MODULES:
        options = [ROOT / "backend" / module / output / f"{module}-0.1.0-SNAPSHOT.jar" for output in ["target-ai-customer", "target-ai", "target"]]
        jars[module] = next((path for path in options if path.is_file()), None)
        if jars[module] is None:
            raise RuntimeError(f"Package {module} first; see the AI connection guide.")
    env = os.environ.copy()
    if (ROOT / ".env").exists():
        for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                name, value = line.split("=", 1)
                env[name.strip()] = value.strip().strip("\"'")
    username = "m10.ai.demo"
    password = "SwiftDropAiDemo123!"
    database = "swiftdrop_iam_ai_demo_" + uuid.uuid4().hex[:8]
    pguser = env.get("POSTGRES_USER", "swiftdrop")
    pgpassword = env.get("POSTGRES_PASSWORD", "changeme_pg")
    docker = subprocess.run(["docker", "compose", "up", "-d", "rabbitmq", "redis", "iam-postgres",
                             "analytics-postgres", "pricing-postgres", "order-postgres"], cwd=ROOT, capture_output=True)
    if docker.returncode:
        raise RuntimeError("Docker dependencies could not be started; inspect docker compose ps.")
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        ready = subprocess.run(["docker", "exec", "swiftdrop-iam-postgres", "pg_isready", "-h", "127.0.0.1", "-U", pguser], capture_output=True)
        if ready.returncode == 0:
            break
        time.sleep(1)
    created = subprocess.run(["docker", "exec", "swiftdrop-iam-postgres", "createdb", "-U", pguser, database], capture_output=True)
    if created.returncode:
        raise RuntimeError("Could not create the separate IAM demo database.")
    virtual_host = "m10-ai-demo-" + uuid.uuid4().hex[:8]
    rabbit_user = env.get("RABBITMQ_DEFAULT_USER", "swiftdrop")
    for command in [["add_vhost", virtual_host], ["set_permissions", "-p", virtual_host, rabbit_user, ".*", ".*", ".*"]]:
        result = subprocess.run(["docker", "exec", "swiftdrop-rabbitmq", "rabbitmqctl", *command], capture_output=True)
        if result.returncode:
            raise RuntimeError("Could not configure the separate RabbitMQ demo virtual host.")
    env.update(AI_PROVIDER="mock", AI_API_KEY="", AI_MODEL="", AI_ENDPOINT="", GATEWAY_URL="http://localhost:8080",
               SPRING_PROFILES_ACTIVE="", JWT_ALGORITHM="HS256", JWT_PRIVATE_KEY="", JWT_PUBLIC_KEY="",
               JWT_SECRET=env.get("JWT_SECRET") or "swiftdrop-local-dev-secret-key-must-be-at-least-256-bits-long!!",
               DB_USERNAME=pguser, DB_PASSWORD=pgpassword,
               RABBITMQ_HOST="localhost", RABBITMQ_PORT="5672",
               RABBITMQ_USERNAME=env.get("RABBITMQ_DEFAULT_USER", "swiftdrop"),
               RABBITMQ_PASSWORD=env.get("RABBITMQ_DEFAULT_PASS", "changeme_rabbit"),
               SPRING_RABBITMQ_VIRTUAL_HOST=virtual_host,
               REDIS_HOST="localhost", REDIS_PORT="6379", REDIS_PASSWORD="",
               AUTH_SERVICE_URL="http://localhost:8081", ORDER_SERVICE_URL="http://localhost:8082",
               PRICING_SERVICE_URL="http://localhost:8083", ANALYTICS_SERVICE_URL="http://localhost:8089",
               BOOTSTRAP_ADMIN_USERNAME=username, BOOTSTRAP_ADMIN_PASSWORD=password,
               BOOTSTRAP_ADMIN_EMAIL="m10.ai.demo@example.test", BOOTSTRAP_ADMIN_DISTRICT="COLOMBO")
    databases = {"auth-iam-service": (5431, database), "pricing-zone-service": (5434, "swiftdrop_pricing"),
                 "customer-order-service": (5433, "swiftdrop_order"), "analytics-ai-service": (5440, "swiftdrop_analytics")}
    OUTPUT.mkdir(parents=True, exist_ok=True)
    processes = []
    records = []
    flags = getattr(subprocess, "CREATE_NO_WINDOW", 0) | getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)
    try:
        for module, port in MODULES.items():
            process_env = env.copy()
            if module in databases:
                dbport, dbname = databases[module]
                process_env["DB_URL"] = f"jdbc:postgresql://localhost:{dbport}/{dbname}"
            with (OUTPUT / f"{module}.log").open("w", encoding="utf-8") as log:
                process = subprocess.Popen(["java", "-Xmx256m", "-jar", str(jars[module]), f"--server.port={port}",
                                            "--logging.level.root=WARN", "--logging.level.com.swiftdrop.gateway=WARN",
                                            "--logging.level.org.springframework.cloud.gateway=WARN"],
                                           cwd=ROOT, env=process_env, stdout=log, stderr=subprocess.STDOUT, creationflags=flags)
            processes.append(process)
            records.append({"service": module, "pid": process.pid, "port": port, "jar": str(jars[module])})
            deadline = time.monotonic() + 120
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError(f"{module} exited; inspect its frontend-demo log.")
                try:
                    with urllib.request.urlopen(f"http://localhost:{port}/actuator/health", timeout=3) as response:
                        if response.status == 200:
                            print(f"READY {module} on {port}", flush=True)
                            break
                except Exception:
                    pass
                time.sleep(1)
            else:
                raise RuntimeError(f"{module} did not become healthy; inspect its frontend-demo log.")
        if not frontend_running:
            with (OUTPUT / "frontend.log").open("w", encoding="utf-8") as log:
                process = subprocess.Popen(["node", "node_modules/vite/bin/vite.js", "--host", "127.0.0.1", "--port", "5173", "--strictPort"],
                                           cwd=ROOT / "frontend", env=env, stdout=log, stderr=subprocess.STDOUT, creationflags=flags)
            processes.append(process)
            records.append({"service": "frontend", "pid": process.pid, "port": 5173})
        else:
            print("REUSED existing Vite frontend on 5173", flush=True)
        (OUTPUT / "session.json").write_text(json.dumps({"processes": records, "iamDatabase": database, "rabbitVirtualHost": virtual_host,
                                                       "username": username, "password": password}, indent=2), encoding="utf-8")
        print("STARTED http://localhost:5173/ops/analytics", flush=True)
        print("Demo username: " + username, flush=True)
        print("Demo password: " + password, flush=True)
        print("Logs and process IDs: " + str(OUTPUT), flush=True)
    except Exception:
        for process in reversed(processes):
            process.terminate()
        raise


if __name__ == "__main__":
    main()
