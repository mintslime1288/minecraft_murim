"""Cross shoulder positions along Z while keeping the arms' own keys intact."""

import argparse
import copy
import json
import math
import uuid
from pathlib import Path


CYCLE = 1.2
DEPTH = 0.55
SIDES = (("rightShoulderBridge", 1), ("leftShoulderBridge", -1))


def bone(project, name):
    return next(element for element in project["elements"]
                if element.get("type") == "armature_bone"
                and element.get("name") == name)


def formatted(value):
    rounded = round(value, 4)
    return "0" if abs(rounded) < 0.00005 else f"{rounded:g}"


def build(source):
    assert source["meta"]["model_format"] == "free"
    assert len(source["animations"]) == 1
    assert source["animations"][0]["length"] == CYCLE
    assert bone(source, "rightArm")["origin"][0] == 5
    assert bone(source, "leftArm")["origin"][0] == -5
    output = copy.deepcopy(source)
    clip = output["animations"][0]
    clip["name"] = "걷기_어깨위치교차_1.2초"
    clip["selected"] = True
    clip["loop"] = "loop"

    for name, side in SIDES:
        animator = clip["animators"][bone(output, name)["uuid"]]
        assert not any(frame["channel"] == "position"
                       for frame in animator["keyframes"])
        rotations = sorted((frame for frame in animator["keyframes"]
                            if frame["channel"] == "rotation"),
                           key=lambda frame: frame["time"])
        assert len(rotations) == 25
        assert [round(frame["time"], 4) for frame in rotations] == [
            round(i * CYCLE / 24, 4) for i in range(25)]
        for i, frame in enumerate(rotations):
            # Remove the fixed clavicle yaw: it would bias the endpoints and
            # force a much larger shoulder translation to cross them.
            frame["data_points"][0]["y"] = "0"
            phase = math.tau * frame["time"] / CYCLE
            animator["keyframes"].append({
                "channel": "position",
                "data_points": [{"x": "0", "y": "0",
                                 "z": formatted(side * DEPTH * math.cos(phase))}],
                "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL,
                                        f"murim-walk-v8/{name}/position/{i}")),
                "time": frame["time"],
                "color": -1,
                "interpolation": "linear",
            })
        positions = [frame for frame in animator["keyframes"]
                     if frame["channel"] == "position"]
        assert positions[0]["data_points"] == positions[-1]["data_points"]

    assert {k: v for k, v in source.items() if k != "animations"} == {
        k: v for k, v in output.items() if k != "animations"}
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
