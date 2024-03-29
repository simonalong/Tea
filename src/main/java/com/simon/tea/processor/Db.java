package com.simon.tea.processor;

import static com.simon.tea.Constant.DEFAULT_CMD;
import static com.simon.tea.Print.*;

import com.simon.tea.Constant;
import com.simon.tea.DBManager;
import com.simon.tea.Print;
import com.simon.tea.annotation.Cmd;
import com.simon.tea.annotation.Module;
import com.simon.tea.annotation.Usage;
import com.simon.tea.context.Context;
import com.simon.tea.meta.CmdTypeEnum;
import com.simon.tea.util.StringUtil;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import lombok.val;
import me.zzp.am.Record;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.util.StringUtils;

/**
 * @author zhouzhenyong
 * @since 2018/6/25 下午4:21
 */
@Module(name = "db")
public class Db {
    @Cmd(value = "show", describe = "展示表的数据：show tableNam，详情，请用命令: usage show", active = false)
    public void showTableData(Context context){
        String secondWord = context.secondWord();
        if (StringUtils.hasText(secondWord)) {
            DBManager db = context.getDbManager();
            if(secondWord.equals(Constant.TABLES)){
                showList(db.listTables());
            } else if(secondWord.equals(Constant.INDEX)){
                val thirdWord = context.thirdWord();
                if(thirdWord.equals(Constant.FROM)){    //系统默认的index
                    db.setTableName(context.fourWord());
                    context.setInput(context.getInput().replaceAll(" from ", " ")); //去除 from
                }else {
                    db.setTableName(context.thirdWord());
                }
                showIndexTable(db.getIndex(), context);
            } else if(secondWord.equals(Constant.STRUCT)){
                db.setTableName(context.thirdWord());
                showColumnTable(db.listColumns(), context);
            }else if(db.containTable(secondWord)){
                printTable(context, secondWord);
            } else{
                showError("表: "+ secondWord + " 不存在");
            }
        }
    }

    @Usage(target = "show")
    public Record usageOfShow(){
        return Record.of(
            "show tableName", "展示第一页，每页"+ Print.PAGE_SIZE+"行数据",
            "show tableNames ", "展示当前库中的所有表，每页"+ Print.PAGE_SIZE+"行数据",
            "show tableName -p num", "展示第num页的数据，每页"+ Print.PAGE_SIZE+"行数据",
            "show tableName -lm 1,200", "展示前1~200条数据，每页"+ Print.PAGE_SIZE+"行数据",
            "show index tableName", "显示表的索引",
            "show struct tableName", "展示表的结构数据"
        );
    }

    private void printTable(Context context, String tableName){
        int pageIndex = 1, startIndex, showSize, totalCnd, showStartIndex = 0, pageBeforeNum;
        String otherMsg = context.getInput().substring("show ".length() + tableName.length());
        String sql = "select * from " + tableName;
        String showSql = sql;

        DBManager db = context.getDbManager();
        if (!StringUtils.isEmpty(otherMsg)) {//解析 show tableName
            pageIndex = context.getPageIndex();
            startIndex = getRangeStart(otherMsg);
            showSize = getRangeSize(otherMsg);
            pageBeforeNum = (pageIndex == 0 ? 0 : pageIndex - 1) * PAGE_SIZE;

            showStartIndex = startIndex + pageBeforeNum;
            if(0 != showSize){//有 -lm a,b 形式
                if(showSize < pageBeforeNum){
                    showError("页签索引过界：总页数为："+(showSize/PAGE_SIZE + 1)+", 页签为："+pageIndex);
                    return;
                }
                showSql += " limit " + showStartIndex + "," + Math.min(showSize - pageBeforeNum, PAGE_SIZE);
                totalCnd = showSize;
            }else{ //有 -p pageNum 形式
                showSql += " limit " + showStartIndex + "," + PAGE_SIZE;
                totalCnd = db.count(sql);
            }
        }else{
            showSql = sql + " limit 0," + PAGE_SIZE;
            totalCnd = db.count(sql);
        }
        showTable(db.all(showSql), totalCnd, pageIndex, showStartIndex, context);
    }

    /**
     *  获取range 字符串
     * @param otherMsg  后面的配置信息
     * @return a,b      这种：a和b都是数字
     */
    @Cacheable(cacheNames = "getRangeStr")
    public String getRangeStr(String otherMsg){
        if(otherMsg.contains(" -lm ")) {
            int index = otherMsg.indexOf(" -lm ");
            String endStr = otherMsg.substring(index + " -lm ".length());
            int endIndex = endStr.indexOf(" ");
            String rangeStr;
            if (endIndex != -1) {
                return endStr.substring(0, endIndex);
            } else {
                return endStr;
            }
        }
        return null;
    }

    /**
     * 获取 -lm 后面的起始位置
     * @param otherMsg 后面的配置信息
     * @return 0 没有 -lm a,b 这种格式，或者语法不正确
     */
    private int getRangeStart(String otherMsg){
        String rangeStr = getRangeStr(otherMsg);
        if(null != rangeStr){
            int spotIndex = rangeStr.indexOf(',');
            if(spotIndex != -1){
                return Integer.valueOf(rangeStr.substring(0, spotIndex));
            }else{
                showError("语法有错误: -lm 后面跟的是两个逗号隔开的数字，如: -lm 2,10");
                return 0;
            }
        }
        return 0;
    }

    /**
     * 获取 -lm 后面的显示数据的大小
     * @param otherMsg 后面的配置信息
     * @return 0 没有 -lm a,b 这种格式，或者语法不正确
     */
    private int getRangeSize(String otherMsg){
        String rangeStr = getRangeStr(otherMsg);
        if(null != rangeStr) {
            int spotIndex = rangeStr.indexOf(',');
            if(spotIndex != -1){
                return Integer.valueOf(rangeStr.substring(spotIndex + 1));
            }else{
                showError("语法有错误: -lm 后面跟的是两个逗号隔开的数字，如: -lm 2,10");
                return 0;
            }
        }
        return 0;
    }



    @Cmd(value = "load", describe = "载入配置", type = CmdTypeEnum.ACTIVITY)
    public void load(Context context){
        try {
            String configName = context.secondWord();
            if (StringUtils.hasText(configName)) {
                Properties properties = new Properties();
                properties.load(new FileInputStream(context.appendPath(configName)));

                context.getDbManager().startDb(
                    StringUtil.valueOf(properties.get("jdbcUrl")),
                    StringUtil.valueOf(properties.get("username")),
                    StringUtil.valueOf(properties.get("password")));
                context.addShowCatalog(configName);
            }
        } catch (IOException e) {
            showError("文件没有找到");
        }
    }

    /**
     * DB 的默认命令函数，没有其他命令匹配，则就会走这个处理
     */
    @Cmd(value = DEFAULT_CMD, describe = "db 里面的默认命令", active = false)
    public void defaultCmd(Context context){
        context.getDbManager().execute();
    }
}
