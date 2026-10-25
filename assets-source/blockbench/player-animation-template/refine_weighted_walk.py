"""Create a separate Blockbench walk draft with a longer stance phase.

Only the existing walk animation is replaced. Geometry, UV, skin, and the
original input file remain intact. This is an authoring asset, not game code.
"""

import argparse
import copy
import json
import math
import uuid
from pathlib import Path


LENGTH = 1.2
STANCE = 0.7
FPS = 60
STEPS = 24
NAMESPACE = uuid.UUID("e934c9e5-d29d-440a-bb88-b69255f5b882")
CHANNELS = (
    "rightLeg", "leftLeg", "rightKnee", "leftKnee",
    "rightArm", "leftArm", "rightElbow", "leftElbow",
    "lowerBack", "chest", "neck",
)


def ease(progress):
    """Zero angular velocity at foot contact and toe-off."""
    return progress * progress * (3 - 2 * progress)


def limb(time):
    """Right or left leg phase, measured from that foot's forward contact."""
    phase = time % LENGTH
    if phase < STANCE:
        progress = phase / STANCE
        return 20 - 40 * ease(progress), 4 + 2 * math.sin(math.pi * progress)
    progress = (phase - STANCE) / (LENGTH - STANCE)
    return -20 + 40 * ease(progress), 4 + 18 * math.sin(math.pi * progress) ** 2


def pose(step):
    time = LENGTH * (step % STEPS) / STEPS
    right_leg, right_knee = limb(time)
    left_leg, left_knee = limb(time - LENGTH / 2)
    phase = math.tau * time / LENGTH
    swing = math.sin(phase)
    rebound = math.cos(2 * phase)
    return {
        "rightLeg": (right_leg, 0, 0),
        "leftLeg": (left_leg, 0, 0),
        "rightKnee": (right_knee, 0, 0),
        "leftKnee": (left_knee, 0, 0),
        "rightArm": (-0.6 * right_leg, 0, 0),
        "leftArm": (-0.6 * left_leg, 0, 0),
        "rightElbow": (4 + (right_knee - 4) / 6, 0, 0),
        "leftElbow": (4 + (left_knee - 4) / 6, 0, 0),
        "lowerBack": (1.2 + 0.4 * rebound, 1.3 * swing, 0.65 * swing),
        "chest": (-0.4 - 0.25 * rebound, -0.65 * swing, -0.35 * swing),
        "neck": (0, -0.65 * swing, 0),
    }


def number(value):
    rounded = round(value, 3)
    return "0" if abs(rounded) < 0.0005 else f"{rounded:g}"


def refine(source):
    assert source["meta"]["model_format"] == "free"
    assert source["resolution"] == {"width": 64, "height": 64}
    assert len(source.get("animations", [])) == 1, "expected one walk clip"
    assert source["animations"][0]["name"].startswith("걷기"), "wrong clip"
    bones = {item["name"]: item for item in source["elements"]
             if item["type"] == "armature_bone"}
    assert set(CHANNELS) <= bones.keys(), "wrong weighted player rig"
    assert pose(0) == pose(STEPS), "pose seam differs"
    output = copy.deepcopy(source)
    animation = output["animations"][0]
    animation["name"] = "걷기_접지회수_다듬기_1.2초"
    animation["loop"] = "loop"
    animation["length"] = LENGTH
    animation["snapping"] = FPS
    animation["selected"] = True
    animators = {}
    for name in CHANNELS:
        keys = []
        for step in range(STEPS + 1):
            values = pose(step)[name]
            keys.append({
                "channel": "rotation",
                "data_points": [dict(zip("xyz", map(number, values)))],
                "uuid": str(uuid.uuid5(NAMESPACE, f"{name}/{step}")),
                "time": round(LENGTH * step / STEPS, 4),
                "color": -1,
                "interpolation": "linear",
            })
        animators[bones[name]["uuid"]] = {
            "name": name,
            "type": "armature_bone",
            "rotation_global": False,
            "quaternion_interpolation": False,
            "keyframes": keys,
        }
    animation["animators"] = animators
    assert {key: value for key, value in output.items() if key != "animations"} == {
        key: value for key, value in source.items() if key != "animations"}
    assert len(animators) == 11
    assert all(len(a["keyframes"]) == STEPS + 1 for a in animators.values())
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    assert args.input.resolve() != args.output.resolve(), "preserve input"
    assert not args.output.exists(), "preserve existing output and user edits"
    source = json.loads(args.input.read_text(encoding="utf-8"))
    output = refine(source)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False,
                                      separators=(",", ":")) + "\n",
                           encoding="utf-8")
    print(args.output)
    print("1.2 s; stance 0.7 s; recovery 0.5 s; 11 bones; 275 keys")


if __name__ == "__main__":
    main()
