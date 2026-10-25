"""Add an editable in-place walk cycle to a saved weighted player model.

This creates a new .bbmodel; it never changes the input file or game runtime.
"""

import argparse
import copy
import json
import math
import uuid
from pathlib import Path


CYCLE_SECONDS = 1.2
FPS = 60
STEPS = 12
CHANNELS = (
    "rightLeg", "leftLeg", "rightKnee", "leftKnee",
    "rightArm", "leftArm", "rightElbow", "leftElbow",
    "lowerBack", "chest", "neck",
)
NAMESPACE = uuid.UUID("203d87c2-4e99-45f6-8ab0-5126b642d3dd")


def pose(step):
    phase = math.tau * (step % STEPS) / STEPS
    stride = math.cos(phase)
    swing = math.sin(phase)
    rebound = math.cos(2 * phase)
    return {
        "rightLeg": (23 * stride, 0, 0),
        "leftLeg": (-23 * stride, 0, 0),
        "rightKnee": (8 - 8 * swing, 0, 0),
        "leftKnee": (8 + 8 * swing, 0, 0),
        "rightArm": (-15 * stride, 0, 0),
        "leftArm": (15 * stride, 0, 0),
        "rightElbow": (5 + 2 * swing, 0, 0),
        "leftElbow": (5 - 2 * swing, 0, 0),
        "lowerBack": (1.5 + 0.8 * rebound, 1.7 * swing, 0.8 * swing),
        "chest": (-0.5 - 0.4 * rebound, -0.9 * swing, -0.5 * swing),
        "neck": (0, -0.8 * swing, 0),
    }


def num(value):
    rounded = round(value, 3)
    return "0" if abs(rounded) < 0.0005 else f"{rounded:g}"


def add_walk(source):
    assert source["meta"]["model_format"] == "free"
    assert source["resolution"] == {"width": 64, "height": 64}
    assert not source.get("animations"), "input already has animations; preserve them"
    bones = {item["name"]: item for item in source["elements"]
             if item["type"] == "armature_bone"}
    assert set(CHANNELS) <= bones.keys(), "wrong player rig"
    assert pose(0) == pose(STEPS), "cycle seam differs"
    output = copy.deepcopy(source)
    animators = {}
    for name in CHANNELS:
        keyframes = []
        for step in range(STEPS + 1):
            angles = pose(step)[name]
            keyframes.append({
                "channel": "rotation",
                "data_points": [dict(zip("xyz", map(num, angles)))],
                "uuid": str(uuid.uuid5(NAMESPACE, f"walk/{name}/{step}")),
                "time": round(CYCLE_SECONDS * step / STEPS, 4),
                "color": -1,
                "interpolation": "linear",
            })
        animators[bones[name]["uuid"]] = {
            "name": name,
            "type": "armature_bone",
            "rotation_global": False,
            "quaternion_interpolation": False,
            "keyframes": keyframes,
        }
    output["animations"] = [{
        "uuid": str(uuid.uuid5(NAMESPACE, "murim/walk/v1")),
        "name": "걷기_수정용_1.2초",
        "loop": "loop",
        "override": False,
        "length": CYCLE_SECONDS,
        "snapping": FPS,
        "selected": True,
        "group_name": "",
        "scope": 0,
        "anim_time_update": "",
        "blend_weight": "",
        "start_delay": "",
        "loop_delay": "",
        "animators": animators,
    }]
    assert output["elements"] == source["elements"]
    assert output["textures"] == source["textures"]
    assert len(animators) == len(CHANNELS)
    assert all(len(item["keyframes"]) == STEPS + 1
               for item in animators.values())
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    assert args.input.resolve() != args.output.resolve(), "input and output differ"
    assert not args.output.exists(), "output exists; preserve user edits"
    source = json.loads(args.input.read_text(encoding="utf-8"))
    output = add_walk(source)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False,
                                      separators=(",", ":")) + "\n",
                           encoding="utf-8")
    print(args.output)
    print("length", CYCLE_SECONDS, "seconds; channels", len(CHANNELS),
          "keyframes", (STEPS + 1) * len(CHANNELS))


if __name__ == "__main__":
    main()
