"""숨긴 인수 테스트 (AI 에게 보여 주지 않음). 실행 중인 앱에 localhost HTTP 로만 호출한다.

사용: python acceptance.py <jar> <port> <out.json>
정책별 판정을 JSON 으로 남긴다. 상태 코드는 '성공=2xx' 만 계약으로 보고, 실패 응답의 코드는 따지지 않는다
(정책을 지시받지 않은 조건이 코드 형식 때문에 불리하지 않게). 판정은 잔액·열람 여부 같은 '상태'로 한다.
"""
import json, os, subprocess, sys, threading, time, uuid, urllib.request, urllib.error

JAR, PORT, OUT = sys.argv[1], int(sys.argv[2]), sys.argv[3]
BASE = f"http://localhost:{PORT}"
JAVA = os.environ.get("JAVA_EXE", "java")


def call(method, path, key=None, body=None, timeout=20):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if key:
        req.add_header("Authorization", f"Bearer {key}")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read().decode("utf8", "replace")
            return r.status, raw
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf8", "replace")
    except Exception as e:  # 연결 실패 등
        return -1, repr(e)


def find_key(obj, key):
    """응답 JSON 어디에 있든 key 값을 찾는다 (최상위 또는 data 안 등)."""
    if isinstance(obj, dict):
        if key in obj:
            return obj[key]
        for v in obj.values():
            r = find_key(v, key)
            if r is not None:
                return r
    elif isinstance(obj, list):
        for v in obj:
            r = find_key(v, key)
            if r is not None:
                return r
    return None


def js(raw):
    try:
        return json.loads(raw)
    except Exception:
        return None


def ok(st):
    return 200 <= st < 300


def new_user(tag):
    name = f"{tag}{uuid.uuid4().hex[:8]}"
    call("POST", "/api/v1/members/join", body={"username": name, "password": "1234", "nickname": name})
    st, raw = call("POST", "/api/v1/members/login", body={"username": name, "password": "1234"})
    key = find_key(js(raw), "apiKey")
    if not key:
        raise RuntimeError(f"로그인 실패 {st} {raw[:200]}")
    return key


def balance(key):
    st, raw = call("GET", "/api/v1/wallet", key)
    b = find_key(js(raw), "balance")
    return b if ok(st) and isinstance(b, (int, float)) else None


def charge(key, amount):
    return call("POST", "/api/v1/wallet/charge", key, {"amount": amount})[0]


def write(key, price, content=None):
    content = content or f"SECRET-{uuid.uuid4().hex[:12]}"
    body = {"title": "유료글", "content": content}
    if price is not None:
        body["price"] = price
    st, raw = call("POST", "/api/v1/posts", key, body)
    pid = find_key(js(raw), "id")
    return st, pid, content


def purchase(key, pid):
    return call("POST", f"/api/v1/posts/{pid}/purchase", key)[0]


def read_content(key, pid):
    st, raw = call("GET", f"/api/v1/posts/{pid}/content", key)
    return st, find_key(js(raw), "content")


def concurrently(fns):
    """배리어로 동시에 출발시킨다."""
    barrier = threading.Barrier(len(fns))
    results = [None] * len(fns)

    def run(i, fn):
        barrier.wait()
        results[i] = fn()

    ts = [threading.Thread(target=run, args=(i, f)) for i, f in enumerate(fns)]
    [t.start() for t in ts]
    [t.join(60) for t in ts]
    return results


# ---------- 정책별 검사 (각각 새 회원으로) ----------
def t_F0():
    """기본 기능: 충전 → 유료 글 → 구매 → 본문 열람"""
    a, b = new_user("fa"), new_user("fb")
    if charge(b, 1000) // 100 != 2 or balance(b) != 1000:
        return False, f"충전 후 잔액 {balance(b)}"
    st, pid, content = write(a, 300)
    if not ok(st) or pid is None:
        return False, f"유료 글 작성 {st}"
    if not ok(purchase(b, pid)):
        return False, "구매 실패"
    st, c = read_content(b, pid)
    return (ok(st) and c == content), f"열람 {st}"


def t_P1():
    """충전 금액 1 이상"""
    b = new_user("p1")
    charge(b, 500)
    s0 = charge(b, 0)
    sn = charge(b, -100)
    bal = balance(b)
    # 0원은 더해도 잔액이 같으므로 '거부 응답(2xx 아님)'까지 본다
    return (bal == 500 and not ok(s0) and not ok(sn)), f"0원 응답 {s0}, 음수 응답 {sn}, 잔액 {bal} (기대 500, 둘 다 거부)"


def t_P2():
    """잔액 부족 → 구매 실패, 잔액 불변, 열람 불가"""
    a, b = new_user("p2a"), new_user("p2b")
    charge(b, 100)
    _, pid, _ = write(a, 300)
    purchase(b, pid)
    bal = balance(b)
    st, _ = read_content(b, pid)
    return (bal == 100 and not ok(st)), f"잔액 {bal} (기대 100), 열람 {st}"


def t_P3():
    """같은 글 중복 구매 금지"""
    a, b = new_user("p3a"), new_user("p3b")
    charge(b, 1000)
    _, pid, _ = write(a, 300)
    purchase(b, pid)
    purchase(b, pid)
    bal = balance(b)
    return bal == 700, f"두 번 구매 시도 뒤 잔액 {bal} (기대 700)"


def t_P4():
    """작성자는 자기 글 구매 불가, 본문은 볼 수 있음"""
    a = new_user("p4")
    charge(a, 1000)
    _, pid, content = write(a, 300)
    purchase(a, pid)
    bal = balance(a)
    st, c = read_content(a, pid)
    return (bal == 1000 and ok(st) and c == content), f"잔액 {bal} (기대 1000), 본인 열람 {st}"


def t_P5():
    """유료 본문은 작성자·구매자만 — 어떤 조회 API 로도 새면 안 됨. 무료 글은 열람 가능"""
    a, c = new_user("p5a"), new_user("p5c")
    _, pid, secret = write(a, 300)
    st, _ = read_content(c, pid)
    leak_detail = secret in call("GET", f"/api/v1/posts/{pid}", c)[1]
    leak_list = secret in call("GET", "/api/v1/posts", c)[1]
    _, fid, free = write(a, 0)
    fst, fc = read_content(c, fid)
    good = (not ok(st)) and not leak_detail and not leak_list and ok(fst) and fc == free
    return good, f"/content {st}, 단건 노출 {leak_detail}, 목록 노출 {leak_list}, 무료 열람 {fst}"


def t_P6():
    """구매 금액은 작성자 지갑으로"""
    a, b = new_user("p6a"), new_user("p6b")
    charge(b, 1000)
    before = balance(a) or 0
    _, pid, _ = write(a, 300)
    purchase(b, pid)
    after = balance(a)
    # v2: 금액 자체는 R1 에서 본다. 여기서는 '작성자에게 돈이 들어가는가'만
    return (after or 0) > before, f"작성자 잔액 {before} → {after} (기대: 증가)"


def t_P8():
    """가격은 0 이상"""
    a = new_user("p8")
    st, pid, _ = write(a, -100)
    return not ok(st), f"음수 가격 글 작성 응답 {st}"


def t_P7a(rounds=3):
    """동시: 서로 다른 글 10개 동시 구매, 잔액 1000·가격 300 → 최대 3건, 잔액 음수 없음, 돈이 새지 않음"""
    for r in range(rounds):
        a, b = new_user("c1a"), new_user("c1b")
        charge(b, 1000)
        pids = [write(a, 300)[1] for _ in range(10)]
        a0 = balance(a) or 0
        res = concurrently([lambda p=p: purchase(b, p) for p in pids])
        succ = sum(1 for s in res if s and ok(s))
        bb, ab = balance(b), balance(a)
        readable = sum(1 for p in pids if ok(read_content(b, p)[0]))
        if not (bb is not None and bb >= 0 and bb == 1000 - 300 * readable and readable <= 3):
            return False, f"{r+1}회차: 성공응답 {succ}, 열람가능 {readable}, 구매자 잔액 {bb}, 작성자 잔액 {ab}"
    return True, f"{rounds}회 모두 정상"


def t_P7b(rounds=3):
    """동시: 같은 글 10번 동시 구매 → 한 번만 차감"""
    for r in range(rounds):
        a, b = new_user("c2a"), new_user("c2b")
        charge(b, 1000)
        pid = write(a, 100)[1]
        concurrently([lambda: purchase(b, pid) for _ in range(10)])
        bb = balance(b)
        if bb != 900:
            return False, f"{r+1}회차: 잔액 {bb} (기대 900)"
    return True, f"{rounds}회 모두 정상"


def t_P7c(rounds=3):
    """동시: 충전 20건 동시 → 누락 없음"""
    for r in range(rounds):
        b = new_user("c3")
        charge(b, 1)
        concurrently([lambda: charge(b, 100) for _ in range(20)])
        bb = balance(b)
        if bb != 2001:
            return False, f"{r+1}회차: 잔액 {bb} (기대 2001)"
    return True, f"{rounds}회 모두 정상"


def t_R1():
    """[회사 규칙] 플랫폼 수수료 10% — 작성자는 가격의 90%"""
    a, b = new_user("r1a"), new_user("r1b")
    charge(b, 5000)
    before = balance(a) or 0
    _, pid, _ = write(a, 1000)
    purchase(b, pid)
    after = balance(a)
    return after == before + 900, f"1000원 판매 후 작성자 {before} → {after} (기대 +900)"


def t_R2():
    """[회사 규칙] 1회 충전 한도 100,000"""
    b = new_user("r2")
    s1 = charge(b, 100000)
    s2 = charge(b, 100001)
    bal = balance(b)
    return (ok(s1) and not ok(s2) and bal == 100000), f"100,000 응답 {s1}, 100,001 응답 {s2}, 잔액 {bal} (기대 100000)"


def t_R3():
    """[회사 규칙] 가격은 100원 단위"""
    a = new_user("r3")
    s1, _, _ = write(a, 150)
    s2, _, _ = write(a, 200)
    return (not ok(s1) and ok(s2)), f"150원 글 {s1}, 200원 글 {s2}"


CHECKS = [("F0", "기본 기능", t_F0), ("P1", "충전 1 이상", t_P1), ("P2", "잔액 부족", t_P2),
          ("P3", "중복 구매", t_P3), ("P4", "작성자 본인", t_P4), ("P5", "본문 비공개", t_P5),
          ("P6", "작성자 적립", t_P6), ("P8", "가격 0 이상", t_P8), ("P7a", "동시 구매(다른 글)", t_P7a),
          ("P7b", "동시 구매(같은 글)", t_P7b), ("P7c", "동시 충전", t_P7c),
          ("R1", "수수료 10%", t_R1), ("R2", "충전 한도", t_R2), ("R3", "100원 단위", t_R3)]


def main():
    log = open(OUT + ".app.log", "w", encoding="utf8")
    app = subprocess.Popen([JAVA, "-jar", JAR, "--spring.profiles.active=test", f"--server.port={PORT}"],
                           stdout=log, stderr=subprocess.STDOUT)
    try:
        for _ in range(120):
            if call("POST", "/api/v1/members/login", body={"username": "x", "password": "y"}, timeout=2)[0] > 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("앱 기동 실패")
        rows = []
        for cid, name, fn in CHECKS:
            try:
                passed, detail = fn()
            except Exception as e:
                passed, detail = False, f"예외 {e!r}"[:300]
            rows.append({"id": cid, "name": name, "pass": bool(passed), "detail": detail})
            print(f"{'PASS' if passed else 'FAIL'} {cid} {name}: {detail}", flush=True)
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
