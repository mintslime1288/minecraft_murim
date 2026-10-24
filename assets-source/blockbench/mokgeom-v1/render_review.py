"""Offline textured geometry preview, not a Blockbench or in-game screenshot."""
from pathlib import Path
import json, math
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parent/'review'
model=json.loads((ROOT/'mokgeom_reference_v1.bbmodel').read_text(encoding='utf-8'))
texture=np.array(Image.open(ROOT/'mokgeom_reference.png').convert('RGB'))
W,H=1500,1250
canvas=np.full((H,W,3),[238,235,229],dtype=np.uint8)
depth=np.full((H,W),-1e20)

def render(yaw, pitch, scale, center, target=(8,7.5,8), clip=None):
    a,b=math.radians(yaw),math.radians(pitch)
    rotation=np.array([[math.cos(a),0,math.sin(a)],
        [math.sin(a)*math.sin(b),math.cos(b),-math.cos(a)*math.sin(b)],
        [-math.sin(a)*math.cos(b),math.sin(b),math.cos(a)*math.cos(b)]])
    def tri(p,uv,light):
        low=np.maximum(np.floor(p[:,:2].min(axis=0)).astype(int),[0,0])
        high=np.minimum(np.ceil(p[:,:2].max(axis=0)).astype(int),[W-1,H-1])
        if clip:
            low=np.maximum(low,clip[:2]);high=np.minimum(high,clip[2:])
        if np.any(high<low):return
        xx,yy=np.meshgrid(np.arange(low[0],high[0]+1)+.5,np.arange(low[1],high[1]+1)+.5)
        x0,y0=p[0,:2];x1,y1=p[1,:2];x2,y2=p[2,:2]
        den=(y1-y2)*(x0-x2)+(x2-x1)*(y0-y2)
        if abs(den)<1e-8:return
        u=((y1-y2)*(xx-x2)+(x2-x1)*(yy-y2))/den
        v=((y2-y0)*(xx-x2)+(x0-x2)*(yy-y2))/den
        w=1-u-v
        z=u*p[0,2]+v*p[1,2]+w*p[2,2]
        region=depth[low[1]:high[1]+1,low[0]:high[0]+1]
        hit=(u>=-1e-6)&(v>=-1e-6)&(w>=-1e-6)&(z>region)
        if not hit.any():return
        tx=np.clip((u*uv[0,0]+v*uv[1,0]+w*uv[2,0]).astype(int),0,1023)
        ty=np.clip((u*uv[0,1]+v*uv[1,1]+w*uv[2,1]).astype(int),0,1023)
        col=np.clip(texture[ty,tx]*light,0,255).astype(np.uint8)
        canvas[low[1]:high[1]+1,low[0]:high[0]+1][hit]=col[hit]
        region[hit]=z[hit]
    for e in model['elements']:
        x0,y0,z0=e['from'];x1,y1,z1=e['to']
        faces={
          'south':([(x0,y1,z1),(x1,y1,z1),(x1,y0,z1),(x0,y0,z1)],(0,0,1)),
          'north':([(x1,y1,z0),(x0,y1,z0),(x0,y0,z0),(x1,y0,z0)],(0,0,-1)),
          'east':([(x1,y1,z1),(x1,y1,z0),(x1,y0,z0),(x1,y0,z1)],(1,0,0)),
          'west':([(x0,y1,z0),(x0,y1,z1),(x0,y0,z1),(x0,y0,z0)],(-1,0,0)),
          'up':([(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],(0,1,0)),
          'down':([(x0,y0,z1),(x1,y0,z1),(x1,y0,z0),(x0,y0,z0)],(0,-1,0))}
        for key,(verts,normal) in faces.items():
            n=rotation@np.array(normal)
            if n[2]<=0:continue
            p=(np.array(verts)-target)@rotation.T
            p[:,0]=p[:,0]*scale+center[0];p[:,1]=center[1]-p[:,1]*scale
            uv=e['faces'][key]['uv'];q=np.array([[uv[0],uv[1]],[uv[2],uv[1]],[uv[2],uv[3]],[uv[0],uv[3]]])
            light=.78+.22*max(0,float(np.dot(n,np.array([-.25,.35,.90]))))
            for indices in [[0,1,2],[0,2,3]]:tri(p[indices],q[indices],light)

render(0,0,30,(260,680))
render(42,6,30,(650,680))
render(35,12,95,(1130,720),target=(7.9,-3.7,8),clip=(900,180,1490,1190))
im=Image.fromarray(canvas)
d=ImageDraw.Draw(im)
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',20)
title=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',38)
d.text((55,35),'MURIMRPG / MOKGEOM',font=title,fill='#342c23')
d.text((55,88),'REFERENCE DETAIL V1   /   CUBOID MODEL REVIEW',font=font,fill='#756b5f')
for x,label in [(110,'01  FRONT'),(510,'02  THREE-QUARTER'),(960,'03  GRIP DETAIL')]:
    d.text((x,145),label,font=font,fill='#716454')
d.text((55,1210),f'{len(model["elements"]):,} cubes | Unmodified reference texture | Offline geometry render - not in-game',font=font,fill='#756b5f')
im.save(ROOT/'mokgeom_review.png')
print(ROOT/'mokgeom_review.png')
