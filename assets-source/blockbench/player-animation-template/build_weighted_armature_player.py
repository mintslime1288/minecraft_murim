"""Build a continuous weighted Blockbench armature from the current wide skin.

This is a Free Model editing source. The existing Java/PAL runtime does not
consume Blockbench armatures directly. A local skin input must never be pushed.
"""

import argparse
import copy
import json
import math
import uuid
from collections import Counter
from pathlib import Path


HERE = Path(__file__).resolve().parent
PUBLIC = HERE / "player_current_java_editable.bbmodel"
OUTPUT = HERE / "player_current_weighted_armature.bbmodel"


def uid(label):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "murimrpg/weighted-player/" + label))


BONE_SPEC = [
    ("root", None, (0, 0, 0), 12),
    ("waist", "root", (0, 12, 0), 4),
    ("body", "waist", (0, 16, 0), 4),
    ("chest", "body", (0, 20, 0), 4),
    ("neck", "chest", (0, 24, 0), 2),
    ("head", "neck", (0, 26, 0), 6),
]
for side, sign in (("right", 1), ("left", -1)):
    x = sign * 6
    BONE_SPEC.extend([
        (side + "Arm", "chest", (sign * 5, 22, 0), 3),
        (side + "UpperArm", side + "Arm", (x, 20, 0), 2),
        (side + "Elbow", side + "UpperArm", (x, 18, 0), 2),
        (side + "Forearm", side + "Elbow", (x, 16, 0), 3),
        (side + "Wrist", side + "Forearm", (x, 13, 0), 1),
        (side + "Hand", side + "Wrist", (x, 12, 0), 1),
        (side + "Leg", "waist", (sign * 2, 12, 0), 2),
        (side + "Thigh", side + "Leg", (sign * 2, 9, 0), 3),
        (side + "Knee", side + "Thigh", (sign * 2, 6, 0), 2),
        (side + "Shin", side + "Knee", (sign * 2, 4, 0), 2),
        (side + "Ankle", side + "Shin", (sign * 2, 2, 0), 2),
        (side + "Foot", side + "Ankle", (sign * 2, 0, 0), 2),
    ])


def interpolate(points, y):
    """Two adjacent bones share each transition; every result sums to one."""
    points = sorted(points, reverse=True)
    if y >= points[0][0]:
        return {points[0][1]: 1.0}
    if y <= points[-1][0]:
        return {points[-1][1]: 1.0}
    for (hi, upper), (lo, lower) in zip(points, points[1:]):
        if lo <= y <= hi:
            fraction = (y - lo) / (hi - lo)
            return {upper: fraction, lower: 1 - fraction}
    raise AssertionError(y)


BODY_BANDS = (12, 13, 18, 19, 20, 24)
HEAD_BANDS = (24, 25, 26, 32)
ARM_BANDS = (12, 13, 17, 18, 19, 23, 24)
LEG_BANDS = (0, 1, 2, 5, 6, 7, 11, 12)


def limb_weights(side, kind, y):
    """Rigid shaft surfaces, with a two-pixel skin transition at each joint."""
    if kind == "Arm":
        if y >= 23:
            return interpolate([(24, "chest"), (23, side + "UpperArm")], y)
        if y >= 19:
            return {side + "UpperArm": 1.0}
        if y >= 17:
            return interpolate([(19, side + "UpperArm"),
                                (17, side + "Forearm")], y)
        if y >= 13:
            return {side + "Forearm": 1.0}
        return interpolate([(13, side + "Forearm"),
                            (12, side + "Hand")], y)
    if y >= 11:
        return interpolate([(12, "waist"), (11, side + "Thigh")], y)
    if y >= 7:
        return {side + "Thigh": 1.0}
    if y >= 5:
        return interpolate([(7, side + "Thigh"),
                            (5, side + "Shin")], y)
    if y >= 2:
        return {side + "Shin": 1.0}
    return interpolate([(2, side + "Shin"), (1, side + "Foot")], y)


def core_weights(y):
    if y >= 26:
        return {"head": 1.0}
    if y >= 24:
        return interpolate([(26, "head"), (24, "chest")], y)
    if y >= 20:
        return {"chest": 1.0}
    if y >= 18:
        return interpolate([(20, "chest"), (18, "body")], y)
    if y >= 13:
        return {"body": 1.0}
    return interpolate([(13, "body"), (12, "waist")], y)


def weights_at(position, region):
    _, y, _ = position
    if region == "core":
        result = core_weights(y)
    elif region.endswith("Arm"):
        side = region[:-3]
        result = limb_weights(side, "Arm", y)
    elif region.endswith("Leg"):
        side = region[:-3]
        result = limb_weights(side, "Leg", y)
    else:
        raise AssertionError(region)
    result = {name: round(value, 6) for name, value in result.items()
              if value > 0.000001}
    if len(result) > 4:
        result = dict(sorted(result.items(), key=lambda item: -item[1])[:4])
    total = sum(result.values())
    if abs(total - 1) > 0.00001:
        result = {name: value / total for name, value in result.items()}
    assert 1 <= len(result) <= 4
    return result


def bone(name, origin, length, index):
    return {
        "isOpen": False, "uuid": uid("bone/" + name),
        "type": "armature_bone", "name": name, "children": [],
        "export": True, "locked": False, "scope": 0,
        "origin": list(origin), "rotation": [0, 0, 0],
        "length": length, "width": 1.25, "connected": False,
        "color": index % 12, "vertex_weights": {},
        "allow_mirror_modeling": True,
    }


def uv_at(point, source, bounds, direction):
    x, y, z = point
    x0, y0, z0, x1, y1, z1 = bounds
    face = source["faces"][direction]
    u0, v0, u1, v1 = face["uv"]
    if direction == "north":
        a, b = (x-x0)/(x1-x0), (y1-y)/(y1-y0)
    elif direction == "south":
        a, b = (x1-x)/(x1-x0), (y1-y)/(y1-y0)
    elif direction == "east":
        a, b = (z1-z)/(z1-z0), (y1-y)/(y1-y0)
    elif direction == "west":
        a, b = (z-z0)/(z1-z0), (y1-y)/(y1-y0)
    elif direction == "up":
        a, b = (x-x0)/(x1-x0), (z-z0)/(z1-z0)
    else:
        a, b = (x-x0)/(x1-x0), (z1-z)/(z1-z0)
    a, b = max(0,min(1,a)), max(0,min(1,b))
    return [round(u0 + a*(u1-u0), 5), round(v0 + b*(v1-v0), 5)]


def mesh(name):
    return {
        "name": name, "color": 7, "origin": [0,0,0], "rotation": [0,0,0],
        "shading": "flat", "export": True, "visibility": True,
        "locked": False, "render_order": "default", "scope": 0,
        "allow_mirror_modeling": True, "vertices": {}, "faces": {},
        "type": "mesh", "uuid": uid("mesh/" + name),
    }


class MeshBuilder:
    def __init__(self, element):
        self.element = element
        self.index = {}

    def vertex(self, point):
        key = tuple(round(value, 5) for value in point)
        if key not in self.index:
            vid = "v" + str(len(self.index)).zfill(5)
            self.index[key] = vid
            self.element["vertices"][vid] = list(key)
        return self.index[key]

    def quad(self, corners, source, bounds, direction):
        ids = [self.vertex(point) for point in corners]
        uv = {vid: uv_at(point, source, bounds, direction)
              for vid, point in zip(ids, corners)}
        fid = "f" + str(len(self.element["faces"])).zfill(5)
        self.element["faces"][fid] = {
            "uv": uv, "vertices": ids, "texture": 0,
        }


def volumes(source):
    elements = {item["name"]: item for item in source["elements"]}
    shapes = {}
    for name in ("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"):
        cube = elements[name]
        bounds = [round(value) for value in cube["from"] + cube["to"]]
        # The Java legs overlap by 0.1 px; align their shared seam at x=0
        # for a manifold base mesh without altering their 4 px width.
        if name == "rightLeg":
            bounds[0], bounds[3] = 0, 4
        elif name == "leftLeg":
            bounds[0], bounds[3] = -4, 0
        shapes[name] = (cube, bounds)
    return elements, shapes


def banded_corners(bounds, direction, lower, upper):
    x0, _, z0, x1, _, z1 = bounds
    if direction == "west":
        return [(x0,lower,z0),(x0,lower,z1),(x0,upper,z1),(x0,upper,z0)]
    if direction == "east":
        return [(x1,lower,z0),(x1,upper,z0),(x1,upper,z1),(x1,lower,z1)]
    if direction == "north":
        return [(x0,lower,z0),(x0,upper,z0),(x1,upper,z0),(x1,lower,z0)]
    if direction == "south":
        return [(x0,lower,z1),(x1,lower,z1),(x1,upper,z1),(x0,upper,z1)]
    if direction == "down":
        return [(x0,lower,z0),(x1,lower,z0),(x1,lower,z1),(x0,lower,z1)]
    return [(x0,upper,z0),(x0,upper,z1),(x1,upper,z1),(x1,upper,z0)]


def make_banded_box(name, source, bounds, bands, overlay=False):
    """One continuous shell; only joint neighborhoods have narrow face bands."""
    y0, y1 = bounds[1], bounds[4]
    assert bands[0] == y0 and bands[-1] == y1
    inflation = source.get("inflate", 0) if overlay else 0
    label = name + ("_weighted_outer" if overlay else "_continuous_base")
    result = mesh(label)
    builder = MeshBuilder(result)
    for direction in ("west", "east", "north", "south", "down", "up"):
        spans = list(zip(bands, bands[1:])) if direction in (
            "west", "east", "north", "south") else [(y0, y1)]
        for lower, upper in spans:
            corners = banded_corners(bounds, direction, lower, upper)
            inflated = []
            for px, py, pz in corners:
                inflated.append((
                    px-inflation if px==bounds[0] else px+inflation if px==bounds[3] else px,
                    py-inflation if py==y0 else py+inflation if py==y1 else py,
                    pz-inflation if pz==bounds[2] else pz+inflation if pz==bounds[5] else pz,
                ))
            ids = [builder.vertex(point) for point in inflated]
            uv = {vid: uv_at(point, source, bounds, direction)
                  for vid, point in zip(ids, corners)}
            fid = "f" + str(len(result["faces"])).zfill(5)
            result["faces"][fid] = {
                "uv": uv, "vertices": ids, "texture": 0,
            }
    return result


def make_core(shapes):
    """Joined head and torso shell with broad shaft faces and an open neck seam."""
    body_source, body_bounds = shapes["body"]
    head_source, head_bounds = shapes["head"]
    assert body_bounds == [-4,12,-2,4,24,2]
    assert head_bounds == [-4,24,-4,4,32,4]
    result = mesh("core_continuous_base")
    builder = MeshBuilder(result)

    def add(source, uv_bounds, patch_bounds, direction, lower, upper):
        builder.quad(banded_corners(patch_bounds, direction, lower, upper),
                     source, uv_bounds, direction)

    for lower, upper in zip(BODY_BANDS, BODY_BANDS[1:]):
        for direction in ("west", "east", "north", "south"):
            add(body_source, body_bounds, body_bounds, direction, lower, upper)
    add(body_source, body_bounds, body_bounds, "down", 12, 24)

    # The head overhangs the torso front and back. Its lower two strips form
    # the visible underside; the torso top is internal and therefore omitted.
    for lower, upper in zip(HEAD_BANDS, HEAD_BANDS[1:]):
        for direction in ("north", "south"):
            add(head_source, head_bounds, head_bounds, direction, lower, upper)
        for z0, z1 in ((-4,-2),(-2,2),(2,4)):
            patch = [-4,24,z0,4,32,z1]
            for direction in ("west", "east"):
                add(head_source, head_bounds, patch, direction, lower, upper)
    for z0, z1 in ((-4,-2),(-2,2),(2,4)):
        patch = [-4,24,z0,4,32,z1]
        add(head_source, head_bounds, patch, "up", 24, 32)
        if z0 != -2:
            add(head_source, head_bounds, patch, "down", 24, 32)
    return result


def build(source):
    assert source["meta"]["model_format"] == "bedrock"
    assert source["resolution"] == {"width":64,"height":64}
    originals, shapes = volumes(source)
    # Keep each limb independent at the shoulders and crotch. Within a limb,
    # the surface stays one continuous weighted mesh across elbow or knee.
    meshes = [make_core(shapes)]
    for name in ("rightArm", "leftArm", "rightLeg", "leftLeg"):
        limb_source, bounds = shapes[name]
        bands = ARM_BANDS if name.endswith("Arm") else LEG_BANDS
        meshes.append(make_banded_box(name, limb_source, bounds, bands))
    for name in ("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"):
        outer_source = originals[name + "_outer"]
        bounds = [round(value) for value in outer_source["from"] + outer_source["to"]]
        if name == "rightLeg":
            bounds[0], bounds[3] = 0, 4
        elif name == "leftLeg":
            bounds[0], bounds[3] = -4, 0
        bands = (HEAD_BANDS if name == "head" else BODY_BANDS if name == "body"
                 else ARM_BANDS if name.endswith("Arm") else LEG_BANDS)
        meshes.append(make_banded_box(name, outer_source, bounds, bands, overlay=True))

    armature = {
        "isOpen": True, "uuid": uid("armature"), "type": "armature",
        "name": "murim_player_armature", "children": [], "export": True,
        "locked": False, "scope": 0,
    }
    # Armature bone origins are LOCAL to their parent. BONE_SPEC stores the
    # intended model-space joint locations so each child needs the difference.
    world_origins = {name: origin for name, _, origin, _ in BONE_SPEC}
    bones = {}
    for index, (name, parent, origin, length) in enumerate(BONE_SPEC):
        parent_origin = world_origins[parent] if parent else (0, 0, 0)
        local_origin = tuple(a - b for a, b in zip(origin, parent_origin))
        bones[name] = bone(name, local_origin, length, index)
    hierarchy = {name: [] for name in bones}
    for name, parent, _, _ in BONE_SPEC:
        if parent:
            bones[parent]["children"].append(bones[name]["uuid"])
            hierarchy[parent].append({"uuid":bones[name]["uuid"],
                                      "isOpen":False,"children":hierarchy[name]})
        else:
            armature["children"].append(bones[name]["uuid"])

    for item in meshes:
        prefix = item["uuid"][:6] + ":"
        for vid, point in item["vertices"].items():
            region = item["name"].split("_")[0]
            if region in ("head", "body"):
                region = "core"
            for name, value in weights_at(point, region).items():
                bones[name]["vertex_weights"][prefix + vid] = value
        armature["children"].append(item["uuid"])

    project = {
        "meta": {"format_version":"5.0", "model_format":"free", "box_uv":False},
        "name": "murim_current_player_weighted_armature",
        "resolution": {"width":64,"height":64},
        "elements": [armature, *bones.values(), *meshes],
        "groups": [],
        "outliner": [{"uuid":armature["uuid"],"isOpen":True,"children":[
            {"uuid":bones["root"]["uuid"],"isOpen":False,
             "children":hierarchy["root"]},
            *(item["uuid"] for item in meshes),
        ]}],
        "textures": copy.deepcopy(source["textures"]),
    }
    validate(project)
    return project


def validate(project):
    elements = project["elements"]
    ids = [entry["uuid"] for entry in elements]
    assert len(ids)==len(set(ids))
    bones = {entry["name"]: entry for entry in elements
             if entry["type"] == "armature_bone"}
    world_origins = {name: origin for name, _, origin, _ in BONE_SPEC}
    for name, parent, expected, _ in BONE_SPEC:
        parent_origin = world_origins[parent] if parent else (0, 0, 0)
        resolved = tuple(a + b for a, b in
                         zip(bones[name]["origin"], parent_origin))
        assert resolved == expected, (name, resolved, expected)
    used = []
    def walk(entries):
        for entry in entries:
            if isinstance(entry,str):
                used.append(entry)
            else:
                used.append(entry["uuid"])
                walk(entry["children"])
    walk(project["outliner"])
    assert Counter(ids)==Counter(used), "outliner orphan or duplicate"
    for item in elements:
        if item["type"] != "mesh":
            continue
        assert item["vertices"] and item["faces"]
        edges = Counter()
        parents = {key: key for key in item["vertices"]}
        def find(key):
            while parents[key] != key:
                key = parents[key]
            return key
        for face in item["faces"].values():
            assert len(face["vertices"])==4
            assert all(key in item["vertices"] for key in face["vertices"])
            assert set(face["vertices"])==set(face["uv"])
            if item["name"].endswith(("_continuous_base", "_weighted_outer")):
                corners = face["vertices"]
                for a, b in zip(corners, corners[1:] + corners[:1]):
                    edges[tuple(sorted((a, b)))] += 1
                    parents[find(a)] = find(b)
        if edges:
            assert all(count == 2 for count in edges.values()), item["name"]
            assert len({find(key) for key in parents}) == 1, item["name"]
        region = item["name"].split("_")[0]
        if region.endswith(("Arm", "Leg")):
            bands = ARM_BANDS if region.endswith("Arm") else LEG_BANDS
            for point in item["vertices"].values():
                assert min(abs(point[1] - level) for level in bands) <= 0.25, (
                    item["name"], point)
        for vid in item["vertices"]:
            key = item["uuid"][:6] + ":" + vid
            total = sum(b.get("vertex_weights",{}).get(key,0)
                        for b in elements if b["type"]=="armature_bone")
            assert math.isclose(total, 1, abs_tol=0.00001), (item["name"],vid,total)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input",type=Path,default=PUBLIC)
    parser.add_argument("--output",type=Path,default=OUTPUT)
    args = parser.parse_args()
    source = json.loads(args.input.read_text(encoding="utf-8"))
    project = build(source)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(project,ensure_ascii=False,separators=(",",":"))
                           + "\n",encoding="utf-8")
    print(args.output)
    print("bones",sum(e["type"]=="armature_bone" for e in project["elements"]))
    print("meshes",sum(e["type"]=="mesh" for e in project["elements"]))
    print("vertices",sum(len(e["vertices"]) for e in project["elements"]
                         if e["type"]=="mesh"))
    print("faces",sum(len(e["faces"]) for e in project["elements"]
                      if e["type"]=="mesh"))


if __name__=="__main__":
    main()
