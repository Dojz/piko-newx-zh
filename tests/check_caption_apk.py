"""Guard the reported presenter VerifyError in a final patched APK.

Optional integration check (requires androguard):
python tests/check_caption_apk.py original.apk patched.apk
Checks original bodies/branches and the entry receiver, so a stale embedded legacy bundle fails.
"""
import sys,json
from zipfile import ZipFile
from loguru import logger
logger.remove()
from androguard.core.dex import DEX
EXT='Lapp/morphe/extension/newx/misc/DownloadCaption;'
SCOPE='Lcom/x/urt/items/post/translate/'

def methods(path, owners=None):
    result={}
    with ZipFile(path) as z:
        for name in z.namelist():
            if not name.endswith('.dex'):continue
            data=z.read(name)
            if owners is None:
                if EXT.encode() not in data:continue
            elif not any(owner.encode() in data for owner in owners):continue
            dex=DEX(data)
            for c in dex.get_classes():
                if owners is None:
                    if not c.get_name().startswith(SCOPE):continue
                elif c.get_name() not in owners:continue
                for m in c.get_methods():
                    if 'Composer' not in m.get_descriptor() or not m.get_code():continue
                    ins=list(m.get_instructions())
                    records=[i for i,x in enumerate(ins) if x.get_name().startswith('invoke-static') and EXT+'->record(' in x.get_output()]
                    if owners is None and not records:continue
                    result[(c.get_name(),m.get_name(),m.get_descriptor())]=(name,m,ins,records)
    return result

def normalized(method):
    indexed=list(method.get_instructions_idx())
    indexes={offset:i for i,(offset,_) in enumerate(indexed)}
    result=[]
    for offset,ins in indexed:
        op=ins.get_name()
        if op.startswith('goto'):op='goto'
        if op.startswith('const-string'):op='const-string'
        values=[]
        for operand in ins.get_operands():
            kind=int(operand[0])
            if kind>=256:values.append(('reference',operand[2]))
            elif kind==3:values.append(('target',indexes[offset+operand[1]*2]))
            else:values.append((kind,operand[1]))
        result.append((op,values))
    return result

patched=methods(sys.argv[2]); assert patched,'No caption entries found'
owners={k[0] for k in patched}
original=methods(sys.argv[1],owners)
bodies=methods(sys.argv[2],owners)
report=[]
for key,(dex,m,ins,records) in patched.items():
    _,old,oldins,_=original[key]
    bodykey=(key[0],key[1]+'$pikoCaption',key[2])
    _,body,bodyins,_=bodies[bodykey]
    assert body.get_access_flags() & 2,(key,'body not private')
    assert body.get_code().get_registers_size()==old.get_code().get_registers_size(),key
    assert body.get_code().get_ins_size()==old.get_code().get_ins_size(),key
    assert normalized(body)==normalized(old),(key,'original body changed')
    incoming=m.get_code().get_ins_size()
    assert m.get_code().get_registers_size()==incoming+3,key
    assert [x.get_name() for x in ins]==['invoke-direct','move-result-object','move-object','iget-object','sget-object','invoke-static','return-object'],(key,[x.get_name() for x in ins])
    assert '$pikoCaption(' in ins[0].get_output(),key
    assert [x[1] for x in ins[0].get_operands() if int(x[0])==0]==list(range(3,3+incoming)),key
    assert ins[1].get_operands()[0][1]==1,key
    assert [x[1] for x in ins[2].get_operands()]==[0,3],key
    assert [x[1] for x in ins[3].get_operands()[:2]]==[0,0],key
    assert ins[3].get_operands()[2][2].startswith(key[0]+'->'),key
    assert ins[4].get_operands()[0][1]==2,key
    assert records==[5],key
    assert [x[1] for x in ins[5].get_operands() if int(x[0])==0]==[0,1,2],key
    assert ins[6].get_operands()[0][1]==1,key
    report.append({'owner':key[0],'method':key[1]+key[2],'dex':dex,'body_registers':body.get_code().get_registers_size(),'entry_registers':m.get_code().get_registers_size(),'original_returns_preserved':sum(x.get_name()=='return-object' for x in oldins)})
print(json.dumps(report,indent=2))
print('PASS: original bodies and branch targets preserved; all entries read fields through their own receiver and preserve returned states')
