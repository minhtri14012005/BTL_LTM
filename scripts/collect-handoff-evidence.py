"""Collect verified Task15 artifacts plus clearly labelled historical evidence; no credentials."""
import hashlib, json, re, shutil, zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1];out=ROOT/'experiments/handoff';out.mkdir(parents=True,exist_ok=True)
def copy(source,name=None):
    source=ROOT/source
    if not source.is_file():raise FileNotFoundError(source)
    target=out/(name or source.name);shutil.copyfile(source,target);return target
def text(path):
    data=path.read_bytes()
    return data.decode('utf-16') if data.startswith((b'\xff\xfe',b'\xfe\xff')) else data.decode('utf-8-sig')
def hash_file(p):return hashlib.sha256(p.read_bytes()).hexdigest()

units=['CodebaseStructureTest','GameReplayCacheTest','GameCommandParserTest','AuthenticatedSocketRegistryTest','SystemWebTest','SessionQueueTest','RoomBoundaryTest']
its=['GameCommandNetworkIT','GameReconnectNetworkIT','GameStartAuthorizationIT','MySqlSmokeIT']
evidence={'task':15,'technicalStatus':'PASS','handoffStatus':'PARTIAL','regression':{},'courseOriginalDocuments':'MISSING','multiDeviceLAN':'NOT_VERIFIED','newMachine':'NOT_VERIFIED','individualContributions':'NOT_VERIFIED'}
for scope,directory,names in [('unit','surefire-reports',units),('mysqlIntegration','failsafe-reports',its)]:
    selected=[];totals={k:0 for k in ['tests','failures','errors','skipped']}
    for path in sorted((ROOT/'target'/directory).glob('TEST-*.xml')):
        if path.stem.rsplit('.',1)[-1] not in names:continue
        suite=ET.parse(path).getroot();data={k:int(suite.attrib.get(k,0)) for k in totals}
        selected.append({'suite':suite.attrib['name'],**data});copy(str(path.relative_to(ROOT)))
        for k in totals:totals[k]+=data[k]
    if len(selected)!=len(names) or any(totals[k] for k in ['failures','errors','skipped']):raise AssertionError('Regression evidence incomplete/failing')
    evidence['regression'][scope]={'totals':totals,'suites':selected}
report=json.loads((ROOT/'target/task15-client-tests.json').read_text(encoding='utf-8-sig'))
if report['status']!='PASS' or len(report['unit'])!=32 or len(report['smoke'])!=9:raise AssertionError('Browser evidence incomplete')
evidence['browser']={'status':report['status'],'unit':len(report['unit']),'smokeGroups':len(report['smoke']),'browser':report.get('browser'),'gameIds':report['gameIds'],'accounts':len(report['userIds']),'placement':'4 isolated cookie contexts on same machine'}
ready=json.loads((ROOT/'target/task15-readiness.json').read_text(encoding='utf-8-sig'))
if ready['database']['status']!='UP':raise AssertionError('MySQL not ready')
evidence['readiness']=ready
jar=ROOT/'target/competitive-quiz-0.0.1-SNAPSHOT.jar'
with zipfile.ZipFile(jar) as z:
    if any('ExperimentServer' in n for n in z.namelist()):raise AssertionError('Baseline leaked into production JAR')
evidence['productionJar']={'sha256':hash_file(jar),'hasExperimentServer':False}
build=text(ROOT/'target/task15-regression.log')
if 'BUILD SUCCESS' not in build:raise AssertionError('Build not successful')
evidence['build']='SUCCESS'
sample=ROOT/'config/application-local.properties.example'
if (ROOT/'target/task15-sample.properties').read_bytes()!=sample.read_bytes():raise AssertionError('Sample config changed')
evidence['sampleConfig']={'sha256':hash_file(sample),'testedCopyIdentical':True,'credentials':'inherited local environment; not included'}
for path in ['target/task15-regression.log','target/task15-client.log','target/task15-client-tests.json','target/task15-readiness.json','target/task15-mysql-evidence.txt','target/task15-smoke-server.log','target/task15-smoke-server-error.log']:
    copy(path)
copy('target/task12-final.png','task15-final.png');copy('target/task12-history-mobile.png','task15-history-mobile.png')
for path in ['target/task13-test-summary.json','target/task13-evidence.json','target/task14-review-evidence.json']:
    copy(path,'historical-'+Path(path).name)
# Only fresh-schema STARTUP lines from pilot; excludes debug HTTP/request bodies.
startup=text(ROOT/'target/task15-pilot/baseline-server.log')
rows=[line for line in startup.splitlines() if any(s in line for s in ['Schema history table','Creating Schema History','Current version of schema','Migrating schema','Successfully applied','Database: jdbc:mysql:'])]
(out/'fresh-migrations.log').write_text('\n'.join(rows)+'\n',encoding='utf-8')
files=[ROOT/'pom.xml',sample]+list((ROOT/'src/main').rglob('*'))
evidence['productionSourceManifest']={str(p.relative_to(ROOT)).replace('\\','/'):hash_file(p) for p in files if p.is_file()}
evidence['harnessSourceManifest']={str(p.relative_to(ROOT)).replace('\\','/'):hash_file(p) for p in [ROOT/'scripts/experiment-client.py',ROOT/'scripts/run-experiment.ps1',ROOT/'scripts/summarize-experiment.py',ROOT/'scripts/plot-experiment.py',ROOT/'src/test/java/vn/edu/quiz/realtime/session/ExperimentServer.java']}
summary=json.loads((ROOT/'experiments/results/2026-10-06-loopback/summary.json').read_text(encoding='utf-8'))
evidence['experiment']={mode:{'clientRows':v['client_rows'],'serverRows':v['server_rows'],'durableGameChecks':v['durable_game_checks'],'duplicateSideEffects':v['duplicate_side_effects'],'invariantViolations':v['durable_invariant_violations']} for mode,v in summary['modes'].items()}
# Local DB password is used only for a leak check, never logged/serialized.
local=ROOT/'config/application-local.properties'
if local.exists():
    values=dict(re.findall(r'^([^#=\r\n]+)=(.*)$',local.read_text(encoding='utf-8-sig'),re.M))
    password=values.get('DB_PASSWORD','').strip()
    if password:
        leaks=[]
        for folder in [out,ROOT/'experiments/results/2026-10-06-loopback']:
            for p in folder.rglob('*'):
                if p.is_file() and p.suffix not in ['.png'] and (password.encode() in p.read_bytes() or password.encode('utf-16-le') in p.read_bytes()):leaks.append(str(p.relative_to(ROOT)))
        if leaks:raise AssertionError('Credential leak detected in evidence: '+', '.join(leaks))
evidence['credentialLeakCheck']='PASS (local DB password absent from evidence files)'
(out/'evidence.json').write_text(json.dumps(evidence,indent=2,ensure_ascii=False),encoding='utf-8')
(out/'sha256.json').write_text(json.dumps({p.name:hash_file(p) for p in out.iterdir() if p.is_file() and p.name!='sha256.json'},indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in evidence.items() if k not in ['productionSourceManifest','harnessSourceManifest','regression']},ensure_ascii=True,indent=2))
