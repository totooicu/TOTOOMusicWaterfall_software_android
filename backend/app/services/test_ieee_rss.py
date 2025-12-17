
import requests
import feedparser   
url = "https://ieeexplore.ieee.org/rss/TOC36.XML"

payload={}
proxy = {
    'http': 'http://localhost:10800',
    'https': 'http://localhost:10800'
}

headers = {
   'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
   'Cookie': 'AWSALBAPP-1=_remove_; AWSALBAPP-2=_remove_; AWSALBAPP-3=_remove_; osano_consentmanager_uuid=4ce48adc-1373-43c0-b623-22767c848deb; osano_consentmanager=HmzXHPixjBaP3bJa50QvEU2A_RW7WHICAaINB8T0Z3dYvh2p6JqF_v_MgCnUdkCFgiopIkn87j3VtdmFYrNAQJGw9WSD0s-OIDafDvAow7c_hiXkG8MjQ1eundlb8UxB-dVSCVNpNP3NAOerfeoLFyNAclBBNrur-mWRH753n9vSfKyjWaBQVoWB3mqgy2lgW9goZdKzdBbRXinsNu1Q3LiR0jeYwz_tvhMqn-ojPpWDkKGPB_TNz-u0tT0BDnlFibTTtkfUz8OIB0cIT7MninJ4dHrh4y-HMcWJQ8UnqBISDZJc3jThWte2iVGGDilgGWGZa_JNnSI; hum_ieee_visitor=0f27e76d-657f-4aca-ac83-831273204d6c; hum_ieee_synced=true; AMCV_8E929CC25A1FB2B30A495C97%40AdobeOrg=0%7CMCMID%7C73254357901763077332194397235230773723; s_ecid=MCMID%7C73254357901763077332194397235230773723; AMCVS_8E929CC25A1FB2B30A495C97%40AdobeOrg=1; s_cc=true; ipList=157.254.20.4; CloudFront-Key-Pair-Id=KBLQQ1K30MUFK; fp=3fc5cc9e6be15aa00878aa2daafbfd01; _zitok=27daafa73fe0dfa1434e1763200433; CloudFront-Policy=eyJTdGF0ZW1lbnQiOiBbeyJSZXNvdXJjZSI6Imh0dHBzOi8vaWVlZXhwbG9yZS5pZWVlLm9yZy9tZWRpYXN0b3JlL0lFRUUvY29udGVudC9tZWRpYS8xMDIxOTU0NC8xMDIxOTU0NS8xMDIyMDAzMS8qIiwiQ29uZGl0aW9uIjp7IkRhdGVMZXNzVGhhbiI6eyJBV1M6RXBvY2hUaW1lIjoxNzYzNjk0ODAzfSwiSXBBZGRyZXNzIjp7IkFXUzpTb3VyY2VJcCI6IjE1Ny4yNTQuMjAuNCJ9fX1dfQ__; CloudFront-Signature=eoQ9YSQovNhjjIn9ZRVY3t2yENtahEhvYYJn0PecUO~fEYIwDYmxtOp1JSopChJFbjDZCmL4PoiTbe3JrgckkmkSZSD~rIh3kJEJgSdk9jYOtXt9yYqwwOW4Vb6Hu2F5XhrrsfINot7dbgNxAYETW2H9UaAIINWN1xzn6YYJj1YbWYYVseZQndeBLFVmSRPqsRbM~g0VZwi50t-oLI0y8MRqBF2mjXQKIaIR2Ydb4rKMdSEtZ5HSUNo~HgmmaNw~gFdeipv2iPNHwd~1buoia2LcRFRg8NpZ9HQMVt1iv8~29S-VJ1c3K~qZsHAUOrddxLeketDYpJiHlSXfAXyphA__; utag_main=v_id:019a86e9e425002277bd32a13c1e0507d003607500bd0$_sn:2$_se:12$_ss:0$_st:1763694860324$vapi_domain:ieeexplore.ieee.org$ses_id:1763692935653%3Bexp-session$_pn:4%3Bexp-session; s_sq=ieeexplore.prod%3D%2526pid%253DXplore%252520Journals%252520%252526%252520Magazines%252520Toc%2526pidt%253D1%2526oid%253Dhttps%25253A%25252F%25252Fieeexplore.ieee.org%25252Frss%25252FTOC36.XML%2526ot%253DA; JSESSIONID=3D527D2506671A5B3350D2B6EC137297; ipCheck=122.224.103.68; kndctr_8E929CC25A1FB2B30A495C97_AdobeOrg_identity=CiY3MzI1NDM1NzkwMTc2MzA3NzMzMjE5NDM5NzIzNTIzMDc3MzcyM1IRCM70p7eoMxgBKgRKUE4zMAPwAZenoNGqMw%3D%3D; AWSALBAPP-0=AAAAAAAAAACEB7f/8ZeXxSSmOOCT0cmQybiUTjhRAfv4ZEOD1uLlKGj5VBZYIBBTbJ11KSvNhDNUD4MkxPzcW6JuOPaKiPQMTloMh5O3XOwzPP6CHD2rvMlXKA9ZZB74wBCifK/rpJY6iyQ; WLSESSION=922907146.47873.0000; TS016349ac=01f15fc87cd00d414f95748e5f0f4d18ee0fa988374d0f1960a572cced24c7420260e5669ebac5924f27a47d9430df27c2f9d08c46; TS8b476361027=0807dc117eab20001904e306310cf3bf86cd3a5819dd4b132d5497e2b4ef8ac0a9acb7ce826d02f508a28ed26f1130008ba7858bc37474e7c8649f4ab79df6a464841ecc11cb22d1b96465219ef7b7319efa1a53d6d01afaa0bdab95f9114483; AWSALBAPP-0=AAAAAAAAAADfiWpyc/oxcJLaaGVpzRamtcoy4jB5J6JK3tcTICUrrrdo8zEH/KlAPXdXHm9axLMnnVT++Z4cVMWBvGv/XVP4UOrytBVr5twxuKNFXuB0WJhk5N5q0qUCsNftUQaOipdqCfE=; AWSALBAPP-1=_remove_; AWSALBAPP-2=_remove_; AWSALBAPP-3=_remove_; WLSESSION=922907146.47873.0000; TS016349ac=01f15fc87c4c3f32035c21a1061637f19f8ec6687642bdbd9a80d0ac3724e5da322fd7c6f9c07c04fc18007789de168a9269fff79d',
   'User-Agent': 'Apifox/1.0.0 (https://apifox.com)',
   'Host': 'ieeexplore.ieee.org',
   'Connection': 'keep-alive'
}

response = requests.request("GET", url, headers=headers,verify=False, data=payload,proxies=proxy)
feed = feedparser.parse(response.content)
print(feed.entries[0]['title'])