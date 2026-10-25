"""Animate lateral arm swing without shifting the character's torso."""

import argparse
import copy
import json
import math
from pathlib import Path


CYCLE = 1.2
SIDE_AMPLITUDE = 5.0


def arm(project, name):
    return next(element for element in project["elements"]
                if element.get("type") == "armature_bone"
                and element.get("name") == name)


def angle_text(angle):
    value = round(angle, 4)
    return "0" if abs(value) < 0.00005 else f"{value:g}"


def build(source):
    assert source["meta"]["model_format"] == "free"
    assert len(source["animations"]) == 1
    assert source["animations"][0]["length"] == CYCLE
    assert arm(source, "rightLeg")["origin"][0] > 0
    assert arm(source, "leftLeg")["origin"][0] < 0

    output = copy.deepcopy(source)
    clip = output["animations"][0]
    clip["name"] = "걷기_팔좌우흔들기_1.2초"
    clip["selected"] = True
    clip["loop"] = "loop"
    for name in ("rightArm", "leftArm"):
        animator = clip["animators"][arm(output, name)["uuid"]]
        keys = sorted((frame for frame in animator["keyframes"]
                       if frame["channel"] == "rotation"),
                      key=lambda frame: frame["time"])
        assert len(keys) == 25
        assert [round(frame["time"], 4) for frame in keys] == [
            round(i * CYCLE / 24, 4) for i in range(25)]
        for frame in keys:
            point = frame["data_points"][0]
            assert float(point["z"]) == 0
            phase = math.tau * frame["time"] / CYCLE
            # Same local Z sign shifts both arms in world X. With shoulders
            # on opposite sides, one opens while the other moves inward.
            point["z"] = angle_text(SIDE_AMPLITUDE * math.sin(phase))
        assert keys[0]["data_points"] == keys[-1]["data_points"]

    assert {k: v for k, v in source.items() if k != "animations"} == {
        k: v for k, v in output.items() if k != "animations"}
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    assert args.input.resolve() != args.output.resolve()
    assert not args.output.exists(), "preserve previous editor work"
    source = json.loads(args.input.read_text(encoding="utf-8"))
    output = build(source)
    args.output.write_text(json.dumps(output, ensure_ascii=False,
                                      separators=(",", ":")) + "\n",
                           encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
