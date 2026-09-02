#!/usr/bin/env python3
"""Fix no-arg collection constructors with context-aware initial capacity."""
import os
import re

ROOT = r"D:\Code\open\ydsz-cloud"
PATTERNS = {
    "new ArrayList<>()": ("ArrayList", 16),
    "new HashMap<>()": ("HashMap", 16),
    "new HashSet<>()": ("HashSet", 16),
    "new LinkedHashMap<>()": ("LinkedHashMap", 16),
}

def get_context(content, pos, window=200):
    """Get text window around position."""
    start = max(0, pos - window)
    end = min(len(content), pos + window)
    return content[start:end]

def get_line(content, pos):
    """Get the full line containing pos."""
    line_start = content.rfind('\n', 0, pos) + 1
    line_end = content.find('\n', pos)
    if line_end == -1:
        line_end = len(content)
    return content[line_start:line_end].strip()

def determine_capacity(collection_type, line_text, full_context, match_text):
    """Determine appropriate initial capacity based on context."""
    
    # Empty return
    if re.match(r'^\s*return\s+new\s+' + collection_type + r'<>\(\)\s*;', line_text):
        return 0
    
    # computeIfAbsent lambda
    if 'computeIfAbsent' in line_text and '->' in line_text:
        return 8 if collection_type == "ArrayList" else 16
    
    # Field declaration
    field_match = re.match(r'^\s*(private|protected|public)\s+(static\s+)?(final\s+)?(Map|List|Set|ArrayList|HashMap|HashSet|LinkedHashMap)\b.*=\s*new\s+' + re.escape(collection_type) + r'<>\(\)', line_text)
    if field_match:
        if collection_type == "ArrayList":
            # Check if listener/handler/observer
            if any(kw in line_text for kw in ['listeners', 'handlers', 'observers']):
                return 4
            return 4  # Generally small lists for fields
        return 16
    
    # this.x = new ...
    if 'this.' in line_text and collection_type in line_text:
        if any(kw in line_text for kw in ['listeners', 'handlers', 'observers']):
            return 4
        return 16
    
    return PATTERNS[match_text][1]  # Default

def find_all_occurrences(content, pattern):
    """Find all start positions of pattern in content."""
    positions = []
    start = 0
    while True:
        pos = content.find(pattern, start)
        if pos == -1:
            break
        positions.append(pos)
        start = pos + 1
    return positions

def process_file(filepath):
    """Process a single Java file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    changes = []
    
    for pattern, (collection_type, default_cap) in PATTERNS.items():
        if pattern not in content:
            continue
        
        positions = find_all_occurrences(content, pattern)
        # Process in reverse to maintain positions
        for pos in reversed(positions):
            line_text = get_line(content, pos)
            context = get_context(content, pos)
            capacity = determine_capacity(collection_type, line_text, context, pattern)
            replacement = f"new {collection_type}<>({capacity})"
            content = content[:pos] + replacement + content[pos + len(pattern)]
            changes.append((line_text, capacity))
    
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return changes
    return []

def main():
    total_files = 0
    total_changes = 0
    
    for root_dir, dirs, files in os.walk(ROOT):
        for filename in files:
            if not filename.endswith('.java'):
                continue
            filepath = os.path.join(root_dir, filename)
            
            # Only process src/main/java files
            if '\\src\\main\\java\\' not in filepath.replace('/', '\\'):
                continue
            
            changes = process_file(filepath)
            if changes:
                total_files += 1
                total_changes += len(changes)
                rel_path = filepath[len(ROOT) + 1:]
                print(f"  {rel_path}: {len(changes)} fixes - capacities: {[c[1] for c in changes]}")
    
    print(f"\nTotal: {total_changes} fixes in {total_files} files")

if __name__ == '__main__':
    main()
