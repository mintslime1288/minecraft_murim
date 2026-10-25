"""Extend the user's saved frame-zero knee pose across an editable walk loop.

The input file is read only. Only rightKnee/leftKnee rotation values change in
the separately named output .bbmodel. No game runtime code is involved.
"""

import argparse
import copy
import json
import math
from pathlib import Path


CYCLE = 1.2
STANCE = 0.7
RIGHT_CONTACT = -23.5
LEFT_AT_TIME_ZERO = -17.632
RECOVERY_FLEX = 8.0


def ease(value):
    return value * value * (3 - 2 * value)


# The left knee is 0.6 seconds into its stance at global time zero. Select
# the toe-off angle so the curve passes through the user's exact left-knee key.
TOE_OFF = RIGHT_CONTACT + (LEFT_AT_TIME_ZERO - RIGHT_CONTACT) / ease(0.6 / STANCE)


def knee_angle(time, contact_time):
    phase = (time - contact_time) % CYCLE
    if phase < STANCE:
        return RIGHT_CONTACT + (TOE_OFF - RIGHT_CONTACT) * ease(phase / STANCE)
    progress = (phase - STANCE) / (CYCLE - STANCE)
    baseline = TOE_OFF + (RIGHT_CONTACT - TOE_OFF) * ease(progress)
    return baseline - RECOVERY_FLEX * math.sin(math.pi * progress) ** 2


def refine(source):
    assert source["meta"]["model_format"] == "free"
    assert len(source.get("animations", [])) == 1
    input_clip = source["animations"][0]
    assert input_clip["length"] == CYCLE
    bones = {item["name"]: item for item in source["elements"]
             if item["type"] == "armature_bone"}
    output = copy.deepcopy(source)
    clip = output["animations"][0]
    clip["name"] = "걷기_사용자무릎자세_연결_1.2초"
    changes = {}
    for side, contact_time in (("right", 0.0), ("left", 0.6)):
        bone = side + "Knee"
        animator = clip["animators"][bones[bone]["uuid"]]
        frames = animator["keyframes"]
        assert len(frames) == 25 and all(frame["channel"] == "rotation"
                                         for frame in frames)
        assert [round(frame["time"], 4) for frame in frames] == [
            round(i * 0.05, 4) for i in range(25)]
        changes[bone] = []
        for frame in frames:
            old = frame["data_points"][0]["x"]
            new = f"{knee_angle(frame['time'], contact_time):.3f}".rstrip("0").rstrip(".")
            frame["data_points"][0]["x"] = new
            changes[bone].append((frame["time"], old, new))
        assert frames[0]["data_points"] == frames[-1]["data_points"]
    assert clip["animators"][bones["rightKnee"]["uuid"]]["keyframes"][0]["data_points"][0]["x"] == "-23.5"
    assert clip["animators"][bones["leftKnee"]["uuid"]]["keyframes"][0]["data_points"][0]["x"] == "-17.632"
    assert {key: value for key, value in output.items() if key != "animations"} == {
        key: value for key, value in source.items() if key != "animations"}
    return output, changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    assert args.input.resolve() != args.output.resolve()
    assert not args.output.exists(), "existing user work must be preserved"
    source = json.loads(args.input.read_text(encoding="utf-8"))
    output, changes = refine(source)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False,
                                      separators=(",", ":")) + "\n",
                           encoding="utf-8")
    print(args.output)
    print("toe-off", round(TOE_OFF, 3), "degrees; knee keys", sum(len(v) for v in changes.values()))


if __name__ == "__main__":
    main()
