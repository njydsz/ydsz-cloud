import sys,pathlib,base64
path=sys.argv[1]
content=base64.b64decode(sys.argv[2])
pathlib.Path(path).parent.mkdir(parents=True,exist_ok=True)
pathlib.Path(path).write_bytes(content)
print("OK:",path)
