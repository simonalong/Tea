package com.simon.tea;

import static com.simon.tea.Print.*;

import com.simon.tea.context.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author zhouzhenyong
 * @since 2018/6/25 下午5:26
 */
public class Screen {
    private Context context = new Context();
    private Parser parser;

    void start() {
        try {
            while (!context.getStop()){
                showCatalog();
                context.setInput(new BufferedReader(new InputStreamReader(System.in)).readLine());
                parser.process();
            }
        }catch (Exception e){
            context.setStop(true);
            e.printStackTrace();
        }
    }

    private void showCatalog() {
        showCyan(context.getCatalog());
        show("> ");
    }

    Screen() {
        parser = new Parser(context);
    }
}
