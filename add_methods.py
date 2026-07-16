import pathlib  
D='d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-docs/src/main/java/com/njydsz/pmis/common/docs'  
p=pathlib.Path(D+'/service/DocumentService.java')  
c=p.read_text(encoding='utf-8')  
c=c.rstrip()  
assert c.endswith('}')  
c=c[:-1]  
methods = ''' 
