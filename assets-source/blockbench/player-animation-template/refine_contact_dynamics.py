"""Extend a saved frame-zero pose with restrained torso motion and foot support.

Produces a new Blockbench authoring file. It does not change Minecraft runtime
animation, the source model, or the saved user reference.
"""

import argparse
import copy
import json
import math
from pathlib import Path
import uuid


CYCLE = 1.2
STANCE = 0.7
STEPS = 24
USER_BONES = (
    "lowerBack", "chest", "rightShoulderBridge", "leftShoulderBridge",
    "rightHipBridge", "leftHipBridge",
)


def number(value):
    rounded = round(value, 4)
    return "0" if abs(rounded) < 0.00005 else f"{rounded:g}"


def ease(progress):
    return progress * progress * (3 - 2 * progress)


def key_angles(project, name):
    bone = next(item for item in project["elements"]
                if item["type"] == "armature_bone" and item["name"] == name)
    animator = project["animations"][0]["animators"].get(bone["uuid"], {})
    values = {}
    for frame in animator.get("keyframes", []):
        if frame["channel"] == "rotation":
            values[round(frame["time"], 4)] = tuple(
                float(frame["data_points"][0][axis]) for axis in "xyz")
    return values


def frame_zero(project, name):
    return key_angles(project, name)[0.0]


def walk_pose(reference, time):
    phase = math.tau * (time % CYCLE) / CYCLE
    wave = math.sin(phase)
    double_wave = math.sin(2 * phase)
    lift = (1 - math.cos(2 * phase)) / 2
    base = {name: frame_zero(reference, name) for name in USER_BONES}
    lb, chest = base["lowerBack"], base["chest"]
    rs, ls = base["rightShoulderBridge"], base["leftShoulderBridge"]
    rh, lh = base["rightHipBridge"], base["leftHipBridge"]
    return {
        "lowerBack": (lb[0] + 1.2 * lift, lb[1] + 2 * wave,
                      lb[2] + 1.1 * wave),
        "chest": (chest[0] - 0.5 * lift, chest[1] - 1.5 * wave,
                  chest[2] - 0.7 * wave),
        "rightShoulderBridge": (rs[0] + 1.2 * wave, rs[1] + 2 * wave,
                                rs[2] + 0.7 * double_wave),
        "leftShoulderBridge": (ls[0] - 1.2 * wave, ls[1] - 2 * wave,
                               ls[2] - 0.7 * double_wave),
        "rightHipBridge": (rh[0], rh[1], rh[2] + 0.8 * wave),
        "leftHipBridge": (lh[0], lh[1], lh[2] - 0.8 * wave),
    }


def support_weight(time, contact_time):
    phase = (time - contact_time) % CYCLE
    return 1 - ease(phase / STANCE) if phase < STANCE else 0.0


def ankle_height(upper_angle, knee_angle):
    """Two six-pixel leg shafts: approximate ankle height above the ground."""
    upper = math.radians(upper_angle)
    lower = math.radians(upper_angle + knee_angle)
    return 12 - 6 * math.cos(upper) - 6 * math.cos(lower)


def support_pose(source, time):
    row = {}
    for side, contact in (("right", 0.0), ("left", CYCLE / 2)):
        upper = key_angles(source, side + "Leg")[round(time, 4)][0]
        knee = key_angles(source, side + "Knee")[round(time, 4)][0]
        weight = support_weight(time, contact)
        row[side] = (weight, ankle_height(upper, knee), upper + knee)
    total = sum(item[0] for item in row.values())
    assert total > 0.0001
    drop = -sum(weight * height for weight, height, _ in row.values()) / total
    drop = max(-0.65, min(0, drop))
    # Soften the final 0.15 s of toe-off before the other foot accepts weight.
    phase = time % CYCLE
    if 0.45 < phase < 0.6 or 1.05 < phase < 1.2:
        next_time, start = ((0.6, 0.45) if phase < 0.6 else
                            (1.2, 1.05))
        target = support_pose(source, next_time)[0]
        mix = ease((phase - start) / 0.15)
        drop += mix * (target - drop)
    ankles = {}
    for side, (weight, _, angle) in row.items():
        ankles[side + "Ankle"] = max(-12, min(12, -weight * angle))
    return drop, ankles


def replace_channel(animator, channel, positions):
    # Retain unrelated channels if Blockbench added them while the user edited.
    kept = [frame for frame in animator.get("keyframes", [])
            if frame["channel"] != channel]
    first = next((frame for frame in animator.get("keyframes", [])
                  if frame["channel"] == channel), None)
    keys = []
    for step, values in enumerate(positions):
        frame = copy.deepcopy(first) if first else {
            "channel": channel, "data_points": [{}], "uuid": "",
            "time": 0, "color": -1, "interpolation": "linear",
        }
        frame["uuid"] = str(uuid.uuid5(uuid.NAMESPACE_URL,
            f"murim-walk-v4/{animator['name']}/{channel}/{step}"))
        frame["time"] = round(CYCLE * step / STEPS, 4)
        frame["data_points"] = [dict(zip("xyz", map(number, values)))]
        frame["interpolation"] = "linear"
        keys.append(frame)
    assert keys[0]["data_points"] == keys[-1]["data_points"]
    animator["keyframes"] = kept + keys


def build(source, reference):
    assert source["meta"]["model_format"] == "free"
    assert len(source["animations"]) == len(reference["animations"]) == 1
    assert source["animations"][0]["length"] == CYCLE
    assert reference["animations"][0]["length"] == CYCLE
    source_bones = {item["name"]: item for item in source["elements"]
                    if item["type"] == "armature_bone"}
    reference_bones = {item["name"]: item for item in reference["elements"]
                       if item["type"] == "armature_bone"}
    assert {name: bone["uuid"] for name, bone in source_bones.items()} == {
        name: bone["uuid"] for name, bone in reference_bones.items()}
    for name in USER_BONES:
        assert 0.0 in key_angles(reference, name), name
    output = copy.deepcopy(source)
    animation = output["animations"][0]
    animation["name"] = "걷기_접지와역동감_사용자자세_1.2초"
    animation["loop"] = "loop"
    animation["snapping"] = 60
    animation["selected"] = True
    for name in (*USER_BONES, "waist", "rightAnkle", "leftAnkle"):
        uid = source_bones[name]["uuid"]
        animator = animation["animators"].setdefault(uid, {
            "name": name, "type": "armature_bone",
            "rotation_global": False, "quaternion_interpolation": False,
        })
        if name in USER_BONES:
            values = [walk_pose(reference, CYCLE * i / STEPS)[name]
                      for i in range(STEPS + 1)]
            replace_channel(animator, "rotation", values)
        elif name == "waist":
            values = [(0, support_pose(source, CYCLE * i / STEPS)[0], 0)
                      for i in range(STEPS + 1)]
            replace_channel(animator, "position", values)
        else:
            values = [(support_pose(source, CYCLE * i / STEPS)[1][name], 0, 0)
                      for i in range(STEPS + 1)]
            replace_channel(animator, "rotation", values)
    assert {k: v for k, v in source.items() if k != "animations"} == {
        k: v for k, v in output.items() if k != "animations"}
    for name in USER_BONES:
        actual = key_angles(output, name)[0.0]
        expected = frame_zero(reference, name)
        assert all(abs(a - b) < 0.00006 for a, b in zip(actual, expected)), name
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    assert args.output.resolve() != args.input.resolve()
    assert not args.output.exists(), "preserve earlier editor work"
    source = json.loads(args.input.read_text(encoding="utf-8"))
    reference = json.loads(args.reference.read_text(encoding="utf-8"))
    output = build(source, reference)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False,
                                      separators=(",", ":")) + "\n",
                           encoding="utf-8")
    print(args.output)
    print("1.2 seconds; 6 user-pose bones, waist bob, 2 ankles")


if __name__ == "__main__":
    main()
