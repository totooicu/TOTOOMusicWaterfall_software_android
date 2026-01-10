import feedparser
from typing import Dict, List, Any
import requests
from datetime import datetime, timedelta,timezone

import requests

url = "https://ieeexplore.ieee.org/rss/TOC36.XML"
proxy = {
    'http': 'http://localhost:10800',
    'https': 'http://localhost:10800'
}
payload={}
headers = {
   'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
   'Cookie': 'AWSALBAPP-1=_remove_; AWSALBAPP-2=_remove_; AWSALBAPP-3=_remove_; osano_consentmanager_uuid=4ce48adc-1373-43c0-b623-22767c848deb; osano_consentmanager=HmzXHPixjBaP3bJa50QvEU2A_RW7WHICAaINB8T0Z3dYvh2p6JqF_v_MgCnUdkCFgiopIkn87j3VtdmFYrNAQJGw9WSD0s-OIDafDvAow7c_hiXkG8MjQ1eundlb8UxB-dVSCVNpNP3NAOerfeoLFyNAclBBNrur-mWRH753n9vSfKyjWaBQVoWB3mqgy2lgW9goZdKzdBbRXinsNu1Q3LiR0jeYwz_tvhMqn-ojPpWDkKGPB_TNz-u0tT0BDnlFibTTtkfUz8OIB0cIT7MninJ4dHrh4y-HMcWJQ8UnqBISDZJc3jThWte2iVGGDilgGWGZa_JNnSI; hum_ieee_visitor=0f27e76d-657f-4aca-ac83-831273204d6c; hum_ieee_synced=true; AMCV_8E929CC25A1FB2B30A495C97%40AdobeOrg=0%7CMCMID%7C73254357901763077332194397235230773723; s_ecid=MCMID%7C73254357901763077332194397235230773723; AMCVS_8E929CC25A1FB2B30A495C97%40AdobeOrg=1; s_cc=true; ipList=157.254.20.4; CloudFront-Key-Pair-Id=KBLQQ1K30MUFK; fp=3fc5cc9e6be15aa00878aa2daafbfd01; _zitok=27daafa73fe0dfa1434e1763200433; CloudFront-Policy=eyJTdGF0ZW1lbnQiOiBbeyJSZXNvdXJjZSI6Imh0dHBzOi8vaWVlZXhwbG9yZS5pZWVlLm9yZy9tZWRpYXN0b3JlL0lFRUUvY29udGVudC9tZWRpYS8xMDIxOTU0NC8xMDIxOTU0NS8xMDIyMDAzMS8qIiwiQ29uZGl0aW9uIjp7IkRhdGVMZXNzVGhhbiI6eyJBV1M6RXBvY2hUaW1lIjoxNzYzNjk0ODAzfSwiSXBBZGRyZXNzIjp7IkFXUzpTb3VyY2VJcCI6IjE1Ny4yNTQuMjAuNCJ9fX1dfQ__; CloudFront-Signature=eoQ9YSQovNhjjIn9ZRVY3t2yENtahEhvYYJn0PecUO~fEYIwDYmxtOp1JSopChJFbjDZCmL4PoiTbe3JrgckkmkSZSD~rIh3kJEJgSdk9jYOtXt9yYqwwOW4Vb6Hu2F5XhrrsfINot7dbgNxAYETW2H9UaAIINWN1xzn6YYJj1YbWYYVseZQndeBLFVmSRPqsRbM~g0VZwi50t-oLI0y8MRqBF2mjXQKIaIR2Ydb4rKMdSEtZ5HSUNo~HgmmaNw~gFdeipv2iPNHwd~1buoia2LcRFRg8NpZ9HQMVt1iv8~29S-VJ1c3K~qZsHAUOrddxLeketDYpJiHlSXfAXyphA__; utag_main=v_id:019a86e9e425002277bd32a13c1e0507d003607500bd0$_sn:2$_se:12$_ss:0$_st:1763694860324$vapi_domain:ieeexplore.ieee.org$ses_id:1763692935653%3Bexp-session$_pn:4%3Bexp-session; s_sq=ieeexplore.prod%3D%2526pid%253DXplore%252520Journals%252520%252526%252520Magazines%252520Toc%2526pidt%253D1%2526oid%253Dhttps%25253A%25252F%25252Fieeexplore.ieee.org%25252Frss%25252FTOC36.XML%2526ot%253DA; JSESSIONID=3D527D2506671A5B3350D2B6EC137297; ipCheck=122.224.103.68; kndctr_8E929CC25A1FB2B30A495C97_AdobeOrg_identity=CiY3MzI1NDM1NzkwMTc2MzA3NzMzMjE5NDM5NzIzNTIzMDc3MzcyM1IRCM70p7eoMxgBKgRKUE4zMAPwAZenoNGqMw%3D%3D; AWSALBAPP-0=AAAAAAAAAACEB7f/8ZeXxSSmOOCT0cmQybiUTjhRAfv4ZEOD1uLlKGj5VBZYIBBTbJ11KSvNhDNUD4MkxPzcW6JuOPaKiPQMTloMh5O3XOwzPP6CHD2rvMlXKA9ZZB74wBCifK/rpJY6iyQ; WLSESSION=922907146.47873.0000; TS016349ac=01f15fc87cd00d414f95748e5f0f4d18ee0fa988374d0f1960a572cced24c7420260e5669ebac5924f27a47d9430df27c2f9d08c46; TS8b476361027=0807dc117eab20001904e306310cf3bf86cd3a5819dd4b132d5497e2b4ef8ac0a9acb7ce826d02f508a28ed26f1130008ba7858bc37474e7c8649f4ab79df6a464841ecc11cb22d1b96465219ef7b7319efa1a53d6d01afaa0bdab95f9114483; AWSALBAPP-0=AAAAAAAAAADlhVbNEXbd3eCwM/cdDwN/RsPWFzjfXloyNYWOkb3gpgCM49jtuUFND3Hk8k2wduASW4Ic96QGM6fttP5yMqyHjqzZWYW5Wt8A49BrjZsBx0L1ZipvNgNvRVg/fv0oK6W6q44=; AWSALBAPP-1=_remove_; AWSALBAPP-2=_remove_; AWSALBAPP-3=_remove_; WLSESSION=922907146.47873.0000; TS016349ac=01f15fc87cf82c6035c587bb3f1f60af7adceac3d81b319ddcfc3022e13c043592def417547fe503b14b498fed8111b13d044c38b0',
   'User-Agent': 'Apifox/1.0.0 (https://apifox.com)',
   'Host': 'ieeexplore.ieee.org',
   'Connection': 'keep-alive'
}
def parse_rss(rss_url: str) -> Dict[str, Any]:
    """解析IEEE的RSS源并返回结构化的内容"""
    print(f"开始解析IEEE RSS: {rss_url}")
    feed=[]
    if False:
        # 从实际RSS URL获取数据
        response = requests.get(rss_url, timeout=10)
        print(f"成功获取RSS内容，状态码: {response.status_code}")
        print(f"内容长度: {len(response.content)} 字节")
        feed = feedparser.parse(response.content)
    else:
        with open(r'E:\StudyData\trae\PaperSummarizer\backend\app\services\rss_exm.xml', 'r', encoding='utf-8') as f:
            feed = feedparser.parse(f.read())

    # 解析RSS
    
    print(f"RSS解析完成，条目数量: {len(feed.entries)}")
    
    # 检查解析结果
    if feed.bozo:
        print(f"RSS解析警告: {feed.bozo_exception}")
    
    # 提取本周的论文
    this_week_papers = []
    all_papers=[]
    # 使用带UTC时区的当前时间
    now = datetime.now(timezone.utc)
    one_week_ago = now - timedelta(days=7)
    print(f">>>开始处理本周论文")
    print(f">>>当前时间(UTC): {now}")
    print(f">>>一周前时间(UTC): {one_week_ago}")
    
    # 检查feed.entries是否为空
    if not feed.entries:
        print(">>>警告: 没有找到论文条目")
    else:
        print(f">>>第一篇论文: {feed.entries[0].get('title', 'Untitled')}")
    
    for idx, entry in enumerate(feed.entries):
        print(f">>>处理条目 #{idx+1}: {entry.get('title', 'Untitled')}:{entry.get('published', '#')}")
        pubDate_str = entry.get('published', '')
        published_date = None
        
        if pubDate_str:
            try:
                # 尝试解析带时区的日期
                published_date = datetime.strptime(pubDate_str, '%a, %d %b %Y %H:%M:%S %z')
            except ValueError:
                try:
                    # 尝试解析不带时区的日期
                    published_date = datetime.strptime(pubDate_str, '%a, %d %b %Y %H:%M:%S')
                    # 如果没有时区信息，假设为UTC
                    published_date = published_date.replace(tzinfo=timezone.utc)
                except ValueError:
                    print(f">>>无法解析日期: {pubDate_str}")
                    published_date = None
        
        # 添加到所有论文列表
        all_papers.append({
            'title': entry.get('title', 'Untitled'),
            'link': entry.get('link', '#'),
            'description': entry.get('description', ''),
            'pubDate': pubDate_str,
            'authors': entry.get('authors', '')
        })
        
        print(f">>>published_date: {published_date}")
        
        # 检查是否为本周论文
        if published_date and published_date >= one_week_ago:
            this_week_papers.append({
                'title': entry.get('title', 'Untitled'),
                'link': entry.get('link', '#'),
                'description': entry.get('description', ''),
                'pubDate': pubDate_str,
                'authors': entry.get('authors', '')
            })
            print(">>>✓ 识别为本周论文")
        else:
            print(">>>✗ 不是本周论文")
    print(f">>>总论文数量: {len(all_papers)}")
    print(f">>>过滤后论文数量: {len(this_week_papers)}")
    #print(this_week_papers[0])
    # 构建返回结果
    
    print(f">>>RSS解析成功完成")
    #按时间排序
    all_papers.sort(key=lambda x: datetime.strptime(x['pubDate'], '%a, %d %b %Y %H:%M:%S %z'), reverse=True)
    this_week_papers.sort(key=lambda x: datetime.strptime(x['pubDate'], '%a, %d %b %Y %H:%M:%S %z'), reverse=True)
    return all_papers,this_week_papers
 

if __name__ == '__main__':
    # 测试RSS源
    rss_url = 'https://ieeexplore.ieee.org/rss/TOC36.XML'
    result = parse_rss(rss_url)
    # print(result)
