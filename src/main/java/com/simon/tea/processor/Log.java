package com.simon.tea.processor;

import com.simon.tea.Processor;
import com.simon.tea.annotation.Module;
import com.simon.tea.context.Context;

/**
 * @author zhouzhenyong
 * @since 2018/6/26 下午5:52
 */
@Module(name = "log")
public class Log implements Processor {

    @Override
    public void process(Context context) {

    }

    @Override
    public boolean isCmd(Context context) {
        return false;
    }
}