"""seed-data.json 을 실제 HTTP API 로 밀어 넣습니다.

순서: 회원가입 -> 그룹 생성과 초대 참여 -> (방문일 오름차순으로) 장소 추가 + 리뷰 -> 데모 그룹 부가 작업.
응답 형식은 {status, message, data}, 오류는 {status, message, error: {code, details}} 입니다.
예상 밖의 상태 코드가 오면 응답 본문을 찍고 종료 코드 1 로 멈춥니다.
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from pathlib import Path

API_BASE_URL = os.environ.get("API_BASE_URL", "http://localhost:8080").rstrip("/")
SEED_FILE = Path(__file__).resolve().parent / "seed-data.json"


def fail(message, status=None, payload=None):
    print(f"[load_seed] 실패: {message}", file=sys.stderr)
    if status is not None:
        print(f"  status={status}", file=sys.stderr)
    if payload is not None:
        print("  body=" + json.dumps(payload, ensure_ascii=False), file=sys.stderr)
    sys.exit(1)


def call(method, path, body=None, token=None):
    """(status, payload) 를 돌려줍니다. HTTP 오류도 본문을 읽어 돌려주고, 연결 오류만 바로 종료합니다."""
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(API_BASE_URL + path, data=data, method=method)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            status = resp.status
            text = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        status = e.code
        text = e.read().decode("utf-8", errors="replace")
    except urllib.error.URLError as e:
        fail(f"{method} {path} 연결 실패: {e.reason}")
    try:
        payload = json.loads(text) if text else {}
    except json.JSONDecodeError:
        payload = {"raw": text}
    return status, payload


def expect(status, payload, allowed, what):
    if status not in allowed:
        fail(f"{what} -> 예상 밖 상태", status, payload)
    return payload.get("data") or {}


class Loader:

    def __init__(self, seed):
        self.seed = seed
        self.tokens = {}
        self.user_ids = {}
        self.group_ids = {}
        self.place_ids = {}
        self.group_place_cache = set()
        self.tag_status = Counter()
        self.reviews_saved = 0
        self.reviews_skipped = 0
        self.tags_extracted = 0
        self.reviews_without_tags = 0
        self.places_by_pid = {p["providerPlaceId"]: p for p in seed["places"]}

    # 1. 회원가입 (이미 있으면 로그인)
    def signup_all(self):
        for user in self.seed["users"]:
            email = user["email"]
            status, payload = call("POST", "/api/auth/signup",
                                   {"email": email, "password": user["password"], "nickname": user["nickname"]})
            if status == 409:
                status, payload = call("POST", "/api/auth/login", {"email": email, "password": user["password"]})
                data = expect(status, payload, (200,), f"login {email}")
            else:
                data = expect(status, payload, (201,), f"signup {email}")
            self.tokens[email] = data["accessToken"]
            self.user_ids[email] = data.get("userId")
        print(f"users ready: {len(self.tokens)}")

    # 2. 그룹 생성 -> 초대 코드 -> 상대 참여
    def create_groups(self):
        for group in self.seed["groups"]:
            owner, member = group["owner"], group["member"]
            status, payload = call("POST", "/api/groups", {"groupType": "COUPLE", "name": group["name"]},
                                   token=self.tokens[owner])
            data = expect(status, payload, (201,), f"create group {group['name']}")
            group_id = data["groupId"]
            self.group_ids[owner] = group_id

            status, payload = call("POST", f"/api/groups/{group_id}/invites", {}, token=self.tokens[owner])
            code = expect(status, payload, (201,), f"invite for group {group_id}")["code"]

            status, payload = call("POST", "/api/groups/members", {"inviteCode": code}, token=self.tokens[member])
            expect(status, payload, (201,), f"join group {group_id} as {member}")
        print(f"groups ready: {len(self.group_ids)}")

    # 3. 장소를 그룹 지도에 추가 (provider + providerPlaceId 로 upsert)
    def ensure_place(self, group_owner, pid, token):
        group_id = self.group_ids[group_owner]
        key = (group_id, pid)
        if key in self.group_place_cache:
            return self.place_ids[pid]
        place = self.places_by_pid[pid]
        body = {"place": {
            "provider": "SEED",
            "providerPlaceId": pid,
            "name": place["name"],
            "address": place["address"],
            "region": place["region"],
            "category": place["category"],
            "priceBand": place["priceBand"],
            "latitude": place["latitude"],
            "longitude": place["longitude"],
        }}
        status, payload = call("POST", f"/api/groups/{group_id}/places", body, token=token)
        if status == 201:
            place_id = payload["data"]["placeId"]
        elif status == 409:
            place_id = self.place_ids.get(pid) or self.lookup_place(place, token)
        else:
            fail(f"add place {pid} to group {group_id}", status, payload)
        self.place_ids[pid] = place_id
        self.group_place_cache.add(key)
        return place_id

    def lookup_place(self, place, token):
        query = urllib.parse.quote(place["name"])
        status, payload = call("GET", f"/api/places?query={query}", token=token)
        content = expect(status, payload, (200,), f"lookup place {place['name']}").get("content") or []
        if not content:
            fail(f"lookup place {place['name']} -> 검색 결과 없음", status, payload)
        for item in content:
            if item.get("providerPlaceId") == place["providerPlaceId"]:
                return item["placeId"]
        return content[0]["placeId"]

    # 4. 리뷰 (방문일 오름차순)
    def post_reviews(self):
        reviews = sorted(self.seed["reviews"], key=lambda r: (r["visitedOn"], r["userEmail"], r["providerPlaceId"]))
        for i, review in enumerate(reviews, start=1):
            token = self.tokens[review["userEmail"]]
            group_owner = review["groupOwnerEmail"]
            place_id = self.ensure_place(group_owner, review["providerPlaceId"], token)
            body = {
                "placeId": place_id,
                "withGroupId": self.group_ids[group_owner],
                "visitedOn": review["visitedOn"],
                "rating": review["rating"],
                "content": review["content"],
            }
            status, payload = call("POST", "/api/reviews", body, token=token)
            if status == 409:
                # 같은 사람, 같은 장소, 같은 날짜가 이미 있으면 (재실행) 건너뜁니다
                self.reviews_skipped += 1
                continue
            data = expect(status, payload, (201,), f"review #{i} {review['userEmail']} {review['providerPlaceId']}")
            self.reviews_saved += 1
            self.tag_status[str(data.get("tagStatus"))] += 1
            tags = extracted_tags(data)
            if tags is not None:
                self.tags_extracted += len(tags)
                if not tags:
                    self.reviews_without_tags += 1
            if i % 50 == 0:
                print(f"reviews posted: {i}/{len(reviews)}")
        print(f"reviews posted: {len(reviews)}/{len(reviews)}")

    # 5. 데모 그룹: 프리미엄 구독 + 리뷰 없이 지도에만 올린 장소
    def demo_extras(self):
        demo = self.seed["demo"]
        owner = demo["ownerEmail"]
        token = self.tokens[owner]
        group_id = self.group_ids[owner]
        for group in self.seed["groups"]:
            if group.get("plan") != "PREMIUM":
                continue
            gid = self.group_ids[group["owner"]]
            status, payload = call("POST", f"/api/groups/{gid}/subscriptions", {"plan": "PREMIUM"},
                                   token=self.tokens[group["owner"]])
            expect(status, payload, (201, 409), f"subscribe PREMIUM group {gid}")
            print(f"group {gid} plan=PREMIUM (status {status})")
        for pid in demo["addedOnly"]:
            self.ensure_place(owner, pid, token)
        print(f"demo group {group_id}: addedOnly={demo['addedOnly']} oneSideOnly={demo['oneSideOnly']}")

    def summary(self):
        print("---- summary ----")
        print(f"reviews saved: {self.reviews_saved} (skipped as duplicate: {self.reviews_skipped})")
        print(f"tagStatus: {dict(self.tag_status)}")
        print(f"tags extracted (응답에 태그 목록이 있을 때만 집계): {self.tags_extracted}")
        print(f"reviews with 0 tags: {self.reviews_without_tags}")
        print(f"places created: {len(self.place_ids)}, group-place links: {len(self.group_place_cache)}")


def extracted_tags(data):
    """리뷰 응답에서 추출된 태그 목록을 찾습니다. 형식을 모르면 None."""
    for key in ("tags", "reviewTags", "extractedTags"):
        value = data.get(key)
        if isinstance(value, list):
            return value
    return None


def main():
    if not SEED_FILE.exists():
        fail(f"{SEED_FILE} 이 없습니다. generate_seed.py 를 먼저 실행하세요")
    seed = json.loads(SEED_FILE.read_text(encoding="utf-8"))
    print(f"API_BASE_URL={API_BASE_URL}")
    loader = Loader(seed)
    loader.signup_all()
    loader.create_groups()
    loader.post_reviews()
    loader.demo_extras()
    loader.summary()


if __name__ == "__main__":
    main()
