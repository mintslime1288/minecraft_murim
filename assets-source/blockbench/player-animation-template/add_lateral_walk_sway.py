"""Add side-to-side weight transfer to an existing editable Blockbench walk."""

import argparse
import copy
import json
import math
import uuid
from pathlib import Path


CYCLE = 1.2
WAIST_AMPLITUDE = 0.12
BACK_AMPLITUDE = 0.48


def format_value(value):
    value = round(value, 4)
    return "0" if abs(value) < 0.00005 else f"{value:g}"


def bone(project, name):
    return next(element for element in project["elements"]
                if element.get("type") == "armature_bone"
                and element.get("name") == name)


def build(source):
    assert source["meta"]["model_format"] == "free"
    assert len(source["animations"]) == 1
    assert source["animations"][0]["length"] == CYCLE
    assert bone(source, "rightLeg")["origin"][0] > 0
    assert bone(source, "leftLeg")["origin"][0] < 0

    output = copy.deepcopy(source)
    clip = output["animations"][0]
    clip["name"] = "걷기_좌우체중이동_1.2초"
    clip["selected"] = True
    clip["loop"] = "loop"
    waist = clip["animators"][bone(output, "waist")["uuid"]]
    waist_positions = sorted((frame for frame in waist["keyframes"]
                              if frame["channel"] == "position"),
                             key=lambda frame: frame["time"])
    assert len(waist_positions) == 25
    assert [round(frame["time"], 4) for frame in waist_positions] == [
        round(i * CYCLE / 24, 4) for i in range(25)]
    for frame in waist_positions:
        assert float(frame["data_points"][0]["x"]) == 0
        phase = math.tau * frame["time"] / CYCLE
        frame["data_points"][0]["x"] = format_value(
            WAIST_AMPLITUDE * math.sin(phase))

    back_uuid = bone(output, "lowerBack")["uuid"]
    back = clip["animators"][back_uuid]
    assert not any(frame["channel"] == "position"
                   for frame in back.get("keyframes", []))
    for i, waist_frame in enumerate(waist_positions):
        phase = math.tau * waist_frame["time"] / CYCLE
        back["keyframes"].append({
            "channel": "position",
            "data_points": [{"x": format_value(BACK_AMPLITUDE * math.sin(phase)),
                             "y": "0", "z": "0"}],
            "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL,
                                    f"murim-walk-v5/lowerBack/position/{i}")),
            "time": waist_frame["time"],
            "color": -1,
            "interpolation": "linear",
        })

    assert {k: v for k, v in source.items() if k != "animations"} == {
        k: v for k, v in output.items() if k != "animations"}
    assert waist_positions[0]["data_points"] == waist_positions[-1]["data_points"]
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    assert args.input.resolve() != args.output.resolve()
    assert not args.output.exists(), "preserve previous edits"
    source = json.loads(args.input.read_text(encoding="utf-8"))
    output = build(source)
    args.output.write_text(json.dumps(output, ensure_ascii=False,
                                      separators=(",", ":")) + "\n",
                           encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
