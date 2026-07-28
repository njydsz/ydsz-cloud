"""
Scan all Java files in ydsz-cronjob-domain for missing imports.

Detects:
- Uses `LocalDateTime` but missing `import java.time.LocalDateTime;`
- Uses `@Schema` but missing `import io.swagger.v3.oas.annotations.media.Schema;`

Inserts the missing import after the package declaration.
"""
import pathlib
import re

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-cronjob\ydsz-cronjob-domain\src\main\java")

# Map: (simple class name, regex pattern to detect usage, full import statement)
CHECKS = [
    ("LocalDateTime", r"\bLocalDateTime\b", "import java.time.LocalDateTime;"),
    ("LocalDate", r"\bLocalDate\b", "import java.time.LocalDate;"),
    ("BigDecimal", r"\bBigDecimal\b", "import java.math.BigDecimal;"),
    ("List", r"\bList<", "import java.util.List;"),
    ("Map", r"\bMap<", "import java.util.Map;"),
    ("Set", r"\bSet<", "import java.util.Set;"),
    ("ArrayList", r"\bArrayList\b", "import java.util.ArrayList;"),
    ("HashMap", r"\bHashMap\b", "import java.util.HashMap;"),
    ("HashSet", r"\bHashSet\b", "import java.util.HashSet;"),
    ("Arrays", r"\bArrays\.", "import java.util.Arrays;"),
    ("Schema", r"@Schema\b", "import io.swagger.v3.oas.annotations.media.Schema;"),
    ("NotNull", r"@NotNull\b", "import jakarta.validation.constraints.NotNull;"),
    ("NotBlank", r"@NotBlank\b", "import jakarta.validation.constraints.NotBlank;"),
    ("Min", r"@Min\b", "import jakarta.validation.constraints.Min;"),
    ("Pattern", r"@Pattern\b", "import jakarta.validation.constraints.Pattern;"),
]

fixed_count = 0
for f in ROOT.rglob("*.java"):
    text = f.read_text(encoding="utf-8")
    new_text = text
    for simple_name, pattern, import_stmt in CHECKS:
        # Skip if already imported
        if import_stmt in new_text:
            continue
        # Check if used (word boundary search)
        if re.search(pattern, new_text):
            # Skip if used only inside a string literal or comment (rough heuristic)
            # We'll be conservative and just add the import
            # Insert after package declaration
            new_text = re.sub(
                r"(package [^;]+;\s*\n)",
                r"\1" + import_stmt + "\n",
                new_text,
                count=1,
            )
            print(f"  + {simple_name} import → {f.name}")
            fixed_count += 1
    if new_text != text:
        f.write_text(new_text, encoding="utf-8")

print(f"\nTotal fixes: {fixed_count}")
