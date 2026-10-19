"""AI가 만든 앱을 다시 띄워 '동시에 요청하면 돈이 사라지는지' 눈으로 확인하는 재생 스크립트.

사용:  python replay/replay.py <실행 이름> [--rounds 3]
예:    python replay/replay.py s-opus-P-2      (포인트 선물 · Opus · 기획서 복붙 2회차)
       python replay/replay.py q-haiku-A-1     (결제 · Haiku · 기능만 1회차)

하는 일
  1. replay/base (원래 학습용 프로젝트)를 복사하고, runs/<실험>/<실행>/AI가-바꾼-코드.patch 를 적용한다.
  2. ./gradlew bootJar 로 빌드한다 (JDK 21 필요, 처음엔 의존성을 받느라 몇 분 걸림).
  3. 앱을 test 프로필(메모리 DB)로 띄우고, 동시에 요청을 보낸 뒤 돈이 맞는지 계산해 보여 준다.
  4. 앱을 끄고 끝낸다.
"""
import argparse, json, os, shutil, subprocess, sys, threading, time, uuid, urllib.request, urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / "replay" / "base"
WORK = ROOT / "replay" / ".work"
PORT = 18990


def find_patch(run):
    hits = list((ROOT / "runs").glob(f"*/{run}/AI가-바꾼-코드.patch"))
    if not hits:
        sys.exit(f"'{run}' 의 코드 변경분을 찾지 못했습니다. runs/ 아래 이름을 확인하세요. (마이그레이션 실험 01 은 재생 대상이 아닙니다)")
    return hits[0]


def build(run):
    d = WORK / run
    if (d / "build" / "libs").exists():
        jars = [j for j in (d / "build" / "libs").glob("*-SNAPSHOT.jar") if "plain" not in j.name]
        if jars:
            return jars[0]
    if d.exists():
        shutil.rmtree(d)
    shutil.copytree(BASE, d)
    if run.startswith("p-"):  # 결제 v1 은 테스트 DB 설정을 바꾸기 전 원본에서 시작했다
        y = d / "src/main/resources/application-test.yml"
        y.write_text(y.read_text(encoding="utf8").replace("db_test_${random.uuid}", "db_dev"), encoding="utf8")
    patch = find_patch(run)
    # 작업 폴더를 독립 git 저장소로 만든다. 바깥 저장소 안에서 git apply 를 하면 경로를 바깥 기준으로 해석해 조용히 건너뛴다
    subprocess.run(["git", "init", "-q"], cwd=d, check=True)
    p = subprocess.run(["git", "apply", "--whitespace=nowarn", str(patch)], cwd=d, capture_output=True, text=True)
    if p.returncode != 0:
        sys.exit("코드 변경분 적용 실패:\n" + p.stderr)
    # 빈 적용 검사: 변경분에 있는 파일 수만큼 실제로 바뀌었는지 센다
    want = sum(1 for l in patch.read_text(encoding="utf8").splitlines() if l.startswith("diff --git "))
    got = len([l for l in subprocess.run(["git", "status", "--porcelain", "-uall"], cwd=d, capture_output=True, text=True).stdout.splitlines() if l.strip()])
    if got < want:
        sys.exit(f"코드 변경분이 일부만 적용됐습니다 (파일 {want}개 중 {got}개). 재생을 멈춥니다.")
    print(f"· AI 코드 적용 완료 (파일 {got}개). 빌드 중... (처음이면 몇 분 걸립니다)", flush=True)
    gw = "gradlew.bat" if os.name == "nt" else "./gradlew"
    p = subprocess.run([str(d / gw) if os.name == "nt" else gw, "bootJar", "-x", "test", "-q", "--console=plain"], cwd=d, capture_output=True, text=True, shell=False)
    if p.returncode != 0:
        sys.exit("빌드 실패:\n" + (p.stdout + p.stderr)[-2000:])
    return [j for j in (d / "build" / "libs").glob("*-SNAPSHOT.jar") if "plain" not in j.name][0]


def call(method, path, key=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(f"http://localhost:{PORT}{path}", data=data, method=method)
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


def find(o, k):
    if isinstance(o, dict):
        if k in o:
            return o[k]
        for v in o.values():
            r = find(v, k)
            if r is not None:
                return r
    if isinstance(o, list):
        for v in o:
            r = find(v, k)
            if r is not None:
                return r
    return None


def js(s):
    try:
        return json.loads(s)
    except Exception:
        return None


def user(tag):
    n = f"{tag}{uuid.uuid4().hex[:6]}"
    call("POST", "/api/v1/members/join", body={"username": n, "password": "1234", "nickname": n})
    return n, find(js(call("POST", "/api/v1/members/login", body={"username": n, "password": "1234"})[1]), "apiKey")


def together(fns):
    b = threading.Barrier(len(fns)); out = [None] * len(fns)
    def go(i, f):
        b.wait(); out[i] = f()
    ts = [threading.Thread(target=go, args=(i, f)) for i, f in enumerate(fns)]
    [t.start() for t in ts]; [t.join(60) for t in ts]
    return out


ok = lambda s: 200 <= s < 300


def scenario_point(rounds):
    bal = lambda k: find(js(call("GET", "/api/v1/points", k)[1]), "balance")
    lost_total = 0
    for r in range(1, rounds + 1):
        (sn, sk), (rn, rk) = user("send"), user("recv")
        call("POST", "/api/v1/points/charge", sk, {"amount": 31_500})
        print(f"\n[{r}회차] 보내는 사람 잔액 31,500P. 10,000P 선물(수수료 500P)을 동시에 10번 보냅니다.")
        print("        잔액으로는 3번까지만 성공해야 합니다 (3 × 10,500P = 31,500P).")
        res = together([lambda: call("POST", "/api/v1/points/transfer", sk, {"toUsername": rn, "amount": 10_000})[0] for _ in range(10)])
        n_ok = sum(1 for s in res if s and ok(s))
        sb, rb = bal(sk), bal(rk)
        paid = 31_500 - (sb or 0)
        print(f"   성공 응답 {n_ok}건 / 보낸 사람 잔액 {sb:,}P (낸 돈 {paid:,}P) / 받은 사람 잔액 {rb:,}P")
        # 받은 건수만큼 수수료가 붙어야 맞다
        expected_paid = (rb // 10_000) * 10_500
        lost = paid - expected_paid
        lost_total += max(lost, 0)
        if lost == 0 and n_ok <= 3:
            print(f"   → 정상: 낸 돈 {paid:,}P = 도착한 선물 {rb // 10_000}건 × 10,500P. 돈이 맞습니다.")
        else:
            print(f"   → 이상: 보낸 사람은 {paid:,}P를 냈는데 받은 사람에게 도착한 선물은 {rb // 10_000}건({rb:,}P)입니다. "
                  f"{max(lost, 0):,}P가 사라졌습니다.")
    return lost_total


def scenario_payment(rounds):
    bal = lambda k: find(js(call("GET", "/api/v1/wallet", k)[1]), "balance")
    lost_total = 0
    for r in range(1, rounds + 1):
        (an, ak), (bn, bk) = user("author"), user("buyer")
        call("POST", "/api/v1/wallet/charge", bk, {"amount": 1_000})
        pids = [find(js(call("POST", "/api/v1/posts", ak, {"title": "유료글", "content": "본문입니다", "price": 300})[1]), "id") for _ in range(10)]
        print(f"\n[{r}회차] 구매자 잔액 1,000원. 300원짜리 글 10개를 동시에 삽니다. 잔액으로는 3개까지만 살 수 있어야 합니다.")
        res = together([lambda p=p: call("POST", f"/api/v1/posts/{p}/purchase", bk)[0] for p in pids])
        n_ok = sum(1 for s in res if s and ok(s))
        readable = sum(1 for p in pids if ok(call("GET", f"/api/v1/posts/{p}/content", bk)[0]))
        b = bal(bk)
        paid = 1_000 - (b or 0)
        print(f"   성공 응답 {n_ok}건 / 본문을 볼 수 있게 된 글 {readable}개 / 구매자 잔액 {b:,}원 (낸 돈 {paid:,}원)")
        lost = readable * 300 - paid
        lost_total += max(lost, 0)
        print(f"   → {'정상' if lost == 0 and readable <= 3 else '이상'}: 글 {readable}개를 얻었으면 {readable * 300:,}원을 냈어야 합니다. "
              + (f"{lost:,}원어치를 공짜로 얻었습니다." if lost > 0 else "돈이 맞습니다."))
    return lost_total


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run"); ap.add_argument("--rounds", type=int, default=3)
    a = ap.parse_args()
    jar = build(a.run)
    print(f"· 앱 기동: {a.run}", flush=True)
    log = open(WORK / f"{a.run}.app.log", "w", encoding="utf8")
    app = subprocess.Popen(["java", "-jar", str(jar), "--spring.profiles.active=test", f"--server.port={PORT}"], stdout=log, stderr=subprocess.STDOUT)
    try:
        for _ in range(120):
            if call("POST", "/api/v1/members/login", body={"username": "x", "password": "y"})[0] > 0:
                break
            time.sleep(1)
        lost = scenario_point(a.rounds) if a.run.startswith("s-") else scenario_payment(a.rounds)
        print("\n=== 결론:", f"{a.rounds}번 중 돈이 사라진 경우가 있었습니다 (합계 {lost:,})." if lost else f"{a.rounds}번 모두 돈이 맞았습니다.")
    finally:
        app.terminate()
        try:
            app.wait(20)
        except Exception:
            app.kill()


if __name__ == "__main__":
    main()
