import pathlib,base64,sys  
B='d:\\Code\\ydsz\\ydsz-pmis\\ydsz-pmis-backend\\ydsz-pmis-common\\ydsz-pmis-common-docs\\src\\main\\java\\com\\njydsz\\pmis\\common\\docs'  
BASE=pathlib.Path(B)  
def wf(r,c):  
    f=BASE/r;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(c,encoding='utf-8');print('OK:',r)  
print('Engine ready') 
