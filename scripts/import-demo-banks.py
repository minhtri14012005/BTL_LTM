"""Import public, five-question demo banks through the real REST API.

Never overwrite existing banks/accounts. Local demo-author credentials are kept
in .env.demo-banks (ignored by Git), not in the dataset or output.
"""
import argparse
import copy
import http.cookiejar
import json
import secrets
import unicodedata
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--origin', default='http://127.0.0.1:8080')
    args = parser.parse_args()
    origin = args.origin.rstrip('/')
    fixture = ROOT/'data/demo-banks'
    dataset = json.loads((fixture/'questions.json').read_text(encoding='utf-8'))
    assert len(dataset['modes']) == 6
    assert all(len(b['questions']) == 5 and b['mode'] != 'QUIZ' for b in dataset['modes'])
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    csrf = {}

    def request(method, path, body=None, multipart=None):
        headers = {'Accept':'application/json'}
        if body is not None:
            body = json.dumps(body,ensure_ascii=False).encode('utf-8')
            headers['Content-Type'] = 'application/json'
        if multipart:
            body, headers['Content-Type'] = multipart
        if method != 'GET':
            headers[csrf['headerName']] = csrf['token']
        try:
            with client.open(urllib.request.Request(origin+path,data=body,headers=headers,method=method),timeout=30) as response:
                data = response.read()
                return json.loads(data) if data and 'application/json' in response.headers.get('Content-Type','') else data
        except urllib.error.HTTPError as error:
            # Application error bodies contain no credentials; do not print request payloads.
            raise RuntimeError(f'{method} {path}: HTTP{error.code} {error.read().decode("utf-8",errors="replace")}') from None

    def upload(filename, kind):
        boundary = 'multigame-demo-'+secrets.token_hex(16)
        media = (fixture/filename).read_bytes()
        mime = 'video/mp4' if kind == 'videos' else 'image/png'
        data = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\nContent-Type: {mime}\r\n\r\n'.encode()+media+f'\r\n--{boundary}--\r\n'.encode(),f'multipart/form-data; boundary={boundary}')
        return request('POST',f'/api/quizzes/{kind}/drafts',multipart=data)

    csrf.update(request('GET','/api/auth/csrf'))
    credentials = ROOT/'.env.demo-banks'
    if credentials.exists():
        account = json.loads(credentials.read_text(encoding='utf-8'))
    else:
        account = dict(username='demo_banks_'+secrets.token_hex(4),password=secrets.token_urlsafe(20))
        request('POST','/api/auth/register',dict(**account,displayName='Tác giả bộ mẫu 5 chế độ'))
        credentials.write_text(json.dumps(account,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    request('POST','/api/auth/login',account)
    csrf.clear()
    csrf.update(request('GET','/api/auth/csrf'))
    me = request('GET','/api/auth/me')
    existing = []
    page = 0
    while True:
        result = request('GET',f'/api/quizzes?scope=MINE&page={page}&size=100')
        existing.extend(result['items'])
        if len(existing) >= result['totalElements']: break
        page += 1
    imported = []
    for bank in dataset['modes']:
        matches = [q for q in existing if q['title'] == bank['title']]
        if len(matches) > 1: raise RuntimeError('Multiple existing banks with same demo title; refusing to overwrite')
        if matches:
            owner = request('GET',f"/api/quizzes/{matches[0]['id']}")
            if owner['mode'] != bank['mode'] or owner['visibility'] != dataset['visibility'] or len(owner['questions']) != 5:
                raise RuntimeError('Existing demo bank was modified; refusing to overwrite')
            for expected,actual in zip(bank['questions'],owner['questions']):
                for key,value in expected.items():
                    if key in ('imageFile','videoFile'):
                        video = key == 'videoFile'
                        ref = actual.get('mediaRef' if video else 'imageRef')
                        if not ref: raise RuntimeError('Existing demo image is missing')
                        request('GET',f"/api/quizzes/{owner['id']}/{'videos' if video else 'images'}/{ref[7:]}")
                    elif actual.get(key) != (list(dict.fromkeys(' '.join(unicodedata.normalize('NFC',a).lower().split()) for a in value)) if key == 'acceptedAnswers' else value):
                        raise RuntimeError('Existing demo question was modified; refusing to overwrite')
            status = 'EXISTING_VERIFIED'
        else:
            questions = copy.deepcopy(bank['questions'])
            for question in questions:
                if 'imageFile' in question:
                    uploaded = upload(question.pop('imageFile'),'images')
                    question['imageRef'] = uploaded['imageRef']
                if 'videoFile' in question:
                    uploaded = upload(question.pop('videoFile'),'videos')
                    question['mediaRef'] = uploaded['mediaRef']
            owner = request('POST','/api/quizzes',dict(mode=bank['mode'],title=bank['title'],visibility=dataset['visibility'],questions=questions))
            status = 'CREATED'
        imported.append(dict(id=owner['id'],mode=owner['mode'],title=owner['title'],questionCount=len(owner['questions']),status=status))
    output = dict(origin=origin,authorUsername=account['username'],authorUserId=me['id'],banks=imported)
    (fixture/'import-result.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    request('POST','/api/auth/logout',{})
    print(json.dumps(output,ensure_ascii=False,indent=2))

if __name__ == '__main__':
    main()
