"""Fetch just one viewport tile per provider; inspect live PNG transparency and CRS metadata."""
import io, json, math, pathlib, urllib.request, xml.etree.ElementTree as ET
from PIL import Image
OUT = pathlib.Path('app/build/verification/services')
OUT.mkdir(parents=True, exist_ok=True)
def read(url):
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'TerraParcel/0.2.0 verification'}),timeout=45) as response:
        return response.read()
root=ET.fromstring(read('https://geoportal.asig.gov.al/service/zrpp/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetCapabilities'))
layer=next(l for l in root.iter('Layer') if l.findtext('Name')=='p_kadas_albscad_072026')
assert any(b.attrib.get('SRS')=='EPSG:900913' for b in layer.findall('BoundingBox'))
metadata=json.loads(read('https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer?f=pjson'))
z=17; n=2**z; lat=41.3275; lon=19.8187
x=int((lon+180)/360*n); y=int((1-math.asinh(math.tan(math.radians(lat)))/math.pi)/2*n)
world=20037508.342789244; span=2*world/n
bbox=','.join(map(str,(-world+x*span,world-(y+1)*span,-world+(x+1)*span,world-y*span)))
urls={
 'satellite':f'https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
 'cadastre':'https://geoportal.asig.gov.al/service/zrpp/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=p_kadas_albscad_072026&STYLES=&SRS=EPSG:900913&BBOX='+bbox+'&WIDTH=256&HEIGHT=256&FORMAT=image/png&TRANSPARENT=TRUE'}
report={'esri_credit':metadata['copyrightText'],'scale_hint':layer.find('ScaleHint').attrib,'tile':[z,x,y],'bbox':bbox}
for name,url in urls.items():
    data=read(url); im=Image.open(io.BytesIO(data)); im.load(); assert im.size==(256,256)
    (OUT/(name+('.png' if im.format=='PNG' else '.jpg'))).write_bytes(data)
    if name=='cadastre':
        px=list(im.convert('RGBA').getdata()); clear=sum(p[3]==0 for p in px); ink=sum(p[3]>0 for p in px)
        assert clear>0 and ink>0, 'Expected transparent background and visible cadastral boundaries'
        report['transparent_pixels']=clear; report['boundary_pixels']=ink
    else: assert len(im.getcolors(65536) or [])>100, 'Expected satellite imagery'
(OUT/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
