package com.zxk.gmall.realtime.util;

import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class KeywordUtil {

    public static List<String> splitKeyWord(String keyword) throws IOException {

        //存放结果
        ArrayList<String> result = new ArrayList<>();

        //创建分词器对象
        StringReader reader = new StringReader(keyword);
        IKSegmenter ikSegmenter = new IKSegmenter(reader, false);

        //提取分词
        Lexeme next = ikSegmenter.next();

        while (next != null) {
            String word = next.getLexemeText();
            result.add(word);

            next = ikSegmenter.next();
        }

        return result;
    }

    public static void main(String[] args) throws IOException {
        System.out.println(splitKeyWord("尚硅谷大数据Flink实时数仓项目"));
    }
}
