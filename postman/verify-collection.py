#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Postman 모음이 컨트롤러의 엔드포인트를 빠짐없이 덮는지 서버를 때리지 않고 검사합니다.

서버를 켜지 않아도 돌아갑니다. 하는 일은 세 가지입니다.

1. 모음 JSON 이 읽히는지, 요청마다 이름, 메서드, URL, 테스트 스크립트가 있는지 봅니다.
2. backend 의 컨트롤러를 직접 읽어 (메서드, 경로) 목록을 뽑고, 모음이 그 전부를 덮는지 맞춥니다.
3. 요청 수, 단언 수, 상태 코드 분포, 오류 코드 분포를 셉니다.
4. node 가 있으면 모든 스크립트가 문법에 맞는 자바스크립트인지까지 봅니다.

사용법
    python3 postman/verify-collection.py
"""

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
COLLECTION = os.path.join(HERE, "lovemaptually.postman_collection.json")
ENVIRONMENT = os.path.join(HERE, "lovemaptually.postman_environment.json")
CONTROLLER_ROOT = os.path.join(REPO, "backend", "src", "main", "java", "com", "lovemaptually")

MAPPING = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "PatchMapping": "PATCH",
    "DeleteMapping": "DELETE",
}

# 컨트롤러를 읽지 못하는 환경을 위한 대조군입니다. 읽을 수 있으면 이 목록은 쓰지 않습니다.
FALLBACK_ENDPOINTS = [
    ("POST", "/api/auth/signup"),
    ("POST", "/api/auth/login"),
    ("POST", "/api/groups"),
    ("GET", "/api/groups/me"),
    ("POST", "/api/groups/{id}/invites"),
    ("POST", "/api/groups/members"),
    ("GET", "/api/invites/{id}"),
    ("GET", "/api/places"),
    ("GET", "/api/places/{id}"),
    ("POST", "/api/groups/{id}/places"),
    ("GET", "/api/groups/{id}/places"),
    ("GET", "/api/groups/{id}/places/{id}"),
    ("POST", "/api/reviews"),
    ("GET", "/api/reviews/{id}"),
    ("GET", "/api/groups/{id}/places/{id}/reviews"),
    ("GET", "/api/groups/{id}/preferences"),
    ("POST", "/api/groups/{id}/recommendation-requests"),
    ("GET", "/api/recommendation-requests/{id}"),
    ("POST", "/api/groups/{id}/subscriptions"),
    ("POST", "/api/groups/{id}/reports"),
    ("GET", "/api/groups/{id}/reports"),
    ("GET", "/api/reports/{id}"),
]


def normalize(path):
    """경로 변수와 실제 번호를 모두 {id} 로 눕혀 비교할 수 있게 만듭니다."""
    path = path.split("?")[0]
    out = []
    for segment in path.strip("/").split("/"):
        if not segment:
            continue
        if segment.startswith("{{") or segment.startswith("{") or segment.isdigit():
            out.append("{id}")
        else:
            out.append(segment)
    return "/" + "/".join(out)


def endpoints_from_controllers():
    """컨트롤러 소스에서 (메서드, 경로) 를 뽑습니다."""
    if not os.path.isdir(CONTROLLER_ROOT):
        return None, []
    found = set()
    files = []
    for root, _, names in os.walk(CONTROLLER_ROOT):
        if os.path.basename(root) != "controller":
            continue
        for name in sorted(names):
            if name.endswith("Controller.java"):
                files.append(os.path.join(root, name))
    if not files:
        return None, []

    for file_path in files:
        with open(file_path, encoding="utf-8") as handle:
            source = handle.read()
        class_at = re.search(r'@RequestMapping\(\s*"([^"]*)"\s*\)', source)
        base = class_at.group(1) if class_at else ""
        for match in re.finditer(
            r"@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)"
            r'(?:\(\s*(?:value\s*=\s*)?"([^"]*)"\s*\))?',
            source,
        ):
            method = MAPPING[match.group(1)]
            suffix = match.group(2) or ""
            found.add((method, normalize(base + suffix)))
    return sorted(found), files


def walk(items, folder=None):
    for item in items:
        if "item" in item:
            for child in walk(item["item"], item.get("name", folder)):
                yield child
        else:
            yield folder, item


def raw_url(request):
    url = request.get("url")
    if isinstance(url, str):
        return url
    if isinstance(url, dict):
        if url.get("raw"):
            return url["raw"]
        return "/" + "/".join(url.get("path", []))
    return ""


def check_javascript(collection):
    """모든 사전 스크립트와 테스트 스크립트가 문법에 맞는지 node 로 확인합니다."""
    node = shutil.which("node")
    if not node:
        return None, []
    chunks = []
    for _, item in walk(collection.get("item", [])):
        for event in item.get("event", []):
            chunks.append({
                "name": item.get("name", ""),
                "listen": event.get("listen", ""),
                "source": "\n".join(event.get("script", {}).get("exec", [])),
            })
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as handle:
        json.dump(chunks, handle, ensure_ascii=False)
        payload = handle.name
    script = (
        "const chunks = JSON.parse(require('fs').readFileSync(process.argv[1], 'utf8'));"
        "const bad = [];"
        "for (const c of chunks) { try { new Function(c.source); }"
        " catch (e) { bad.push(c.name + ' / ' + c.listen + ': ' + e.message); } }"
        "process.stdout.write(JSON.stringify(bad));"
    )
    try:
        result = subprocess.run([node, "-e", script, payload],
                                capture_output=True, text=True, timeout=60)
        broken = json.loads(result.stdout or "[]")
    except Exception as error:  # node 가 있어도 실패할 수 있어 검사를 통째로 건너뜁니다.
        return None, ["자바스크립트 검사를 돌리지 못했습니다: %s" % error]
    finally:
        os.unlink(payload)
    return len(chunks), broken


def main():
    problems = []

    if not os.path.exists(COLLECTION):
        print("모음 파일이 없습니다: %s" % COLLECTION)
        return 1
    with open(COLLECTION, encoding="utf-8") as handle:
        collection = json.load(handle)
    with open(ENVIRONMENT, encoding="utf-8") as handle:
        environment = json.load(handle)

    schema = collection.get("info", {}).get("schema", "")
    if "v2.1.0" not in schema:
        problems.append("모음 스키마가 v2.1.0 이 아닙니다: %s" % schema)

    requests = list(walk(collection.get("item", [])))
    assertions = 0
    save_lines = 0
    status_counter = Counter()
    error_counter = Counter()
    covered = set()
    per_folder = Counter()

    for folder, item in requests:
        name = item.get("name", "")
        per_folder[folder] += 1
        if not name:
            problems.append("이름이 없는 요청이 있습니다 (폴더 %s)" % folder)
        request = item.get("request", {})
        if not request.get("method"):
            problems.append("메서드가 없습니다: %s" % name)
        url_text = raw_url(request)
        if not url_text:
            problems.append("URL 이 없습니다: %s" % name)
        if not request.get("description"):
            problems.append("설명이 없습니다: %s" % name)

        scripts = [
            event
            for event in item.get("event", [])
            if event.get("listen") == "test" and event.get("script", {}).get("exec")
        ]
        if not scripts:
            problems.append("테스트 스크립트가 없습니다: %s" % name)
            continue
        body = "\n".join("\n".join(event["script"]["exec"]) for event in scripts)
        count = body.count("pm.test(")
        if count == 0:
            problems.append("단언이 하나도 없습니다: %s" % name)
        assertions += count
        save_lines += body.count("pm.collectionVariables.set(")
        error_counter.update(re.findall(r"error\.code\)\.to\.eql\('([A-Z_]+)'\)", body))

        path = normalize(url_text.replace("{{baseUrl}}", ""))
        covered.add((request["method"], path))

        matched = re.search(r"(\d{3})\s*$", name)
        if matched:
            status_counter[matched.group(1)] += 1
        else:
            problems.append("이름 끝에 기대 상태 코드가 없습니다: %s" % name)

    controller_endpoints, controller_files = endpoints_from_controllers()
    source = "컨트롤러 소스"
    if controller_endpoints is None:
        controller_endpoints = FALLBACK_ENDPOINTS
        source = "내장 대조군(컨트롤러를 읽지 못했습니다)"

    missing = [pair for pair in controller_endpoints if pair not in covered]
    extra = [pair for pair in sorted(covered) if pair not in set(controller_endpoints)]

    print("러브맵츄얼리 Postman 모음 검사")
    print("=" * 62)
    print("모음 파일            %s" % os.path.basename(COLLECTION))
    print("스키마               %s" % ("Collection v2.1.0" if "v2.1.0" in schema else schema))
    print("환경 변수 개수       %d" % len(environment.get("values", [])))
    print()
    print("폴더 %d개, 요청 %d개, 단언 %d개, 변수 저장 %d회"
          % (len(collection.get("item", [])), len(requests), assertions, save_lines))
    for folder in collection.get("item", []):
        print("  - %-16s 요청 %2d개" % (folder["name"], per_folder[folder["name"]]))
    print()
    print("엔드포인트 대조 기준 %s (파일 %d개)" % (source, len(controller_files)))
    print("컨트롤러 엔드포인트  %d개" % len(controller_endpoints))
    print("모음이 덮은 엔드포인트 %d개" % len([p for p in controller_endpoints if p in covered]))
    if missing:
        print("덮지 못한 엔드포인트:")
        for method, path in missing:
            print("  - %-6s %s" % (method, path))
        problems.append("덮지 못한 엔드포인트가 %d개 있습니다" % len(missing))
    else:
        print("덮지 못한 엔드포인트 없음")
    if extra:
        print("컨트롤러에 없는 경로를 부르는 요청:")
        for method, path in extra:
            print("  - %-6s %s" % (method, path))
        problems.append("컨트롤러에 없는 경로가 %d개 있습니다" % len(extra))
    print()
    print("상태 코드 분포")
    for code, count in sorted(status_counter.items()):
        print("  %s  %2d건" % (code, count))
    print()
    print("검사하는 오류 코드 %d종" % len(error_counter))
    for code, count in sorted(error_counter.items()):
        print("  %-28s %d건" % (code, count))
    print()

    script_count, broken = check_javascript(collection)
    if script_count is None:
        print("스크립트 문법 검사   건너뜀 (node 가 없습니다)")
        problems.extend(broken)
    elif broken:
        print("스크립트 문법 검사   %d개 중 %d개 실패" % (script_count, len(broken)))
        for line in broken:
            print("  - %s" % line)
        problems.append("문법이 깨진 스크립트가 %d개 있습니다" % len(broken))
    else:
        print("스크립트 문법 검사   %d개 모두 통과" % script_count)
    print()

    if problems:
        print("문제 %d건" % len(problems))
        for problem in problems:
            print("  - %s" % problem)
        return 1
    print("문제 없음. 모음이 컨트롤러의 엔드포인트를 모두 덮습니다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
