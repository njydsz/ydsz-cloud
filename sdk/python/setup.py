from setuptools import setup, find_packages

setup(
    name="pmis-rule-client",
    version="1.0.0",
    description="PMIS 规则引擎 Python SDK",
    packages=find_packages(),
    python_requires=">=3.8",
    entry_points={
        "console_scripts": [
            "pmis-rule-cli=pmis_rule_cli:main",
        ],
    },
)
