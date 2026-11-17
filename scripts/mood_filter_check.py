import json,random,urllib.request,urllib.parse,sys
BASE="https://www.streamflixvip.online/api/tmdb"
cache={}
def disc(t,params):
    q=dict(params); q['path']=f'/discover/{t}'
    url=BASE+'?'+urllib.parse.urlencode(q)
    if url in cache: return cache[url]
    r=json.load(urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'okhttp'}),timeout=20)).get('results',[])
    cache[url]=r; return r
G={}
for t in ('movie','tv'):
    try:
        for g in json.load(urllib.request.urlopen(BASE+'?'+urllib.parse.urlencode({'path':f'/genre/{t}/list','language':'pt-BR'}),timeout=20))['genres']: G[g['id']]=g['name']
    except Exception as e: print('genre list fail',e)
NEW=len(sys.argv)>1 and sys.argv[1]=='new'
ADULT={'movie':'16,10751','tv':'16,10751,10762'}
MOODS={ # name: (movie, tv, extra_without_movie, extra_without_tv)
 'Quero rir':('35','35','27',''),
 'Suspense / Terror':('27|53','9648' if NEW else '9648|80','28,12,35,10402,10770,36,10752,10749,99','10759,35,10764,99,10767'),
 'Ação':('28','10759','',''),
 'Romance':('10749',None,'27',''),
 'Pra família':('10751|16','10751|10762','',''),
 'Chorar / Drama':('18','18','35','35,10764'),
}
for name,(mg,tg,xm,xt) in MOODS.items():
    if len(sys.argv)>2 and not name.startswith(sys.argv[2]): continue
    picks=[]
    for i in range(20):
        types=[x for x in (('movie',mg,xm),('tv',tg,xt)) if x[1]]
        pool=[]
        for t,g,x in types:
            p={'sort_by':'popularity.desc','include_adult':'false','vote_count.gte':'200' if t=='movie' else '60','page':str(random.randint(1,8)),'with_genres':g,'language':'pt-BR'}
            if NEW and name!='Pra família':
                p['without_genres']=','.join(filter(None,[ADULT[t],x]))
                if t=='movie' and name.startswith('Suspense'):
                    p['with_genres']='27' if random.random()<0.6 else '53'
                if t=='tv' and name.startswith('Suspense'):
                    del p['with_genres']; p['with_keywords']='315058'; p['page']=str(random.randint(1,4))
            for r in disc(t,p):
                if not r.get('poster_path') or not r.get('overview'): continue
                gi=set(r.get('genre_ids',[]))
                if NEW:
                    want={int(v) for v in p['with_genres'].replace(',','|').split('|')} if 'with_genres' in p else set()
                    bad={int(v) for v in ','.join(filter(None,[ADULT[t],x])).split(',')} if name!='Pra família' else set()
                    if (want and not gi&want) or gi&bad: continue
                pool.append((t,r))
        t,r=random.choice(pool)
        picks.append(f"{(r.get('title') or r.get('name'))} [{t}; {', '.join(G.get(x,str(x)) for x in r.get('genre_ids',[]))}]")
    print(f"\n== {name} ({'novo' if NEW else 'atual'}) ==")
    for p in picks: print(' -',p)
