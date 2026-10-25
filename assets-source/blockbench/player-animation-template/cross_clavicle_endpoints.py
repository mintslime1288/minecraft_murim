"""Cross shoulder endpoints during walking without editing arm or torso keys."""

import argparse
import copy
import json
import math
from pathlib import Path


CYCLE = 1.2
SHOULDERS = ("rightShoulderBridge", "leftShoulderBridge")


def bone(project, name):
    return next(element for element in project["elements"]
                if element.get("type") == "armature_bone"
                and element.get("name") == name)


def value_text(value):
    rounded = round(value, 4)
    return "0" if abs(rounded) < 0.00005 else f"{rounded:g}"


def rotation_keys(project, name):
    uid = bone(project, name)["uuid"]
    return sorted((frame for frame in project["animations"][0]
                   ["animators"][uid]["keyframes"]
                   if frame["channel"] == "rotation"),
                  key=lambda frame: frame["time"])


def endpoint_depth(side, angle):
    # The arm root sits at x=+5 / -5 from the central clavicle pivot.
    return -side * 5 * math.sin(math.radians(angle))


def build(source):
    assert source["meta"]["model_format"] == "free"
    assert len(source["animations"]) == 1
    assert source["animations"][0]["length"] == CYCLE
    assert bone(source, "rightArm")["origin"][0] == 5
    assert bone(source, "leftArm")["origin"][0] == -5

    output = copy.deepcopy(source)
    clip = output["animations"][0]
    clip["name"] = "걷기_어깨끝점교차_1.2초"
    clip["selected"] = True
    clip["loop"] = "loop"
    for name in SHOULDERS:
        source_keys = rotation_keys(source, name)
        target_keys = rotation_keys(output, name)
        assert len(source_keys) == len(target_keys) == 25
        assert [round(frame["time"], 4) for frame in target_keys] == [
            round(i * CYCLE / 24, 4) for i in range(25)]
        base_y = float(source_keys[0]["data_points"][0]["y"])
        assert -12 <= base_y <= -8
        for frame in target_keys:
            phase = math.tau * frame["time"] / CYCLE
            frame["data_points"][0]["y"] = value_text(
                base_y * math.cos(phase))
        assert target_keys[0]["data_points"] == target_keys[-1]["data_points"]

    assert {k: v for k, v in source.items() if k != "animations"} == {
        k: v for k, v in output.items() if k != "animations"}
    right = rotation_keys(output, SHOULDERS[0])
    left = rotation_keys(output, SHOULDERS[1])
    for i in (0, 12, 24):
        rz = endpoint_depth(+1, float(right[i]["data_points"][0]["y"]))
        lz = endpoint_depth(-1, float(left[i]["data_points"][0]["y"]))
        assert rz * lz < 0
        assert (rz > 0) == (i != 12)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    assert args.input.resolve() != args.output.resolve()
    assert not args.output.exists(), "preserve earlier editor work"
    source = json.loads(args.input.read_text(encoding="utf-8"))
    output = build(source)
    args.output.write_text(json.dumps(output, ensure_ascii=False,
                                      separators=(",", ":")) + "\n",
                           encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
