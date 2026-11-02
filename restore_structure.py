#!/usr/bin/env python3
import shutil, tarfile
from pathlib import Path

root = Path(".")
bundle = root / "SmartReceiverPro_Bundle.tar.gz"
if bundle.exists():
    with tarfile.open(bundle, "r:gz") as tar:
        tar.extractall(path=root)

for item in list(root.iterdir()):
    if item.is_file() and "__" in item.name and not item.name.startswith("."):
        rel_target = Path(*item.name.split("__"))
        target_path = root / rel_target
        target_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(item, target_path)
        print(f"Restored: {item.name} -> {rel_target}")
