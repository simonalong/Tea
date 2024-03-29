package com.simon.tea;

import static com.simon.tea.Constant.DEFAULT_CMD;
import static com.simon.tea.Constant.SYS_MODULE;
import static com.simon.tea.Print.*;

import com.simon.tea.annotation.Module;
import com.simon.tea.context.Context;
import com.simon.tea.meta.CfgPath;
import com.simon.tea.util.FileUtil;
import com.simon.tea.util.MapUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * 命令分析和执行管理器
 *
 * @author zhouzhenyong
 * @since 2018/6/25 下午10:05
 */
@RequiredArgsConstructor(staticName = "of")
public class CfgManager extends SystemManager{

    @NonNull
    private Context context;
    //key 是目录， value的key 是具体的命令如：show/find
    private Map<String, Map<String, CmdHandler>> moduleCmdMap = new HashMap<>();
    //key 是目录，value 是每个目录模块中的默认命令
    private Map<String, CmdHandler> moduleDefaultCmdMap = new HashMap<>();
    //key 是目录，value是对应目录下的配置集合
    private Map<String, List<CfgPath>> configMap = new HashMap<>();

    void analyse() {
        Optional.ofNullable(getCurrentHandler()).map(h -> {//寻找匹配的命令
            context.setTakeTime();
            h.handle(context);
            return "";
        }).orElseGet(() -> Optional.ofNullable(getDefaultHandler()).map(h -> {//用默认命令
            context.setTakeTime();
            h.handle(context);
            return "";
        }).orElseGet(() -> {
            showCmdError(context.getInput());
            return null;
        }));
    }

    private CmdHandler getCurrentHandler(){
        return handler(context.getCmdHandlerMap().get(context.firstWord()));
    }

    private CmdHandler getDefaultHandler(){
        return handler(moduleDefaultCmdMap.get(context.getCurrentCatalog()));
    }

    private CmdHandler handler(CmdHandler handler){
        if(null != handler && handler.getCmdEntity().getActive()){
            return handler;
        }
        return null;
    }

    public void loadCmd() {
        context.getCmdHandlerMap().putAll(moduleCmdMap.get(context.getCurrentCatalog()));
    }

    public void unloadCmd() {
        MapUtil.removeAll(context.getCmdHandlerMap(), moduleCmdMap.get(context.getCurrentCatalog()));
    }

    public List<String> getCfgList(String catalog) {
        return configMap.get(catalog).stream().map(CfgPath::getName).collect(Collectors.toList());
    }

    public List<String> getCfgList() {
        return configMap.get(context.getCurrentCatalog()).stream().map(CfgPath::getName).collect(Collectors.toList());
    }

    public void rmv(String fileName){
        val cfgList = configMap.get(context.getCurrentCatalog());
        val cowList = new CopyOnWriteArrayList<CfgPath>(configMap.get(context.getCurrentCatalog()));
        cowList.forEach(c->{
            if(c.getName().equals(fileName)){
                cfgList.remove(c);
            }
        });
    }

    public void rename(String oldName, String newName){
        val cfgList = configMap.get(context.getCurrentCatalog());
        if(!cfgList.isEmpty()){
            cfgList.forEach(c->{
                if(c.getName().equals(oldName)){
                    c.setName(newName);
                }
            });
        }
    }

    public boolean isModule(String module) {
        return getCfgList().contains(module);
    }

    public void addNewCfg(String fileName) {
        configMap.computeIfPresent(context.getCurrentCatalog(), (key, value) -> {
            value.add(CfgPath.builder().name(fileName).path(context.getCurrentPath() + "/" + fileName).build());
            return value;
        });
    }

    public String getFilePath(String fileName) {
        return configMap.get(context.getCurrentCatalog()).stream().filter(cfgPath -> cfgPath.getName().equals(fileName))
            .findFirst().map(CfgPath::getPath).orElse("");
    }

    void addModule(Module module, Map<String, CmdHandler> cmdMap) {
        if (module.name().equals(SYS_MODULE)) {
            moduleCmdMap.putIfAbsent(module.name(), cmdMap);
            loadCmd();
        } else {
            moduleCmdMap.putIfAbsent(context.appendCatalog(module.name()), cmdMap);
            loadSysCfg(module.name());
            loadModuleCfg(module.name());

            //读入默认配置命令
            Optional.ofNullable(cmdMap.get(DEFAULT_CMD))
                .map(cmd -> moduleDefaultCmdMap.putIfAbsent(context.appendCatalog(module.name()), cmd));
        }
    }

    /**
     * 装载系统配置
     *
     * @param moduleName 模块名字
     */
    private void loadSysCfg(String moduleName) {
        configMap.compute(SYS_MODULE, (key, value) -> {
            if (value == null) {
                List<CfgPath> list = new ArrayList<>();
                list.add(CfgPath.builder().name(moduleName).build());
                return list;
            } else {
                value.add(CfgPath.builder().name(moduleName).build());
            }
            return value;
        });
    }

    /**
     * 装载模块配置
     *
     * @param moduleName 模块名字
     */
    private void loadModuleCfg(String moduleName) {
        try {
            //创建每个模块对应的文件夹方便存储配置数据
            if (!FileUtil.fileExist(Constant.MODULE_PATH + "/" + moduleName + "/.")) {
                FileUtil.createFile(Constant.MODULE_PATH + "/" + moduleName + "/.");
            } else {
                List<String> cfgList = FileUtil.readListFromPath(Constant.MODULE_PATH + "/" + moduleName);
                String moduleCatalog = context.appendCatalog(moduleName);
                configMap.compute(moduleCatalog, (key, value) -> {
                    if (null == value) {
                        return cfgList.stream().map(cfg -> CfgPath.builder().name(cfg)
                            .path(Constant.MODULE_PATH + "/" + moduleName + "/" + cfg).build())
                            .collect(Collectors.toList());
                    } else {
                        showError("模块" + key + "已经存在");
                        return Collections.emptyList();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
