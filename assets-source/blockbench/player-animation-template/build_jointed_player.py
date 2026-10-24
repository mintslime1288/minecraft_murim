"""Split the current wide player into editable, connected Blockbench bones.

Only the public neutral input is written into Git. A local model with the
installed Minecraft skin may be passed with --input/--output for editing.
"""

import argparse
import copy
import json
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "player_current_java_editable.bbmodel"
PUBLIC = ROOT / "player_current_jointed_editable.bbmodel"
SIDES = ("north", "east", "south", "west")


def uid(label):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "murimrpg/jointed-player/" + label))


def node(bone_id, children):
    return {"uuid": bone_id, "isOpen": False, "children": children}


def group(model, template, name, origin):
    bone = copy.deepcopy(template)
    bone["uuid"] = uid("bone/" + name)
    bone["name"] = name
    bone["origin"] = list(origin)
    bone["rotation"] = [0, 0, 0]
    bone["children"] = []
    model["groups"].append(bone)
    return bone["uuid"]


def piece(model, source, name, lower, upper, old_lower, old_upper):
    cube = copy.deepcopy(source)
    cube["name"] = name
    cube["uuid"] = uid("cube/" + name)
    cube["from"][1] = lower
    cube["to"][1] = upper
    for face_name in SIDES:
        uv = list(source["faces"][face_name]["uv"])
        top_v, bottom_v = uv[1], uv[3]
        span = old_upper - old_lower
        uv[1] = top_v + (old_upper - upper) * (bottom_v - top_v) / span
        uv[3] = top_v + (old_upper - lower) * (bottom_v - top_v) / span
        cube["faces"][face_name]["uv"] = uv
    model["elements"].append(cube)
    return cube["uuid"]


def connector(model, source, name, y, inset=0.4):
    """An inner plug is concealed at rest and covers the hinge's inner gap."""
    cube = copy.deepcopy(source)
    cube["name"] = name
    cube["uuid"] = uid("cube/" + name)
    cube["from"] = [source["from"][0] + inset, y - 0.8,
                    source["from"][2] + inset]
    cube["to"] = [source["to"][0] - inset, y + 0.8,
                  source["to"][2] - inset]
    cube["inflate"] = 0
    cube["uv_offset"] = copy.deepcopy(source.get("uv_offset", [0, 0]))
    for face_name in SIDES:
        uv = list(source["faces"][face_name]["uv"])
        v = uv[1] + (source["to"][1] - y) * (uv[3] - uv[1]) / 12
        uv[1], uv[3] = v - 0.8, v + 0.8
        cube["faces"][face_name]["uv"] = uv
    model["elements"].append(cube)
    return cube["uuid"]


def socket(model, source, name, lower, upper, x0, x1, z0, z1):
    cube = copy.deepcopy(source)
    cube["name"] = name
    cube["uuid"] = uid("cube/" + name)
    cube["from"] = [x0, lower, z0]
    cube["to"] = [x1, upper, z1]
    cube["inflate"] = 0
    model["elements"].append(cube)
    return cube["uuid"]


def make_chain(model, originals, groups, source_name, base_bone,
               segments, names, item=None):
    """Segments are listed top to bottom as (upper, lower) pairs."""
    base = originals[source_name]
    overlay = originals[source_name + "_outer"]
    x = (base["from"][0] + base["to"][0]) / 2
    z = (base["from"][2] + base["to"][2]) / 2
    root_children = []
    current_children = root_children
    for index, ((upper, lower), name) in enumerate(zip(segments, names)):
        if index:
            bone_id = group(model, groups[base_bone],
                            source_name + "_" + name, (x, upper, z))
            next_children = []
            current_children.append(node(bone_id, next_children))
            current_children = next_children
        current_children.append(piece(model, base, source_name + "_" + name,
                                      lower, upper, base["from"][1], base["to"][1]))
        current_children.append(piece(model, overlay,
                                      source_name + "_" + name + "_outer",
                                      lower, upper, overlay["from"][1],
                                      overlay["to"][1]))
        if index:
            current_children.append(connector(model, base,
                                              source_name + "_" + name + "_joint",
                                              upper))
    if source_name.endswith("Arm"):
        side = 1 if source_name.startswith("right") else -1
        root_children.append(socket(model, base, source_name + "_shoulder_socket",
                                    21.2, 23.6,
                                    3.3 if side == 1 else -5.5,
                                    5.5 if side == 1 else -3.3,
                                    -1.35, 1.35))
    if source_name.endswith("Leg"):
        root_children.append(connector(model, base,
                                       source_name + "_hip_socket", 12))
    if item:
        current_children.append(node(groups[item]["uuid"], []))
    return root_children


def make_spine(model, originals, groups):
    base, overlay = originals["body"], originals["body_outer"]
    groups["body"]["origin"] = [0, 12, 0]
    children = []
    current = children
    for index, (lower, upper, name) in enumerate((
            (12, 15, "pelvis"), (15, 18, "abdomen"),
            (18, 21, "chest"), (21, 24, "shoulders"))):
        if index:
            bone_id = group(model, groups["body"], "body_" + name,
                            (0, lower, 0))
            next_children = []
            current.append(node(bone_id, next_children))
            current = next_children
            current.append(connector(model, base, "body_" + name + "_joint",
                                     lower, inset=0.5))
        current.append(piece(model, base, "body_" + name,
                             lower, upper, 12, 24))
        current.append(piece(model, overlay, "body_" + name + "_outer",
                             lower, upper, 12, 24))
    return children, current


def build(source):
    model = copy.deepcopy(source)
    assert model["meta"]["model_format"] == "bedrock"
    originals = {item["name"]: item for item in source["elements"]}
    groups = {item["name"]: item for item in model["groups"]}
    expected = ("body", "rightArm", "leftArm", "rightLeg", "leftLeg")
    assert all(name in originals and name + "_outer" in originals
               for name in expected)
    removed = {name for part in expected for name in (part, part + "_outer")}
    model["elements"] = [item for item in model["elements"]
                         if item["name"] not in removed]
    remaining = {item["name"]: item["uuid"] for item in model["elements"]}

    # Build the spine from pelvis upward, so head and arms follow chest turns.
    spine, top_spine = make_spine(model, originals, groups)
    arm_spans = [(24, 21), (21, 18), (18, 16), (16, 13), (13, 12)]
    arm_names = ["shoulder", "upper", "elbow", "forearm", "hand"]
    leg_spans = [(12, 10), (10, 7), (7, 5), (5, 2), (2, 0)]
    leg_names = ["hip", "thigh", "knee", "shin", "foot"]
    right_arm = make_chain(model, originals, groups, "rightArm", "rightArm",
                           arm_spans, arm_names, "rightItem")
    left_arm = make_chain(model, originals, groups, "leftArm", "leftArm",
                          arm_spans, arm_names, "leftItem")
    right_leg = make_chain(model, originals, groups, "rightLeg", "rightLeg",
                           leg_spans, leg_names)
    left_leg = make_chain(model, originals, groups, "leftLeg", "leftLeg",
                          leg_spans, leg_names)

    # Attach head and arms to the upper spine, legs to the pelvis. The
    # existing named bones remain available for familiar Blockbench keys.
    top_spine.extend([
        node(group(model, groups["head"], "neck", (0, 24, 0)), [
            socket(model, originals["body"], "neck_socket",
                   23.1, 25, -1.5, 1.5, -1.25, 1.25),
            node(groups["head"]["uuid"], [remaining["head"],
                 node(groups["helmet"]["uuid"], [remaining["head_outer"]])])]),
        node(groups["rightArm"]["uuid"], right_arm),
        node(groups["leftArm"]["uuid"], left_arm),
        node(groups["cape"]["uuid"], [remaining["cape"]]),
    ])
    # The original waist bone is retained as a whole-body pivot. Its child
    # body carries the spine; leg roots remain direct children of waist.
    model["outliner"] = [node(groups["root"]["uuid"], [
        node(groups["waist"]["uuid"], [
            node(groups["body"]["uuid"], spine),
            node(groups["rightLeg"]["uuid"], right_leg),
            node(groups["leftLeg"]["uuid"], left_leg),
        ])
    ])]
    model["name"] = "murim_current_java_player_jointed"
    validate(model)
    return model


def validate(model):
    ids = [item["uuid"] for kind in ("elements", "groups")
           for item in model[kind]]
    assert len(ids) == len(set(ids)), "duplicate UUID"
    seen = []

    def walk(entries):
        for entry in entries:
            if isinstance(entry, str):
                seen.append(entry)
            else:
                seen.append(entry["uuid"])
                walk(entry["children"])

    walk(model["outliner"])
    assert len(seen) == len(ids) and set(seen) == set(ids), "orphan or duplicate"
    assert len(model["textures"]) == 1
    assert model["resolution"] == {"width": 64, "height": 64}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=SOURCE)
    parser.add_argument("--output", type=Path, default=PUBLIC)
    args = parser.parse_args()
    source = json.loads(args.input.read_text(encoding="utf-8"))
    model = build(source)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf-8")
    print(args.output, len(model["elements"]), "cubes,",
          len(model["groups"]), "bones")


if __name__ == "__main__":
    main()
