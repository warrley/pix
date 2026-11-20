#!/usr/bin/env python3
"""Simulador contínuo de tráfego para validar todas as métricas de observabilidade da API PIX."""

from __future__ import annotations

import argparse
import json
import os
import random
import signal
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


DEFAULT_BASE_URL = "http://localhost:3000"
ALICE_EMAIL = "alice.test@example.com"
BOB_EMAIL = "bob.test@example.com"
SEED_PASSWORD = "password123"
TOKEN_REFRESH_SECONDS = 600  # Tokens duram 15 min; renova a cada 10 min

KEY_METRICS_PREFIXES = (
    "http_requests_total",
    "pix_transfers_total",
    "pix_transfer_amount_cents_total",
    "pix_amount_reais_total",
    "pix_keys_operations_total",
    "pix_security_events_total",
    "pix_auth_rate_limited_total",
    "pix_notifications_delivery_total",
    "rails_db_query_duration_seconds_count",
)


class StopRequested(Exception):
    pass


def load_dotenv(dotenv_path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not dotenv_path.is_file():
        return values

    for raw_line in dotenv_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, val = line.split("=", 1)
        values[key.strip()] = val.strip().strip('"').strip("'")
    return values


def request_json(
    base_url: str,
    method: str,
    path: str,
    *,
    token: str | None = None,
    payload: dict | None = None,
    timeout: float = 5.0,
) -> tuple[int, dict | str]:
    url = f"{base_url.rstrip('/')}{path}"
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    headers = {"Accept": "application/json"}
    if payload is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, data=data, headers=headers, method=method.upper())
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return resp.status, parse_json_or_text(body)
    except urllib.error.HTTPError as err:
        body = err.read().decode("utf-8", errors="replace")
        return err.code, parse_json_or_text(body)


def parse_json_or_text(raw: str) -> dict | str:
    if not raw.strip():
        return {}
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw


def login(base_url: str, email: str, password: str) -> tuple[str, int]:
    status, body = request_json(
        base_url,
        "POST",
        "/api/v1/auth/token",
        payload={"email": email, "password": password},
    )
    if status != 201 or not isinstance(body, dict) or not body.get("data"):
        raise RuntimeError(f"Falha ao autenticar {email}: HTTP {status} -> {body}")
    data = body["data"]
    return data["access_token"], int(data["user"]["id"])


def fetch_primary_account_id(base_url: str, token: str, user_id: int) -> int:
    status, body = request_json(base_url, "GET", f"/api/v1/users/{user_id}/accounts", token=token)
    if status != 200 or not isinstance(body, dict) or not body.get("data"):
        raise RuntimeError(f"Falha ao listar contas do usuario {user_id}: HTTP {status} -> {body}")
    return int(body["data"][0]["id"])


def print_metrics_snapshot(base_url: str, metrics_token: str | None) -> None:
    if not metrics_token:
        return
    status, body = request_json(base_url, "GET", "/metrics", token=metrics_token)
    if status != 200 or not isinstance(body, str):
        print(f"  [metrics] falha ao consultar /metrics: HTTP {status}")
        return

    print("  [metrics snapshot]")
    for line in body.splitlines():
        if line.startswith("#") or not line:
            continue
        if line.startswith(KEY_METRICS_PREFIXES):
            print(f"    {line}")


class PixTrafficSimulator:
    def __init__(
        self,
        *,
        base_url: str,
        metrics_token: str | None,
        interval: float,
        trigger_rate_limit: bool,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.metrics_token = metrics_token
        self.interval = interval
        self.trigger_rate_limit = trigger_rate_limit

        self.alice_token = ""
        self.alice_user_id = 0
        self.alice_account_id = 0
        self.bob_token = ""
        self.bob_user_id = 0
        self.bob_account_id = 0
        self.last_auth_at = 0.0
        self.last_rate_limit_at = 0.0
        self.bob_fraud_blocked = False
        self.cycle = 0

    def ensure_sessions(self) -> None:
        now = time.monotonic()
        if self.alice_token and (now - self.last_auth_at) < TOKEN_REFRESH_SECONDS:
            return

        self.alice_token, self.alice_user_id = login(self.base_url, ALICE_EMAIL, SEED_PASSWORD)
        self.alice_account_id = fetch_primary_account_id(self.base_url, self.alice_token, self.alice_user_id)

        self.bob_token, self.bob_user_id = login(self.base_url, BOB_EMAIL, SEED_PASSWORD)
        self.bob_account_id = fetch_primary_account_id(self.base_url, self.bob_token, self.bob_user_id)

        self.last_auth_at = now
        print(
            f"[auth] Sessoes renovadas: Alice(user={self.alice_user_id}, acc={self.alice_account_id}) "
            f"| Bob(user={self.bob_user_id}, acc={self.bob_account_id})"
        )

    def ensure_daily_limits(self) -> None:
        for token, acc_id in ((self.alice_token, self.alice_account_id), (self.bob_token, self.bob_account_id)):
            request_json(
                self.base_url,
                "PATCH",
                f"/api/v1/accounts/{acc_id}/limit",
                token=token,
                payload={"daily_limit": "50000.00"},
            )

    def cleanup(self) -> None:
        if self.bob_fraud_blocked and self.alice_token and self.bob_account_id:
            try:
                request_json(
                    self.base_url,
                    "PATCH",
                    f"/api/v1/accounts/{self.bob_account_id}/unblock_fraud",
                    token=self.alice_token,
                )
                self.bob_fraud_blocked = False
            except Exception:
                pass

    def run_cycle(self) -> None:
        self.cycle += 1
        self.ensure_sessions()
        if self.cycle == 1:
            self.ensure_daily_limits()

        events: list[str] = []

        # 1. Health check e consultas de leitura (HTTP RED + SQL SELECT)
        s_up, _ = request_json(self.base_url, "GET", "/up")
        request_json(self.base_url, "GET", f"/api/v1/accounts/{self.alice_account_id}", token=self.alice_token)
        request_json(self.base_url, "GET", f"/api/v1/accounts/{self.alice_account_id}/limit", token=self.alice_token)
        request_json(
            self.base_url,
            "GET",
            f"/api/v1/pix_keys/lookup?key_value={urllib.parse.quote(BOB_EMAIL)}",
            token=self.alice_token,
        )
        request_json(self.base_url, "GET", f"/api/v1/accounts/{self.alice_account_id}/transfers", token=self.alice_token)
        request_json(
            self.base_url,
            "GET",
            f"/api/v1/accounts/{self.alice_account_id}/reports/statement",
            token=self.alice_token,
        )
        request_json(
            self.base_url,
            "GET",
            f"/api/v1/users/{self.alice_user_id}/reports/transactions",
            token=self.alice_token,
        )
        events.append(f"reads(up={s_up})")

        # 2. Ciclo de vida de Chave PIX (create + cancel + erro de validacao)
        s_key, key_body = request_json(
            self.base_url,
            "POST",
            f"/api/v1/accounts/{self.alice_account_id}/pix_keys",
            token=self.alice_token,
            payload={"pix_key": {"key_type": "random"}},
        )
        if s_key == 201 and isinstance(key_body, dict):
            key_id = key_body["data"]["id"]
            request_json(self.base_url, "DELETE", f"/api/v1/pix_keys/{key_id}", token=self.alice_token)
            events.append(f"pix_key(created+cancelled={key_id})")

        request_json(
            self.base_url,
            "POST",
            f"/api/v1/accounts/{self.alice_account_id}/pix_keys",
            token=self.alice_token,
            payload={"pix_key": {"key_type": "email", "key_value": "email-invalido"}},
        )

        # 3. Transferencia PIX concluida (Alice -> Bob) + consulta de recibo + retorno (Bob -> Alice)
        amount = f"{random.uniform(5.0, 45.0):.2f}"
        s_tx, tx_body = request_json(
            self.base_url,
            "POST",
            "/api/v1/transfers",
            token=self.alice_token,
            payload={
                "transfer": {
                    "source_account_id": self.alice_account_id,
                    "pix_key": BOB_EMAIL,
                    "amount": amount,
                    "description": f"Simulacao ciclo {self.cycle}",
                }
            },
        )
        tx_id = tx_body.get("data", {}).get("id") if isinstance(tx_body, dict) else None
        events.append(f"tx_alice_to_bob(R${amount},http={s_tx})")

        if tx_id:
            request_json(self.base_url, "GET", f"/api/v1/transfers/{tx_id}", token=self.alice_token)

            # A cada 2 ciclos, estorna (refund); nos demais, Bob devolve via nova transferencia para equilibrar saldos
            if self.cycle % 2 == 0:
                s_ref, _ = request_json(
                    self.base_url,
                    "POST",
                    f"/api/v1/transfers/{tx_id}/refund",
                    token=self.alice_token,
                    payload={"reason": "Simulacao de estorno"},
                )
                # Tentativa repetida gera refund_rejected (already_refunded)
                request_json(
                    self.base_url,
                    "POST",
                    f"/api/v1/transfers/{tx_id}/refund",
                    token=self.alice_token,
                    payload={"reason": "Estorno duplicado"},
                )
                events.append(f"refund(http={s_ref})")
            else:
                # Tentativa de cancelamento em transferencia ja concluida -> gera cancel_rejected
                request_json(
                    self.base_url,
                    "POST",
                    f"/api/v1/transfers/{tx_id}/cancel",
                    token=self.alice_token,
                    payload={"reason": "Teste cancelamento"},
                )
                request_json(
                    self.base_url,
                    "POST",
                    "/api/v1/transfers",
                    token=self.bob_token,
                    payload={
                        "transfer": {
                            "source_account_id": self.bob_account_id,
                            "pix_key": ALICE_EMAIL,
                            "amount": amount,
                            "description": f"Retorno ciclo {self.cycle}",
                        }
                    },
                )
                events.append("tx_bob_to_alice+cancel_rejected")

        # 4. Transferencia falha (saldo insuficiente)
        s_fail, _ = request_json(
            self.base_url,
            "POST",
            "/api/v1/transfers",
            token=self.alice_token,
            payload={
                "transfer": {
                    "source_account_id": self.alice_account_id,
                    "pix_key": BOB_EMAIL,
                    "amount": "999999.00",
                }
            },
        )
        events.append(f"tx_insufficient(http={s_fail})")

        # 5. Eventos de Seguranca e Rota Desconhecida (cardinalidade controlada em OTHER)
        request_json(self.base_url, "GET", f"/api/v1/accounts/{self.alice_account_id}", token="token_invalido")
        request_json(self.base_url, "GET", f"/api/v1/accounts/{self.alice_account_id}", token=self.bob_token)
        request_json(
            self.base_url,
            "PATCH",
            f"/api/v1/accounts/{self.alice_account_id}/block_fraud",
            token=self.bob_token,
            payload={"reason": "Teste permissao"},
        )
        request_json(self.base_url, "GET", f"/api/v1/rota-desconhecida-{self.cycle}", token=self.alice_token)
        request_json(self.base_url, "GET", "/metrics", token="metrics_token_invalido")
        events.append("security+other_route")

        # 6. A cada 3 ciclos, simula bloqueio antifraude temporario na conta do Bob + tentativa bloqueada + desbloqueio
        if self.cycle % 3 == 0:
            self.bob_fraud_blocked = True
            try:
                request_json(
                    self.base_url,
                    "PATCH",
                    f"/api/v1/accounts/{self.bob_account_id}/block_fraud",
                    token=self.alice_token,
                    payload={"reason": "Simulacao antifraude"},
                )
                request_json(
                    self.base_url,
                    "POST",
                    "/api/v1/transfers",
                    token=self.alice_token,
                    payload={
                        "transfer": {
                            "source_account_id": self.alice_account_id,
                            "pix_key": BOB_EMAIL,
                            "amount": "10.00",
                        }
                    },
                )
            finally:
                request_json(
                    self.base_url,
                    "PATCH",
                    f"/api/v1/accounts/{self.bob_account_id}/unblock_fraud",
                    token=self.alice_token,
                )
                self.bob_fraud_blocked = False
            events.append("fraud_block_cycle")

        # 7. Rate limit em login_by_identity (dispara no maximo 1x a cada 75s para nao atingir login_by_ip=20/min)
        now = time.monotonic()
        if self.trigger_rate_limit and (self.last_rate_limit_at == 0.0 or (now - self.last_rate_limit_at) >= 75.0):
            probe_email = f"rate_probe_{int(time.time())}@example.com"
            statuses = []
            for _ in range(6):
                code, _ = request_json(
                    self.base_url,
                    "POST",
                    "/api/v1/auth/token",
                    payload={"email": probe_email, "password": "wrong-password"},
                )
                statuses.append(code)
            self.last_rate_limit_at = now
            events.append(f"rate_limit_probe({statuses[-1]})")

        print(f"[ciclo {self.cycle}] " + " | ".join(events))
        print_metrics_snapshot(self.base_url, self.metrics_token)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Simula requisicoes variadas na API PIX para observar metricas em tempo real."
    )
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help=f"URL base da API (padrao: {DEFAULT_BASE_URL})")
    parser.add_argument(
        "--metrics-token",
        default=None,
        help="Token Bearer de /metrics (se omitido, le METRICS_BEARER_TOKEN do ambiente ou do .env)",
    )
    parser.add_argument("--interval", type=float, default=2.0, help="Intervalo em segundos entre ciclos (padrao: 2.0)")
    parser.add_argument("--duration", type=float, default=0.0, help="Duracao total em segundos (0 = infinito ate Ctrl+C)")
    parser.add_argument("--once", action="store_true", help="Executa apenas 1 ciclo e encerra")
    parser.add_argument(
        "--no-rate-limit",
        action="store_true",
        help="Nao dispara burst de 6 tentativas para testar pix_auth_rate_limited_total",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = Path(__file__).resolve().parents[2]
    dotenv = load_dotenv(repo_root / ".env")
    metrics_token = args.metrics_token or os.environ.get("METRICS_BEARER_TOKEN") or dotenv.get("METRICS_BEARER_TOKEN")

    def handle_signal(_signum: int, _frame: object) -> None:
        raise StopRequested()

    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)

    simulator = PixTrafficSimulator(
        base_url=args.base_url,
        metrics_token=metrics_token,
        interval=max(args.interval, 0.2),
        trigger_rate_limit=not args.no_rate_limit,
    )

    print(f"Iniciando simulador de trafego em {args.base_url} (Ctrl+C para encerrar)...")
    if not metrics_token:
        print("Aviso: METRICS_BEARER_TOKEN nao definido; snapshot de /metrics no terminal sera omitido.")

    started_at = time.monotonic()
    try:
        while True:
            simulator.run_cycle()
            if args.once:
                break
            if args.duration > 0 and (time.monotonic() - started_at) >= args.duration:
                break
            time.sleep(simulator.interval)
    except StopRequested:
        print("\nEncerrando simulador...")
    except Exception as exc:
        print(f"\nErro no simulador: {exc}", file=sys.stderr)
        return 1
    finally:
        simulator.cleanup()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
