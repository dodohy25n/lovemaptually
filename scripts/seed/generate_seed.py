"""데모 시드 데이터 생성기.

고정 시드로 장소 40곳, 사용자 26명, 커플 13팀, 리뷰 약 250건을 만들어
scripts/seed/seed-data.json 에 씁니다. 두 번 실행해도 바이트 단위로 같은 결과가 나옵니다.

리뷰 문장은 backend/src/main/resources/ai/matching-table.json 의 표현 목록에서 만들기 때문에
백엔드 태그 추출기가 그대로 읽을 수 있습니다.
"""

import json
import random
import re
from datetime import date, timedelta
from pathlib import Path

SEED = 20260904
ROOT = Path(__file__).resolve().parents[2]
MATCHING_TABLE = ROOT / "backend" / "src" / "main" / "resources" / "ai" / "matching-table.json"
OUTPUT = Path(__file__).resolve().parent / "seed-data.json"

PASSWORD = "demo1234!"
DEMO_OWNER = "dohyeon@lovemap.dev"
DEMO_MEMBER = "jiwoo@lovemap.dev"

REGION_CENTER = {
    "인사동": (37.574, 126.985),
    "여의도": (37.526, 126.925),
    "성수": (37.544, 127.056),
    "연남": (37.562, 126.925),
}
REGION_STREET = {
    "인사동": "서울 종로구 인사동길",
    "여의도": "서울 영등포구 여의대로",
    "성수": "서울 성동구 연무장길",
    "연남": "서울 마포구 성미산로",
}

# (이름, 카테고리) 10개씩. 모두 지어낸 상호입니다.
PLACE_NAMES = {
    "인사동": [
        ("달빛찻집", "카페"), ("한지빛전시관", "전시"), ("골목밥상", "식당"), ("청류국수", "식당"),
        ("북촌담소", "카페"), ("연화당떡집", "디저트"), ("소나무뜰", "카페"), ("먹골전집", "술집"),
        ("돌담길산책로", "산책"), ("운현정원", "산책"),
    ],
    "여의도": [
        ("강바람카페", "카페"), ("윤슬다이닝", "식당"), ("한강뷰라운지", "술집"), ("샛강산책길", "산책"),
        ("여의나루베이커리", "디저트"), ("파크뷰전시홀", "전시"), ("노을식탁", "식당"), ("갈대밭길", "산책"),
        ("스카이라인펍", "술집"), ("모던빵집", "디저트"),
    ],
    "성수": [
        ("붉은벽돌창고", "카페"), ("성수공방전시", "전시"), ("골목양식당", "식당"), ("철길옆카페", "카페"),
        ("수제맥주공장", "술집"), ("연무장디저트", "디저트"), ("서울숲둘레길", "산책"), ("아뜰리에성수", "전시"),
        ("불향식당", "식당"), ("옥상정원바", "술집"),
    ],
    "연남": [
        ("연남서가", "카페"), ("경의선숲길", "산책"), ("동네파스타", "식당"), ("소금빵연구소", "디저트"),
        ("골목위스키바", "술집"), ("연남작은전시", "전시"), ("초록지붕카페", "카페"), ("매콤분식당", "식당"),
        ("나무그늘테라스", "카페"), ("밤산책바", "술집"),
    ],
}

# 카테고리별로 성격이 될 만한 태그 풀
TRAIT_POOL = {
    "카페": ["조용함", "조명", "좌석", "뷰", "사진", "넓이", "청결", "인테리어", "야외석", "단맛", "음료",
           "웨이팅", "대화", "혼잡도", "가성비", "영업시간", "반려동물", "접근성"],
    "식당": ["청결", "맵기", "단맛", "양", "재료", "메뉴수", "플레이팅", "웨이팅", "주차", "예약", "가성비",
           "추가요금", "접근성", "둘만의공간", "기념일", "혼잡도"],
    "전시": ["조용함", "사진", "넓이", "인테리어", "실내활동", "혼잡도", "접근성", "코스연계", "웨이팅", "가성비"],
    "산책": ["뷰", "야경", "사진", "산책", "코스연계", "혼잡도", "반려동물", "접근성", "실내활동", "주차"],
    "술집": ["조용함", "조명", "야경", "인테리어", "야외석", "영업시간", "추가요금", "가성비", "대화",
           "둘만의공간", "예약", "음료"],
    "디저트": ["단맛", "플레이팅", "음료", "사진", "인테리어", "웨이팅", "가성비", "넓이", "좌석"],
}

# 사람들이 대체로 같은 쪽을 원하는 태그. 반대쪽을 원하면 문장이 어색해집니다.
CONSENSUS_WANT = {
    "청결": "HIGH", "음료": "HIGH", "재료": "HIGH", "가성비": "HIGH", "접근성": "HIGH", "화장실": "HIGH",
    "주차": "HIGH", "뷰": "HIGH", "사진": "HIGH", "인테리어": "HIGH", "플레이팅": "HIGH", "좌석": "HIGH",
    "대화": "HIGH", "웨이팅": "LOW", "추가요금": "LOW", "혼잡도": "LOW", "야경": "HIGH", "코스연계": "HIGH",
    "산책": "HIGH", "기념일": "HIGH", "둘만의공간": "HIGH", "조용함": "HIGH", "양": "HIGH", "예약": "HIGH",
    "영업시간": "HIGH", "반려동물": "HIGH", "메뉴수": "HIGH", "넓이": "HIGH", "실내활동": "HIGH", "야외석": "HIGH",
}
# 조명, 맵기, 단맛은 취향이 갈리는 태그라 HIGH/LOW 를 반반으로 뽑습니다
# 장소 성격도 사람 취향과 같은 방향으로 치우치게 둡니다 (완전 50:50이면 부자연스럽습니다)
CONSENSUS_TRAIT_PROB = 0.6

SYNTH_NICKNAMES = [
    ("민준", "서연"), ("지호", "하은"), ("도윤", "수아"), ("시우", "지민"), ("예준", "채원"), ("주원", "다은"),
    ("현우", "유나"), ("건우", "시은"), ("우진", "소율"), ("선우", "예린"), ("연우", "가은"), ("정우", "나윤"),
]

POSITIVE_TAILS = ["좋았어요", "만족했어요", "괜찮았어요", "마음에 들었어요"]
NEGATIVE_TAILS = ["힘들었어요", "아쉬웠어요", "별로였어요", "불편했어요", "실망했어요"]

# 표현 끝부분 -> (연결형, 과거 종결형). 끝부분을 통째로 바꿉니다. 긴 키가 앞에 와야 합니다.
SUFFIX_RULES = [
    ("안 매워", ("안 매워서", "안 매웠어요")),
    ("안 맵", ("안 매워서", "안 매웠어요")),
    ("안 들", ("안 들려서", "안 들렸어요")),
    ("못 들어", ("못 들어가서", "못 들어갔어요")),
    ("들어", ("들어가서", "들어갔어요")),
    ("안 나", ("안 나와서", "안 나왔어요")),
    ("잘 나", ("잘 나와서", "잘 나왔어요")),
    ("보고 오", ("보고 오게 돼서", "보고 오게 됐어요")),
    ("하고 가", ("하고 가서", "하고 갔어요")),
    ("같이", ("같이 갈 수 있어서", "같이 갈 수 있었어요")),
    ("즐길", ("즐길 게 많아서", "즐길 게 많았어요")),
    ("볼거리", ("볼거리가 많아서", "볼거리가 많았어요")),
    ("늦게까지", ("늦게까지 해서", "늦게까지 했어요")),
    ("새벽까지", ("새벽까지 해서", "새벽까지 했어요")),
    ("밤늦게", ("밤늦게까지 해서", "밤늦게까지 했어요")),
    ("날에도", ("날에도 갈 수 있어서", "날에도 갈 수 있었어요")),
    ("기다", ("기다려서", "기다렸어요")),
    ("어울", ("어울려서", "어울렸어요")),
    ("배부르", ("배불러서", "배불렀어요")),
    ("북적", ("북적여서", "북적였어요")),
    ("감질", ("감질나서", "감질났어요")),
    ("부담", ("부담돼서", "부담됐어요")),
    ("예쁘", ("예뻐서", "예뻤어요")),
    ("나쁘", ("나빠서", "나빴어요")),
    ("아프", ("아파서", "아팠어요")),
    ("맛있", ("맛있어서", "맛있었어요")),
    ("멋", ("멋져서", "멋졌어요")),
    ("비싸", ("비싸서", "비쌌어요")),
    ("가격이 세", ("가격이 세서", "가격이 셌어요")),
    ("돼", ("돼서", "됐어요")),
    ("안 받", ("안 받아서", "안 받았어요")),
    ("위생이 안", ("위생이 안 좋아서", "위생이 안 좋았어요")),
    ("상태가 안", ("상태가 안 좋아서", "상태가 안 좋았어요")),
    ("안", ("안 돼서", "안 됐어요")),
    ("불가", ("불가라", "불가였어요")),
    ("동반", ("동반이 돼서", "동반이 됐어요")),
    ("시끄럽", ("시끄러워서", "시끄러웠어요")),
    ("더럽", ("더러워서", "더러웠어요")),
    ("촌스", ("촌스러워서", "촌스러웠어요")),
    ("어렵", ("어려워서", "어려웠어요")),
    ("어려", ("어려워서", "어려웠어요")),
    ("시끄러", ("시끄러워서", "시끄러웠어요")),
    ("더러", ("더러워서", "더러웠어요")),
    ("가까", ("가까워서", "가까웠어요")),
    ("아쉬", ("아쉬워서", "아쉬웠어요")),
    ("여유로", ("여유로워서", "여유로웠어요")),
    ("어두", ("어두워서", "어두웠어요")),
    ("쉬", ("쉬워서", "쉬웠어요")),
    ("좋", ("좋아서", "좋았어요")),
    ("많", ("많아서", "많았어요")),
    ("밝", ("밝아서", "밝았어요")),
    ("짧", ("짧아서", "짧았어요")),
    ("낡", ("낡아서", "낡았어요")),
    ("괜찮", ("괜찮아서", "괜찮았어요")),
    ("닫", ("닫아서", "닫았어요")),
    ("않", ("않아서", "않았어요")),
    ("좁", ("좁아서", "좁았어요")),
    ("작", ("작아서", "작았어요")),
    ("달", ("달아서", "달았어요")),
    ("있", ("있어서", "있었어요")),
    ("없", ("없어서", "없었어요")),
    ("위생적", ("위생적이라", "위생적이었어요")),
    ("감성적", ("감성적이라", "감성적이었어요")),
    ("적", ("적어서", "적었어요")),
    ("길", ("길어서", "길었어요")),
    ("멀", ("멀어서", "멀었어요")),
    ("힘들", ("힘들어서", "힘들었어요")),
    ("넓", ("넓어서", "넓었어요")),
    ("별로", ("별로여서", "별로였어요")),
    ("만석", ("만석이라", "만석이었어요")),
    ("분", ("분이라", "분이었어요")),
    ("분위기", ("분위기라", "분위기였어요")),
    ("딱", ("딱이라", "딱이었어요")),
    ("안쪽", ("안쪽이라", "안쪽이었어요")),
    ("바로 앞", ("바로 앞이라", "바로 앞이었어요")),
    ("위주", ("위주라", "위주였어요")),
    ("1메뉴", ("1메뉴라", "1메뉴였어요")),
    ("몇 개", ("몇 개라", "몇 개였어요")),
    ("심심한 맛", ("심심한 맛이라", "심심한 맛이었어요")),
    ("만한 길", ("만한 길이라", "만한 길이었어요")),
    ("금지", ("금지라", "금지였어요")),
    ("프렌들리", ("프렌들리라", "프렌들리였어요")),
    ("소음", ("소음이 있어서", "소음이 있었어요")),
]
# "-하다" 로 활용하는 어간
HADA_STEMS = [
    "조용", "차분", "고요", "깨끗", "청결", "깔끔", "담백", "달달", "달콤", "푸짐", "신선", "싱싱", "다양", "평범",
    "훌륭", "밍밍", "얼얼", "칼칼", "강", "약", "넉넉", "부족", "지저분", "프라이빗", "혼잡", "한산", "한적",
    "저렴", "착", "편", "불편", "단출", "근사", "슴슴", "널찍", "답답", "푹신", "딱딱", "떠들썩", "왁자지껄",
    "가능", "어둑",
]
# "X가 있어서" 로 푸는 명사 표현
NOUN_EXIST = [
    "테라스", "루프탑", "포토존", "소파", "산책로", "자릿세", "콜키지", "서비스 차지", "추가 요금", "추가요금",
    "추가 비용", "야외 테이블", "야외 자리", "브레이크타임", "실내 활동", "개별 룸",
]
# "X(이)라" 로 푸는 명사구 표현
NOUN_IRA = [
    "둘만의 공간", "독립된 공간", "넓은 매장", "좁은 매장", "밝은 조명", "어두운 조명", "신선한 재료",
    "합리적인 가격",
]
# 자연스럽게 못 푸는 표현은 건너뜁니다
SKIP_EXPRESSIONS = {"가격 대비", "재료 상태가", "매운", "순한", "환한", "가성비 최고", "야경 맛집", "사진 맛집",
                    "뷰맛집", "인생샷", "사진이 잘", "사진이 잘 안"}


def usable_forms(expression):
    """연결형이 원래 표현(정규식)에 걸려야 태그 추출기가 읽을 수 있습니다."""
    literal = expression.replace("[0-9]+", "30")
    forms = conjugate(literal)
    if forms is None or not re.search(expression, forms[0]):
        return None
    return forms


def has_batchim(char):
    code = ord(char) - 0xAC00
    return 0 <= code < 11172 and code % 28 != 0


def is_past_form(char):
    # 받침이 ㅆ 이면 과거형 (었, 았, 했, 됐, 렀 ...)
    code = ord(char) - 0xAC00
    return 0 <= code < 11172 and code % 28 == 20 and char != "있"


def present_form(conn):
    """연결형 -> 현재 종결형 (조용해서 -> 조용해요, 40분이라 -> 40분이에요)."""
    if conn.endswith("이라"):
        return conn[:-2] + "이에요"
    if conn.endswith("라"):
        return conn[:-1] + "예요"
    return conn[:-1] + "요"


def conjugate(expression):
    """표현 하나를 (연결형, 과거 종결형) 으로 만듭니다. 못 만들면 None."""
    if expression in SKIP_EXPRESSIONS:
        return None
    last = expression[-1]
    if is_past_form(last) or last in ("고", "서", "은", "는", "한", "된", "운"):
        return None
    if expression in NOUN_EXIST:
        particle = "이" if has_batchim(last) else "가"
        return expression + particle + " 있어서", expression + particle + " 있었어요"
    if expression in NOUN_IRA:
        if has_batchim(last):
            return expression + "이라", expression + "이었어요"
        return expression + "라", expression + "였어요"
    for stem in HADA_STEMS:
        if expression.endswith(stem):
            return expression + "해서", expression + "했어요"
    for key, (conn, past) in SUFFIX_RULES:
        if expression.endswith(key):
            head = expression[: -len(key)]
            return head + conn, head + past
    return None


def build_clause(rng, expression, cue_side):
    """표현 하나로 절을 만듭니다. cue_side: 'POS' / 'NEG' / None. 종결형 문장을 돌려줍니다."""
    literal = expression
    if "[0-9]+" in expression:
        literal = expression.replace("[0-9]+", str(rng.choice([30, 40, 50])))
    forms = conjugate(literal)
    if forms is None:
        raise AssertionError(f"활용할 수 없는 표현: {expression!r}")
    conn, past = forms
    if cue_side == "POS":
        sentence = conn + " " + rng.choice(POSITIVE_TAILS)
    elif cue_side == "NEG":
        sentence = conn + " " + rng.choice(NEGATIVE_TAILS)
    else:
        # 과거형이 어간을 바꾸는 표현(돼 -> 됐)은 현재형으로 씁니다
        sentence = past if re.search(expression, past) else present_form(conn)
    if not re.search(expression, sentence):
        raise AssertionError(f"표현 검증 실패: {expression!r} -> {sentence!r}")
    return sentence


def join_sentences(rng, sentences):
    """"~어요" 로 끝나는 문장들을 "는데 / 고 / ." 로 잇습니다."""
    out = sentences[0]
    previous = None
    for s in sentences[1:]:
        # 현재형 문장(조용해요) 뒤에는 "는데/고" 를 못 붙이므로 마침표로만 잇고, "는데" 는 연달아 쓰지 않습니다
        if not is_past_form(out[-3]):
            joiner = ". "
        elif previous == "는데 ":
            joiner = rng.choice(["고 ", ". "])
        else:
            joiner = rng.choice(["는데 ", "고 ", ". "])
        previous = joiner
        if joiner == ". ":
            out = out + ". " + s
        else:
            out = out[:-2] + joiner + s
    return out


def load_table():
    with MATCHING_TABLE.open(encoding="utf-8") as f:
        table = json.load(f)
    by_tag = {}
    for tag in table["tags"]:
        usable = {}
        for side in ("high", "low"):
            good = [e for e in tag[side] if usable_forms(e) is not None]
            if not good:
                raise AssertionError(f"{tag['name']} {side} 쪽에 쓸 표현이 없습니다")
            usable[side.upper()] = good
        by_tag[tag["name"]] = usable
    return by_tag


def make_review_text(rng, table, traits, wants, force_tags=None, max_clauses=3):
    """장소 성격과 사용자 취향으로 리뷰 본문을 만듭니다."""
    names = [t for t, _ in traits]
    k = rng.randint(1, min(max_clauses, len(names)))
    chosen = list(force_tags or [])
    # 사용자가 신경 쓰는 태그를 먼저 언급해야 문장의 감정이 별점과 맞습니다
    cared = [t for t in names if t not in chosen and t in wants]
    others = [t for t in names if t not in chosen and t not in wants]
    rng.shuffle(cared)
    rng.shuffle(others)
    chosen += (cared + others)[: max(0, k - len(chosen))]
    trait_map = dict(traits)
    sentences = []
    for tag in chosen:
        side = trait_map[tag]
        expression = rng.choice(table[tag][side])
        want = wants.get(tag)
        cue = None if want is None else ("POS" if want == side else "NEG")
        sentences.append(build_clause(rng, expression, cue))
    return join_sentences(rng, sentences)


def flip(side):
    return "LOW" if side == "HIGH" else "HIGH"


def random_traits(rng, category):
    pool = list(TRAIT_POOL[category])
    rng.shuffle(pool)
    result = []
    for tag in pool[: rng.randint(2, 4)]:
        if tag in CONSENSUS_WANT:
            side = CONSENSUS_WANT[tag] if rng.random() < CONSENSUS_TRAIT_PROB else flip(CONSENSUS_WANT[tag])
        else:
            side = rng.choice(["HIGH", "LOW"])
        result.append([tag, side])
    return result


# 인사동 10곳은 데모 시나리오에 맞춰 성격을 손으로 정합니다
DEMO_PLACE_TRAITS = {
    "SEED-0001": [["조용함", "HIGH"], ["음료", "HIGH"], ["웨이팅", "LOW"]],
    "SEED-0002": [["조용함", "HIGH"], ["사진", "HIGH"], ["실내활동", "HIGH"]],
    "SEED-0003": [["재료", "HIGH"], ["청결", "HIGH"], ["가성비", "HIGH"]],
    "SEED-0004": [["맵기", "LOW"], ["양", "HIGH"], ["인테리어", "HIGH"]],
    "SEED-0005": [["웨이팅", "HIGH"], ["혼잡도", "HIGH"], ["좌석", "LOW"]],
    "SEED-0006": [["단맛", "HIGH"], ["플레이팅", "HIGH"], ["웨이팅", "LOW"]],
    "SEED-0007": [["조용함", "HIGH"], ["뷰", "HIGH"], ["웨이팅", "LOW"], ["음료", "HIGH"]],
    "SEED-0008": [["조용함", "LOW"], ["웨이팅", "HIGH"], ["가성비", "HIGH"]],
    "SEED-0009": [["산책", "HIGH"], ["혼잡도", "HIGH"], ["코스연계", "HIGH"]],
    "SEED-0010": [["산책", "HIGH"], ["조용함", "HIGH"], ["반려동물", "HIGH"]],
}

# 데모 커플 취향: 맵기는 반대 (SPLIT), 조용함 HIGH 와 웨이팅 LOW 는 일치
DEMO_WANTS = {
    DEMO_OWNER: {"조용함": "HIGH", "웨이팅": "LOW", "맵기": "LOW", "음료": "HIGH", "청결": "HIGH",
                 "재료": "HIGH", "사진": "HIGH", "혼잡도": "LOW"},
    DEMO_MEMBER: {"조용함": "HIGH", "웨이팅": "LOW", "맵기": "HIGH", "음료": "HIGH", "청결": "HIGH",
                  "인테리어": "HIGH", "플레이팅": "HIGH", "좌석": "HIGH", "단맛": "HIGH"},
}

DEMO_ADDED_ONLY = ["SEED-0009", "SEED-0010"]
DEMO_ONE_SIDE_ONLY = "SEED-0006"
DEMO_CANDIDATES = ["SEED-0007", "SEED-0008"]


def make_places(rng):
    places = []
    n = 0
    for region, names in PLACE_NAMES.items():
        lat0, lng0 = REGION_CENTER[region]
        for name, category in names:
            n += 1
            pid = f"SEED-{n:04d}"
            places.append({
                "providerPlaceId": pid,
                "name": name,
                "address": f"{REGION_STREET[region]} {rng.randint(3, 98)}",
                "region": region,
                "category": category,
                "priceBand": rng.randint(1, 4),
                "latitude": round(lat0 + rng.uniform(-0.004, 0.004), 6),
                "longitude": round(lng0 + rng.uniform(-0.004, 0.004), 6),
                "traits": DEMO_PLACE_TRAITS.get(pid) or random_traits(rng, category),
            })
    return places


def random_wants(rng, all_tags):
    tags = list(all_tags)
    rng.shuffle(tags)
    wants = {}
    for tag in tags[: rng.randint(7, 9)]:
        wants[tag] = CONSENSUS_WANT.get(tag) or rng.choice(["HIGH", "LOW"])
    return dict(sorted(wants.items()))


def make_users(rng, all_tags):
    users = [
        {"email": DEMO_OWNER, "nickname": "도현", "password": PASSWORD, "wants": DEMO_WANTS[DEMO_OWNER], "bias": 0.0},
        {"email": DEMO_MEMBER, "nickname": "지우", "password": PASSWORD, "wants": DEMO_WANTS[DEMO_MEMBER], "bias": 0.0},
    ]
    groups = [{"owner": DEMO_OWNER, "member": DEMO_MEMBER, "name": "도현과 지우", "plan": "PREMIUM"}]
    for i, (a, b) in enumerate(SYNTH_NICKNAMES, start=1):
        pair = []
        for suffix, nick in (("a", a), ("b", b)):
            pair.append({
                "email": f"couple{i:02d}{suffix}@lovemap.dev",
                "nickname": nick,
                "password": PASSWORD,
                "wants": random_wants(rng, all_tags),
                "bias": rng.choice([0.7, -0.7, 0.0, 0.0]),
            })
        users.extend(pair)
        groups.append({"owner": pair[0]["email"], "member": pair[1]["email"], "name": f"{a}과 {b}", "plan": "FREE"})
    return users, groups


def match_score(traits, wants):
    hits = [1.0 if wants[tag] == side else -1.0 for tag, side in traits if tag in wants]
    return sum(hits) / len(hits) if hits else 0.0


def synth_rating(rng, place, user):
    raw = 3 + match_score(place["traits"], user["wants"]) * 1.5 + user["bias"] + rng.gauss(0, 0.5)
    return max(1, min(5, int(round(raw))))


def random_date(rng, start=date(2026, 3, 1), end=date(2026, 8, 31)):
    return start + timedelta(days=rng.randint(0, (end - start).days))


def make_synthetic_reviews(rng, table, places, users, groups):
    by_email = {u["email"]: u for u in users}
    by_pid = {p["providerPlaceId"]: p for p in places}
    insa = [p["providerPlaceId"] for p in places
            if p["region"] == "인사동" and p["providerPlaceId"] not in DEMO_ADDED_ONLY]
    others = [p["providerPlaceId"] for p in places if p["region"] != "인사동"]
    reviews = []
    seen = set()

    def add(email, pid, day, owner):
        key = (email, pid, day)
        if key in seen:
            return
        seen.add(key)
        user = by_email[email]
        place = by_pid[pid]
        reviews.append({
            "userEmail": email,
            "providerPlaceId": pid,
            "visitedOn": day.isoformat(),
            "rating": synth_rating(rng, place, user),
            "content": make_review_text(rng, table, place["traits"], user["wants"]),
            "groupOwnerEmail": owner,
        })

    for idx, g in enumerate(groups[1:]):
        owner, member = g["owner"], g["member"]
        shared_n = rng.randint(7, 9)
        # 인사동을 2~3곳 섞어 추천 후보에 공동 평가자가 생기게 합니다
        insa_pick = rng.sample(insa, rng.randint(2, 3))
        candidate = DEMO_CANDIDATES[idx % 2]
        if candidate not in insa_pick:
            insa_pick[0] = candidate
        shared = insa_pick + rng.sample(others, shared_n - len(insa_pick))
        rng.shuffle(shared)
        for pid in shared:
            day = random_date(rng)
            add(owner, pid, day, owner)
            add(member, pid, day, owner)
        # 각자 따로 간 곳 1~3곳 (겹침 70% 이상 유지)
        for email in (owner, member):
            pool = [pid for pid in others + insa if pid not in shared]
            for pid in rng.sample(pool, rng.randint(1, 3)):
                add(email, pid, random_date(rng), owner)
    return reviews


def make_demo_reviews(rng, table, places, users):
    by_email = {u["email"]: u for u in users}
    by_pid = {p["providerPlaceId"]: p for p in places}

    def review(email, pid, day, rating, force):
        return {
            "userEmail": email,
            "providerPlaceId": pid,
            "visitedOn": day,
            "rating": rating,
            "content": make_review_text(rng, table, by_pid[pid]["traits"], by_email[email]["wants"], force_tags=force),
            "groupOwnerEmail": DEMO_OWNER,
        }

    plan = [
        # (장소, 도현 별점, 지우 별점, 날짜, 반드시 언급할 태그)
        ("SEED-0001", 5, 5, "2026-07-18", ["조용함"]),
        ("SEED-0002", 4, 4, "2026-08-02", ["조용함", "사진"]),
        ("SEED-0003", 5, 4, "2026-08-09", ["재료"]),
        ("SEED-0004", 5, 2, "2026-08-15", ["맵기"]),
        ("SEED-0005", 2, 3, "2026-08-23", ["웨이팅"]),
    ]
    reviews = []
    for pid, r1, r2, day, force in plan:
        reviews.append(review(DEMO_OWNER, pid, day, r1, force))
        reviews.append(review(DEMO_MEMBER, pid, day, r2, force))
    # 도현의 달빛찻집 재방문
    reviews.append(review(DEMO_OWNER, "SEED-0001", "2026-08-29", 4, ["음료"]))
    # 지우만 간 곳 (otherReviewsLocked 데모)
    reviews.append(review(DEMO_MEMBER, DEMO_ONE_SIDE_ONLY, "2026-08-30", 4, ["단맛"]))
    return reviews


def validate(places, users, groups, reviews):
    assert len(places) == 40 and len(users) == 26 and len(groups) == 13
    keys = set()
    for r in reviews:
        key = (r["userEmail"], r["providerPlaceId"], r["visitedOn"])
        assert key not in keys, f"중복 리뷰 {key}"
        keys.add(key)
        assert 1 <= r["rating"] <= 5 and r["content"]
    owner_places = {r["providerPlaceId"] for r in reviews if r["userEmail"] == DEMO_OWNER}
    member_places = {r["providerPlaceId"] for r in reviews if r["userEmail"] == DEMO_MEMBER}
    assert DEMO_ONE_SIDE_ONLY not in owner_places and DEMO_ONE_SIDE_ONLY in member_places
    for pid in DEMO_ADDED_ONLY + DEMO_CANDIDATES:
        assert pid not in owner_places and pid not in member_places, pid

    synth_emails = {u["email"] for u in users} - {DEMO_OWNER, DEMO_MEMBER}
    insa_ids = {p["providerPlaceId"] for p in places if p["region"] == "인사동"}
    insa_with_synth = {r["providerPlaceId"] for r in reviews
                       if r["userEmail"] in synth_emails and r["providerPlaceId"] in insa_ids}
    assert len(insa_with_synth) >= 6, sorted(insa_with_synth)
    assert set(DEMO_CANDIDATES) <= insa_with_synth

    by_user = {}
    for r in reviews:
        by_user.setdefault(r["userEmail"], set()).add((r["providerPlaceId"], r["visitedOn"]))
    for g in groups[1:]:
        a, b = by_user[g["owner"]], by_user[g["member"]]
        for mine, other in ((a, b), (b, a)):
            assert 8 <= len(mine) <= 12, (g["name"], len(mine))
            assert len(mine & other) / len(mine) >= 0.7, (g["name"], len(mine & other), len(mine))
    return insa_with_synth


def main():
    rng = random.Random(SEED)
    table = load_table()
    places = make_places(rng)
    users, groups = make_users(rng, list(table.keys()))
    reviews = make_synthetic_reviews(rng, table, places, users, groups)
    reviews += make_demo_reviews(rng, table, places, users)
    reviews.sort(key=lambda r: (r["visitedOn"], r["userEmail"], r["providerPlaceId"]))
    insa_with_synth = validate(places, users, groups, reviews)

    data = {
        "places": places,
        "users": users,
        "groups": groups,
        "reviews": reviews,
        "demo": {
            "ownerEmail": DEMO_OWNER,
            "memberEmail": DEMO_MEMBER,
            "addedOnly": DEMO_ADDED_ONLY,
            "oneSideOnly": DEMO_ONE_SIDE_ONLY,
            "candidates": DEMO_CANDIDATES,
        },
    }
    OUTPUT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    demo_owner = sum(1 for r in reviews if r["userEmail"] == DEMO_OWNER)
    demo_member = sum(1 for r in reviews if r["userEmail"] == DEMO_MEMBER)
    print(f"places={len(places)} users={len(users)} groups={len(groups)} reviews={len(reviews)}")
    print(f"demo reviews: 도현={demo_owner} 지우={demo_member}")
    print(f"인사동 places with synthetic reviews: {len(insa_with_synth)}")
    print(f"written: {OUTPUT}")


if __name__ == "__main__":
    main()
