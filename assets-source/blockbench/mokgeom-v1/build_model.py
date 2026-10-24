"""Build an editable cuboid item from the supplied reference, without editing its pixels.

Only geometry and UVs are generated. The reference PNG is copied byte-for-byte.
No game resources are installed by this script. Pillow/numpy are authoring tools.
"""
from pathlib import Path
import argparse, base64, hashlib, json, math, shutil, uuid
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--reference', type=Path, default=Path(r'C:\Users\namja\Downloads\Meshy_AI_026339ae7a1f6064e1610b8c0d78e425132645a0ff091027336b7f19d5befc43.png'))
REF = parser.parse_args().reference
OUT = ROOT / 'review'
OUT.mkdir(exist_ok=True)
if not REF.is_file():
    raise SystemExit(f'Reference image not found: {REF}. Supply its current path with --reference.')
if REF.resolve() != (OUT / 'mokgeom_reference.png').resolve():
    shutil.copyfile(REF, OUT / 'mokgeom_reference.png')
pixels = np.array(Image.open(REF).convert('RGB')).astype(float)
mask = (pixels[:,:,0]-pixels[:,:,2]>20) & (pixels[:,:,0]>50)
elements, groups = [], {}
S = 31.0
def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL, 'murim-mokgeom-v1/'+name))
def X(p): return 8+(p-512)/S
def Y(p): return -8+(998-p)/S
def bounds(y):
    if 707 <= y < 742:
        return 480., 526.+(y-707)*.055
    if 752 <= y < 998:
        l=481.+(y-752)*.075
        r=529.+(y-752)*.062
        if y>988:
            inset=(y-988)*.7
            l+=inset; r-=inset
        return l,r
    xs = np.where(mask[int(y)])[0]
    return float(xs.min()+1), float(xs.max()-1)
def cube(group, name, a, b, uv, side_uv=None):
    key = uid(name)
    def face(rect): return {'uv':[round(v,5) for v in rect], 'texture':0}
    side_uv = side_uv or uv
    faces = {f:face(side_uv) for f in ['east','west','up','down']}
    faces['south']=face(uv)
    faces['north']=face([uv[2],uv[1],uv[0],uv[3]])
    elements.append({'name':name,'type':'cube','uuid':key,'from':[round(v,6) for v in a],
                     'to':[round(v,6) for v in b],'origin':[8,0,8], 'rotation':[0,0,0],
                     'box_uv':False,'rescale':False,'locked':False,'visibility':True,
                     'autouv':0,'color':list(groups).index(group) if group in groups else len(groups),
                     'faces':faces})
    groups.setdefault(group,[]).append(key)

def body(group, y0, y1, step, strips, depth):
    for y in range(y0,y1,step):
        yy=min(y+step,y1)
        l,r=bounds((y+yy)/2)
        for i in range(strips):
            t0=-1+2*i/strips; t1=-1+2*(i+1)/strips
            x0=l+(r-l)*i/strips; x1=l+(r-l)*(i+1)/strips
            # Rounded elliptical section; the blade has a subtly flatter ridge.
            t=(t0+t1)/2
            z=depth*math.sqrt(max(.04,1-t*t))
            cube(group,f'{group}_{y:04}_{i:02}',[X(x0),Y(yy),8-z],[X(x1),Y(y),8+z],
                 [x0,y,x1,yy],[(l+r)/2-1,y,(l+r)/2+1,yy])

body('01_Blade_curved_bevel',30,707,3,7,.29)
body('02_Wooden_collar',707,742,3,7,.53)
body('04_Grip_rounded_wood',752,998,3,9,.57)

# Three concentric elliptical tiers form the rounded, dark wooden tsuba.
for tier,(bottom,top,rx,rz) in enumerate([(-.10,.00,1.48,1.00),(.00,.23,1.56,1.08),(.23,.31,1.46,.98)]):
    for j in range(52):
        x0=-rx+2*rx*j/52; x1=-rx+2*rx*(j+1)/52
        z=rz*math.sqrt(max(.01,1-((x0+x1)/(2*rx))**2))
        px0=455+(j/52)*97; px1=455+((j+1)/52)*97
        # Sample the solid wooden rim, rather than the surrounding photo background.
        cube('03_Tsuba_beveled_disc',f'tsuba_{tier}_{j}',[7.75+x0,bottom,8-z],[7.75+x1,top,8+z],
             [485+j*.60,742,487+j*.60,749],[485+j*.60,742,487+j*.60,748])

# Relief follows actual light cord pixels, preserving the reference's crossing wraps.
# The unseen back repeats the same wrapping material as an explicit reconstruction.
for y in range(760,979,3):
    l,r=bounds(y+1)
    for x in range(math.ceil(l)+2,math.floor(r)-2,3):
        color=pixels[y:y+3,x:x+3].mean(axis=(0,1))
        red,green,blue=color
        if green/red<.755 or blue/max(green,1)<.61: continue
        t=((x+1.5)-(l+r)/2)/((r-l)/2)
        if abs(t)>.94: continue
        surface=.57*math.sqrt(1-t*t)
        for sign in [-1,1]:
            z=8+sign*surface
            cube('05_Raised_cord_weave',f'cord_{y}_{x}_{sign}',[X(x),Y(y+3),z-.035],[X(x+3),Y(y),z+.035],
                 [x,y,x+3,y+3])

display={
 'gui':{'rotation':[0,0,-38],'translation':[0,0,0],'scale':[.53,.53,.53]},
 'thirdperson_righthand':{'rotation':[0,-90,0],'translation':[0,9.0,1.0],'scale':[.72,.72,.72]},
 'thirdperson_lefthand':{'rotation':[0,90,0],'translation':[0,9.0,1.0],'scale':[.72,.72,.72]},
 'firstperson_righthand':{'rotation':[0,-90,18],'translation':[1,6,0],'scale':[.65,.65,.65]},
 'firstperson_lefthand':{'rotation':[0,90,-18],'translation':[1,6,0],'scale':[.65,.65,.65]},
 'ground':{'rotation':[0,0,90],'translation':[0,3,0],'scale':[.43,.43,.43]},
 'fixed':{'rotation':[0,0,-38],'translation':[0,0,0],'scale':[.53,.53,.53]}}
# JSON syntax is valid YAML; keep editable grip transforms outside generator code.
display.update(json.loads((ROOT / 'grip-display.yaml').read_text(encoding='utf-8')))
model={'meta':{'format_version':'4.10','model_format':'java_block','box_uv':False},
 'name':'MurimRPG Mokgeom - Reference Detail v1','model_identifier':'mokgeom_reference_v1',
 'resolution':{'width':1024,'height':1024},'ambientocclusion':False,'gui_light':'front',
 'elements':elements,'outliner':[{'name':name,'uuid':uid(name),'origin':[8,0,8],
 'rotation':[0,0,0],'export':True,'isOpen':False,'visibility':True,'children':children} for name,children in groups.items()],
 'textures':[{'name':'mokgeom_reference.png','id':'0','uuid':uid('texture'),'relative_path':'mokgeom_reference.png',
              'namespace':'murimrpg','folder':'item','width':1024,'height':1024,'uv_width':1024,'uv_height':1024,
              'source':'data:image/png;base64,'+base64.b64encode(REF.read_bytes()).decode(),'internal':True,'mode':'bitmap'}],
 'display':display}
(OUT/'mokgeom_reference_v1.bbmodel').write_text(json.dumps(model,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
java_elements=[]
for e in elements:
    java_elements.append({'name':e['name'],'from':e['from'],'to':e['to'],
                         'faces':{f:{'uv':[round(v/64,7) for v in data['uv']],'texture':'#0'} for f,data in e['faces'].items()}})
java={'credit':'MurimRPG reference-derived cuboid model; review before deployment',
 'ambientocclusion':False,'gui_light':'front','textures':{'0':'murimrpg:item/mokgeom_reference','particle':'murimrpg:item/mokgeom_reference'},
 'display':display,'elements':java_elements}
(OUT/'mokgeom_reference_v1.json').write_text(json.dumps(java,separators=(',',':')),encoding='utf-8')
manifest={'status':'REVIEW_ONLY_NOT_INSTALLED','cubes':len(elements),'faces':len(elements)*6,
 'triangles_upper_bound':len(elements)*12,'groups':{k:len(v) for k,v in groups.items()},
 'texture_sha256':hashlib.sha256(REF.read_bytes()).hexdigest(),
 'texture_note':'Original PNG is byte-for-byte unchanged. UVs sample reference surfaces.',
 'back_note':'Unseen rear surface inferred using the same front-side material.',
 'validation':{'all_positive_volumes':all(all(b>a for a,b in zip(e['from'],e['to'])) for e in elements),
               'all_coordinates_in_legacy_bounds':all(-16<=v<=32 for e in elements for v in e['from']+e['to']),
               'all_uvs_in_image':all(0<=v<=1024 for e in elements for face in e['faces'].values() for v in face['uv'])}}
assert all(manifest['validation'].values()),manifest
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(json.dumps(manifest,indent=2))
