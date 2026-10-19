"""포인트 선물하기 — 숨긴 인수 테스트 (AI 비공개). 사용: python acc3.py <jar> <port> <out.json>
상태 코드는 '성공=2xx' 만 계약으로 보고, 판정은 잔액으로 한다. 충전은 10만 단위로 나눠서 한다(AI 가 스스로 둔 충전 한도에 걸리지 않게).
분류: 명시(기획서에 분명히 적힌 것) / 모호(기획서 문장이 두 가지로 읽힘) / 누락(기획서에 없음)
"""
import json, os, subprocess, sys, threading, time, uuid, urllib.request, urllib.error

JAR, PORT, OUT = sys.argv[1], int(sys.argv[2]), sys.argv[3]
BASE = f"http://localhost:{PORT}"


def call(method, path, key=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if key:
        req.add_header("Authorization", f"Bearer {key}")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read().decode("utf8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf8", "replace")
    except Exception as e:
        return -1, repr(e)


def find(o, key):
    if isinstance(o, dict):
        if key in o:
            return o[key]
        for v in o.values():
            r = find(v, key)
            if r is not None:
                return r
    elif isinstance(o, list):
        for v in o:
            r = find(v, key)
            if r is not None:
                return r
    return None


def js(raw):
    try:
        return json.loads(raw)
    except Exception:
        return None


ok = lambda st: 200 <= st < 300


def user(tag):
    name = f"{tag}{uuid.uuid4().hex[:8]}"
    call("POST", "/api/v1/members/join", body={"username": name, "password": "1234", "nickname": name})
    st, raw = call("POST", "/api/v1/members/login", body={"username": name, "password": "1234"})
    key = find(js(raw), "apiKey")
    if not key:
        raise RuntimeError(f"로그인 실패 {st}")
    return name, key


def bal(key):
    st, raw = call("GET", "/api/v1/points", key)
    b = find(js(raw), "balance")
    return b if ok(st) and isinstance(b, (int, float)) else None


def charge(key, total):
    while total > 0:
        n = min(100_000, total)
        st, _ = call("POST", "/api/v1/points/charge", key, {"amount": n})
        if not ok(st):
            return False
        total -= n
    return True


def send(key, to, amount):
    return call("POST", "/api/v1/points/transfer", key, {"toUsername": to, "amount": amount})[0]


def pair(tag):
    (sn, sk), (rn, rk) = user(tag + "s"), user(tag + "r")
    return sk, rn, rk


def fee_check(amount, expect_fee):
    sk, rn, rk = pair("f")
    if not charge(sk, amount + 20_000):
        return False, "충전 실패"
    b0 = bal(sk)
    st = send(sk, rn, amount)
    b1, r1 = bal(sk), bal(rk)
    got = None if b0 is None or b1 is None else b0 - b1 - amount
    good = ok(st) and r1 == amount and got == expect_fee
    return good, f"{amount:,}P 선물 → 응답 {st}, 수수료 {got} (기대 {expect_fee}), 받은 사람 {r1}"


def t_T0():
    return fee_check(20_000, 1_000)


def t_F1():
    return fee_check(10_000, 500)


def t_F2():
    return fee_check(10_001, 1_000)


def t_F3():
    return fee_check(100_000, 1_000)


def t_F4():
    return fee_check(123_450, 120)


def t_F5():
    return fee_check(6_000_000, 5_000)


def t_F6():
    sk, rn, rk = pair("g")
    charge(sk, 50_000)
    send(sk, rn, 30_000)
    r = bal(rk)
    return r == 30_000, f"30,000P 선물 → 받은 사람 {r} (기대 30000, 수수료를 떼지 않음)"


def t_L1():
    sk, rn, rk = pair("m")
    charge(sk, 10_000)
    s1 = send(sk, rn, 999)
    s2 = send(sk, rn, 1_000)
    return (not ok(s1) and ok(s2) and bal(rk) == 1_000), f"999P 응답 {s1}, 1,000P 응답 {s2}, 받은 사람 {bal(rk)}"


def t_L2():
    (n, k) = user("self")
    charge(k, 10_000)
    st = send(k, n, 1_000)
    b = bal(k)
    return (not ok(st) and b == 10_000), f"자기에게 선물 응답 {st}, 잔액 {b} (기대 10000)"


def t_L3():
    sk, rn, rk = pair("i")
    charge(sk, 10_400)
    st = send(sk, rn, 10_000)  # 수수료 500 → 10,500 필요
    b, r = bal(sk), bal(rk)
    return (not ok(st) and b == 10_400 and r == 0), f"잔액 10,400 으로 10,000P(+수수료 500) 선물 → 응답 {st}, 보낸 사람 {b}, 받은 사람 {r}"


def t_L4():
    sk, rn, rk = pair("d")
    if not charge(sk, 10_020_000):
        return False, "충전 실패"
    s1 = send(sk, rn, 9_990_000)
    s2 = send(sk, rn, 10_000)  # 금액 합계 정확히 1,000만 → 허용 (수수료는 한도에 넣지 않음)
    s3 = send(sk, rn, 1_000)  # 합계 1,000만 초과 → 거부
    r = bal(rk)
    good = ok(s1) and ok(s2) and not ok(s3) and r == 10_000_000
    return good, f"999만 응답 {s1}, 이어서 1만 응답 {s2}, 이어서 1천 응답 {s3}, 받은 사람 {r:,} (기대 10,000,000)" if r is not None else f"응답 {s1},{s2},{s3}"


def t_X1():
    (n, k) = user("x")
    charge(k, 10_000)
    st = send(k, "nouser" + uuid.uuid4().hex[:6], 1_000)
    b = bal(k)
    return (not ok(st) and b == 10_000), f"없는 아이디 응답 {st}, 잔액 {b}"


def t_C1(rounds=3):
    for r in range(rounds):
        sk, rn, rk = pair("c")
        charge(sk, 31_500)  # 10,000P(+500) 세 번 분량
        barrier = threading.Barrier(10)
        res = []

        def go():
            barrier.wait()
            res.append(send(sk, rn, 10_000))

        ts = [threading.Thread(target=go) for _ in range(10)]
        [t.start() for t in ts]
        [t.join(60) for t in ts]
        b, rr = bal(sk), bal(rk)
        n = (rr or 0) // 10_000
        if not (b is not None and b >= 0 and n <= 3 and b == 31_500 - n * 10_500):
            return False, f"{r+1}회차: 성공응답 {sum(1 for s in res if ok(s))}, 받은 사람 {rr}, 보낸 사람 {b}"
    return True, f"{rounds}회 모두 정상"


CHECKS = [("T0", "기본 선물·수수료", "명시", t_T0), ("F1", "1만P → 500P", "명시", t_F1), ("F2", "1만 초과 → 1,000P", "명시", t_F2),
          ("F4", "0.1%·10P 절사", "명시", t_F4), ("F5", "최대 5,000P", "명시", t_F5), ("L1", "최소 1,000P", "명시", t_L1),
          ("L2", "자기 자신 금지", "명시", t_L2),
          ("F3", "정확히 10만P → 1,000P", "모호", t_F3), ("F6", "받는 사람 전액", "모호", t_F6),
          ("L3", "잔액 = 금액+수수료", "모호", t_L3), ("L4", "1일 한도 = 금액 합계", "모호", t_L4),
          ("C1", "동시 선물", "누락", t_C1), ("X1", "없는 아이디", "누락", t_X1)]


def main():
    log = open(OUT + ".app.log", "w", encoding="utf8")
    app = subprocess.Popen([os.environ.get("JAVA_EXE", "java"), "-jar", JAR, "--spring.profiles.active=test", f"--server.port={PORT}"],
                           stdout=log, stderr=subprocess.STDOUT)
    try:
        for _ in range(120):
            if call("POST", "/api/v1/members/login", body={"username": "x", "password": "y"})[0] > 0:
                break
            time.sleep(1)
        rows = []
        for cid, name, kind, fn in CHECKS:
            try:
                passed, detail = fn()
            except Exception as e:
                passed, detail = False, f"예외 {e!r}"[:300]
            rows.append({"id": cid, "name": name, "kind": kind, "pass": bool(passed), "detail": detail})
            print(f"{'PASS' if passed else 'FAIL'} {cid} [{kind}] {name}: {detail}", flush=True)
        json.dump(rows, open(OUT, "w", encoding="utf8"), ensure_ascii=False, indent=1)
        print(f"합계 {sum(r['pass'] for r in rows)}/{len(rows)}")
    finally:
        app.terminate()
        try:
            app.wait(20)
        except Exception:
            app.kill()


if __name__ == "__main__":
    main()
