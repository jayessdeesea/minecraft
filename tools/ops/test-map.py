"""Regenerates the generated regions of docs/test-map.md from the source tree.

    python -I tools/ops/test-map.py [--check]

Reads every module's main and test Java sources, builds the class-level dependency graph of the main code, files classes
and test classes into areas, and rewrites each `<!-- generated:NAME -->` ... `<!-- /generated:NAME -->` region of the
doc; the prose between them is left alone. The rules for what counts as a reference and where a class or test is filed
are the doc's (its opening section), and `resolver` and `build` below. `--check` writes nothing and exits 1 when a
region would change. A main class no rule places, and a name in AREAS that names no class, are printed to stderr.
"""
import os
import re
import sys
from collections import Counter, defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DOC = os.path.join(ROOT, "docs", "test-map.md")
P = "io.github.jayessdeesea.minecraft."

# Which modules each module's main code may name: its compile-scope Maven dependencies and itself (`app` has `landing`
# at test scope only). Its tests may also name what its tests depend on.
MODULES = {"protocol": {"protocol"}, "command": {"command"}, "bot": {"bot", "protocol", "command"},
           "command-ai": {"command-ai", "command"}, "landing": {"landing"},
           "app": {"app", "protocol", "command", "command-ai", "bot"}}
TEST_MODULES = dict(MODULES, app=MODULES["app"] | {"landing"})

TIERS = [("Foundations", ["values", "contract", "wire", "world-model"]),
         ("Getting about", ["navigation", "movement"]),
         ("Fighting", ["combat"]),
         ("Trades", ["dig-build", "craft", "stores-supply", "food-farm", "wood"]),
         ("The soldier", ["soldier"]),
         ("Command", ["squad", "platoon", "model"]),
         ("Garrison shell", ["garrison-shell"]),
         ("Landing queue", ["landing"]),
         ("Test harness", ["simulator", "suite"])]

# Classes of bot, command and app filed by name, where their package doesn't say their area. A name here that names no
# such class is printed as stale. A command class not named here is filed by layer: values at layer 0-1, else platoon.
AREAS = {
    "contract": "Order Report SoldierStatus Soldier Progress DutyOrder DutyYield Kit Script Setbacks Directive "
                "SquadOrder Supply",
    "movement": "Gait Mover Body Trip GateHabit Hands",
    "combat": "Contact Engagement Ballistics Strike ContactLog Quiver ReactToContact SquadContact HostileMobRegistry "
              "WeaponStats",
    "navigation": "Clearing Kept Footing Stairway AccessRoute WayOut RouteEffort PlanMemo TerrainSurveyor",
    "dig-build": "Digger Miner Breacher Staircase Clearer Filler Mason Builder Lighting Lamplighter Torchmaker "
                 "Perimeter MineHead Fixtures",
    "food-farm": "Farmer Herder Hunter Livestock Rations Forage WaterCarrier BakerDuty FarmDuties Subsistence "
                 "FarmMission",
    "wood": "Forester TreeFarm TreeChunk WoodDuties",
    "stores-supply": "Quartermaster Depositor Pack ChestWindow Handover StockLevels SupplyRun Gatherer Scavenger Hotbar "
                     "Fitter MetalPool ResourceSurveyor Armourer",
    "craft": "Workshop Crafter",
    "soldier": "DeterministicSoldier SoldierActivity ActivityTrack EndermanGaze",
    "squad": "SquadLeader DrillSquadLeader Cohesion CrossLevel Tasking Recovery LinkUp Readiness Formation Formations "
             "Logstat ReviewOrders TaskRegister WorkOrders SquadStatus ShareWatch Escort Resupply",
    "platoon": "PlatoonLeader DrillPlatoonLeader BasePlan Plan Objective Phase PhaseTracker Keep Flatten FlattenPlan "
               "Renewable ProductionLevels DayWork OperatorCommands PlatoonSergeant ScoutMission WorkArea LevelPlan "
               "LevelPace PatrolBase Role RegistersSaid Site ResourceSite Foothold Stockpile ArmourCheck",
}
# Then by package, the first prefix that matches.
PACKAGES = [("protocol", "wire"), ("landing", "landing"), ("command.ai", "model"), ("bot.world", "world-model"),
            ("bot.nav", "navigation"), ("bot.engineer", "navigation"), ("bot.craft", "craft"),
            ("bot.farm", "food-farm"), ("app", "garrison-shell")]
# A test class with no main class of its name is filed by the first prefix of its name here, before falling back to the
# highest-layer class it names - which for a squad sim is the shell's runner, saying nothing of what it tests.
THEMES = [("Sim", "simulator"), ("Architecture", "suite"), ("WaterCombat", "combat"), ("Combat", "combat"),
          ("Movement", "movement"),
          ("Mine", "dig-build"), ("Wall", "dig-build"), ("CampLight", "dig-build"), ("Food", "food-farm"),
          ("Routing", "navigation"), ("FlattenTops", "platoon"), ("TaskOrganisation", "platoon"),
          ("DecisionPace", "squad"), ("Squad", "squad"), ("Soldier", "soldier")]
# The classes whose reach the doc's prose leans on.
KEY = ["Pathfinder", "Footing", "Clearing", "Mover", "Gait", "Body", "DeterministicSoldier", "Contact", "Hardness",
       "Digger", "Miner", "Farmer", "Forester", "DrillSquadLeader", "DrillPlatoonLeader", "SoldierRunner"]

SIM = {"SimServer", "SimClock", "SimWorlds", "SquadLeaderScenarios"}
# A squad sim drives soldiers through the runner against SimServer; a test of the simulator's parts, or of a decision on
# SimClock alone, is a sim part.
FAKES = {"FakeCraftingServer", "FakeChestServer", "Battle", "SyntheticWorld", "Terrain", "Scenario"}
KINDS = ["unit/table", "soldier sim", "sim part", "squad sim", "stuck replay", "by-name IT", "architecture", "support"]
TEST_ANNOTATION = re.compile(r"@(Test|ParameterizedTest|RepeatedTest|TestFactory)\b")
SUFFIX = re.compile(r"(SimulationTest|EvalsTest|EvalTest|LiveIT|ScenarioIT|IT|Tests|Test)$")
# One pass, so a `//` inside a string is a string's and a `"` inside a comment is a comment's.
LEXEME = re.compile(r'""".*?"""|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'|//[^\n]*|/\*.*?\*/', re.S)
PACKAGE = re.compile(r"^\s*package\s+([\w.]+);", re.M)
IMPORT = re.compile(r"^\s*import\s+(static\s+)?([\w.]+?)(\.\*)?;", re.M)
DECLARED = re.compile(r"\b(?:class|record|enum|interface)\s+([A-Z]\w*)")
QUALIFIED = re.compile(re.escape(P) + r"[\w.]*[A-Z]\w*")
# A capitalised word not reached through a dot: `Digger.Progress` names Digger's own type, not command's Progress.
WORD = re.compile(r"(?<![\w.])[A-Z][A-Za-z0-9]*\b")


def strip(src):
    return LEXEME.sub(lambda m: " " if m[0].startswith("/") else '""', src)


def scan(kind):
    classes = {}
    for module in MODULES:
        top = os.path.join(ROOT, module, "src", kind, "java")
        for folder, _, names in os.walk(top):
            for name in names:
                if not name.endswith(".java") or name in ("package-info.java", "module-info.java"):
                    continue
                path = os.path.join(folder, name)
                with open(path, encoding="utf-8", errors="replace") as f:
                    src = f.read()
                found = PACKAGE.search(src)
                pkg = found[1] if found else ""
                body = strip(src)
                code = IMPORT.sub(" ", PACKAGE.sub(" ", body))
                classes[pkg + "." + name[:-5]] = dict(
                    module=module, kind=kind, pkg=pkg.replace(P, ""), name=name[:-5], src=src, body=body,
                    code=code, declared=set(DECLARED.findall(code)) - {name[:-5]},
                    imports=[(bool(s), path_, bool(star)) for s, path_, star in IMPORT.findall(body)],
                    lines=src.count("\n") + 1, path=os.path.relpath(path, ROOT).replace("\\", "/"))
    return classes


def resolver(main):
    """A class names another when it imports it (a class, or a static import from it), names it fully qualified, or
    names it by its simple name where that is in scope: the same package, or a package imported whole - and not a type
    of its own by that name."""
    by_fq = {P + c["pkg"] + "." + c["name"]: fq for fq, c in main.items()}

    def refs(c, self_fq=None):
        allowed = (TEST_MODULES if c.get("kind") == "test" else MODULES)[c["module"]]

        def ok(fq):
            return fq is not None and fq != self_fq and main[fq]["module"] in allowed

        out = set()
        whole = {P + c["pkg"]}
        for static, path_, star in c["imports"]:
            if star and not static:
                whole.add(path_)
                continue
            parts = path_.split(".")
            for i in range(len(parts), 0, -1):  # the longest prefix that is a class: a static member's class
                fq = by_fq.get(".".join(parts[:i]))
                if fq:
                    if ok(fq):
                        out.add(fq)
                    break
        for q in set(QUALIFIED.findall(c["code"])):
            parts = q.split(".")
            for i in range(len(parts), 0, -1):
                fq = by_fq.get(".".join(parts[:i]))
                if fq:
                    if ok(fq):
                        out.add(fq)
                    break
        for word in set(WORD.findall(c["code"])) - c["declared"]:
            for package in whole:
                fq = by_fq.get(package + "." + word)
                if ok(fq):
                    out.add(fq)
        return out
    return refs


def components(graph):
    """Tarjan's strongly connected components, iteratively; emitted dependencies first."""
    index, low, on, stack, out, counter = {}, {}, set(), [], [], [0]
    for root in graph:
        if root in index:
            continue
        work = [(root, iter(sorted(graph[root])))]
        index[root] = low[root] = counter[0]; counter[0] += 1
        stack.append(root); on.add(root)
        while work:
            v, it = work[-1]
            advanced = False
            for w in it:
                if w not in index:
                    index[w] = low[w] = counter[0]; counter[0] += 1
                    stack.append(w); on.add(w)
                    work.append((w, iter(sorted(graph[w]))))
                    advanced = True
                    break
                if w in on:
                    low[v] = min(low[v], index[w])
            if advanced:
                continue
            work.pop()
            if work:
                low[work[-1][0]] = min(low[work[-1][0]], low[v])
            if low[v] == index[v]:
                comp = []
                while True:
                    w = stack.pop(); on.discard(w); comp.append(w)
                    if w == v:
                        break
                out.append(comp)
    return out


def area_of(c):
    if c["module"] in ("bot", "command", "app"):
        for area, names in AREAS.items():
            if c["name"] in names.split():
                return area
    for prefix, area in PACKAGES:
        if c["pkg"] == prefix or c["pkg"].startswith(prefix + "."):
            return area
    if c["pkg"] == "command":
        return "values" if c["layer"] <= 1 else "platoon"
    return None


def build():
    main, tests = scan("main"), scan("test")
    refs = resolver(main)
    graph = {fq: refs(c, fq) for fq, c in main.items()}
    comps = components(graph)
    comp_of = {v: i for i, comp in enumerate(comps) for v in comp}
    layer = {}
    for i, comp in enumerate(comps):
        layer[i] = 1 + max((layer[comp_of[w]] for v in comp for w in graph[v] if comp_of[w] != i), default=-1)
    for fq, c in main.items():
        c["layer"] = layer[comp_of[fq]]
    unplaced = sorted(fq for fq, c in main.items() if area_of(c) is None)
    for c in main.values():
        c["area"] = area_of(c) or "unplaced"
    named_here = {c["name"] for c in main.values() if c["module"] in ("bot", "command", "app")}
    stale = sorted(n for names in AREAS.values() for n in names.split() if n not in named_here)

    closure = {}

    def reach_of(fq):
        if fq not in closure:
            seen, todo = set(), [fq]
            while todo:
                for w in graph[todo.pop()]:
                    if w not in seen:
                        seen.add(w); todo.append(w)
            closure[fq] = seen
        return closure[fq]

    by_name = defaultdict(list)
    for fq, c in main.items():
        by_name[c["name"]].append(fq)
    test_names = {t["name"] for t in tests.values()}
    support_refs = defaultdict(set)
    for fq, t in tests.items():
        t["direct"] = refs(t)
        t["uses"] = {n for n in set(WORD.findall(t["code"])) if n in test_names and n != t["name"]}
        t["is_test"] = bool(TEST_ANNOTATION.search(t["code"]))
        support_refs[t["name"]] |= t["direct"]
    for t in tests.values():
        # a test that drives the code only through a support class (a fake world) tests what that class names
        named = set(t["direct"])
        if not named:
            for u in t["uses"]:
                named |= support_refs.get(u, set())
        t["named"] = named
        reach = set(named)
        for d in named:
            reach |= reach_of(d)
        t["reach"] = reach
        t["slow"] = bool(re.search(r'@Tag\(\s*"slow"\s*\)', t["src"]))
        t["count"] = len(TEST_ANNOTATION.findall(t["code"]))
        stem = SUFFIX.sub("", t["name"])
        subject = [d for d in t["direct"] if main[d]["name"] == stem] or \
                  [fq for fq in by_name.get(stem, []) if main[fq]["module"] in TEST_MODULES[t["module"]]][:1]
        t["subject"] = main[subject[0]]["name"] if subject else None
        theme = next((area for prefix, area in THEMES if t["name"].startswith(prefix)), None)
        if t["module"] in ("landing", "protocol"):
            t["area"], t["inferred"] = ("landing" if t["module"] == "landing" else "wire"), False
        elif subject:
            t["area"], t["inferred"] = main[subject[0]]["area"], False
        elif theme:
            t["area"], t["inferred"] = theme, False
        elif named:
            top = max(named, key=lambda f: (main[f]["layer"], main[f]["name"]))
            t["area"], t["inferred"] = main[top]["area"], True
        else:
            t["area"], t["inferred"] = None, True
        t["kind"] = kind_of(t, {main[d]["name"] for d in t["direct"]})
    return main, tests, graph, comps, unplaced, stale, reach_of


def kind_of(t, names):
    if t["name"].endswith("IT"):
        return "by-name IT"
    if t["name"] == "StuckEvalsTest":
        return "stuck replay"
    if not t["is_test"]:
        return "support"
    if t["name"] == "ArchitectureTest":
        return "architecture"
    if t["uses"] & SIM:
        return "squad sim" if "SimServer" in t["uses"] and "SoldierRunner" in names else "sim part"
    if t["uses"] & FAKES:
        return "soldier sim"
    return "unit/table"


def regions(main, tests, graph, comps):
    tier_of = {a: tier for tier, areas in TIERS for a in areas}
    real = [t for t in tests.values() if t["kind"] != "support"]
    out = {}

    rows = ["| Tier | Area | Main classes | Main lines | Own test classes | Test lines | Test classes reaching it |",
            "|---|---|---|---|---|---|---|"]
    for tier, areas in TIERS:
        for a in areas:
            mc = [c for c in main.values() if c["area"] == a]
            own = [t for t in real if t["area"] == a]
            reaching = sum(1 for t in real if any(main[d]["area"] == a for d in t["reach"]))
            rows.append(f"| {tier} | {a} | {len(mc)} | {sum(c['lines'] for c in mc)} | {len(own)} | "
                        f"{sum(t['lines'] for t in own)} | {reaching} |")
    out["tiers"] = rows

    by_name = {c["name"]: fq for fq, c in main.items()}
    rows = ["| Class | Area | Layer | Test classes reaching it | Naming it directly |", "|---|---|---|---|---|"]
    for n in KEY:
        fq = by_name.get(n)
        if fq:
            reach = sum(1 for t in real if fq in t["reach"])
            direct = sum(1 for t in real if fq in t["direct"])
            rows.append(f"| `{n}` | {main[fq]['area']} | {main[fq]['layer']} | {reach} | {direct} |")
    out["key"] = rows

    rows = []
    for comp in sorted((c for c in comps if len(c) > 1), key=lambda c: (-len(c), sorted(c))):
        names = sorted(main[v]["name"] for v in comp)
        modules = sorted({main[v]["module"] for v in comp})
        rows.append(f"- {len(comp)} classes ({', '.join(modules)}): {', '.join('`' + n + '`' for n in names)}")
    out["cycles"] = rows

    # Each edge out of a cycle from a class to one in a tier listed after its own.
    rank = {a: i for i, (_, areas) in enumerate(TIERS) for a in areas}
    comp_of = {v: i for i, comp in enumerate(comps) for v in comp}
    up = defaultdict(list)
    for fq, c in main.items():
        for d in graph[fq]:
            a, b = c["area"], main[d]["area"]
            if a in rank and b in rank and rank[b] > rank[a] and comp_of[fq] != comp_of[d]:
                up[fq].append(d)
    rows = [f"- `{main[fq]['name']}` ({main[fq]['area']}) reads "
            + ", ".join(f"`{main[d]['name']}` ({main[d]['area']})" for d in sorted(ds, key=lambda d: main[d]["name"]))
            for fq, ds in sorted(up.items(), key=lambda kv: (rank[main[kv[0]]["area"]], main[kv[0]]["name"]))]
    out["upward"] = rows or ["- none"]

    rows = ["| Tier | " + " | ".join(k for k in KINDS if k != "support") + " | Test lines |",
            "|---|" + "---|" * (len(KINDS) - 1) + "---|"]
    for tier, areas in TIERS:
        ts = [t for t in real if t["area"] in areas]
        counts = Counter(t["kind"] for t in ts)
        rows.append(f"| {tier} | " + " | ".join(str(counts[k] or "-") for k in KINDS if k != "support") +
                    f" | {sum(t['lines'] for t in ts)} |")
    rest = [t for t in real if t["area"] not in tier_of]
    if rest:
        counts = Counter(t["kind"] for t in rest)
        rows.append("| Unplaced | " + " | ".join(str(counts[k] or "-") for k in KINDS if k != "support") +
                    f" | {sum(t['lines'] for t in rest)} |")
    out["kinds"] = rows

    rows = []
    groups = [(tier, areas) for tier, areas in TIERS] + [("Unplaced", [None, "unplaced"])]
    for tier, areas in groups:
        ts = [t for t in real if t["area"] in areas]
        if not ts:
            continue
        rows += [f"### {tier}", ""]
        for a in areas:
            ats = sorted((t for t in ts if t["area"] == a), key=lambda t: (KINDS.index(t["kind"]), t["name"]))
            if not ats:
                continue
            rows += [f"#### {a or 'no main class named'} ({len(ats)})", "",
                     "| Test | Kind | Lines | Tests | Slow | Subject |", "|---|---|---|---|---|---|"]
            for t in ats:
                subject = t["subject"] or ("?" if t["inferred"] else "-")
                rows.append(f"| [{t['name']}](../{t['path']}) | {t['kind']} | {t['lines']} | {t['count']} | "
                            f"{'slow' if t['slow'] else ''} | {subject} |")
            rows.append("")
    support = sorted(t["name"] for t in tests.values() if t["kind"] == "support")
    rows += ["### Test support", "", ", ".join(f"`{n}`" for n in support), ""]
    out["appendix"] = rows

    total = sum(1 for _ in tests.values())
    out["totals"] = [f"{total} test files: {len(real)} test classes and {len(support)} support classes; "
                     f"{sum(t['lines'] for t in tests.values())} lines of tests against "
                     f"{sum(c['lines'] for c in main.values())} of main code, {len(main)} main classes in "
                     f"{1 + max(c['layer'] for c in main.values())} layers once each cycle is taken as one."]
    return out


def rewrite(text, generated):
    for name, rows in generated.items():
        pattern = re.compile(r"(<!-- generated:%s -->\n).*?(<!-- /generated:%s -->)" % (name, name), re.S)
        if not pattern.search(text):
            raise SystemExit(f"docs/test-map.md has no generated:{name} region")
        body = "\n".join(rows).rstrip("\n") + "\n"
        text = pattern.sub(lambda m: m[1] + body + m[2], text)
    return text


def main_():
    main, tests, graph, comps, unplaced, stale, _ = build()
    for fq in unplaced:
        print("unplaced: " + fq + " - give it an area in AREAS", file=sys.stderr)
    for n in stale:
        print("stale: " + n + " in AREAS names no class of bot, command or app", file=sys.stderr)
    with open(DOC, encoding="utf-8") as f:
        old = f.read()
    new = rewrite(old, regions(main, tests, graph, comps))
    if "--check" in sys.argv:
        print("docs/test-map.md is " + ("current" if new == old else "stale: run tools/ops/test-map.py"))
        sys.exit(0 if new == old else 1)
    if new != old:
        with open(DOC, "w", encoding="utf-8", newline="\n") as f:
            f.write(new)
    print("docs/test-map.md " + ("rewritten" if new != old else "already current"))


if __name__ == "__main__":
    main_()
