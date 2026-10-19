"""고정 시나리오를 돌려 응답의 '모양'을 기록한다 (재구현 전후 비교용).

사용: python snapshot.py <jar> <port> <out.json>
기록: 단계마다 상태 코드, resultCode, JSON 키 구조(타입 포함). 회사 규칙 3개가 바꿀 값(작성자 잔액, 한도 경계, 100원 단위)은 피한다.
id·날짜·키·이름 같은 매번 바뀌는 값은 기록하지 않는다. 잔액은 회사 규칙과 무관한 구매자 잔액만 숫자로 남긴다.
"""
import json, os, subprocess, sys, time, uuid, urllib.request, urllib.error

JAR, PORT, OUT = sys.argv[1], int(sys.argv[2]), sys.argv[3]
BASE = f"http://localhost:{PORT}"


def call(method, path, key=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if key:
        req.add_header("Authorization", f"Bearer {key}")
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, r.read().decode("utf8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf8", "replace")
    except Exception as e:
        return -1, repr(e)


def js(raw):
    try:
        return json.loads(raw)
    except Exception:
        return None


def shape(o):
    """키 구조와 타입만 남긴다. 리스트는 첫 원소의 구조."""
    if isinstance(o, dict):
        return {k: shape(v) for k, v in sorted(o.items())}
    if isinstance(o, list):
        return [shape(o[0])] if o else []
    if o is None:
        return "null"
    return type(o).__name__


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


steps = []


def rec(name, st, raw, keep=None):
    j = js(raw)
    row = {"step": name, "status": st, "resultCode": find(j, "resultCode") if isinstance(j, dict) else None,
           "shape": shape(j) if j is not None else ("<non-json>" if raw else "<empty>")}
    if keep:
        row.update(keep)
    steps.append(row)
    return j


def user(tag):
    name = f"{tag}{uuid.uuid4().hex[:6]}"
    rec(f"{tag}: 가입", *call("POST", "/api/v1/members/join", body={"username": name, "password": "1234", "nickname": name}))
    st, raw = call("POST", "/api/v1/members/login", body={"username": name, "password": "1234"})
    j = rec(f"{tag}: 로그인", st, raw)
    return find(j, "apiKey")


def run():
    a, b, c = user("작성자"), user("구매자"), user("제3자")
    rec("구매자: 지갑 조회(처음)", *call("GET", "/api/v1/wallet", b))
    st, raw = call("POST", "/api/v1/wallet/charge", b, {"amount": 1000})
    rec("구매자: 1000 충전", st, raw)
    rec("구매자: 0 충전", *call("POST", "/api/v1/wallet/charge", b, {"amount": 0}))
    rec("구매자: 음수 충전", *call("POST", "/api/v1/wallet/charge", b, {"amount": -5}))
    rec("구매자: amount 누락", *call("POST", "/api/v1/wallet/charge", b, {}))
    st, raw = call("POST", "/api/v1/posts", a, {"title": "유료글", "content": "비밀본문입니다", "price": 300})
    j = rec("작성자: 유료 글(300) 작성", st, raw)
    pid = find(j, "id")
    st, raw = call("POST", "/api/v1/posts", a, {"title": "무료글", "content": "무료본문입니다"})
    j = rec("작성자: 무료 글(price 생략) 작성", st, raw)
    fid = find(j, "id")
    rec("작성자: 음수 가격 글", *call("POST", "/api/v1/posts", a, {"title": "음수글", "content": "본문입니다", "price": -100}))
    rec("제3자: 유료 글 단건 조회", *call("GET", f"/api/v1/posts/{pid}", c))
    rec("제3자: 글 목록", *call("GET", "/api/v1/posts", c))
    rec("제3자: 유료 본문 열람", *call("GET", f"/api/v1/posts/{pid}/content", c))
    rec("제3자: 무료 본문 열람", *call("GET", f"/api/v1/posts/{fid}/content", c))
    rec("작성자: 자기 유료 본문 열람", *call("GET", f"/api/v1/posts/{pid}/content", a))
    rec("작성자: 자기 글 구매", *call("POST", f"/api/v1/posts/{pid}/purchase", a))
    rec("구매자: 무료 글 구매", *call("POST", f"/api/v1/posts/{fid}/purchase", b))
    st, raw = call("POST", f"/api/v1/posts/{pid}/purchase", b)
    rec("구매자: 유료 글 구매", st, raw)
    rec("구매자: 같은 글 재구매", *call("POST", f"/api/v1/posts/{pid}/purchase", b))
    rec("구매자: 구매 후 본문 열람", *call("GET", f"/api/v1/posts/{pid}/content", b))
    st, raw = call("GET", "/api/v1/wallet", b)
    rec("구매자: 구매 후 잔액", st, raw, {"balance": find(js(raw), "balance")})
    st, raw = call("POST", "/api/v1/posts", a, {"title": "비싼글", "content": "비싼본문입니다", "price": 5000})
    eid = find(js(raw), "id")
    rec("제3자: 잔액 부족 구매", *call("POST", f"/api/v1/posts/{eid}/purchase", c))
    rec("비로그인: 지갑 조회", *call("GET", "/api/v1/wallet"))
    rec("비로그인: 본문 열람", *call("GET", f"/api/v1/posts/{fid}/content"))
    rec("없는 글 구매", *call("POST", "/api/v1/posts/999999/purchase", b))
    # 결제와 무관한 기존 API
    rec("기존: 시드 글 1 조회", *call("GET", "/api/v1/posts/1", c))
    rec("기존: 시드 글 1 댓글 목록", *call("GET", "/api/v1/posts/1/comments", c))
    rec("기존: 내 정보", *call("GET", "/api/v1/members/me", c))
    rec("기존: 글 수정(작성자)", *call("PUT", f"/api/v1/posts/{fid}", a, {"title": "수정글", "content": "수정본문입니다"}))
    rec("기존: 글 수정(남)", *call("PUT", f"/api/v1/posts/{fid}", c, {"title": "수정글", "content": "수정본문입니다"}))
    rec("기존: 댓글 작성", *call("POST", f"/api/v1/posts/{fid}/comments", c, {"content": "댓글입니다"}))


def main():
    log = open(OUT + ".app.log", "w", encoding="utf8")
    app = subprocess.Popen([os.environ.get("JAVA_EXE", "java"), "-jar", JAR, "--spring.profiles.active=test", f"--server.port={PORT}"],
                           stdout=log, stderr=subprocess.STDOUT)
    try:
        for _ in range(120):
            if call("POST", "/api/v1/members/login", body={"username": "x", "password": "y"})[0] > 0:
                break
            time.sleep(1)
        run()
        json.dump(steps, open(OUT, "w", encoding="utf8"), ensure_ascii=False, indent=1)
        print(f"steps {len(steps)}")
    finally:
        app.terminate()
        try:
            app.wait(20)
        except Exception:
            app.kill()


if __name__ == "__main__":
    main()
