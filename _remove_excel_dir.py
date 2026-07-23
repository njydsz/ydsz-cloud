import pathlib
import shutil

# Delete empty excel directory
excel_dir = pathlib.Path(r'd:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-base/src/main/java/com/njydsz/common/base/excel')
if excel_dir.exists():
    if excel_dir.is_dir():
        # Check if empty (no Java files)
        java_files = list(excel_dir.glob('*.java'))
        if not java_files:
            print(f"Removing empty directory: {excel_dir}")
            excel_dir.rmdir()
        else:
            print(f"Directory not empty (contains {len(java_files)} Java files): {excel_dir}")
    else:
        print(f"Path exists but is not a directory: {excel_dir}")
else:
    print(f"Directory does not exist: {excel_dir}")