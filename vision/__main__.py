"""Run with python -m vision <command> [script arguments]."""
import argparse
import runpy
import sys

COMMANDS = {
    "monitor": "classroom_monitor",
    "register": "face_register",
    "verify": "face_verify",
    "recognize": "face_recognition",
    "analyze": "yolo_face_analysis",
}


def main():
    parser = argparse.ArgumentParser(description="Classroom Python vision tools")
    parser.add_argument("command", choices=COMMANDS)
    parser.add_argument("arguments", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    module = f"vision.{COMMANDS[args.command]}"
    sys.argv = [module, *args.arguments]
    runpy.run_module(module, run_name="__main__")


if __name__ == "__main__":
    main()
