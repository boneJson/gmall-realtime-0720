package com.zxk.gmall.realtime.app.func;

import com.zxk.gmall.realtime.util.KeywordUtil;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;

import java.io.IOException;
import java.util.List;


@FunctionHint(output = @DataTypeHint("ROW<word STRING>"))
public class SplitFunction extends TableFunction<Row> {
    public void eval(String keyword) {

        List<String> list = null;

        try {
            list = KeywordUtil.splitKeyWord(keyword);
            for (String word : list) {
                collect(Row.of(word));
            }
        } catch (IOException e){
            //异常处理逻辑为,切词失败不抛,选择处理:返回原词
            collect(Row.of(keyword));
        }
    }
}
