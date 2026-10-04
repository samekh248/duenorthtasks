W,H,GAP=360,740,40
ACC="#0050EF"
F="font-family=\"Selawik, 'Segoe UI', 'Open Sans', sans-serif\""
out=[]
def t(x,y,s,size,w=400,fill="#FFF",op=1,extra=""):
    out.append(f'<text x="{x}" y="{y}" {F} font-size="{size}" font-weight="{w}" fill="{fill}" fill-opacity="{op}" {extra}>{s}</text>')
def r(x,y,w,h,fill="none",stroke=None,sw=2,op=1):
    s=f' stroke="{stroke}" stroke-width="{sw}"' if stroke else ''
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill}" fill-opacity="{op}"{s}/>')
def checkbox(x,y,checked=False):
    if checked:
        r(x,y,22,22,ACC,"#FFF",2)
        out.append(f'<path d="M{x+5} {y+11} l4 5 l8 -10" stroke="#FFF" stroke-width="2.5" fill="none"/>')
    else: r(x,y,22,22,"none","#FFF",2)
def appbar(ox,icons,labels=None):
    y=H-72
    r(ox,y,W,72,"#1F1F1F")
    n=len(icons); start=ox+W/2-(n*64)/2+8
    for i,ic in enumerate(icons):
        cx=start+i*64+24; cy=y+30
        out.append(f'<circle cx="{cx}" cy="{cy}" r="19" fill="none" stroke="#FFF" stroke-width="2"/>')
        out.append(f'<text x="{cx}" y="{cy+7}" text-anchor="middle" {F} font-size="20" fill="#FFF">{ic}</text>')
        if labels: t(cx,y+64,labels[i],10,extra='text-anchor="middle"')
    t(ox+W-34,y+20,"•••",14,extra='letter-spacing="1"')
def phone(i,title):
    ox=i*(W+GAP)
    out.append(f'<g>')
    r(ox,0,W,H,"#000")
    # status bar
    t(ox+W-60,18,"18:35",12,op=.8)
    t(ox,H+30,title,15,w=600,fill="#333",extra='class="cap"')
    return ox
# 1 choose a service
ox=phone(0,"1. choose a service")
t(ox+12,60,"DUE NORTH TASKS",14,w=600)
t(ox+10,130,"sync with",46,w=300)
for k,(a,b) in enumerate([("google tasks","Gmail, Calendar and Google Tasks"),("microsoft to do","Outlook and Microsoft To Do")]):
    y=210+k*100
    r(ox+12,y,72,72,ACC)
    t(ox+48,y+48,"✓" if k==0 else "◆",30,extra='text-anchor="middle"')
    t(ox+100,y+30,a,24,w=300)
    t(ox+100,y+54,b,12,op=.6)
t(ox+12,450,"one service at a time. you can switch",15,op=.6)
t(ox+12,472,"later in settings.",15,op=.6)
out.append('</g>')
# 2 pivot
ox=phone(1,"2. lists pivot")
# progress dots
for d in range(5): out.append(f'<circle cx="{ox+120+d*14}" cy="28" r="2" fill="{ACC}"/>')
t(ox+12,60,"DUE NORTH TASKS",14,w=600)
out.append(f'<svg x="{ox}" y="0" width="{W}" height="{H}" overflow="hidden">')
t(10,124,"groceries",52,w=300)
t(275,124,"work",52,w=300,op=.4)
out.append('</svg>')
tasks=[("buy milk","due tomorrow",False),("eggs, a dozen","",False),("coffee beans","due fri 10/9",False),("olive oil","",False)]
y=170
for name,due,done in tasks:
    checkbox(ox+14,y)
    t(ox+52,y+18,name,21,w=350)
    if due: t(ox+52,y+40,due,13,fill="#1BA1E2" if False else ACC, w=600)
    y+=62 if due else 52
t(ox+12,y+24,"completed (2)",15,op=.6)
y+=44
for name in ["bread","bananas"]:
    checkbox(ox+14,y,True)
    t(ox+52,y+18,name,21,w=350,op=.5,extra='text-decoration="line-through"')
    y+=52
appbar(ox,["+","⟳","⇅"])
out.append('</g>')
# 3 detail
ox=phone(2,"3. task detail")
t(ox+12,60,"GROCERIES",14,w=600)
t(ox+10,120,"buy milk",46,w=300)
t(ox+12,170,"notes",15,op=.6)
r(ox+12,180,W-24,64,"#FFF",op=.12)
t(ox+22,206,"2%, the big one",15)
t(ox+12,280,"due",15,op=.6)
r(ox+12,290,W-24,40,"none","#FFF",2,op=1)
t(ox+22,317,"monday, october 5",17)
t(ox+12,370,"steps",15,op=.6)
for k,(s,d) in enumerate([("check the fridge first",True),("bring a bag",False)]):
    yy=384+k*44; checkbox(ox+14,yy,d); t(ox+52,yy+18,s,17,op=.5 if d else 1)
t(ox+52,384+2*44+18,"+ add step",17,fill=ACC)
appbar(ox,["✓","💾","🗑"],["complete","save","delete"])
# label expanded hint
out.append('</g>')
# 4 theme
ox=phone(3,"4. settings: theme")
t(ox+12,60,"SETTINGS",14,w=600)
out.append(f'<svg x="{ox}" y="0" width="{W}" height="{H}" overflow="hidden">')
t(10,124,"theme",52,w=300)
t(180,124,"account",52,w=300,op=.4)
out.append('</svg>')
t(ox+12,170,"background",15,op=.6)
r(ox+12,180,W-24,40,"none","#FFF",2)
t(ox+22,207,"dark",17)
t(ox+12,260,"accent color",15,op=.6)
accents=["#A4C400","#60A917","#008A00","#00ABA9","#1BA1E2","#0050EF","#6A00FF","#AA00FF","#F472D0","#D80073","#A20025","#E51400","#FA6800","#F0A30A","#E3C800","#825A2C","#6D8764","#647687","#76608A","#87794E"]
s=76
for k,c in enumerate(accents):
    col,row=k%4,k//4
    x=ox+12+col*(s+8); y=272+row*(s+8)
    r(x,y,s,s,c)
    if c==ACC: r(x+3,y+3,s-6,s-6,"none","#FFF",3)
out.append('</g>')
TW=4*W+3*GAP
svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-20 -20 {TW+40} {H+70}" width="{TW+40}" height="{H+70}"><rect x="-20" y="-20" width="{TW+40}" height="{H+70}" fill="#F2F2F2"/>'+"\n".join(out)+'</svg>'
import sys
open(sys.argv[1],"w").write(svg)
