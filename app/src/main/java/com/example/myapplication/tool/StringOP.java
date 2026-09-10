package com.example.myapplication.tool;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringOP {
    public static Date String2Date(String stringDate){
        Date date = null;
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            date = dateFormat.parse(stringDate);
        } catch (ParseException e) {
            e.printStackTrace(); // Handle the exception according to your needs
        }
        return date;
    }
    public static ArrayList<Integer> reFindPlace(String sourceS, String regoalS) {
        ArrayList<Integer> P = new ArrayList<Integer>();
        Pattern boldPattern = Pattern.compile(regoalS);//匹配被两个星号"**"围起来的内容
        Matcher m = boldPattern.matcher(sourceS);
        while (m.find())P.add(m.start());
        return P;
    }

    public static int indexNum(ArrayList<Integer> arra,int goalNum){
        int i=0,j=arra.size(),m=(i+j)/2;
        if(j==0)return -1;
        for(;i<j;){
            if(arra.get(m)>goalNum)j=m-1;
            else i=m+1;
            m=(i+j)/2;
        }
//        System.out.println("m>>>"+m+">>>size>>>"+arra.size());
        if(m>=arra.size())m--;
        if(arra.get(m) >goalNum)m--;
//
        return m;
    }

//获取括号内容
    public static ArrayList<String> reGetMid(String sourceS, String regoalSL, String regoalSR){
        ArrayList<Integer> PL,PR;
        PL=reFindPlace(sourceS,regoalSL);
        PR=reFindPlace(sourceS,regoalSR);
//        System.out.println("PL>>>"+PL);
//        System.out.println("PR>>>"+PR);
//        System.out.println("find>>>"+indexNum(PL,6));

        int LR=regoalSR.length();
        int l,r;
        ArrayList<String>S=new ArrayList<String>();
        for(r=0; PR.size()>r; r++){
            l=indexNum(PL,PR.get(r));
//            if(l==-2)l=PL.size()-1;
//            System.out.println((l)+"|"+(r));
            if(l==-1)continue;


            if(l>=0){S.add(sourceS.substring(PL.get(l),PR.get(r)+LR));
                PL.remove(l);
            }
//            System.out.println("PL>>>"+PL);
//            System.out.println("PR>>>"+PR);

        }
        return S;
    }

    //_aa_bb_cc_->aa,bb,cc
    //00b包含开头与结尾
    public static  ArrayList<String> reGetMid2(String sourceS, String regoalS, int op){
    ArrayList<Integer>P=reFindPlace(sourceS,regoalS);
        ArrayList<String>S=new ArrayList<>();
        if((op&1)!=0)S.add(sourceS.substring(0,P.get(0)));
        int i = 0;
        for (; i < P.size()-1; i++) {
            S.add(sourceS.substring(P.get(i),P.get(i+1)));
        }
        if((op&2)!=0)S.add(sourceS.substring(P.get(i)));

        return S;
    }
    public static ArrayList<String> splitHtmlTagsContent(String text) {
        ArrayList<String> tagsContent = new ArrayList<>();

        // 使用正则表达式匹配 HTML 标签及其内容
        Pattern pattern = Pattern.compile("<[^>]+>([^<]*)</[^>]+>");
        Matcher matcher = pattern.matcher(text);

        // 将匹配到的 HTML 标签内容添加到列表中
        while (matcher.find()) {
            // 获取标签内容
            String content = matcher.group(1).trim();

            // 添加到列表中
            tagsContent.add(content);
        }
        for(int i=0;i<tagsContent.size();i++)if(tagsContent.get(i)==null|| Objects.equals(tagsContent.get(i), "")){
            tagsContent.remove(i);i--;
        }
        return  tagsContent;
    }

    public static void main(String[] args) {
        ArrayList<Integer> P;

//        P=reFindPlace("abcdabcaabc","z");
//        System.out.println("P>>>"+indexNum(P,4));
//        System.out.println(reGetMid("【【【】】【】】】【】","【","】"));

//        System.out.println(reGetMid2(">>>aa>>>bb>>>cc",">>>",3));

        String text="<div class=\"per-phone\" data-v-39fab836><span data-v-39fab836>英</span><span class=\"phonetic\" data-v-39fab836>/ həˈləʊ /</span><div class=\"phraseSpeech phonetic-speech\" data-v-9a937c9a data-v-39fab836><a title=\"点击发音\" href=\"javascript:;\" class=\"pronounce\" data-v-9a937c9a></a></div></div>, <div class=\"per-phone\" data-v-39fab836><span data-v-39fab836>美</span><span class=\"phonetic\" data-v-39fab836>/ həˈloʊ /</span><div class=\"phraseSpeech phonetic-speech\" data-v-9a937c9a data-v-39fab836><a title=\"点击发音\" href=\"javascript:;\" class=\"pronounce\" data-v-9a937c9a></a></div></div>";
        List<String> rs=splitHtmlTagsContent(text);
        System.out.println(rs);
        for(int i=0;i<rs.size();i++)if(rs.get(i)==null|| Objects.equals(rs.get(i), "")){
            rs.remove(i);i--;
        }
        System.out.println(rs);
    }
}
