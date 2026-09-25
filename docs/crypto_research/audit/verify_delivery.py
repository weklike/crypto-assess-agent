"""Validate research artifacts only. Does NOT run the user's project or evaluate labels.
Run: python audit/verify_delivery.py (requires PyYAML).
"""
from pathlib import Path
from fractions import Fraction as F
from collections import Counter
import csv, hashlib, json
import yaml
R=Path(__file__).resolve().parents[1]
def rounded_positive_fraction(v:F,places:int)->str:
    if v < 0: raise ValueError('This audit expects nonnegative scores.')
    scale=10**places
    scaled=v*scale
    n=(2*scaled.numerator+scaled.denominator)//(2*scaled.denominator)
    return f'{n//scale}.{n%scale:0{places}d}'
qs=[json.loads(x) for x in (R/'drafts/retrieval_queries.candidates.20260925.jsonl').read_text(encoding='utf-8').splitlines()]
assert len(qs)==150
assert len({q['id'] for q in qs})==150
assert len({q['query'] for q in qs})==150
assert Counter(q['style'] for q in qs)=={'colloquial':50,'paraphrase':50,'keyword':50}
assert Counter(q['layer'] for q in qs)=={'物理和环境':18,'网络和通信':24,'设备和计算':21,'应用和数据':27,'管理制度':15,'人员管理':15,'建设运行':15,'应急处置':15}
for q in qs:
    assert set(q)=={'id','query','gold_refs','style','layer','level','note'}
    assert q['gold_refs']==[] and q['level'] is None
pr=json.loads((R/'drafts/retrieval_queries.provenance.json').read_text(encoding='utf-8'))
assert len(pr['records'])==150
assert len({p['intent_group'] for p in pr['records']})==50
assert all(n==3 for n in Counter(p['intent_group'] for p in pr['records']).values())
assert all(p['owner_confirmed'] is False and p['synthetic'] is True for p in pr['records'])
y=yaml.safe_load((R/'drafts/scoring_arithmetic.review-candidates.yaml').read_text(encoding='utf-8'))
assert len(y['cases'])==36
checks=[]
for c in y['cases']:
    a=c['inputs'];kind=c['kind'];places=4
    assert c['owner_confirmed'] is False and c['synthetic'] is True
    if kind=='object_dak':
        if not a['D']:v=F(0)
        elif a['A'] and a['K']:v=F(1)
        elif a['K']:v=F(1,2)*F(a['Ra'])
        elif a['A']:v=F(1,2)*F(a['Rk'])
        else:v=F(1,4)*F(a['Ra'])*F(a['Rk'])
    elif kind=='compensation':v=max(F(a['PA'])/2,F(a['PB']))
    elif kind=='unit_mean':v=sum(map(F,a['object_scores']))/len(a['object_scores'])
    elif kind=='layer_weighted':v=sum(F(s)*F(w) for s,w in zip(a['unit_scores'],a['unit_weights']))/sum(map(F,a['unit_weights']))
    elif kind=='management_unit':v={'符合':F(1),'部分符合':F(1,2),'不符合':F(0)}[a['judgment']]
    elif kind=='total':
        scores=list(a['layer_scores'].values());weights=list(a['layer_weights'].values());v=F(0);places=2
        for inds,cap in [(range(4),70),(range(4,8),30)]:
            use=[i for i in inds if scores[i] is not None]
            v+=cap*sum(F(scores[i])*F(weights[i]) for i in use)/sum(F(weights[i]) for i in use)
    elif kind=='undefined_boundary':
        assert c['candidate_expected'] is None
        checks.append({'id':c['id'],'status':'PENDING_OWNER_DECISION','numeric_pass_claimed':False});continue
    else:raise AssertionError(kind)
    actual=rounded_positive_fraction(v,places)
    assert actual==c['candidate_expected'],(c['id'],actual,c['candidate_expected'])
    checks.append({'id':c['id'],'arithmetic_check':'PASS','recomputed':actual})
assert sum(c.get('arithmetic_check')=='PASS' for c in checks)==35
for fn,n in [('标准规范化50条抽查.csv',50),('题库20题抽查.csv',20),('要求条款覆盖登记.csv',64)]:
    with (R/'worksheets'/fn).open(encoding='utf-8-sig',newline='') as f:assert len(list(csv.DictReader(f)))==n
with (R/'docs/02_来源登记.csv').open(encoding='utf-8-sig',newline='') as f:src=list(csv.DictReader(f))
assert len(src)==24 and len({s['编号'] for s in src})==24
assert (R/'docs/05_模拟系统场景研究卡.md').read_text(encoding='utf-8').count('所有者填写：')==12
for f in R.rglob('*.yaml'):yaml.safe_load(f.read_text(encoding='utf-8'))
for f in R.rglob('*.md'):
    assert f.read_text(encoding='utf-8').count('```')%2==0,f
# Check inputs only in this runtime; files need not exist after delivery.
fp=json.loads((R/'audit/输入文件指纹.json').read_text(encoding='utf-8'))['uploaded_files_sha256']
original_status={}
for name,digest in fp.items():
    p=Path('/mnt/data')/name
    if p.exists():
        assert hashlib.sha256(p.read_bytes()).hexdigest()==digest
        original_status[name]='UNCHANGED'
    else:original_status[name]='NOT_PRESENT_IN_THIS_RUNTIME'
res={'date':'2026-09-25','scope':'Only delivered research-file structure and independent rational arithmetic; not project tests or formal evaluation.','queries':{'count':150,'unique_ids':150,'unique_texts':150,'intent_groups':50,'style_count':dict(Counter(q['style'] for q in qs)),'gold_labeled':0,'synthetic_ratio':1.0,'structure':'PASS'},'arithmetic':{'numeric_checked':35,'numeric_checks_passed':35,'pending_boundaries':1,'owner_reviewed':False,'application_scoring_test_run':False,'checks':checks},'sources_registered':24,'scenario_research_cards':12,'original_files':original_status,'project_tests_run':False,'formal_eval_run':False,'standard_normalization_run':False,'qa_bank_downloaded':False,'llm_api_calls':0}
(R/'audit/交付验证记录.json').write_text(json.dumps(res,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'research_structure':'PASS','queries':150,'intent_groups':50,'arithmetic_numeric_checks':'35/35','pending_boundary':1,'sources':24,'scenario_cards':12,'project_tests':'NOT_RUN','formal_eval':'NOT_RUN','original_files':original_status},ensure_ascii=False,indent=2))
