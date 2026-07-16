{
  "extends": ["@commitlint/config-conventional"],
  "rules": {
    "type-enum": [
      2,
      "always",
      ["feat", "fix", "docs", "style", "refactor", "perf", "test", "build", "ci", "chore", "revert"]
    ],
    "scope-enum": [
      2,
      "always",
      [
        "frontend",
        "gateway",
        "auth",
        "user",
        "project",
        "finance",
        "resource",
        "workflow",
        "report",
        "agent",
        "notification",
        "common",
        "deploy",
        "doc"
      ]
    ],
    "subject-max-length": [2, "always", 50],
    "body-max-line-length": [2, "always", 100]
  }
}
